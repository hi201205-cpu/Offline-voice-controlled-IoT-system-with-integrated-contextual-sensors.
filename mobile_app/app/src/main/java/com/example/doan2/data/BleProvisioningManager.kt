package com.example.doan2.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPayload: String = ""

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun startScan(timeoutMs: Long = 10000) {
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
        _statusMessage.value = "Đang quét tìm thiết bị SmartHome xung quanh..."

        val scanFilter = ScanFilter.Builder()
            .build()

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
                _statusMessage.value = "Không tìm thấy thiết bị nào ở gần. Hãy đảm bảo ESP32 đang bật chế độ Setup."
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
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
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
        _statusMessage.value = "Đang kết nối Bluetooth tới ${device.name ?: device.address}..."

        try {
            bluetoothGatt?.close()
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Lỗi kết nối GATT: ${e.localizedMessage}"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _status.value = BleStatus.CONNECTED_GATT
                _statusMessage.value = "Đã kết nối Bluetooth! Đang đọc dịch vụ..."
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (_status.value != BleStatus.SUCCESS) {
                    _status.value = BleStatus.IDLE
                    _statusMessage.value = "Đã ngắt kết nối Bluetooth."
                }
            }
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
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }

                    // Gửi thông tin Wi-Fi
                    val configChar = service.getCharacteristic(CHAR_CONFIG_UUID)
                    if (configChar != null && pendingPayload.isNotEmpty()) {
                        mainHandler.postDelayed({
                            _status.value = BleStatus.SENDING_CONFIG
                            _statusMessage.value = "Đang gửi thông tin Wi-Fi sang thiết bị..."
                            configChar.value = pendingPayload.toByteArray(Charsets.UTF_8)
                            configChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            gatt.writeCharacteristic(configChar)
                        }, 500)
                    }
                } else {
                    _status.value = BleStatus.ERROR
                    _statusMessage.value = "Không tìm thấy Service SmartHome trên thiết bị"
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _status.value = BleStatus.ESP_CONNECTING_WIFI
                _statusMessage.value = "Đã gửi Wi-Fi! Đang chờ ESP32 kiểm tra kết nối..."
            } else {
                _status.value = BleStatus.FAILED
                _statusMessage.value = "Không thể gửi dữ liệu Wi-Fi sang ESP32"
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
                            _statusMessage.value = "ESP32 đang kết nối vào Wi-Fi..."
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
