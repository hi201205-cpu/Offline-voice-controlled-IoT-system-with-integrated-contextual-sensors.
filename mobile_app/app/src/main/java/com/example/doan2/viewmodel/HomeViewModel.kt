package com.example.doan2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doan2.data.model.*
import com.example.doan2.data.repository.AuthRepository
import com.example.doan2.data.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val deviceState: DeviceState = DeviceState(),
    val sensorData: SensorData = SensorData(),
    val sensorHistory: List<SensorHistoryPoint> = emptyList(),
    val doorAlert: DoorAlert? = null,
    val autoConfig: AutoConfig = AutoConfig(),
    val gardenConfig: GardenConfig = GardenConfig(),
    val suggestionPending: SuggestionPending? = null,
    val userProfile: UserProfile? = null,

    val processingLight: Boolean = false,
    val processingFan: Boolean = false,
    val processingCurtain: Boolean = false,
    val processingGarden: Boolean = false,
    val processingVoice: Boolean = false,
    val processingDoor: Boolean = false,

    val lightError: Boolean = false,
    val fanError: Boolean = false,
    val curtainError: Boolean = false,
    val gardenError: Boolean = false,
    val voiceError: Boolean = false,
    val doorError: Boolean = false,

    val toastMessage: String? = null,
    val showChartDialog: Boolean = false,
    val showGardenSheet: Boolean = false
)

