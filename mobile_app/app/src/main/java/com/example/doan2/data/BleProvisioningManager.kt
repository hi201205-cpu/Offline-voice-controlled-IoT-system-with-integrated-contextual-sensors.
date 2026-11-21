package com.example.doan2.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult as WifiScanResult
import android.bluetooth.le.ScanResult as BleScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class BleStatus {
    IDLE,
    SCANNING,
    DEVICE_FOUND,
    CONNECTING_GATT,
    CONNECTED_GATT,
    SENDING_CONFIG,
    ESP_CONNECTING_WIFI,
    SUCCESS,
    FAILED,
    ERROR
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

data class WifiNetworkItem(
    val ssid: String,
    val rssi: Int,
    val is24GHz: Boolean,
    val isConnected: Boolean = false
)

@SuppressLint("MissingPermission")
class BleProvisioningManager(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHAR_CONFIG_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val CHAR_STATUS_UUID: UUID = UUID.fromString("1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val _status = MutableStateFlow(BleStatus.IDLE)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Sẵn sàng")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _scannedWifiNetworks = MutableStateFlow<List<WifiNetworkItem>>(emptyList())
    val scannedWifiNetworks: StateFlow<List<WifiNetworkItem>> = _scannedWifiNetworks.asStateFlow()

    private val _isScanningWifi = MutableStateFlow(false)
    val isScanningWifi: StateFlow<Boolean> = _isScanningWifi.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPayload: String = ""

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun scanWifiNetworks() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        _isScanningWifi.value = true

        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: Exception) {}

        mainHandler.postDelayed({
            try {
                val currentSsid = getConnectedWifiSsid(context)
                @Suppress("DEPRECATION")
                val results: List<WifiScanResult> = wifiManager.scanResults ?: emptyList()

                val items = results
                    .filter { !it.SSID.isNullOrBlank() }
                    .distinctBy { it.SSID.trim().removeSurrounding("\"") }
                    .map { res ->
                        val cleanSsid = res.SSID.trim().removeSurrounding("\"")
                        val is24 = res.frequency in 2400..2500
                        WifiNetworkItem(
                            ssid = cleanSsid,
                            rssi = res.level,
                            is24GHz = is24,
                            isConnected = cleanSsid.equals(currentSsid, ignoreCase = true)
                        )
                    }
                    .sortedWith(
                        compareByDescending<WifiNetworkItem> { it.isConnected }
                            .thenByDescending { it.is24GHz }
                            .thenByDescending { it.rssi }
                    )

                if (items.isNotEmpty()) {
                    _scannedWifiNetworks.value = items
                } else if (currentSsid.isNotBlank()) {
                    _scannedWifiNetworks.value = listOf(
                        WifiNetworkItem(ssid = currentSsid, rssi = -50, is24GHz = true, isConnected = true)
                    )
                }
            } catch (e: Exception) {
                val currentSsid = getConnectedWifiSsid(context)
                if (currentSsid.isNotBlank()) {
                    _scannedWifiNetworks.value = listOf(
                        WifiNetworkItem(ssid = currentSsid, rssi = -50, is24GHz = true, isConnected = true)
                    )
                }
            } finally {
                _isScanningWifi.value = false
            }
        }, 600)
    }

    fun startScan(timeoutMs: Long = 15000) {
        if (!isBluetoothEnabled()) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Bluetooth chưa được bật trên điện thoại"
            return
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Không tìm thấy bộ quét BLE"
            return
        }

        _discoveredDevices.value = emptyList()
        _status.value = BleStatus.SCANNING
        _statusMessage.value = "Đang quét tìm thiết bị xung quanh..."

        val scanFilter = ScanFilter.Builder().build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        } catch (e: Exception) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Lỗi khi quét BLE: ${e.localizedMessage}"
            return
        }

        // Tự động dừng quét sau timeout
        mainHandler.postDelayed({
            stopScan()
            if (_discoveredDevices.value.isEmpty() && _status.value == BleStatus.SCANNING) {
                _status.value = BleStatus.IDLE
                _statusMessage.value = "Không tìm thấy thiết bị nào ở gần. Hãy bật chế độ Setup trên thiết bị."
            }
        }, timeoutMs)
    }

    fun stopScan() {
        if (_status.value == BleStatus.SCANNING) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (_: Exception) {}
            if (_discoveredDevices.value.isEmpty()) {
                _status.value = BleStatus.IDLE
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: BleScanResult?) {
            val device = result?.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: ""

            // Lọc các thiết bị có tên SmartHome hoặc ESP32
            if (name.contains("SmartHome", ignoreCase = true) || name.contains("ESP32", ignoreCase = true)) {
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.address == device.address }) {
                    current.add(DiscoveredBleDevice(name, device.address, device))
                    _discoveredDevices.value = current
                    _status.value = BleStatus.DEVICE_FOUND
                    _statusMessage.value = "Tìm thấy thiết bị: $name"
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Quét BLE thất bại (Mã lỗi: $errorCode)"
        }
    }

    fun connectAndSendWifi(
        device: BluetoothDevice,
        ssid: String,
        pass: String,
        homeId: String = "HOME-2026-ESP32",
        ownerEmail: String = "",
        ownerUid: String = ""
    ) {
        stopScan()
        val json = org.json.JSONObject().apply {
            put("ssid", ssid)
            put("pass", pass)
            put("home", homeId)
            if (ownerEmail.isNotBlank()) put("owner", ownerEmail)
            if (ownerUid.isNotBlank()) put("uid", ownerUid)
        }
        pendingPayload = json.toString()

        _status.value = BleStatus.CONNECTING_GATT
        _statusMessage.value = "Đang kết nối Bluetooth tới thiết bị..."

        try {
            disconnect()
            mainHandler.postDelayed({
                try {
                    bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        device.connectGatt(context, false, gattCallback)
                    }
                } catch (e: Exception) {
                    _status.value = BleStatus.ERROR
                    _statusMessage.value = "Lỗi kết nối Bluetooth: ${e.localizedMessage}"
                }
            }, 200)
        } catch (e: Exception) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Lỗi kết nối GATT: ${e.localizedMessage}"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                _status.value = BleStatus.CONNECTED_GATT
                _statusMessage.value = "Đã kết nối! Đang tối ưu gói tin..."
                // Yêu cầu MTU lớn (512 bytes) để gửi toàn bộ JSON cấu hình trong 1 lần
                val requested = gatt?.requestMtu(512) ?: false
                if (!requested) {
                    gatt?.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (_status.value != BleStatus.SUCCESS) {
                    _status.value = BleStatus.FAILED
                    _statusMessage.value = "Mất kết nối Bluetooth. Vui lòng thử lại hoặc bấm giữ nút trên thiết bị 5s."
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                _status.value = BleStatus.FAILED
                _statusMessage.value = "Lỗi kết nối Bluetooth (Mã: $status). Hãy thử lại."
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            // MTU đã được thương lượng thành công, bắt đầu khám phá dịch vụ
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    // Đăng ký nhận notify từ Status characteristic
                    val statusChar = service.getCharacteristic(CHAR_STATUS_UUID)
                    if (statusChar != null) {
                        gatt.setCharacteristicNotification(statusChar, true)
                        val descriptor = statusChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                            return // Chờ onDescriptorWrite rồi mới gửi dữ liệu
                        }
                    }

                    // Nếu không có descriptor, gửi cấu hình ngay
                    sendPendingConfig(gatt)
                } else {
                    _status.value = BleStatus.ERROR
                    _statusMessage.value = "Không tìm thấy Service SmartHome trên thiết bị"
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            // Đã đăng ký notify thành công, giờ gửi cấu hình Wi-Fi
            sendPendingConfig(gatt)
        }

        private fun sendPendingConfig(gatt: BluetoothGatt?) {
            if (gatt == null || pendingPayload.isEmpty()) return
            val service = gatt.getService(SERVICE_UUID) ?: return
            val configChar = service.getCharacteristic(CHAR_CONFIG_UUID) ?: return

            mainHandler.post {
                _status.value = BleStatus.SENDING_CONFIG
                _statusMessage.value = "Đang gửi thông tin Wi-Fi sang thiết bị..."

                val bytes = pendingPayload.toByteArray(Charsets.UTF_8)
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(configChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    configChar.value = bytes
                    @Suppress("DEPRECATION")
                    configChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(configChar)
                }

                if (!success) {
                    _status.value = BleStatus.FAILED
                    _statusMessage.value = "Lỗi khi gửi gói tin sang thiết bị"
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _status.value = BleStatus.ESP_CONNECTING_WIFI
                _statusMessage.value = "Đã gửi Wi-Fi! Đang chờ thiết bị kiểm tra kết nối..."
            } else {
                _status.value = BleStatus.FAILED
                _statusMessage.value = "Thiết bị từ chối gói tin cấu hình Wi-Fi"
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == CHAR_STATUS_UUID) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    val code = value[0].toInt()
                    when (code) {
                        4 -> { // BLE_PROV_CONNECTING_WIFI
                            _status.value = BleStatus.ESP_CONNECTING_WIFI
                            _statusMessage.value = "Thiết bị đang kết nối vào Wi-Fi..."
                        }
                        5 -> { // BLE_PROV_SUCCESS
                            _status.value = BleStatus.SUCCESS
                            _statusMessage.value = "Cài đặt Wi-Fi thành công! Thiết bị đã trực tuyến."
                            mainHandler.postDelayed({ disconnect() }, 2000)
                        }
                        6 -> { // BLE_PROV_FAILED
                            _status.value = BleStatus.FAILED
                            _statusMessage.value = "Kết nối Wi-Fi thất bại! Sai mật khẩu hoặc SSID không tồn tại."
                        }
                    }
                }
            }
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (_: Exception) {}
    }
}

@Suppress("DEPRECATION")
fun getConnectedWifiSsid(context: Context): String {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connManager?.activeNetwork
            val caps = connManager?.getNetworkCapabilities(network)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val wifiInfo = caps.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid != "0x") {
                    return ssid.trim()
                }
            }
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = wifiManager?.connectionInfo
        if (info != null && info.networkId != -1) {
            val ssid = info.ssid?.removeSurrounding("\"")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid != "0x") {
                return ssid.trim()
            }
        }
    } catch (_: Exception) {}
    return ""
}
