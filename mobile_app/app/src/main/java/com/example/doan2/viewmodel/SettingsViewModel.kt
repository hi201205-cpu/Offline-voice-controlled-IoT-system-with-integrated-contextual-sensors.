package com.example.doan2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doan2.data.model.AutoConfig
import com.example.doan2.data.model.GardenConfig
import com.example.doan2.data.model.UserPermission
import com.example.doan2.data.model.UserProfile
import com.example.doan2.data.repository.AuthRepository
import com.example.doan2.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.doan2.data.model.AutoRuleItem

data class SettingsUiState(
    val autoConfig: AutoConfig = AutoConfig(),
    val gardenConfig: GardenConfig = GardenConfig(),
    val userEmail: String? = null,
    val userProfile: UserProfile? = null,
    val members: List<UserProfile> = emptyList(),
    val generatedInviteCode: String? = null,
    val isGeneratingInvite: Boolean = false,

    // Automation Rules (matching Web)
    val automationMasterEnabled: Boolean = true,
    val automationRules: List<AutoRuleItem> = emptyList(),
    val showAddEditRuleDialog: Boolean = false,
    val editingRule: AutoRuleItem? = null,

    // Change Password States
    val isGoogleAccount: Boolean = false,
    val hasGoogleProvider: Boolean = false,
    val hasPasswordProvider: Boolean = false,
    val isSendingResetEmail: Boolean = false,
    val isSettingUpPassword: Boolean = false,
    val isReloadingSecurity: Boolean = false,
    val currentPasswordInput: String = "",
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val showCurrentPass: Boolean = false,
    val showNewPass: Boolean = false,
    val showConfirmPass: Boolean = false,
    val isChangingPassword: Boolean = false,

    val toastMessage: String? = null
)

class SettingsViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            userEmail = authRepository.currentUserEmail,
            isGoogleAccount = authRepository.isGoogleAccount()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            firebaseRepository.observeAutoConfig().collect { auto ->
                _uiState.value = _uiState.value.copy(autoConfig = auto)
            }
        }

        viewModelScope.launch {
            firebaseRepository.observeAutomationMaster().collect { enabled ->
                _uiState.value = _uiState.value.copy(automationMasterEnabled = enabled)
            }
        }

        viewModelScope.launch {
            firebaseRepository.observeAutomationRules().collect { rules ->
                _uiState.value = _uiState.value.copy(automationRules = rules)
            }
        }

        viewModelScope.launch {
            firebaseRepository.observeGardenConfig().collect { garden ->
                _uiState.value = _uiState.value.copy(gardenConfig = garden)
            }
        }

        viewModelScope.launch {
            authRepository.observeUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    userEmail = profile?.email ?: authRepository.currentUserEmail
                )
            }
        }

        viewModelScope.launch {
            firebaseRepository.observeMembers().collect { list ->
                _uiState.value = _uiState.value.copy(members = list)
            }
        }

        refreshUserSecurityState(showToast = false)
    }

    fun onCurrentPasswordChanged(input: String) {
        _uiState.value = _uiState.value.copy(currentPasswordInput = input)
    }

    fun onNewPasswordChanged(input: String) {
        _uiState.value = _uiState.value.copy(newPasswordInput = input)
    }

    fun onConfirmPasswordChanged(input: String) {
        _uiState.value = _uiState.value.copy(confirmPasswordInput = input)
    }

    fun toggleShowCurrentPass() {
        _uiState.value = _uiState.value.copy(showCurrentPass = !_uiState.value.showCurrentPass)
    }

    fun toggleShowNewPass() {
        _uiState.value = _uiState.value.copy(showNewPass = !_uiState.value.showNewPass)
    }

    fun toggleShowConfirmPass() {
        _uiState.value = _uiState.value.copy(showConfirmPass = !_uiState.value.showConfirmPass)
    }

    fun refreshUserSecurityState(showToast: Boolean = false) {
        _uiState.value = _uiState.value.copy(isReloadingSecurity = true)
        authRepository.reloadUser { success ->
            val hasGoogle = authRepository.hasGoogleProvider()
            val hasPass = authRepository.hasPasswordProvider()
            val isGoogleOnly = authRepository.isGoogleOnlyAccount()
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isReloadingSecurity = false,
                    isGoogleAccount = isGoogleOnly,
                    hasGoogleProvider = hasGoogle,
                    hasPasswordProvider = hasPass,
                    userEmail = authRepository.currentUserEmail,
                    toastMessage = if (showToast) {
                        if (hasPass) "Đã cập nhật: Tài khoản đã có mật khẩu đăng nhập!"
                        else "Tài khoản hiện chỉ đăng nhập qua Google, chưa có mật khẩu."
                    } else _uiState.value.toastMessage
                )
            }
        }
    }

    fun setupPasswordDirectly() {
        val state = _uiState.value
        val pass = state.newPasswordInput
        val confirm = state.confirmPasswordInput

        if (pass.length < 6) {
            _uiState.value = state.copy(toastMessage = "Mật khẩu mới phải có ít nhất 6 ký tự")
            return
        }
        if (pass != confirm) {
            _uiState.value = state.copy(toastMessage = "Mật khẩu xác nhận không khớp")
            return
        }

        _uiState.value = state.copy(isSettingUpPassword = true)
        authRepository.setupPasswordForGoogleAccount(pass) { success, msg ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isSettingUpPassword = false,
                    toastMessage = msg,
                    newPasswordInput = if (success) "" else _uiState.value.newPasswordInput,
                    confirmPasswordInput = if (success) "" else _uiState.value.confirmPasswordInput,
                    currentPasswordInput = ""
                )
                if (success) {
                    refreshUserSecurityState(showToast = false)
                }
            }
        }
    }

    fun sendGooglePasswordSetupEmail() {
        val email = _uiState.value.userEmail ?: authRepository.currentUserEmail
        if (email.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Không tìm thấy email tài khoản!")
            return
        }
        _uiState.value = _uiState.value.copy(isSendingResetEmail = true)
        authRepository.sendPasswordResetEmail(email) { success, msg ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isSendingResetEmail = false,
                    toastMessage = msg
                )
            }
        }
    }

    fun submitChangePassword() {
        val curr = _uiState.value
        _uiState.value = curr.copy(isChangingPassword = true)

        authRepository.changePassword(
            currentPass = curr.currentPasswordInput,
            newPass = curr.newPasswordInput,
            confirmPass = curr.confirmPasswordInput
        ) { success, msg ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isChangingPassword = false,
                    currentPasswordInput = if (success) "" else _uiState.value.currentPasswordInput,
                    newPasswordInput = if (success) "" else _uiState.value.newPasswordInput,
                    confirmPasswordInput = if (success) "" else _uiState.value.confirmPasswordInput,
                    toastMessage = msg ?: (if (success) "Đổi mật khẩu thành công! Mật khẩu mới đã có hiệu lực." else "Thất bại")
                )
            }
        }
    }

    fun generateInviteCode() {
        val email = _uiState.value.userEmail ?: authRepository.currentUserEmail ?: "Master"
        _uiState.value = _uiState.value.copy(isGeneratingInvite = true)
        firebaseRepository.generateInviteCode(email) { success, codeOrErr ->
            viewModelScope.launch {
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingInvite = false,
                        generatedInviteCode = codeOrErr,
                        toastMessage = "Đã tạo mã mời: $codeOrErr"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingInvite = false,
                        toastMessage = codeOrErr ?: "Tạo mã thất bại"
                    )
                }
            }
        }
    }

    fun updateMemberPermission(targetUid: String, perm: UserPermission) {
        firebaseRepository.updateMemberPermissions(targetUid, perm) { success, err ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (success) "Đã cập nhật quyền thành viên" else (err ?: "Lỗi")
                )
            }
        }
    }

    fun updateMemberSinglePermission(targetUid: String, permKey: String, isAllowed: Boolean) {
        firebaseRepository.updateMemberPermission(targetUid, permKey, isAllowed) { success, err ->
            viewModelScope.launch {
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        toastMessage = err ?: "Lỗi cập nhật quyền"
                    )
                }
            }
        }
    }

    fun unpairMember(targetUid: String) {
        firebaseRepository.unpairMember(targetUid) { success, err ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (success) "Đã xóa thành viên khỏi nhà" else (err ?: "Lỗi")
                )
            }
        }
    }

    fun onAutoEnabledChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            autoConfig = _uiState.value.autoConfig.copy(enabled = enabled)
        )
    }

    fun onFanTempChanged(temp: Double) {
        val rounded = (Math.round(temp * 2.0) / 2.0).coerceIn(20.0, 45.0)
        _uiState.value = _uiState.value.copy(
            autoConfig = _uiState.value.autoConfig.copy(fanTempOn = rounded)
        )
    }

    fun onNoPersonMinsChanged(mins: Int) {
        val clamped = mins.coerceIn(1, 120)
        _uiState.value = _uiState.value.copy(
            autoConfig = _uiState.value.autoConfig.copy(noPersonMinutes = clamped)
        )
    }

    fun saveAutoConfig() {
        firebaseRepository.updateAutoConfig(_uiState.value.autoConfig)
        _uiState.value = _uiState.value.copy(
            toastMessage = "Thiết bị áp dụng trong khoảng 30 giây"
        )
    }

    fun onAutomationMasterChanged(enabled: Boolean) {
        firebaseRepository.setAutomationMaster(enabled)
        _uiState.value = _uiState.value.copy(automationMasterEnabled = enabled)
    }

    fun toggleRuleEnabled(ruleId: String, enabled: Boolean) {
        val currentRules = _uiState.value.automationRules
        val updated = currentRules.map {
            if (it.id == ruleId) it.copy(enabled = enabled) else it
        }
        _uiState.value = _uiState.value.copy(automationRules = updated)
        firebaseRepository.saveAutomationRules(updated)
    }

    fun openAddRuleDialog() {
        _uiState.value = _uiState.value.copy(showAddEditRuleDialog = true, editingRule = null)
    }

    fun openEditRuleDialog(rule: AutoRuleItem) {
        _uiState.value = _uiState.value.copy(showAddEditRuleDialog = true, editingRule = rule)
    }

    fun closeRuleDialog() {
        _uiState.value = _uiState.value.copy(showAddEditRuleDialog = false, editingRule = null)
    }

    fun saveRule(rule: AutoRuleItem) {
        val currentRules = _uiState.value.automationRules.toMutableList()
        val index = currentRules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            currentRules[index] = rule
        } else {
            val newRule = if (rule.id.isBlank()) rule.copy(id = "rule-${System.currentTimeMillis()}") else rule
            currentRules.add(newRule)
        }
        _uiState.value = _uiState.value.copy(
            automationRules = currentRules,
            showAddEditRuleDialog = false,
            editingRule = null,
            toastMessage = "Đã lưu quy tắc tự động hóa"
        )
        firebaseRepository.saveAutomationRules(currentRules)
    }

    fun deleteRule(ruleId: String) {
        val updated = _uiState.value.automationRules.filter { it.id != ruleId }
        _uiState.value = _uiState.value.copy(
            automationRules = updated,
            toastMessage = "Đã xóa quy tắc"
        )
        firebaseRepository.saveAutomationRules(updated)
    }

    fun testRule(rule: AutoRuleItem) {
        firebaseRepository.testAutomationRule(rule) { success, err ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (success) "Đã gửi lệnh chạy thử “${rule.name}” tới thiết bị" else (err ?: "Lỗi chạy thử")
                )
            }
        }
    }

    fun updateUserProfile(
        name: String,
        phone: String,
        dob: String,
        gender: String,
        address: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        authRepository.updateUserProfile(name, phone, dob, gender, address) { success, msg ->
            if (success) {
                _uiState.value = _uiState.value.copy(toastMessage = msg)
            }
            onResult(success, msg)
        }
    }

    fun unlockKeypad(onResult: ((Boolean, String?) -> Unit)? = null) {
        firebaseRepository.unlockKeypad { success, err ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (success) "Đã mở khóa an ninh bàn phím thành công!" else (err ?: "Lỗi mở khóa")
                )
                onResult?.invoke(success, err)
            }
        }
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
