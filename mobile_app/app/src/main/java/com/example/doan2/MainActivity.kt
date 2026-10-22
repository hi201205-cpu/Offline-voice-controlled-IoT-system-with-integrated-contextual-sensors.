package com.example.doan2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.doan2.ui.screens.LoginScreen
import com.example.doan2.ui.screens.MainContainerScreen
import com.example.doan2.ui.theme.Doan2Theme
import com.example.doan2.viewmodel.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeController = remember { com.example.doan2.ui.theme.ThemeController(applicationContext) }
            Doan2Theme(themeController = themeController) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val authState by authViewModel.uiState.collectAsState()

                    var isLoggedIn by remember(authState.isLoggedIn) {
                        mutableStateOf(authState.isLoggedIn)
                    }

                    if (isLoggedIn) {
                        val sessionKey = authState.currentUserEmail ?: "default_user"
                        key(sessionKey) {
                            val homeViewModel: HomeViewModel = viewModel(key = "home_$sessionKey")
                            val doorViewModel: DoorViewModel = viewModel(key = "door_$sessionKey")
                            val historyViewModel: HistoryViewModel = viewModel(key = "history_$sessionKey")
                            val settingsViewModel: SettingsViewModel = viewModel(key = "settings_$sessionKey")
                            val automationViewModel: AutomationViewModel = viewModel(key = "automation_$sessionKey")

                            MainContainerScreen(
                                authViewModel = authViewModel,
                                homeViewModel = homeViewModel,
                                doorViewModel = doorViewModel,
                                historyViewModel = historyViewModel,
                                settingsViewModel = settingsViewModel,
                                automationViewModel = automationViewModel,
                                onLogout = { isLoggedIn = false }
                            )
                        }
                    } else {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onLoginSuccess = { isLoggedIn = true }
                        )
                    }
                }
            }
        }
    }
}
