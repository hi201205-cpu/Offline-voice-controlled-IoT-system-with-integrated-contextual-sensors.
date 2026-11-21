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

    private var pendingSsid: String = ""
    private var pendingPass: String = ""
    private var pendingHome: String = "HOME-2026-ESP32"
    private var pendingOwner: String = ""
    private var pendingUid: String = ""
    private var currentMtu: Int = 23
    private var retryCount: Int = 0
    private var isDescriptorPending: Boolean = false
    private var mtuHandled: Boolean = false

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

        pendingSsid = ssid.trim()
        pendingPass = pass.trim()
        pendingHome = homeId.trim().ifEmpty { "HOME-2026-ESP32" }
        pendingOwner = ownerEmail.trim()
        pendingUid = ownerUid.trim()
        currentMtu = 23
        retryCount = 0
        isDescriptorPending = false
        mtuHandled = false

        _status.value = BleStatus.CONNECTING_GATT
        _statusMessage.value = "Đang kết nối Bluetooth tới thiết bị..."

        cleanupGatt()

        // Chờ 400ms để Bluetooth stack giải phóng scan trước khi tạo GATT connection
        mainHandler.postDelayed({
            performGattConnect(device)
        }, 400)
    }

    private fun performGattConnect(device: BluetoothDevice) {
        val appContext = context.applicationContext
        val remoteDevice = bluetoothAdapter?.getRemoteDevice(device.address) ?: device

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                remoteDevice.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                remoteDevice.connectGatt(appContext, false, gattCallback)
            }
        } catch (e: Exception) {
            _status.value = BleStatus.ERROR
            _statusMessage.value = "Lỗi kết nối Bluetooth: ${e.localizedMessage}"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            // 1. Kiểm tra lỗi trạng thái GATT (e.g. status 133, 19, 8...)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // Tự động thử lại 1 lần nếu gặp lỗi 133 phổ biến trên Android
                if (status == 133 && retryCount < 1) {
                    retryCount++
                    mainHandler.post {
                        _statusMessage.value = "Tái kết nối lần $retryCount..."
                    }
                    cleanupGatt()
                    mainHandler.postDelayed({
                        gatt?.device?.let { performGattConnect(it) }
                    }, 600)
                    return
                }

                mainHandler.post {
                    _status.value = BleStatus.FAILED
                    _statusMessage.value = when (status) {
                        133 -> "Thiết bị bận hoặc chưa sẵn sàng (Lỗi 133). Bấm 'Gửi cấu hình' để thử lại."
                        19 -> "Thiết bị đóng kết nối (Lỗi 19). Vui lòng nhấn giữ nút 5s để khởi động lại Bluetooth."
                        8 -> "Hết thời gian kết nối Bluetooth (Lỗi 8). Vui lòng để điện thoại gần thiết bị hơn."
                        else -> "Lỗi kết nối Bluetooth (Mã: $status). Vui lòng bấm thử lại."
                    }
                }
                cleanupGatt()
                return
            }

            // 2. Kết nối GATT thành công
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mainHandler.post {
                    _status.value = BleStatus.CONNECTED_GATT
                    _statusMessage.value = "Đã kết nối! Đang đọc thông tin thiết bị..."
                }

                // Chờ 300ms rồi mới khám phá dịch vụ (tiêu chuẩn vàng Android BLE)
                mainHandler.postDelayed({
                    try {
                        gatt?.discoverServices()
                    } catch (e: Exception) {
                        mainHandler.post {
                            _status.value = BleStatus.ERROR
                            _statusMessage.value = "Lỗi đọc dịch vụ: ${e.localizedMessage}"
                        }
                    }
                }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.post {
                    if (_status.value != BleStatus.SUCCESS && _status.value != BleStatus.ESP_CONNECTING_WIFI) {
                        _status.value = BleStatus.FAILED
                        _statusMessage.value = "Mất kết nối Bluetooth. Vui lòng bấm giữ nút trên thiết bị 5s."
                    }
                }
                cleanupGatt()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            mtuHandled = true
            currentMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            val service = gatt?.getService(SERVICE_UUID) ?: return
            setupNotificationsAndSend(gatt, service)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                mainHandler.post {
                    _status.value = BleStatus.FAILED
                    _statusMessage.value = "Không thể đọc dịch vụ thiết bị (Mã: $status)"
                }
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                mainHandler.post {
                    _status.value = BleStatus.ERROR
                    _statusMessage.value = "Không tìm thấy Service SmartHome trên thiết bị"
                }
                return
            }

            // Thử yêu cầu MTU 256 để gửi JSON nhanh
            // Nếu thiết bị không phản hồi MTU trong 500ms, tự động gửi dạng compact (MTU 23)
            val mtuTimeoutRunnable = Runnable {
                if (!mtuHandled) {
                    mtuHandled = true
                    setupNotificationsAndSend(gatt, service)
                }
            }
            mainHandler.postDelayed(mtuTimeoutRunnable, 500)

            try {
                val requested = gatt.requestMtu(256)
                if (!requested) {
                    mainHandler.removeCallbacks(mtuTimeoutRunnable)
                    mtuHandled = true
                    setupNotificationsAndSend(gatt, service)
                }
            } catch (_: Exception) {
                mainHandler.removeCallbacks(mtuTimeoutRunnable)
                mtuHandled = true
                setupNotificationsAndSend(gatt, service)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            isDescriptorPending = false
            val service = gatt?.getService(SERVICE_UUID) ?: return
            mainHandler.postDelayed({ sendConfigPayload(gatt, service) }, 150)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            mainHandler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _status.value = BleStatus.ESP_CONNECTING_WIFI
                    _statusMessage.value = "Đã gửi Wi-Fi! Đang chờ thiết bị xác thực kết nối..."
                } else {
                    _status.value = BleStatus.FAILED
                    _statusMessage.value = "Thiết bị từ chối gói tin cấu hình Wi-Fi (Mã: $status)"
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == CHAR_STATUS_UUID) {
                handleStatusUpdate(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == CHAR_STATUS_UUID) {
                handleStatusUpdate(value)
            }
        }
    }

    private fun setupNotificationsAndSend(gatt: BluetoothGatt, service: BluetoothGattService) {
        val statusChar = service.getCharacteristic(CHAR_STATUS_UUID)
        if (statusChar != null && !isDescriptorPending) {
            gatt.setCharacteristicNotification(statusChar, true)
            val descriptor = statusChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
            if (descriptor != null) {
                isDescriptorPending = true
                mainHandler.postDelayed({
                    try {
                        val descSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                        if (!descSuccess) {
                            sendConfigPayload(gatt, service)
                        }
                    } catch (_: Exception) {
                        sendConfigPayload(gatt, service)
                    }
                }, 150)
                return
            }
        }

        mainHandler.postDelayed({ sendConfigPayload(gatt, service) }, 200)
    }

    private fun sendConfigPayload(gatt: BluetoothGatt, service: BluetoothGattService) {
        val configChar = service.getCharacteristic(CHAR_CONFIG_UUID) ?: return

        mainHandler.post {
            _status.value = BleStatus.SENDING_CONFIG
            _statusMessage.value = "Đang gửi thông tin Wi-Fi sang thiết bị..."
        }

        val usableBytes = currentMtu - 3

        // Nếu MTU >= 120 bytes -> gửi JSON đầy đủ
        // Nếu MTU là 23 (usable 20 bytes):
        // Gửi định dạng pipe cực gọn: "SSID|PASS" (vừa khít 20 bytes của chuẩn BLE MTU 23)
        val payloadString: String = if (usableBytes >= 120) {
            val json = org.json.JSONObject().apply {
                put("ssid", pendingSsid)
                put("pass", pendingPass)
                put("home", pendingHome)
                if (pendingOwner.isNotBlank()) put("owner", pendingOwner)
                if (pendingUid.isNotBlank()) put("uid", pendingUid)
            }
            json.toString()
        } else {
            if (pendingHome == "HOME-2026-ESP32" || pendingHome.isBlank()) {
                "$pendingSsid|$pendingPass"
            } else {
                "$pendingSsid|$pendingPass|$pendingHome"
            }
        }

        val bytes = payloadString.toByteArray(Charsets.UTF_8)

        mainHandler.postDelayed({
            try {
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
                    mainHandler.post {
                        _status.value = BleStatus.FAILED
                        _statusMessage.value = "Lỗi khi ghi thông tin Wi-Fi sang thiết bị"
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    _status.value = BleStatus.FAILED
                    _statusMessage.value = "Lỗi gửi cấu hình: ${e.localizedMessage}"
                }
            }
        }, 150)
    }

    private fun handleStatusUpdate(value: ByteArray?) {
        if (value != null && value.isNotEmpty()) {
            val code = value[0].toInt()
            mainHandler.post {
                when (code) {
                    3 -> { // BLE_PROV_CONFIG_RECEIVED
                        _status.value = BleStatus.ESP_CONNECTING_WIFI
                        _statusMessage.value = "Thiết bị đã nhận Wi-Fi, đang xác thực..."
                    }
                    4 -> { // BLE_PROV_CONNECTING_WIFI
                        _status.value = BleStatus.ESP_CONNECTING_WIFI
                        _statusMessage.value = "Thiết bị đang kết nối vào Wi-Fi..."
                    }
                    5 -> { // BLE_PROV_SUCCESS
                        _status.value = BleStatus.SUCCESS
                        _statusMessage.value = "Cài đặt Wi-Fi thành công! Thiết bị đã trực tuyến."
                        mainHandler.postDelayed({ cleanupGatt() }, 2500)
                    }
                    6 -> { // BLE_PROV_FAILED
                        _status.value = BleStatus.FAILED
                        _statusMessage.value = "Kết nối Wi-Fi thất bại! Sai mật khẩu hoặc tín hiệu yếu."
                    }
                }
            }
        }
    }

    private fun cleanupGatt() {
        try {
            bluetoothGatt?.disconnect()
        } catch (_: Exception) {}
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        isDescriptorPending = false
        mtuHandled = false
    }

    fun disconnect() {
        cleanupGatt()
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
