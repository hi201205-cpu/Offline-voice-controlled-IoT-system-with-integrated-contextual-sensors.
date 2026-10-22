package com.example.doan2.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.data.model.PasscodeItem
import com.example.doan2.data.model.UserProfile
import com.example.doan2.ui.components.GlassCard
import com.example.doan2.ui.theme.*
import com.example.doan2.viewmodel.AuthViewModel
import com.example.doan2.viewmodel.DoorViewModel
import com.example.doan2.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    doorViewModel: DoorViewModel? = null,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val doorUiState = doorViewModel?.uiState?.collectAsState()?.value
    val context = LocalContext.current
    val themeController = LocalThemeController.current

    val isMaster = uiState.userProfile?.role == "master" || uiState.userEmail?.equals("xhai2005@gmail.com", ignoreCase = true) == true

    var memberToUnpair by remember { mutableStateOf<UserProfile?>(null) }
    var expandedMemberUids by remember { mutableStateOf(setOf<String>()) }

    var isThemeExpanded by remember { mutableStateOf(false) }
    var isKeypadExpanded by remember { mutableStateOf(false) }
    var isMembersExpanded by remember { mutableStateOf(false) }
    var isChangePassExpanded by remember { mutableStateOf(false) }
    var isProfileExpanded by remember { mutableStateOf(false) }

    var profileName by remember { mutableStateOf("") }
    var profilePhone by remember { mutableStateOf("") }
    var profileDob by remember { mutableStateOf("") }
    var profileGender by remember { mutableStateOf("") }
    var profileAddress by remember { mutableStateOf("") }
    var isSavingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let { prof ->
            profileName = prof.name
            profilePhone = prof.phone
            profileDob = prof.dob
            profileGender = prof.gender
            profileAddress = prof.address
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            settingsViewModel.clearToastMessage()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // Section 1: Title
        item {
            Text(
                text = "Cài đặt & Tùy chọn",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Section 2: Giao diện hiển thị (Collapsible Accordion)
        item {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    val currentThemeText = when (themeController.themeMode) {
                        AppThemeMode.LIGHT -> "Chế độ Sáng"
                        AppThemeMode.DARK -> "Chế độ Tối"
                        AppThemeMode.SYSTEM -> "Theo hệ thống"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThemeExpanded = !isThemeExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AuroraAccentStart.copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = AuroraAccentStart,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Giao diện hiển thị",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = currentThemeText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }

                        IconButton(
                            onClick = { isThemeExpanded = !isThemeExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isThemeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isThemeExpanded) "Thu gọn" else "Mở rộng",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (isThemeExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val themes = listOf(
                                Triple("Sáng", AppThemeMode.LIGHT, Icons.Default.LightMode),
                                Triple("Tối", AppThemeMode.DARK, Icons.Default.DarkMode),
                                Triple("Hệ thống", AppThemeMode.SYSTEM, Icons.Default.BrightnessAuto)
                            )

                            themes.forEach { (label, mode, icon) ->
                                val isSelected = themeController.themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .background(
                                            if (isSelected) AuroraAccentStart else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { themeController.updateTheme(mode) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Mật khẩu mở cửa Keypad (Collapsible Accordion)
        if (doorViewModel != null) {
            item {
                val passcodes = doorUiState?.passcodes ?: emptyList()
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isKeypadExpanded = !isKeypadExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AuroraAccentStart.copy(alpha = 0.2f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Pin,
                                            contentDescription = null,
                                            tint = AuroraAccentStart,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Mật khẩu Keypad",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${passcodes.size} mã PIN mô hình ESP32",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isKeypadExpanded = !isKeypadExpanded },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isKeypadExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isKeypadExpanded) "Thu gọn" else "Mở rộng",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (isKeypadExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Danh sách mã PIN cửa mô hình:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                                Button(
                                    onClick = { doorViewModel.openAddDialog() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Thêm mã mới", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (passcodes.isEmpty()) {
                                Text(
                                    text = "Chưa có mật khẩu nào.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    passcodes.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color.White.copy(alpha = 0.05f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.name,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                    val dateStr = if (item.createdTs > 0) {
                                                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.createdTs))
                                                    } else "Gần đây"
                                                    Text(
                                                        text = "Tạo lúc: $dateStr",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = { doorViewModel.confirmDeletePasscode(item) },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, StateError),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StateError),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text("Xóa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Quản lý mã mời & Thành viên (Collapsible Accordion)
        if (isMaster) {
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isMembersExpanded = !isMembersExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AuroraAccentEnd.copy(alpha = 0.2f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = null,
                                            tint = AuroraAccentEnd,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Quản lý mã mời & Thành viên",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${uiState.members.size} thành viên gia đình đã liên kết",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isMembersExpanded = !isMembersExpanded },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMembersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isMembersExpanded) "Thu gọn" else "Mở rộng",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (isMembersExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(14.dp))

                        // Generate Invite Code (1 time use)
                        Button(
                            onClick = { settingsViewModel.generateInviteCode() },
                            enabled = !uiState.isGeneratingInvite,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraAccentStart,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tạo mã mời mới (Dùng 1 lần)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        uiState.generatedInviteCode?.let { code ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AuroraAccentStart.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuroraAccentEnd),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Mã mời vừa tạo:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                        Text(code, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AuroraAccentEnd)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Thành viên gia đình đã liên kết (${uiState.members.size}):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.members.isEmpty()) {
                            Text(
                                text = "Chưa có thành viên nào liên kết.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                uiState.members.forEach { member ->
                                    val isExpanded = expandedMemberUids.contains(member.uid)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isExpanded) AuroraAccentStart.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        expandedMemberUids = if (isExpanded) expandedMemberUids - member.uid else expandedMemberUids + member.uid
                                                    },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = AuroraAccentStart.copy(alpha = 0.2f),
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = null,
                                                                tint = AuroraAccentStart,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = member.name.ifEmpty { member.email },
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                        Text(
                                                            text = member.email,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        expandedMemberUids = if (isExpanded) expandedMemberUids - member.uid else expandedMemberUids + member.uid
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = if (isExpanded) "Thu gọn" else "Chi tiết",
                                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }

                                            if (isExpanded) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                                Spacer(modifier = Modifier.height(10.dp))

                                                Text(
                                                    text = "Liên kết: HOME-2026-ESP32 · Vai trò: Thành viên",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Row 1: Quyền thiết bị
                                                Text(
                                                    text = "Quyền điều khiển thiết bị:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    MemberPermCheckbox("Đèn", member.permissions.light) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "light", it)
                                                    }
                                                    MemberPermCheckbox("Quạt", member.permissions.fan) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "fan", it)
                                                    }
                                                    MemberPermCheckbox("Rèm", member.permissions.curtain) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "curtain", it)
                                                    }
                                                    MemberPermCheckbox("Cửa chính", member.permissions.door) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "door", it)
                                                    }
                                                    MemberPermCheckbox("Đèn sân", member.permissions.yard) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "yard", it)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Row 2: Quyền nâng cao
                                                Text(
                                                    text = "Quyền nâng cao:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    MemberPermCheckbox("Cấu hình tự động", member.permissions.auto) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "auto", it)
                                                    }
                                                    MemberPermCheckbox("Thêm mật khẩu Keypad", member.permissions.passcodeAdd) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "passcodeAdd", it)
                                                    }
                                                    MemberPermCheckbox("Xóa mật khẩu Keypad", member.permissions.passcodeDel) {
                                                        settingsViewModel.updateMemberSinglePermission(member.uid, "passcodeDel", it)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                OutlinedButton(
                                                    onClick = { memberToUnpair = member },
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, StateError),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StateError),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Hủy liên kết thành viên này", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Section 5: Đổi mật khẩu tài khoản (Collapsible Accordion)
        item {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isChangePassExpanded = !isChangePassExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AuroraAccentEnd.copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = AuroraAccentEnd,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Đổi mật khẩu tài khoản",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Cập nhật mật khẩu đăng nhập",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }

                        IconButton(
                            onClick = { isChangePassExpanded = !isChangePassExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isChangePassExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isChangePassExpanded) "Thu gọn" else "Mở rộng",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (isChangePassExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                    // Current Password
                    OutlinedTextField(
                        value = uiState.currentPasswordInput,
                        onValueChange = { settingsViewModel.onCurrentPasswordChanged(it) },
                        label = { Text("Mật khẩu hiện tại") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { settingsViewModel.toggleShowCurrentPass() }) {
                                Icon(
                                    imageVector = if (uiState.showCurrentPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (uiState.showCurrentPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // New Password
                    OutlinedTextField(
                        value = uiState.newPasswordInput,
                        onValueChange = { settingsViewModel.onNewPasswordChanged(it) },
                        label = { Text("Mật khẩu mới (tối thiểu 6 ký tự)") },
                        leadingIcon = { Icon(imageVector = Icons.Default.LockReset, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { settingsViewModel.toggleShowNewPass() }) {
                                Icon(
                                    imageVector = if (uiState.showNewPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (uiState.showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Confirm New Password
                    OutlinedTextField(
                        value = uiState.confirmPasswordInput,
                        onValueChange = { settingsViewModel.onConfirmPasswordChanged(it) },
                        label = { Text("Xác nhận mật khẩu mới") },
                        leadingIcon = { Icon(imageVector = Icons.Default.LockReset, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { settingsViewModel.toggleShowConfirmPass() }) {
                                Icon(
                                    imageVector = if (uiState.showConfirmPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (uiState.showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { settingsViewModel.submitChangePassword() },
                        enabled = !uiState.isChangingPassword,
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text(
                            text = if (uiState.isChangingPassword) "Đang cập nhật..." else "Cập nhật mật khẩu mới",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    }
                }
            }
        }

        // Section 6: Thông tin tài khoản & Cập nhật hồ sơ (Collapsible Accordion)
        item {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isProfileExpanded = !isProfileExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AuroraAccentStart.copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = AuroraAccentStart,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Thông tin tài khoản & Hồ sơ",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${uiState.userEmail ?: "Chưa xác định"} · ${if (isMaster) "Chủ nhà" else "Thành viên"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }

                        IconButton(
                            onClick = { isProfileExpanded = !isProfileExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isProfileExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isProfileExpanded) "Thu gọn" else "Mở rộng",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (isProfileExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                    // Họ và tên
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Họ và tên") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Số điện thoại
                    OutlinedTextField(
                        value = profilePhone,
                        onValueChange = { profilePhone = it },
                        label = { Text("Số điện thoại") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ngày sinh & Giới tính trong cùng 1 hàng
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = profileDob,
                            onValueChange = { profileDob = it },
                            label = { Text("Ngày sinh (dd/mm/yyyy)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        )

                        OutlinedTextField(
                            value = profileGender,
                            onValueChange = { profileGender = it },
                            label = { Text("Giới tính") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Địa chỉ
                    OutlinedTextField(
                        value = profileAddress,
                        onValueChange = { profileAddress = it },
                        label = { Text("Địa chỉ nơi ở") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Nút cập nhật hồ sơ
                    Button(
                        onClick = {
                            isSavingProfile = true
                            settingsViewModel.updateUserProfile(
                                name = profileName,
                                phone = profilePhone,
                                dob = profileDob,
                                gender = profileGender,
                                address = profileAddress
                            ) { success, _ ->
                                isSavingProfile = false
                            }
                        },
                        enabled = !isSavingProfile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraAccentStart,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSavingProfile) "Đang lưu..." else "Cập nhật hồ sơ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Nút đăng xuất
                    Button(
                        onClick = {
                            authViewModel.logout()
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StateError.copy(alpha = 0.85f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Đăng xuất")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đăng xuất tài khoản",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    }
                }
            }
        }
    }

    // Confirmation Dialog: Unpair / Remove Member
    if (memberToUnpair != null) {
        val mem = memberToUnpair!!
        AlertDialog(
            onDismissRequest = { memberToUnpair = null },
            title = {
                Text(
                    text = "Xác nhận hủy liên kết",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Bạn có chắc chắn muốn ngắt quyền liên kết của thành viên '${mem.name.ifEmpty { mem.email }}' khỏi ngôi nhà không?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.unpairMember(mem.uid)
                        memberToUnpair = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StateError)
                ) {
                    Text("Hủy liên kết")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToUnpair = null }) {
                    Text("Quay lại")
                }
            }
        )
    }

    // Add Passcode Dialog (from doorViewModel)
    if (doorViewModel != null && doorUiState?.showAddDialog == true) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { doorViewModel.dismissAddDialog() },
            title = {
                Text(
                    text = stringResource(R.string.add_passcode_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên người dùng / mật khẩu") },
                        placeholder = { Text("VD: Bố mẹ, Khách") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) code = it },
                        label = { Text("Mã PIN (4 - 8 chữ số)") },
                        placeholder = { Text("1234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { doorViewModel.addPasscode(name, code) },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart)
                ) {
                    Text("Lưu mã")
                }
            },
            dismissButton = {
                TextButton(onClick = { doorViewModel.dismissAddDialog() }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Delete Passcode Confirmation Dialog (from doorViewModel)
    if (doorViewModel != null && doorUiState?.passcodeToDelete != null) {
        val itemToDelete = doorUiState.passcodeToDelete
        AlertDialog(
            onDismissRequest = { doorViewModel.dismissDeleteDialog() },
            title = {
                Text(
                    text = "Xóa mật khẩu",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Bạn có chắc muốn xóa mật khẩu '${itemToDelete?.name}' khỏi bàn phím ESP32 không?")
            },
            confirmButton = {
                Button(
                    onClick = { doorViewModel.executeDeletePasscode() },
                    colors = ButtonDefaults.buttonColors(containerColor = StateError)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { doorViewModel.dismissDeleteDialog() }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun MemberPermCheckbox(
    label: String,
    checked: Boolean,
    labelColor: Color = MaterialTheme.colorScheme.onBackground,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF00B4D8),
                checkmarkColor = Color.White,
                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor
        )
    }
}
