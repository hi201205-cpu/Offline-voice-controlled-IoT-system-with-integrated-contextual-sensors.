package com.example.doan2.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.ui.components.AuroraBackground
import com.example.doan2.ui.components.GlassCard
import com.example.doan2.ui.components.UnpairedLockOverlay
import com.example.doan2.ui.theme.*
import com.example.doan2.viewmodel.*
import java.util.Calendar

sealed class BottomTab(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    object Home : BottomTab("home", R.string.tab_home, Icons.Default.Home)
    object Automation : BottomTab("automation", R.string.tab_auto, Icons.Default.AutoAwesome)
    object History : BottomTab("history", R.string.tab_history, Icons.Default.History)
    object Settings : BottomTab("settings", R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainContainerScreen(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    doorViewModel: DoorViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    automationViewModel: AutomationViewModel,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf<BottomTab>(BottomTab.Home) }
    val authState by authViewModel.uiState.collectAsState()
    val profile = authState.userProfile
    val context = LocalContext.current

    LaunchedEffect(authState.toastMessage) {
        authState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            authViewModel.clearToastMessage()
        }
    }

    val tabs = listOf(
        BottomTab.Home,
        BottomTab.Automation,
        BottomTab.History,
        BottomTab.Settings
    )

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingText = if (currentHour in 5..11) "Chào buổi sáng" else "Chào buổi tối"

    val isMaster = profile?.role == "master" || authState.currentUserEmail?.equals("xhai2005@gmail.com", ignoreCase = true) == true
    val isPaired = profile?.isPaired == true || isMaster

    AuroraBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    // Clean Modern Top Header
                    val displayName = profile?.name?.ifEmpty { null }
                        ?: authState.currentUserEmail?.substringBefore("@")
                        ?: "Bạn"

                    GlassCard(
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$greetingText, $displayName!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isMaster -> StateOk.copy(alpha = 0.25f)
                                        !isPaired -> StateWarning.copy(alpha = 0.25f)
                                        else -> AuroraAccentStart.copy(alpha = 0.25f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            isMaster -> "Master"
                                            !isPaired -> "Chưa liên kết"
                                            else -> "Thành viên"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isMaster -> StateOk
                                            !isPaired -> StateWarning
                                            else -> AuroraAccentEnd
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    // Floating Glass Bottom Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        GlassCard(
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tabs.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    val interactionSource = remember { MutableInteractionSource() }

                                    val pillBackground = if (isSelected) {
                                        Brush.horizontalGradient(listOf(AuroraAccentStart, AuroraAccentEnd))
                                    } else {
                                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(pillBackground)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null
                                            ) {
                                                selectedTab = tab
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = stringResource(tab.titleRes),
                                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(tab.titleRes),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    if (!isPaired && selectedTab != BottomTab.Settings) {
                        UnpairedLockOverlay(
                            showDialog = authState.showPairingDialog,
                            codeInput = authState.pairingCodeInput,
                            isLoading = authState.isLoading,
                            onOpenDialog = { authViewModel.openPairingDialog() },
                            onCloseDialog = { authViewModel.closePairingDialog() },
                            onCodeChange = { authViewModel.onPairingCodeChanged(it) },
                            onSubmitCode = { authViewModel.submitPairingCode() }
                        )
                    } else {
                        when (selectedTab) {
                            is BottomTab.Home -> {
                                HomeScreen(
                                    homeViewModel = homeViewModel,
                                    navigateToDoorTab = { selectedTab = BottomTab.Automation },
                                    navigateToSettingsTab = { selectedTab = BottomTab.Settings }
                                )
                            }
                            is BottomTab.Automation -> {
                                AutomationScreen(
                                    automationViewModel = automationViewModel
                                )
                            }
                            is BottomTab.History -> {
                                HistoryScreen(
                                    historyViewModel = historyViewModel
                                )
                            }
                            is BottomTab.Settings -> {
                                SettingsScreen(
                                    settingsViewModel = settingsViewModel,
                                    authViewModel = authViewModel,
                                    doorViewModel = doorViewModel,
                                    onLogout = onLogout
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
