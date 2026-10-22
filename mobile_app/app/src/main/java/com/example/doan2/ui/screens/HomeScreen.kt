package com.example.doan2.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.ui.components.*
import com.example.doan2.ui.theme.*
import com.example.doan2.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    navigateToDoorTab: () -> Unit,
    navigateToSettingsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            homeViewModel.clearToastMessage()
        }
    }

    val presenceText = when (uiState.sensorData.presence) {
        "present" -> stringResource(R.string.presence_present)
        "leaving" -> stringResource(R.string.presence_leaving)
        else -> stringResource(R.string.presence_absent)
    }

    val summaryText = stringResource(
        R.string.header_summary_format,
        uiState.sensorData.temp,
        uiState.sensorData.hum,
        presenceText
    )

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Connection & Alert Banners
        ConnectionBanner(
            isDisconnected = homeViewModel.isDisconnected,
            lastSeen = uiState.deviceState.esp32LastSeen
        )
        DoorAlertBanner(
            alert = uiState.doorAlert,
            keypadLocked = uiState.deviceState.keypadLocked,
            onAck = { homeViewModel.ackDoorAlert() },
            onUnlockKeypad = { homeViewModel.unlockKeypad() }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Temperature & Humidity Card is the first hero card

            // Large Temperature / Humidity Tile
            item(span = { GridItemSpan(2) }) {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    onClick = { homeViewModel.openChartDialog() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Thermostat,
                                    contentDescription = stringResource(R.string.tile_temp_hum),
                                    tint = StateError,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.tile_temp_hum),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${uiState.sensorData.temp} °C  ·  ${uiState.sensorData.hum}%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Xem biểu đồ",
                            tint = AuroraAccentEnd,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Presence Tile
            item(span = { GridItemSpan(2) }) {
                val motionMs = if (uiState.sensorData.lastMotionTs < 10_000_000_000L) uiState.sensorData.lastMotionTs * 1000L else uiState.sensorData.lastMotionTs
                val motionTimeStr = if (uiState.sensorData.lastMotionTs > 0) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    stringResource(R.string.last_motion_format, sdf.format(Date(motionMs)))
                } else {
                    stringResource(R.string.no_motion_yet)
                }

                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AuroraAccentStart.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsRun,
                                    contentDescription = stringResource(R.string.tile_presence),
                                    tint = AuroraAccentEnd,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = presenceText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = motionTimeStr,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }

            // 1. Room Light Tile
            item {
                val isLightOn = uiState.deviceState.light
                DeviceTileCard(
                    title = stringResource(R.string.tile_light),
                    statusText = if (isLightOn) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    icon = if (isLightOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                    isOn = isLightOn,
                    isProcessing = uiState.processingLight,
                    isErrorText = uiState.lightError,
                    enabled = !homeViewModel.isDisconnected,
                    onClick = { homeViewModel.toggleLight() }
                )
            }

            // 2. Fan Tile
            item {
                val isFanOn = uiState.deviceState.fan
                DeviceTileCard(
                    title = stringResource(R.string.tile_fan),
                    statusText = if (isFanOn) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    icon = if (isFanOn) Icons.Filled.Air else Icons.Outlined.Air,
                    isOn = isFanOn,
                    isProcessing = uiState.processingFan,
                    isErrorText = uiState.fanError,
                    enabled = !homeViewModel.isDisconnected,
                    onClick = { homeViewModel.toggleFan() }
                )
            }

            // 3. Curtain Tile
            item {
                val isCurtainOpen = uiState.deviceState.curtain == "open"
                DeviceTileCard(
                    title = stringResource(R.string.tile_curtain),
                    statusText = if (isCurtainOpen) stringResource(R.string.state_open) else stringResource(R.string.state_closed),
                    icon = if (isCurtainOpen) Icons.Filled.Curtains else Icons.Outlined.Curtains,
                    isOn = isCurtainOpen,
                    isProcessing = uiState.processingCurtain,
                    isErrorText = uiState.curtainError,
                    enabled = !homeViewModel.isDisconnected,
                    onClick = { homeViewModel.toggleCurtain() }
                )
            }

            // 4. Garden Light Tile
            item {
                val isGardenOn = uiState.deviceState.garden

                DeviceTileCard(
                    title = stringResource(R.string.tile_garden),
                    statusText = if (isGardenOn) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    icon = if (isGardenOn) Icons.Filled.WbSunny else Icons.Outlined.WbSunny,
                    isOn = isGardenOn,
                    isProcessing = uiState.processingGarden,
                    isErrorText = uiState.gardenError,
                    enabled = !homeViewModel.isDisconnected,
                    onClick = { homeViewModel.toggleGarden() }
                )
            }

            // 5. Main Door Tile
            item {
                val isDoorOpen = uiState.deviceState.door == "open"
                val isKeypadLocked = uiState.deviceState.keypadLocked
                DeviceTileCard(
                    title = stringResource(R.string.tile_door),
                    statusText = when {
                        isKeypadLocked -> "Khóa an ninh bàn phím"
                        isDoorOpen -> stringResource(R.string.state_open)
                        else -> stringResource(R.string.state_closed)
                    },
                    icon = when {
                        isKeypadLocked -> Icons.Default.Lock
                        isDoorOpen -> Icons.Default.LockOpen
                        else -> Icons.Default.Lock
                    },
                    isOn = isDoorOpen && !isKeypadLocked,
                    isProcessing = uiState.processingDoor,
                    isErrorText = isKeypadLocked || uiState.doorError,
                    showSwitch = false,
                    enabled = !homeViewModel.isDisconnected && !isKeypadLocked,
                    onClick = {
                        if (!isKeypadLocked) {
                            homeViewModel.openDoor()
                        }
                    }
                )
            }

            // 6. Voice Recognition & LED Tile
            item {
                val isVoiceOn = uiState.deviceState.voice
                DeviceTileCard(
                    title = stringResource(R.string.tile_voice),
                    statusText = if (isVoiceOn) stringResource(R.string.state_on) else stringResource(R.string.state_off),
                    icon = if (isVoiceOn) Icons.Filled.Mic else Icons.Outlined.Mic,
                    isOn = isVoiceOn,
                    isProcessing = uiState.processingVoice,
                    isErrorText = uiState.voiceError,
                    enabled = !homeViewModel.isDisconnected,
                    onClick = { homeViewModel.toggleVoice() }
                )
            }
        }
    }

    // Automation Suggestion Dialog
    uiState.suggestionPending?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { homeViewModel.dismissSuggestion() },
            title = {
                Text(
                    text = "Gợi ý tự động hóa",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Gợi ý: ${suggestion.reason.ifEmpty { "Nhiệt độ phòng cao" }} (Nhiệt độ: ${suggestion.temp}°C). Bạn có muốn bật quạt không?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { homeViewModel.acceptSuggestion() }
                ) {
                    Text("Đồng ý")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { homeViewModel.dismissSuggestion() }
                ) {
                    Text("Bỏ qua")
                }
            }
        )
    }

    // Temperature & Humidity History Chart Dialog
    if (uiState.showChartDialog) {
        TempHumChartDialog(
            points = uiState.sensorHistory,
            onDismiss = { homeViewModel.closeChartDialog() }
        )
    }

    // Garden Light Detail BottomSheet
    if (uiState.showGardenSheet) {
        var mode by remember { mutableStateOf(uiState.gardenConfig.mode) }
        var darkBelow by remember { mutableFloatStateOf(uiState.gardenConfig.darkBelow.toFloat()) }
        var brightAbove by remember { mutableFloatStateOf(uiState.gardenConfig.brightAbove.toFloat()) }

        ModalBottomSheet(
            onDismissRequest = { homeViewModel.closeGardenSheet() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.garden_settings_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Switch Segmented Glass Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.garden_mode),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row {
                        FilterChip(
                            selected = mode == "auto",
                            onClick = { mode = "auto" },
                            label = { Text(stringResource(R.string.mode_auto)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = mode == "manual",
                            onClick = { mode = "manual" },
                            label = { Text(stringResource(R.string.mode_manual)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.light_level_format, uiState.sensorData.lightLevel),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.garden_dark_below, darkBelow.toInt()),
                    fontSize = 14.sp
                )
                Slider(
                    value = darkBelow,
                    onValueChange = { darkBelow = it },
                    valueRange = 0f..100f,
                    steps = 100
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.garden_bright_above, brightAbove.toInt()),
                    fontSize = 14.sp
                )
                Slider(
                    value = brightAbove,
                    onValueChange = { brightAbove = it },
                    valueRange = 0f..100f,
                    steps = 100
                )

                if (darkBelow >= brightAbove) {
                    Text(
                        text = stringResource(R.string.garden_error_threshold),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        homeViewModel.saveGardenConfig(mode, darkBelow.toInt(), brightAbove.toInt())
                    },
                    enabled = darkBelow < brightAbove,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.btn_save))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
