package com.example.doan2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doan2.data.model.UserProfile
import com.example.doan2.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val pass: String = "",
    val fullName: String = "",
    val confirmPass: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val currentUserEmail: String? = null,
    val userProfile: UserProfile? = null,
    val showPairingDialog: Boolean = false,
    val pairingCodeInput: String = "",
    val showForgotPasswordDialog: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = authRepository.isLoggedIn,
            currentUserEmail = authRepository.currentUserEmail
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (authRepository.isLoggedIn) {
            observeProfile()
        }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            authRepository.observeUserProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    currentUserEmail = profile?.email ?: authRepository.currentUserEmail
                )
            }
        }
    }

    fun onFullNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(fullName = newName, errorMessage = null)
    }

    fun onConfirmPassChanged(newConfirmPass: String) {
        _uiState.value = _uiState.value.copy(confirmPass = newConfirmPass, errorMessage = null)
    }

    fun onEmailChanged(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail, errorMessage = null)
    }

    fun onPassChanged(newPass: String) {
        _uiState.value = _uiState.value.copy(pass = newPass, errorMessage = null)
    }

    fun toggleAuthMode() {
        _uiState.value = _uiState.value.copy(
            isSignUpMode = !_uiState.value.isSignUpMode,
            errorMessage = null
        )
    }

    fun loginOrRegister(onSuccess: () -> Unit) {
        val currentState = _uiState.value



        if (currentState.email.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Vui lòng nhập Email")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(currentState.email).matches()) {
            _uiState.value = currentState.copy(errorMessage = "Email không đúng định dạng")
            return
        }
        if (currentState.pass.length < 6) {
            _uiState.value = currentState.copy(errorMessage = "Mật khẩu phải có ít nhất 6 ký tự")
            return
        }

        if (currentState.isSignUpMode) {
            if (currentState.confirmPass != currentState.pass) {
                _uiState.value = currentState.copy(errorMessage = "Mật khẩu xác nhận không khớp")
                return
            }
        }

        _uiState.value = currentState.copy(isLoading = true, errorMessage = null)

        val callback: (Boolean, String?) -> Unit = { success, error ->
            viewModelScope.launch {
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        currentUserEmail = authRepository.currentUserEmail,
                        errorMessage = null
                    )
                    observeProfile()
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error ?: "Thất bại"
                    )
                }
            }
        }

        if (currentState.isSignUpMode) {
            authRepository.signUp(currentState.email, currentState.pass, currentState.fullName, callback)
        } else {
            authRepository.signIn(currentState.email, currentState.pass, callback)
        }
    }

    fun loginWithGoogle(idToken: String, onSuccess: () -> Unit) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        authRepository.signInWithGoogle(idToken) { success, error ->
            viewModelScope.launch {
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        currentUserEmail = authRepository.currentUserEmail,
                        errorMessage = null
                    )
                    observeProfile()
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error ?: "Đăng nhập Google thất bại"
                    )
                }
            }
        }
    }

    fun openPairingDialog() {
        _uiState.value = _uiState.value.copy(showPairingDialog = true, pairingCodeInput = "")
    }

    fun closePairingDialog() {
        _uiState.value = _uiState.value.copy(showPairingDialog = false, pairingCodeInput = "")
    }

    fun onForgotPasswordClicked() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Vui lòng nhập email của bạn",
                toastMessage = "Vui lòng nhập email của bạn"
            )
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email không đúng định dạng")
            return
        }
        _uiState.value = _uiState.value.copy(showForgotPasswordDialog = true, errorMessage = null)
    }

    fun closeForgotPasswordDialog() {
        _uiState.value = _uiState.value.copy(showForgotPasswordDialog = false)
    }

    fun confirmSendPasswordReset() {
        val email = _uiState.value.email.trim()
        _uiState.value = _uiState.value.copy(isLoading = true, showForgotPasswordDialog = false)
        authRepository.sendPasswordResetEmail(email) { success, msg ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    toastMessage = msg ?: (if (success) "Đã gửi email khôi phục mật khẩu." else "Gửi thất bại"),
                    errorMessage = if (!success) msg else null
                )
            }
        }
    }

    fun onPairingCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(pairingCodeInput = code)
    }

    fun submitPairingCode() {
        val code = _uiState.value.pairingCodeInput.trim()
        if (code.isBlank()) {
            _uiState.value = _uiState.value.copy(toastMessage = "Vui lòng nhập mã liên kết HM-XXXX")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)
        authRepository.redeemInviteCode(code) { success, msg ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showPairingDialog = if (success) false else _uiState.value.showPairingDialog,
                    toastMessage = msg ?: (if (success) "Thành công" else "Thất bại")
                )
            }
        }
    }

    fun logout() {
        authRepository.signOut()
        _uiState.value = AuthUiState(
            isLoggedIn = false,
            currentUserEmail = null,
            userProfile = null
        )
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