class HomeViewModel(
    private val repository: FirebaseRepository = FirebaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lightJob: Job? = null
    private var fanJob: Job? = null
    private var curtainJob: Job? = null
    private var gardenJob: Job? = null
    private var voiceJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeDeviceState().collect { ds ->
                val current = _uiState.value
                lightJob?.cancel()
                fanJob?.cancel()
                curtainJob?.cancel()
                gardenJob?.cancel()
                voiceJob?.cancel()
                _uiState.value = current.copy(
                    deviceState = ds,
                    processingLight = false,
                    processingFan = false,
                    processingCurtain = false,
                    processingGarden = false,
                    processingVoice = false,
                    lightError = false,
                    fanError = false,
                    curtainError = false,
                    gardenError = false,
                    voiceError = false
                )
            }
        }

        viewModelScope.launch {
            authRepository.observeUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(userProfile = profile)
            }
        }

        viewModelScope.launch {
            repository.observeSensorLatest().collect { sensors ->
                _uiState.value = _uiState.value.copy(sensorData = sensors)
            }
        }

        viewModelScope.launch {
            repository.observeSensorHistory().collect { history ->
                _uiState.value = _uiState.value.copy(sensorHistory = history)
            }
        }

        viewModelScope.launch {
            repository.observeDoorAlert().collect { alert ->
                _uiState.value = _uiState.value.copy(doorAlert = alert)
            }
        }

        viewModelScope.launch {
            repository.observeAutoConfig().collect { auto ->
                _uiState.value = _uiState.value.copy(autoConfig = auto)
            }
        }

        viewModelScope.launch {
            repository.observeGardenConfig().collect { garden ->
                _uiState.value = _uiState.value.copy(gardenConfig = garden)
            }
        }

        viewModelScope.launch {
            repository.observeSuggestionPending().collect { suggestion ->
                _uiState.value = _uiState.value.copy(suggestionPending = suggestion)
            }
        }
    }

    val isDisconnected: Boolean
        get() {
            fun toSeconds(value: Long): Long {
                if (value <= 0L) return 0L
                return if (value > 2_000_000_000L) value / 1000L else value
            }

            val esp32Sec = toSeconds(uiState.value.deviceState.esp32LastSeen)
            val sensorSec = toSeconds(uiState.value.sensorData.ts)

            val latestTsSec = maxOf(esp32Sec, sensorSec)
            if (latestTsSec == 0L) return true

            val nowSec = System.currentTimeMillis() / 1000L
            val ageSec = nowSec - latestTsSec

            return ageSec > 60L || ageSec < -300L
        }

    private fun checkPermission(permName: String): Boolean {
        val profile = uiState.value.userProfile
        val email = profile?.email ?: authRepository.currentUserEmail ?: ""
        val isMaster = profile?.role == "master" || email.equals(com.example.doan2.data.repository.AuthRepository.masterEmail, ignoreCase = true)
        if (isMaster) return true
        if (profile == null) return false
        val perm = profile.permissions
        return when (permName) {
            "light" -> perm.light
            "fan" -> perm.fan
            "curtain" -> perm.curtain
            "yard" -> perm.yard
            "door" -> perm.door
            "auto" -> perm.auto
            "voice" -> perm.voice
            "passcodes" -> perm.passcodes
            "passcodeAdd" -> perm.passcodeAdd
            "passcodeDel" -> perm.passcodeDel
            else -> false
        }
    }

    fun toggleLight() {
        if (!checkPermission("light")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền bật/tắt đèn phòng")
            return
        }
        if (isDisconnected) {
            _uiState.value = _uiState.value.copy(toastMessage = "Thiết bị đang offline (quá 30s)")
            return
        }
        val target = !_uiState.value.deviceState.light
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"

        _uiState.value = _uiState.value.copy(processingLight = true, lightError = false)
        repository.setLight(value = target, onResult = { success, error ->
            if (success) {
                repository.logWebAction("light", if (target) "Bật" else "Tắt", userEmail, "${if (target) "Bật" else "Tắt"} · Đèn phòng")
            } else {
                lightJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    processingLight = false,
                    lightError = true,
                    toastMessage = error ?: "Lỗi ghi Firebase"
                )
            }
        })

        lightJob?.cancel()
        lightJob = viewModelScope.launch {
            delay(5000L)
            val curr = _uiState.value
            if (curr.deviceState.isLightPending || curr.processingLight) {
                _uiState.value = curr.copy(
                    processingLight = false,
                    lightError = true,
                    toastMessage = "Thiết bị chưa phản hồi"
                )
            }
        }
    }

    fun toggleFan() {
        if (!checkPermission("fan")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền bật/tắt quạt")
            return
        }
        if (isDisconnected) {
            _uiState.value = _uiState.value.copy(toastMessage = "Thiết bị đang offline (quá 30s)")
            return
        }
        val target = !_uiState.value.deviceState.fan
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"

        _uiState.value = _uiState.value.copy(processingFan = true, fanError = false)
        repository.setFan(value = target, onResult = { success, error ->
            if (success) {
                repository.logWebAction("fan", if (target) "Bật" else "Tắt", userEmail, "${if (target) "Bật" else "Tắt"} · Quạt")
            } else {
                fanJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    processingFan = false,
                    fanError = true,
                    toastMessage = error ?: "Lỗi ghi Firebase"
                )
            }
        })

        fanJob?.cancel()
        fanJob = viewModelScope.launch {
            delay(5000L)
            val curr = _uiState.value
            if (curr.deviceState.isFanPending || curr.processingFan) {
                _uiState.value = curr.copy(
                    processingFan = false,
                    fanError = true,
                    toastMessage = "Thiết bị chưa phản hồi"
                )
            }
        }
    }

    fun toggleCurtain() {
        if (!checkPermission("curtain")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền đóng/mở rèm")
            return
        }
        if (isDisconnected) {
            _uiState.value = _uiState.value.copy(toastMessage = "Thiết bị đang offline (quá 30s)")
            return
        }
        val targetOpen = _uiState.value.deviceState.curtain != "open"
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"

        _uiState.value = _uiState.value.copy(processingCurtain = true, curtainError = false)
        repository.setCurtain(onResult = { success, error ->
            if (success) {
                repository.logWebAction("curtain", if (targetOpen) "Mở" else "Đóng", userEmail, "${if (targetOpen) "Mở" else "Đóng"} · Rèm cửa")
            } else {
                curtainJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    processingCurtain = false,
                    curtainError = true,
                    toastMessage = error ?: "Lỗi ghi Firebase"
                )
            }
        })

        curtainJob?.cancel()
        curtainJob = viewModelScope.launch {
            delay(5000L)
            val curr = _uiState.value
            if (curr.deviceState.isCurtainPending || curr.processingCurtain) {
                _uiState.value = curr.copy(
                    processingCurtain = false,
                    curtainError = true,
                    toastMessage = "Thiết bị chưa phản hồi"
                )
            }
        }
    }

    fun toggleGarden() {
        if (!checkPermission("yard")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền điều khiển đèn sân")
            return
        }
        if (isDisconnected) {
            _uiState.value = _uiState.value.copy(toastMessage = "Thiết bị đang offline (quá 30s)")
            return
        }
        val target = !_uiState.value.deviceState.garden
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"

        _uiState.value = _uiState.value.copy(processingGarden = true, gardenError = false)
        repository.setGarden(value = target, onResult = { success, error ->
            if (success) {
                repository.logWebAction("yard", if (target) "Bật" else "Tắt", userEmail, "${if (target) "Bật" else "Tắt"} · Đèn sân")
            } else {
                gardenJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    processingGarden = false,
                    gardenError = true,
                    toastMessage = error ?: "Lỗi ghi Firebase"
                )
            }
        })

        gardenJob?.cancel()
        gardenJob = viewModelScope.launch {
            delay(5000L)
            val curr = _uiState.value
            if (curr.deviceState.isGardenPending || curr.processingGarden) {
                _uiState.value = curr.copy(
                    processingGarden = false,
                    gardenError = true,
                    toastMessage = "Thiết bị chưa phản hồi"
                )
            }
        }
    }

    fun toggleVoice() {
        if (!checkPermission("voice")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền điều khiển nhận diện giọng nói & LED")
            return
        }
        if (isDisconnected) {
            _uiState.value = _uiState.value.copy(toastMessage = "Thiết bị đang offline (quá 30s)")
            return
        }
        val target = !_uiState.value.deviceState.voice
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"
        val userName = uiState.value.userProfile?.name ?: ""

        // Cập nhật giao diện ngay lập tức (optimistic update giống Web)
        _uiState.value = _uiState.value.copy(
            deviceState = _uiState.value.deviceState.copy(voiceActual = target, voiceDesired = target),
            processingVoice = false,
            voiceError = false
        )

        repository.setVoice(value = target, onResult = { success, error ->
            if (success) {
                repository.logWebAction(
                    "voice",
                    if (target) "Bật" else "Tắt",
                    userEmail,
                    "${if (target) "Bật" else "Tắt"} · Nhận diện giọng nói & LED",
                    userName
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    deviceState = _uiState.value.deviceState.copy(voiceActual = !target, voiceDesired = !target),
                    voiceError = true,
                    toastMessage = error ?: "Lỗi ghi Firebase"
                )
            }
        })
    }

    fun openDoor() {
        if (!checkPermission("door")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền mở cửa")
            return
        }
        if (isDisconnected) {
            _uiState.value = _uiState.value.copy(toastMessage = "Thiết bị đang offline (quá 30s)")
            return
        }
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"
        val userName = uiState.value.userProfile?.name ?: ""

        _uiState.value = _uiState.value.copy(
            processingDoor = true,
            doorError = false,
            toastMessage = "Đang gửi lệnh mở cửa..."
        )

        repository.openDoor { success, error ->
            if (success) {
                repository.logWebAction(
                    "door",
                    "Mở",
                    userEmail,
                    "Mở · Cửa chính",
                    userName
                )
                viewModelScope.launch {
                    delay(4000L)
                    _uiState.value = _uiState.value.copy(processingDoor = false)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    processingDoor = false,
                    doorError = true,
                    toastMessage = error ?: "Lỗi mở cửa"
                )
            }
        }
    }

    fun saveGardenConfig(mode: String, darkBelow: Int, brightAbove: Int) {
        if (!checkPermission("yard")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền cài đặt đèn sân")
            return
        }
        if (darkBelow >= brightAbove) {
            _uiState.value = _uiState.value.copy(toastMessage = "Ngưỡng tối phải nhỏ hơn ngưỡng sáng")
            return
        }
        val config = GardenConfig(mode, darkBelow, brightAbove)
        repository.updateGardenConfig(config)
        _uiState.value = _uiState.value.copy(
            showGardenSheet = false,
            toastMessage = "Đã lưu cài đặt đèn sân"
        )
    }

    fun ackDoorAlert() {
        repository.ackDoorAlert()
    }

    fun unlockKeypad(onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!checkPermission("door")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền quản lý cửa chính hoặc mở khóa bàn phím!")
            onResult?.invoke(false, "Không có quyền")
            return
        }
        repository.unlockKeypad(onResult)
    }

    fun acceptSuggestion() {
        repository.acceptSuggestion()
        _uiState.value = _uiState.value.copy(
            toastMessage = "Đã bật quạt theo gợi ý"
        )
    }

    fun dismissSuggestion() {
        repository.dismissSuggestion()
    }

    fun openChartDialog() {
        _uiState.value = _uiState.value.copy(showChartDialog = true)
    }

    fun closeChartDialog() {
        _uiState.value = _uiState.value.copy(showChartDialog = false)
    }

    fun openGardenSheet() {
        _uiState.value = _uiState.value.copy(showGardenSheet = true)
    }

    fun closeGardenSheet() {
        _uiState.value = _uiState.value.copy(showGardenSheet = false)
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
