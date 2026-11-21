package com.example.doan2.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.doan2.data.BleProvisioningManager
import com.example.doan2.data.BleStatus
import com.example.doan2.data.DiscoveredBleDevice
import com.example.doan2.data.getConnectedWifiSsid
import com.example.doan2.data.isProvisioning
import com.example.doan2.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BleProvisioningDialog(
    currentUserEmail: String,
    currentUid: String = "",
    onDismiss: () -> Unit,
    onSuccess: (homeId: String) -> Unit
) {
    val context = LocalContext.current
    val bleManager = remember { BleProvisioningManager(context) }
    val bleStatus by bleManager.status.collectAsState()
    val isBusy = bleStatus.isProvisioning || bleStatus == BleStatus.SUCCESS
    val bleStatusMsg by bleManager.statusMessage.collectAsState()
    val discoveredBleDevices by bleManager.discoveredDevices.collectAsState()

    var selectedDevice by remember { mutableStateOf<DiscoveredBleDevice?>(null) }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var wifiPassVisible by remember { mutableStateOf(false) }
    var homeId by remember { mutableStateOf("HOME-2026-ESP32") }

    val scannedWifiNetworks by bleManager.scannedWifiNetworks.collectAsState()
    val isScanningWifi by bleManager.isScanningWifi.collectAsState()

    LaunchedEffect(selectedDevice) {
        if (selectedDevice != null) {
            bleManager.stopScan()
            bleManager.scanWifiNetworks()
            val autoSsid = getConnectedWifiSsid(context)
            if (autoSsid.isNotBlank() && wifiSsid.isBlank()) {
                wifiSsid = autoSsid
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val subCardBg = if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9)
    val subCardBorder = if (isDark) Color(0xFF475569) else Color(0xFFE2E8F0)

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        if (granted) {
            bleManager.startScan()
        } else {
            Toast.makeText(context, "Cần cấp quyền Bluetooth để quét thiết bị", Toast.LENGTH_SHORT).show()
        }
    }

    val checkAndStartScan = {
        val needsPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermissions) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            blePermissionLauncher.launch(perms)
        } else {
            bleManager.startScan()
        }
    }

    // Auto-detect Wi-Fi and start scan on open
    LaunchedEffect(Unit) {
        val autoSsid = getConnectedWifiSsid(context)
        if (autoSsid.isNotBlank()) {
            wifiSsid = autoSsid
        }
        checkAndStartScan()
    }

    // Trigger success callback when provision succeeds
    LaunchedEffect(bleStatus) {
        if (bleStatus == BleStatus.SUCCESS) {
            onSuccess(homeId.trim().ifEmpty { "HOME-2026-ESP32" })
            delay(1800)
            bleManager.stopScan()
            bleManager.disconnect()
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bleManager.stopScan()
            bleManager.disconnect()
        }
    }

    Dialog(
        onDismissRequest = {
            if (bleStatus != BleStatus.SENDING_CONFIG && bleStatus != BleStatus.ESP_CONNECTING_WIFI) {
                bleManager.stopScan()
                bleManager.disconnect()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBg,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AuroraAccentStart.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = AuroraAccentStart,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (selectedDevice == null) "Quét tìm thiết bị" else "Cài đặt Wi-Fi",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Kết nối qua Bluetooth",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            bleManager.stopScan()
                            bleManager.disconnect()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (bleStatus) {
                        BleStatus.SUCCESS -> StateOk.copy(alpha = 0.15f)
                        BleStatus.FAILED, BleStatus.ERROR -> StateError.copy(alpha = 0.15f)
                        BleStatus.SCANNING, BleStatus.CONNECTING_GATT, BleStatus.ESP_CONNECTING_WIFI, BleStatus.SENDING_CONFIG -> AuroraAccentStart.copy(alpha = 0.12f)
                        else -> subCardBg
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bleStatus == BleStatus.SCANNING ||
                            bleStatus == BleStatus.CONNECTING_GATT ||
                            bleStatus == BleStatus.SENDING_CONFIG ||
                            bleStatus == BleStatus.ESP_CONNECTING_WIFI) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AuroraAccentStart
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        } else if (bleStatus == BleStatus.SUCCESS) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StateOk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Text(
                            text = bleStatusMsg,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (bleStatus) {
                                BleStatus.SUCCESS -> StateOk
                                BleStatus.FAILED, BleStatus.ERROR -> StateError
                                BleStatus.SCANNING, BleStatus.CONNECTING_GATT, BleStatus.ESP_CONNECTING_WIFI, BleStatus.SENDING_CONFIG -> AuroraAccentStart
                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Phase 1: Device List
                if (selectedDevice == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thiết bị tìm thấy (${discoveredBleDevices.size})",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (bleStatus == BleStatus.SCANNING) {
                            TextButton(
                                onClick = { bleManager.stopScan() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Dừng", fontSize = 12.sp, color = StateError)
                            }
                        } else {
                            TextButton(
                                onClick = { checkAndStartScan() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quét lại", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (discoveredBleDevices.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = subCardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (bleStatus == BleStatus.SCANNING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp,
                                        color = AuroraAccentStart
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Đang tìm kiếm thiết bị ở gần...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothDisabled,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Chưa phát hiện thiết bị nào.",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "💡 Mẹo: Nhấn giữ nút trên thiết bị trong 5 giây để bật chế độ cài đặt Bluetooth.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            items(discoveredBleDevices) { dev ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = subCardBg,
                                    border = BorderStroke(1.dp, subCardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedDevice = dev
                                            val autoSsid = getConnectedWifiSsid(context)
                                            if (autoSsid.isNotBlank()) wifiSsid = autoSsid
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = dev.name.ifEmpty { "Thiết bị SmartHome" },
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = dev.address,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                selectedDevice = dev
                                                val autoSsid = getConnectedWifiSsid(context)
                                                if (autoSsid.isNotBlank()) wifiSsid = autoSsid
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("Chọn", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Phase 2: Enter Wi-Fi Credentials for selected device
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AuroraAccentStart.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, AuroraAccentStart.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = AuroraAccentStart,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedDevice?.name?.ifEmpty { "Thiết bị SmartHome" } ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = selectedDevice?.address ?: "",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                                TextButton(
                                    onClick = { selectedDevice = null },
                                    enabled = !isBusy,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Đổi", fontSize = 11.5.sp)
                                }
                            }
                        }

                        // Danh sách Wi-Fi xung quanh
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chọn mạng Wi-Fi (${scannedWifiNetworks.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(
                                onClick = { bleManager.scanWifiNetworks() },
                                enabled = !isBusy && !isScanningWifi,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                if (isScanningWifi) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = AuroraAccentStart)
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Quét lại", fontSize = 11.5.sp)
                            }
                        }

                        if (scannedWifiNetworks.isNotEmpty()) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 135.dp)
                            ) {
                                items(scannedWifiNetworks) { wifiItem ->
                                    val isSelected = wifiSsid == wifiItem.ssid
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AuroraAccentStart.copy(alpha = 0.15f) else subCardBg,
                                        border = BorderStroke(1.dp, if (isSelected) AuroraAccentStart else subCardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isBusy) { wifiSsid = wifiItem.ssid }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    tint = if (isSelected) AuroraAccentStart else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = wifiItem.ssid,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (wifiItem.isConnected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = StateOk.copy(alpha = 0.18f),
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Đang kết nối",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = StateOk,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (wifiItem.is24GHz) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AuroraAccentStart.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "2.4G",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = AuroraAccentStart,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = wifiSsid,
                            enabled = !isBusy,
                            onValueChange = { wifiSsid = it },
                            label = { Text("Tên mạng Wi-Fi (SSID)") },
                            leadingIcon = {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = wifiPass,
                            enabled = !isBusy,
                            onValueChange = { wifiPass = it },
                            label = { Text("Mật khẩu Wi-Fi") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { wifiPassVisible = !wifiPassVisible }) {
                                    Icon(
                                        imageVector = if (wifiPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (wifiPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = homeId,
                            enabled = !isBusy,
                            onValueChange = { homeId = it },
                            label = { Text("Mã ngôi nhà (Home ID)") },
                            leadingIcon = {
                                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedDevice = null },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Text("Quay lại")
                            }

                            Button(
                                onClick = {
                                    if (wifiSsid.isNotBlank()) {
                                        selectedDevice?.device?.let { dev ->
                                            bleManager.connectAndSendWifi(
                                                device = dev,
                                                ssid = wifiSsid,
                                                pass = wifiPass,
                                                homeId = homeId.trim().ifEmpty { "HOME-2026-ESP32" },
                                                ownerEmail = currentUserEmail,
                                                ownerUid = currentUid
                                            )
                                        }
                                    } else {
                                        Toast.makeText(context, "Vui lòng nhập tên Wi-Fi", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isBusy && wifiSsid.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(46.dp)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Đang nạp...", fontSize = 13.sp)
                                } else {
                                    Text("Gửi cấu hình", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
