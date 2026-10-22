package com.example.doan2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doan2.data.model.AutoConfig
import com.example.doan2.data.model.AutoRuleItem
import com.example.doan2.data.model.GardenConfig
import com.example.doan2.data.model.UserProfile
import com.example.doan2.data.repository.AuthRepository
import com.example.doan2.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AutomationUiState(
    val autoConfig: AutoConfig = AutoConfig(),
    val gardenConfig: GardenConfig = GardenConfig(),
    val automationMasterEnabled: Boolean = true,
    val automationRules: List<AutoRuleItem> = emptyList(),
    val showAddEditRuleDialog: Boolean = false,
    val editingRule: AutoRuleItem? = null,
    val toastMessage: String? = null,
    val userProfile: UserProfile? = null
)

class AutomationViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(userProfile = profile)
            }
        }

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
    }

    private fun checkAutoPermission(): Boolean {
        val profile = _uiState.value.userProfile
        val email = profile?.email ?: authRepository.currentUserEmail ?: ""
        val isMaster = profile?.role == "master" || email.equals(AuthRepository.masterEmail, ignoreCase = true)
        if (isMaster) return true
        if (profile == null) return false
        return profile.permissions.auto
    }

    fun onAutomationMasterChanged(enabled: Boolean) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        firebaseRepository.setAutomationMaster(enabled)
        _uiState.value = _uiState.value.copy(automationMasterEnabled = enabled)
    }

    fun toggleRuleEnabled(ruleId: String, enabled: Boolean) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        val currentRules = _uiState.value.automationRules
        val updated = currentRules.map {
            if (it.id == ruleId) it.copy(enabled = enabled) else it
        }
        _uiState.value = _uiState.value.copy(automationRules = updated)
        firebaseRepository.saveAutomationRules(updated)
    }

    fun openAddRuleDialog() {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(showAddEditRuleDialog = true, editingRule = null)
    }

    fun openEditRuleDialog(rule: AutoRuleItem) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(showAddEditRuleDialog = true, editingRule = rule)
    }

    fun closeRuleDialog() {
        _uiState.value = _uiState.value.copy(showAddEditRuleDialog = false, editingRule = null)
    }

    fun saveRule(rule: AutoRuleItem) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
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
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        val updated = _uiState.value.automationRules.filter { it.id != ruleId }
        _uiState.value = _uiState.value.copy(
            automationRules = updated,
            toastMessage = "Đã xóa quy tắc"
        )
        firebaseRepository.saveAutomationRules(updated)
    }

    fun testRule(rule: AutoRuleItem) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        firebaseRepository.testAutomationRule(rule) { success, err ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (success) "Đã gửi lệnh chạy thử “${rule.name}” tới thiết bị" else (err ?: "Lỗi chạy thử")
                )
            }
        }
    }

    // Firmware ESP32 config
    fun onAutoEnabledChanged(enabled: Boolean) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(
            autoConfig = _uiState.value.autoConfig.copy(enabled = enabled)
        )
    }

    fun onFanTempChanged(temp: Double) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        val rounded = (Math.round(temp * 2.0) / 2.0).coerceIn(20.0, 45.0)
        _uiState.value = _uiState.value.copy(
            autoConfig = _uiState.value.autoConfig.copy(fanTempOn = rounded)
        )
    }

    fun onNoPersonMinsChanged(mins: Int) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        val clamped = mins.coerceIn(1, 120)
        _uiState.value = _uiState.value.copy(
            autoConfig = _uiState.value.autoConfig.copy(noPersonMinutes = clamped)
        )
    }

    fun saveAutoConfig() {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        firebaseRepository.updateAutoConfig(_uiState.value.autoConfig)
        _uiState.value = _uiState.value.copy(
            toastMessage = "Thiết bị ESP32 áp dụng trong khoảng 30 giây"
        )
    }

    // Garden Light config
    fun onGardenModeChanged(mode: String) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(
            gardenConfig = _uiState.value.gardenConfig.copy(mode = mode)
        )
    }

    fun onGardenDarkBelowChanged(valPercent: Int) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(
            gardenConfig = _uiState.value.gardenConfig.copy(darkBelow = valPercent)
        )
    }

    fun onGardenBrightAboveChanged(valPercent: Int) {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        _uiState.value = _uiState.value.copy(
            gardenConfig = _uiState.value.gardenConfig.copy(brightAbove = valPercent)
        )
    }

    fun saveGardenConfig() {
        if (!checkAutoPermission()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Chủ nhà (Master) chưa cấp quyền chỉnh sửa cấu hình Tự động hóa cho bạn!")
            return
        }
        val config = _uiState.value.gardenConfig
        if (config.darkBelow >= config.brightAbove) {
            _uiState.value = _uiState.value.copy(
                toastMessage = "Ngưỡng tối phải nhỏ hơn ngưỡng sáng"
            )
            return
        }
        firebaseRepository.updateGardenConfig(config)
        _uiState.value = _uiState.value.copy(
            toastMessage = "Đã lưu cấu hình đèn sân"
        )
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
