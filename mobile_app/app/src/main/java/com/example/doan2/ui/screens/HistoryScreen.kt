package com.example.doan2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.ui.components.GlassCard
import com.example.doan2.ui.theme.*
import com.example.doan2.viewmodel.HistoryItem
import com.example.doan2.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatHistoryTimestamp(rawTs: Long): String {
    if (rawTs <= 0L) return "Giờ chưa đồng bộ"
    val sec = if (rawTs > 1_000_000_000_000L) rawTs / 1000L else rawTs
    if (sec < 1_700_000_000L) return "Giờ chưa đồng bộ"
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date(sec * 1000L))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by historyViewModel.uiState.collectAsState()

    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val filterLabels = listOf(
        "Tất cả",
        "Giọng nói",
        "Cửa / Keypad",
        "Web & App",
        "Tự động hóa"
    )

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { historyViewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Tìm kiếm theo tên, thiết bị, email...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm"
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { historyViewModel.onSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Xóa tìm kiếm")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Filter Chips Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filterLabels.size) { index ->
                FilterChip(
                    selected = uiState.selectedFilterIndex == index,
                    onClick = { historyViewModel.setFilterIndex(index) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AuroraAccentStart,
                        selectedLabelColor = Color.White,
                        containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F5F9),
                        labelColor = if (isDark) TextSecondaryDark else TextPrimaryLight
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.selectedFilterIndex == index,
                        borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFFD6E4F0),
                        selectedBorderColor = AuroraAccentStart
                    ),
                    label = {
                        Text(
                            text = filterLabels[index],
                            fontWeight = if (uiState.selectedFilterIndex == index) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        if (uiState.filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_history),
                    fontSize = 15.sp,
                    color = TextSecondaryDark
                )
            }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val newestKey = uiState.filteredItems.firstOrNull()?.let { "${it::class.simpleName}_${it.id}_${it.ts}" }
            LaunchedEffect(newestKey) {
                if (uiState.filteredItems.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.filteredItems, key = { "${it::class.simpleName}_${it.id}_${it.ts}" }) { item ->
                    HistoryLogCard(item = item)
                }
            }
        }
    }
}

