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
import android.util.Log
import org.json.JSONObject
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

val BleStatus.isProvisioning: Boolean
    get() = this == BleStatus.CONNECTING_GATT || this == BleStatus.CONNECTED_GATT ||
        this == BleStatus.SENDING_CONFIG || this == BleStatus.ESP_CONNECTING_WIFI

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
class BleProvisioningManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "BleProvisioning"
        private const val SETUP_NAME = "SmartHome-ESP32-Setup"
        private const val MAX_PAYLOAD_BYTES = 512
        private const val OPERATION_TIMEOUT_MS = 10_000L
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHAR_CONFIG_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val CHAR_STATUS_UUID: UUID = UUID.fromString("1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e")
        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null

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

    private enum class Phase { WAIT, CONNECT, MTU, DISCOVER, SUBSCRIBE, WRITE, RESULT }

    private class Session(
        val device: BluetoothDevice,
        val payload: ByteArray,
        val isRetry: Boolean = false,
        val skipMtuRequest: Boolean = false
    ) {
        var phase = Phase.WAIT
        var gatt: BluetoothGatt? = null
        var mtu = 23
        var config: BluetoothGattCharacteristic? = null
        var submittedBytes = 0
    }

    // All scan, GATT and timeout state belongs to the main looper.
    private var session: Session? = null
    private var scanCallback: ScanCallback? = null
    private var scanTimeout: Runnable? = null
    private var deadline: Runnable? = null
    private var closingGatt: BluetoothGatt? = null
    private var closeTimeout: Runnable? = null

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    fun scanWifiNetworks() {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        _isScanningWifi.value = true

        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: Exception) {}

        mainHandler.postDelayed({
            try {
                val currentSsid = getConnectedWifiSsid(appContext)
                @Suppress("DEPRECATION")
                val results: List<WifiScanResult> = wifiManager.scanResults ?: emptyList()

                val items = results
                    .filter { !it.SSID.isNullOrBlank() }
                    .distinctBy { it.SSID }
                    .map { res ->
                        val cleanSsid = res.SSID
                        val is24 = res.frequency in 2400..2500
                        WifiNetworkItem(
                            ssid = cleanSsid,
                            rssi = res.level,
                            is24GHz = is24,
                            isConnected = cleanSsid == currentSsid
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
                val currentSsid = getConnectedWifiSsid(appContext)
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

    fun startScan(timeoutMs: Long = 15_000L) = onMain {
        if (session != null || scanCallback != null) return@onMain
        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) {
                show(BleStatus.ERROR, "Bluetooth chưa bật hoặc điện thoại không hỗ trợ quét BLE.")
                return@onMain
            }
            _discoveredDevices.value = emptyList()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: BleScanResult) {
                    val source = this
                    mainHandler.post {
                        if (scanCallback === source) recordResult(result)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    val source = this
                    mainHandler.post {
                        if (scanCallback === source) {
                            endScan()
                            show(BleStatus.ERROR, "Quét BLE thất bại (mã $errorCode).")
                        }
                    }
                }
            }
            bleScanner = scanner
            scanCallback = callback
            show(BleStatus.SCANNING, "Đang quét tìm ESP32...")
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, callback)
            scanTimeout = Runnable { if (scanCallback === callback) stopScan() }.also {
                mainHandler.postDelayed(it, timeoutMs)
            }
        } catch (e: RuntimeException) {
            endScan()
            show(BleStatus.ERROR, "Không thể quét BLE. Kiểm tra Bluetooth và quyền của ứng dụng.")
            Log.w(TAG, "startScan: ${e.javaClass.simpleName}")
        }
    }

    private fun recordResult(result: BleScanResult) {
        if (!result.isConnectable) return
        val record = result.scanRecord ?: return
        if (record.serviceUuids?.any { it.uuid == SERVICE_UUID } != true && record.deviceName != SETUP_NAME) return
        val device = result.device
        val current = _discoveredDevices.value
        if (current.any { it.address == device.address }) return
        val name = record.deviceName ?: SETUP_NAME
        _discoveredDevices.value = current + DiscoveredBleDevice(name, device.address, device)
        _statusMessage.value = "Đã thấy ${_discoveredDevices.value.size} thiết bị. Bạn có thể chọn ESP32 để cấu hình."
    }

    fun stopScan() = onMain {
        endScan()
        if (_status.value == BleStatus.SCANNING) {
            if (_discoveredDevices.value.isEmpty()) {
                show(BleStatus.IDLE, "Chưa tìm thấy ESP32. Kiểm tra chế độ Setup rồi quét lại.")
            } else {
                show(BleStatus.DEVICE_FOUND, "Đã quét xong. Chọn ESP32 để cấu hình.")
            }
        }
    }

    private fun endScan() {
        scanTimeout?.let(mainHandler::removeCallbacks)
        scanTimeout = null
        val callback = scanCallback ?: return
        scanCallback = null
        try {
            bleScanner?.stopScan(callback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopScan: ${e.javaClass.simpleName}")
        } finally {
            bleScanner = null
        }
    }

    fun connectAndSendWifi(
        device: BluetoothDevice,
        ssid: String,
        pass: String,
        homeId: String = "HOME-2026-ESP32",
        ownerEmail: String = "",
        ownerUid: String = ""
    ) = onMain {
        if (session != null) return@onMain
        endScan()
        if (ssid.toByteArray(Charsets.UTF_8).size !in 1..32 ||
            listOf(ssid, pass, homeId, ownerEmail, ownerUid).any { '\u0000' in it }) {
            show(BleStatus.ERROR, "SSID phải dài 1–32 byte và cấu hình không được chứa ký tự NUL.")
            return@onMain
        }
        if (pass.toByteArray(Charsets.UTF_8).size > 64) {
            show(BleStatus.ERROR, "Mật khẩu Wi-Fi vượt quá 64 byte.")
            return@onMain
        }
        val payload = JSONObject().apply {
            put("ssid", ssid)
            put("pass", pass)
            put("home", homeId.trim().ifEmpty { "HOME-2026-ESP32" })
            put("owner", ownerEmail.trim())
            put("uid", ownerUid.trim())
        }.toString().toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_PAYLOAD_BYTES) {
            show(BleStatus.ERROR, "Cấu hình vượt quá giới hạn 512 byte của ESP32.")
            return@onMain
        }
        activate(Session(device, payload))
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) action() else mainHandler.post { action() }
    }

    private fun show(status: BleStatus, message: String) {
        _statusMessage.value = message
        _status.value = status
    }

    private fun activate(s: Session) {
        session = s
        show(BleStatus.CONNECTING_GATT, if (s.isRetry) "Đang thử kết nối lần hai..." else "Đang kết nối ESP32...")
        if (closingGatt == null) scheduleConnect(s)
    }

    private fun scheduleConnect(s: Session) {
        // Back off only after a failed attempt. A selected scan result is already a BluetoothDevice.
        if (s.isRetry) arm(s, 800L) { connect(s) } else connect(s)
    }

    private fun connect(s: Session) {
        s.phase = Phase.CONNECT
        Log.i(TAG, "connectGatt attempt=${if (s.isRetry) 2 else 1}")
        arm(s, 35_000L) { fail(s, "Hết thời gian thiết lập liên kết BLE.", retry = true) }
        try {
            s.gatt = s.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (s.gatt == null) fail(s, "Android không tạo được kết nối GATT.")
        } catch (e: RuntimeException) {
            operationError(s, e)
        }
    }

    private fun arm(s: Session, delayMs: Long, action: () -> Unit) {
        cancelDeadline()
        deadline = Runnable { if (session === s) action() }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun cancelDeadline() {
        deadline?.let(mainHandler::removeCallbacks)
        deadline = null
    }

    private fun expect(s: Session, phase: Phase) {
        s.phase = phase
        Log.d(TAG, "phase=$phase")
        arm(s, if (phase == Phase.RESULT) 40_000L else OPERATION_TIMEOUT_MS) {
            if (phase == Phase.MTU) {
                // The MTU request may still be outstanding. Reopen before using 20-byte chunks.
                fail(s, "ESP32 chưa phản hồi yêu cầu MTU.", retry = true, skipMtu = true)
            } else {
                fail(s, when (phase) {
                    Phase.RESULT -> "Chưa nhận được kết quả Wi-Fi từ ESP32."
                    Phase.WRITE -> "ESP32 chưa xác nhận gói cấu hình."
                    else -> "Hết thời gian chờ Bluetooth ở bước $phase."
                })
            }
        }
    }

    private fun event(gatt: BluetoothGatt, disconnected: Boolean = false, action: (Session) -> Unit) {
        mainHandler.post {
            if (disconnected && closingGatt === gatt) {
                finishClose(gatt)
                return@post
            }
            val s = session ?: return@post
            if (s.gatt !== gatt) return@post
            try {
                action(s)
            } catch (e: RuntimeException) {
                operationError(s, e)
            }
        }
    }

    private fun operationError(s: Session, e: RuntimeException) {
        Log.w(TAG, "${s.phase}: ${e.javaClass.simpleName}")
        fail(s, if (e is SecurityException) "Ứng dụng thiếu quyền Bluetooth."
            else "Android không thực hiện được thao tác Bluetooth (${s.phase}).")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val disconnected = newState == BluetoothProfile.STATE_DISCONNECTED
            event(gatt, disconnected) { s ->
                Log.i(TAG, "onConnectionStateChange phase=${s.phase} status=$status newState=$newState")
                if (status != BluetoothGatt.GATT_SUCCESS || disconnected) {
                    val retry = s.phase == Phase.CONNECT && status in setOf(0, 8, 62, 133, 147)
                    val message = if (s.submittedBytes > 0) "Mất liên kết khi gửi hoặc chờ kết quả Wi-Fi"
                        else "Kết nối BLE bị ngắt ở bước ${s.phase}"
                    fail(s, "$message (mã $status).", retry = retry, disconnected = disconnected)
                } else if (newState == BluetoothProfile.STATE_CONNECTED && s.phase == Phase.CONNECT) {
                    show(BleStatus.CONNECTED_GATT, "Đã kết nối ESP32. Đang chuẩn bị gửi cấu hình...")
                    if (s.skipMtuRequest) discover(s, gatt)
                    else {
                        expect(s, Phase.MTU)
                        if (!gatt.requestMtu(512)) discover(s, gatt)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) = event(gatt) { s ->
            if (s.phase != Phase.MTU) return@event
            if (status == BluetoothGatt.GATT_SUCCESS && mtu in 23..517) s.mtu = mtu
            Log.d(TAG, "onMtuChanged status=$status mtu=${s.mtu}")
            discover(s, gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = event(gatt) { s ->
            if (s.phase != Phase.DISCOVER) return@event
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(s, "Đọc dịch vụ BLE thất bại (mã $status).")
            } else {
                subscribe(s, gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            event(gatt) { s ->
                if (s.phase != Phase.SUBSCRIBE || descriptor.uuid != CLIENT_CONFIG_DESCRIPTOR ||
                    descriptor.characteristic.uuid != CHAR_STATUS_UUID) return@event
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail(s, "ESP32 từ chối bật thông báo (mã $status).")
                } else {
                    show(BleStatus.SENDING_CONFIG, "Đang gửi cấu hình Wi-Fi...")
                    writeNext(s, gatt)
                }
            }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            event(gatt) { s ->
                if (s.phase != Phase.WRITE || characteristic.uuid != CHAR_CONFIG_UUID) return@event
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail(s, "ESP32 từ chối gói cấu hình (mã $status).")
                } else if (s.submittedBytes < s.payload.size) {
                    writeNext(s, gatt)
                } else {
                    expect(s, Phase.RESULT)
                    if (_status.value != BleStatus.ESP_CONNECTING_WIFI) {
                        show(BleStatus.ESP_CONNECTING_WIFI, "Đã gửi cấu hình. Đang chờ kết quả từ ESP32...")
                    }
                }
            }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_STATUS_UUID) receiveStatus(gatt, characteristic.value?.firstOrNull())
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == CHAR_STATUS_UUID) receiveStatus(gatt, value.firstOrNull())
        }
    }

    private fun discover(s: Session, gatt: BluetoothGatt) {
        expect(s, Phase.DISCOVER)
        if (!gatt.discoverServices()) fail(s, "Android không bắt đầu được bước đọc dịch vụ.")
    }

    @Suppress("DEPRECATION")
    private fun subscribe(s: Session, gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        val config = service?.getCharacteristic(CHAR_CONFIG_UUID)
        val status = service?.getCharacteristic(CHAR_STATUS_UUID)
        val cccd = status?.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
        if (config == null || config.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
            status == null || status.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 || cccd == null) {
            fail(s, "ESP32 thiếu dịch vụ, characteristic hoặc CCCD cần cho cấu hình Wi-Fi.")
            return
        }
        s.config = config
        expect(s, Phase.SUBSCRIBE)
        if (!gatt.setCharacteristicNotification(status, true)) {
            fail(s, "Android không bật được thông báo trạng thái.")
            return
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val started = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            cccd.value = value
            gatt.writeDescriptor(cccd)
        }
        if (!started) fail(s, "Android không gửi được yêu cầu bật thông báo đến ESP32.")
    }

    @Suppress("DEPRECATION")
    private fun writeNext(s: Session, gatt: BluetoothGatt) {
        val config = checkNotNull(s.config)
        val start = s.submittedBytes
        val end = minOf(start + minOf(s.mtu - 3, 512), s.payload.size)
        val chunk = s.payload.copyOfRange(start, end)
        expect(s, Phase.WRITE)
        val started = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(config, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            config.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            config.value = chunk
            gatt.writeCharacteristic(config)
        }
        if (started) s.submittedBytes = end else fail(s, "Android không gửi được gói cấu hình Wi-Fi.")
    }

    private fun receiveStatus(gatt: BluetoothGatt, code: Byte?) = event(gatt) { s ->
        // The peripheral can notify from its onWrite before Android receives the write response.
        if (s.submittedBytes != s.payload.size || (s.phase != Phase.WRITE && s.phase != Phase.RESULT)) return@event
        when (code?.toInt()) {
            3 -> show(BleStatus.ESP_CONNECTING_WIFI, "ESP32 đã nhận cấu hình.")
            4 -> show(BleStatus.ESP_CONNECTING_WIFI, "ESP32 đang kết nối Wi-Fi...")
            5 -> {
                show(BleStatus.SUCCESS, "ESP32 đã kết nối Wi-Fi thành công.")
                release(s)
            }
            6 -> fail(s, "ESP32 báo cấu hình Wi-Fi thất bại. Kiểm tra SSID và mật khẩu.")
            else -> Unit // Ignore idle/advertising and status codes introduced by newer firmware.
        }
    }

    private fun fail(
        s: Session, message: String, retry: Boolean = false, skipMtu: Boolean = false,
        disconnected: Boolean = false
    ) {
        if (session !== s) return
        Log.w(TAG, "${s.phase}: $message")
        release(s, disconnected)
        if (retry && !s.isRetry && s.submittedBytes == 0) {
            activate(Session(s.device, s.payload, isRetry = true, skipMtuRequest = skipMtu))
        } else {
            show(BleStatus.FAILED, message)
        }
    }

    private fun release(s: Session, disconnected: Boolean = false) {
        cancelDeadline()
        session = null
        val gatt = s.gatt ?: return
        closingGatt = gatt
        if (disconnected) {
            finishClose(gatt)
        } else {
            // Some stacks omit STATE_DISCONNECTED when cancelling a pending connect.
            closeTimeout = Runnable { finishClose(gatt) }.also { mainHandler.postDelayed(it, 1_500L) }
            try {
                gatt.disconnect()
            } catch (_: RuntimeException) {
                finishClose(gatt)
            }
        }
    }

    private fun finishClose(gatt: BluetoothGatt) {
        if (closingGatt !== gatt) return
        closeTimeout?.let(mainHandler::removeCallbacks)
        closeTimeout = null
        closingGatt = null
        try {
            gatt.close()
        } catch (e: RuntimeException) {
            Log.w(TAG, "close: ${e.javaClass.simpleName}")
        }
        session?.takeIf { it.phase == Phase.WAIT }?.let(::scheduleConnect)
    }

    fun disconnect() = onMain {
        stopScan()
        session?.let {
            release(it)
            show(BleStatus.IDLE, "Đã hủy cấu hình Wi-Fi.")
        }
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
                    return ssid
                }
            }
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = wifiManager?.connectionInfo
        if (info != null && info.networkId != -1) {
            val ssid = info.ssid?.removeSurrounding("\"")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid != "0x") {
                return ssid
            }
        }
    } catch (_: Exception) {}
    return ""
}
