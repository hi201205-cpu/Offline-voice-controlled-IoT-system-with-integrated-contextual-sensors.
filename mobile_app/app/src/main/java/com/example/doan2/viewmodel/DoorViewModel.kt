package com.example.doan2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doan2.data.model.DeviceState
import com.example.doan2.data.model.PasscodeItem
import com.example.doan2.data.model.SensorData
import com.example.doan2.data.model.UserProfile
import com.example.doan2.data.repository.AuthRepository
import com.example.doan2.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoorUiState(
    val deviceState: DeviceState = DeviceState(),
    val passcodes: List<PasscodeItem> = emptyList(),
    val sensorData: SensorData = SensorData(),
    val userProfile: UserProfile? = null,
    val isPendingAction: Boolean = false,
    val pendingMessage: String? = null,
    val toastMessage: String? = null,
    val showAddDialog: Boolean = false,
    val passcodeToDelete: PasscodeItem? = null
)

class DoorViewModel(
    private val repository: FirebaseRepository = FirebaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoorUiState())
    val uiState: StateFlow<DoorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDeviceState().collect { ds ->
                _uiState.value = _uiState.value.copy(deviceState = ds)
            }
        }

        viewModelScope.launch {
            authRepository.observeUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(userProfile = profile)
            }
        }

        viewModelScope.launch {
            repository.observePasscodes().collect { passList ->
                _uiState.value = _uiState.value.copy(passcodes = passList)
            }
        }

        viewModelScope.launch {
            repository.observeSensorLatest().collect { sensors ->
                _uiState.value = _uiState.value.copy(sensorData = sensors)
            }
        }
    }

    val isOnline: Boolean
        get() {
            fun toSeconds(value: Long): Long {
                if (value <= 0L) return 0L
                return if (value > 2_000_000_000L) value / 1000L else value
            }

            val esp32Sec = toSeconds(uiState.value.deviceState.esp32LastSeen)
            val sensorSec = toSeconds(uiState.value.sensorData.ts)

            val latestTsSec = maxOf(esp32Sec, sensorSec)
            if (latestTsSec == 0L) return false

            val nowSec = System.currentTimeMillis() / 1000L
            val ageSec = nowSec - latestTsSec

            return ageSec in -300L..60L
        }

    private fun checkPermission(permName: String): Boolean {
        val profile = uiState.value.userProfile
        val email = profile?.email ?: authRepository.currentUserEmail ?: ""
        val isMaster = profile?.role == "master" || email.equals(com.example.doan2.data.repository.AuthRepository.masterEmail, ignoreCase = true)
        if (isMaster) return true
        if (profile == null) return false
        return when (permName) {
            "door" -> profile.permissions.door
            "passcodes" -> profile.permissions.passcodes
            "passcodeAdd" -> profile.permissions.passcodeAdd
            "passcodeDel" -> profile.permissions.passcodeDel
            else -> false
        }
    }

    fun closeDoor() {
        if (!checkPermission("door")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền đóng cửa")
            return
        }
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"
        repository.closeDoor { success, _ ->
            if (success) {
                repository.logWebAction("door", "Đóng", userEmail, "Đóng · Cửa chính")
            }
        }
        _uiState.value = _uiState.value.copy(toastMessage = "Đã gửi lệnh đóng cửa")
    }

    fun openDoorConfirmed() {
        if (!checkPermission("door")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền mở cửa")
            return
        }
        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"
        repository.openDoor { success, _ ->
            if (success) {
                repository.logWebAction("door", "Mở", userEmail, "Mở · Cửa chính")
            }
        }
        _uiState.value = _uiState.value.copy(toastMessage = "Đã gửi lệnh mở cửa")
    }

    fun addPasscode(name: String, code: String) {
        if (!checkPermission("passcodeAdd")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền thêm mật khẩu Keypad cho bạn!")
            return
        }
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Vui lòng nhập tên mật khẩu")
            return
        }
        if (code.length !in 4..8 || !code.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(toastMessage = "Mã số phải từ 4 đến 8 chữ số")
            return
        }

        _uiState.value = _uiState.value.copy(
            isPendingAction = true,
            pendingMessage = "Đang gửi mật khẩu tới thiết bị..."
        )

        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"

        repository.addPasscode(name, code) { success, msg ->
            viewModelScope.launch {
                if (success) {
                    repository.logWebAction("passcodes", "Thêm", userEmail, "Thêm mật khẩu: $name")
                }
                _uiState.value = _uiState.value.copy(
                    isPendingAction = false,
                    pendingMessage = null,
                    showAddDialog = if (success) false else _uiState.value.showAddDialog,
                    toastMessage = if (success) "Đã thêm mật khẩu thành công" else (msg ?: "Thất bại")
                )
            }
        }
    }

    fun confirmDeletePasscode(item: PasscodeItem) {
        if (!checkPermission("passcodeDel")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền xóa mật khẩu Keypad cho bạn!")
            return
        }
        if (_uiState.value.passcodes.size <= 1) {
            _uiState.value = _uiState.value.copy(toastMessage = "Không thể xóa khi chỉ còn 1 mật khẩu")
            return
        }
        _uiState.value = _uiState.value.copy(passcodeToDelete = item)
    }

    fun executeDeletePasscode() {
        val item = _uiState.value.passcodeToDelete ?: return
        if (!checkPermission("passcodeDel")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền xóa mật khẩu Keypad cho bạn!")
            return
        }

        _uiState.value = _uiState.value.copy(
            passcodeToDelete = null,
            isPendingAction = true,
            pendingMessage = "Đang yêu cầu xóa mật khẩu..."
        )

        val userEmail = uiState.value.userProfile?.email ?: authRepository.currentUserEmail ?: "App"

        repository.deletePasscode(item.id, _uiState.value.passcodes.size) { success, msg ->
            viewModelScope.launch {
                if (success) {
                    repository.logWebAction("passcodes", "Xóa", userEmail, "Xóa mật khẩu: ${item.name}")
                }
                _uiState.value = _uiState.value.copy(
                    isPendingAction = false,
                    pendingMessage = null,
                    toastMessage = if (success) "Đã xóa mật khẩu thành công" else (msg ?: "Thất bại")
                )
            }
        }
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(passcodeToDelete = null)
    }

    fun unlockKeypad(onResult: ((Boolean, String?) -> Unit)? = null) {
        if (!checkPermission("door")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Bạn không có quyền quản lý cửa chính hoặc mở khóa bàn phím!")
            onResult?.invoke(false, "Không có quyền")
            return
        }
        repository.unlockKeypad { success, err ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (success) "Đã mở khóa an ninh bàn phím!" else (err ?: "Mở khóa thất bại")
                )
                onResult?.invoke(success, err)
            }
        }
    }

    fun openAddDialog() {
        if (!checkPermission("passcodeAdd")) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền thêm mật khẩu Keypad cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