@Composable
fun HistoryLogCard(item: HistoryItem) {
    val timeStr = formatHistoryTimestamp(item.ts)

    when (item) {
        is HistoryItem.Door -> {
            val log = item.log
            val isLockout = log.event == "lockout" || log.event == "locked"
            val isWrongPass = log.event == "wrong_password" || log.event == "denied"
            val isUnlockKeypad = log.event == "unlocked_keypad" || log.event == "keypad_unlock"
            val isError = isLockout || isWrongPass
            val isAutoClose = log.source == "auto"

            val eventTitle: String
            val detailStr: String
            val sourceBadge: String

            when {
                isLockout -> {
                    eventTitle = "Khóa an ninh bàn phím (4 lần sai)"
                    detailStr = "Nhập sai mật khẩu 4 lần liên tiếp; bàn phím đã bị khóa cứng"
                    sourceBadge = "Bàn phím ngoài"
                }
                isWrongPass -> {
                    eventTitle = "Từ chối mở cửa (Sai mật khẩu)"
                    detailStr = log.reason?.takeIf { it.isNotBlank() } ?: "Nhập sai mật khẩu trên mô hình"
                    sourceBadge = "Bàn phím ngoài"
                }
                isUnlockKeypad -> {
                    eventTitle = "Mở khóa bàn phím ngoài"
                    detailStr = log.reason?.takeIf { it.isNotBlank() } ?: "Đã mở khóa an ninh từ xa"
                    sourceBadge = "Bàn phím ngoài"
                }
                log.source == "keypad" -> {
                    val whoStr = if (!log.who.isNullOrEmpty()) " (${log.who})" else ""
                    eventTitle = "Mở cửa bằng bàn phím Keypad$whoStr"
                    detailStr = "Nhập đúng mật khẩu$whoStr trên mô hình"
                    sourceBadge = "Bàn phím ngoài"
                }
                log.source == "button" -> {
                    eventTitle = "Bấm nút mở cửa trong nhà"
                    detailStr = "Nhấn nút vật lý (GPIO 47) trên mô hình"
                    sourceBadge = "Nút bấm vật lý"
                }
                isAutoClose -> {
                    eventTitle = "Tự động đóng cửa"
                    detailStr = log.reason?.takeIf { it.isNotBlank() } ?: "Tự động đóng sau 5 giây"
                    sourceBadge = "Tự động"
                }
                log.event == "open" -> {
                    eventTitle = "Mở cửa chính"
                    detailStr = log.reason?.takeIf { it.isNotBlank() } ?: (if (!log.who.isNullOrEmpty()) "Thực hiện bởi: ${log.who}" else "Mở cửa")
                    sourceBadge = if (log.source.isNotEmpty()) log.source else "Cửa chính"
                }
                log.event == "close" -> {
                    eventTitle = "Đóng cửa chính"
                    detailStr = log.reason?.takeIf { it.isNotBlank() } ?: "Đóng cửa"
                    sourceBadge = if (log.source.isNotEmpty()) log.source else "Cửa chính"
                }
                else -> {
                    eventTitle = log.event.ifEmpty { "Thao tác cửa" }
                    detailStr = log.reason ?: ""
                    sourceBadge = if (log.source.isNotEmpty()) log.source else "Cửa chính"
                }
            }

            GlassCard(
                shape = RoundedCornerShape(16.dp),
                fillColor = if (isError) DoorAlertRedBg else null,
                borderColor = if (isError) StateError.copy(alpha = 0.6f) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isError) StateError.copy(alpha = 0.25f) else Color(0xFF16A34A).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "[$sourceBadge]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isError) StateError else Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = timeStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isError) StateError else AuroraAccentEnd
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isError) StateError.copy(alpha = 0.25f) else AuroraAccentStart.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when {
                                        isLockout -> Icons.Default.Lock
                                        isWrongPass -> Icons.Default.Warning
                                        isAutoClose -> Icons.Default.LockClock
                                        else -> Icons.Default.DoorFront
                                    },
                                    contentDescription = eventTitle,
                                    tint = if (isError) StateError else AuroraAccentEnd,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = eventTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isError) StateError else MaterialTheme.colorScheme.onBackground
                            )
                            if (detailStr.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = detailStr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isError) StateError.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }

        is HistoryItem.Web -> {
            val log = item.log
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge showing who performed it (Hiển thị Email người thực thi)
                        val emailOnly = if (log.user.contains("@")) log.user else (if (log.userName.contains("@")) log.userName else log.user.ifEmpty { "Người dùng" })
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEA580C).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "Bởi: $emailOnly",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFB923C),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = timeStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuroraAccentEnd
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEA580C).copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = log.label,
                                    tint = Color(0xFFFB923C),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = log.label.ifEmpty { "${log.action} · ${log.device}" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val executorEmail = if (log.user.contains("@")) log.user else (if (log.userName.contains("@")) log.userName else log.user.ifEmpty { "N/A" })
                            Text(
                                text = "Thực hiện bởi: $executorEmail",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }

        is HistoryItem.Auto -> {
            val log = item.log
            val actionTitle = when (log.action) {
                "fan_on" -> stringResource(R.string.log_auto_fan_on)
                "fan_off" -> stringResource(R.string.log_auto_fan_off)
                "light_off" -> stringResource(R.string.log_auto_light_off)
                "garden_on" -> stringResource(R.string.log_auto_garden_on)
                "garden_off" -> stringResource(R.string.log_auto_garden_off)
                else -> log.action
            }

            GlassCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Purple Badge [Tự động hóa]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF9333EA).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "[Tự động hóa]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC084FC),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = timeStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuroraAccentEnd
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF9333EA).copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SmartButton,
                                    contentDescription = actionTitle,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = actionTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (log.reason.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.log_auto_reason, log.reason),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }

        is HistoryItem.Voice -> {
            val log = item.log
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Blue Badge [Giọng nói]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "[Giọng nói]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = timeStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuroraAccentEnd
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Giọng nói",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = stringResource(R.string.log_voice_keyword, log.keyword),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.log_voice_confidence, log.confidence),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}
