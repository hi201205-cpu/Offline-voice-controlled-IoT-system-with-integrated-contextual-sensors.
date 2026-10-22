package com.example.doan2.ui.screens



import android.widget.Toast

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.AutoAwesome

import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material.icons.filled.WbSunny

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.example.doan2.data.model.AutoAction

import com.example.doan2.data.model.AutoRuleItem

import com.example.doan2.ui.components.GlassCard

import com.example.doan2.ui.components.PillSwitch

import com.example.doan2.ui.theme.AuroraAccentEnd

import com.example.doan2.ui.theme.AuroraAccentStart

import com.example.doan2.ui.theme.StateError

import com.example.doan2.viewmodel.AutomationViewModel



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AutomationScreen(

    automationViewModel: AutomationViewModel,

    modifier: Modifier = Modifier

) {

    val uiState by automationViewModel.uiState.collectAsState()

    val context = LocalContext.current

    var ruleToDelete by remember { mutableStateOf<AutoRuleItem?>(null) }



    LaunchedEffect(uiState.toastMessage) {

        uiState.toastMessage?.let {

            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()

            automationViewModel.clearToastMessage()

        }

    }



    LazyColumn(

        modifier = modifier

            .fillMaxSize()

            .padding(horizontal = 16.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp),

        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)

    ) {

        // Section 1: Title & Master Switch

        item {

            GlassCard(

                shape = RoundedCornerShape(24.dp),

                modifier = Modifier.fillMaxWidth()

            ) {

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(20.dp)

                ) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Icon(

                                imageVector = Icons.Default.AutoAwesome,

                                contentDescription = null,

                                tint = AuroraAccentStart,

                                modifier = Modifier.size(28.dp)

                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {

                                Text(

                                    text = "Tự động hóa",

                                    fontSize = 20.sp,

                                    fontWeight = FontWeight.Bold,

                                    color = MaterialTheme.colorScheme.onBackground

                                )

                                Text(

                                    text = "${uiState.automationRules.count { it.enabled }} quy tắc đang bật",

                                    fontSize = 13.sp,

                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)

                                )

                            }

                        }



                        PillSwitch(

                            checked = uiState.automationMasterEnabled,

                            onCheckedChange = { automationViewModel.onAutomationMasterChanged(it) }

                        )

                    }

                }

            }

        }



        // Section 2: Custom Automation Rules List

        item {

            GlassCard(

                shape = RoundedCornerShape(24.dp),

                modifier = Modifier.fillMaxWidth()

            ) {

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(20.dp)

                ) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Text(

                            text = "Quy tắc tự động hóa",

                            fontSize = 17.sp,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onBackground

                        )



                        Button(

                            onClick = { automationViewModel.openAddRuleDialog() },

                            colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),

                            shape = RoundedCornerShape(12.dp),

                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

                        ) {

                            Text("＋ Thêm quy tắc", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        }

                    }



                    Spacer(modifier = Modifier.height(14.dp))



                    if (uiState.automationRules.isEmpty()) {

                        Text(

                            text = "Chưa có quy tắc tự động hóa nào.",

                            fontSize = 13.sp,

                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),

                            modifier = Modifier.padding(vertical = 12.dp)

                        )

                    } else {

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                            uiState.automationRules.forEach { rule ->

                                Surface(

                                    shape = RoundedCornerShape(16.dp),

                                    color = Color.White.copy(alpha = 0.05f),

                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),

                                    modifier = Modifier.fillMaxWidth()

                                ) {

                                    Column(modifier = Modifier.padding(14.dp)) {

                                        Row(

                                            modifier = Modifier.fillMaxWidth(),

                                            horizontalArrangement = Arrangement.SpaceBetween,

                                            verticalAlignment = Alignment.CenterVertically

                                        ) {

                                            Text(

                                                text = rule.name,

                                                fontSize = 15.sp,

                                                fontWeight = FontWeight.Bold,

                                                color = MaterialTheme.colorScheme.onBackground

                                            )

                                            PillSwitch(

                                                checked = rule.enabled,

                                                onCheckedChange = { automationViewModel.toggleRuleEnabled(rule.id, it) }

                                            )

                                        }



                                        Spacer(modifier = Modifier.height(6.dp))



                                        // Condition description

                                        val condText = when (rule.condition) {

                                            "temp_high" -> "Nhiệt độ cao hơn ${rule.threshold} °C"

                                            "temp_low" -> "Nhiệt độ thấp hơn ${rule.threshold} °C"

                                            "hum_high" -> "Độ ẩm cao hơn ${rule.threshold} %"

                                            "hum_low" -> "Độ ẩm thấp hơn ${rule.threshold} %"

                                            "no_motion" -> "Không có chuyển động trong ${rule.threshold.toInt()} phút"

                                            "motion" -> "Phát hiện có chuyển động"

                                            "dark" -> "Trời tối (cảm biến ánh sáng)"

                                            "time" -> "Khung giờ ${rule.from} - ${rule.to}"

                                            else -> rule.condition

                                        }



                                        val actText = rule.actions.joinToString(", ") { act ->

                                            val devName = when (act.device) {

                                                "light" -> "Đèn phòng"

                                                "fan" -> "Quạt"

                                                "yard" -> "Đèn sân"

                                                "curtain" -> "Rèm"

                                                "door" -> "Cửa"

                                                else -> act.device

                                            }

                                            "${if (act.value == "on") "Bật/Mở" else "Tắt/Đóng"} $devName"

                                        }



                                        Text(

                                            text = "NẾU: $condText",

                                            fontSize = 12.sp,

                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

                                        )

                                        Text(

                                            text = "THÌ: $actText · ${if (rule.mode == "ask") "Hỏi trước khi làm" else "Tự động làm luôn"}",

                                            fontSize = 12.sp,

                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

                                        )



                                        Spacer(modifier = Modifier.height(10.dp))



                                        Row(

                                            modifier = Modifier.fillMaxWidth(),

                                            horizontalArrangement = Arrangement.End,

                                            verticalAlignment = Alignment.CenterVertically

                                        ) {

                                            TextButton(

                                                onClick = { automationViewModel.testRule(rule) },

                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

                                            ) {

                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))

                                                Spacer(modifier = Modifier.width(4.dp))

                                                Text("Chạy thử", fontSize = 12.sp)

                                            }

                                            TextButton(

                                                onClick = { automationViewModel.openEditRuleDialog(rule) },

                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

                                            ) {

                                                Text("Sửa", fontSize = 12.sp)

                                            }

                                            TextButton(

                                                onClick = { ruleToDelete = rule },

                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),

                                                colors = ButtonDefaults.textButtonColors(contentColor = StateError)

                                            ) {

                                                Text("Xóa", fontSize = 12.sp)

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



    // Dialog Add/Edit Automation Rule

    if (uiState.showAddEditRuleDialog) {

        val editing = uiState.editingRule

        var ruleName by remember { mutableStateOf(editing?.name ?: "") }

        var condition by remember { mutableStateOf(editing?.condition ?: "temp_high") }

        var threshold by remember { mutableStateOf((editing?.threshold ?: 30.0).toString()) }

        var fromTime by remember { mutableStateOf(editing?.from ?: "18:00") }

        var toTime by remember { mutableStateOf(editing?.to ?: "22:00") }

        var actionDevice by remember { mutableStateOf(editing?.actions?.firstOrNull()?.device ?: "fan") }

        var actionValue by remember { mutableStateOf(editing?.actions?.firstOrNull()?.value ?: "on") }

        var mode by remember { mutableStateOf(editing?.mode ?: "auto") }



        AlertDialog(

            onDismissRequest = { automationViewModel.closeRuleDialog() },

            title = {

                Text(

                    text = if (editing != null) "Sửa quy tắc tự động" else "Thêm quy tắc mới",

                    fontWeight = FontWeight.Bold

                )

            },

            text = {

                Column(

                    modifier = Modifier.fillMaxWidth(),

                    verticalArrangement = Arrangement.spacedBy(10.dp)

                ) {

                    OutlinedTextField(

                        value = ruleName,

                        onValueChange = { ruleName = it },

                        label = { Text("Tên quy tắc") },

                        placeholder = { Text("Ví dụ: Bật quạt khi trời nóng") },

                        singleLine = true,

                        modifier = Modifier.fillMaxWidth()

                    )



                    Text("ĐIỀU KIỆN · NẾU", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuroraAccentEnd)

                    val conditions = listOf(

                        "temp_high" to "Nhiệt độ cao hơn X °C",

                        "temp_low" to "Nhiệt độ thấp hơn X °C",

                        "hum_high" to "Độ ẩm cao hơn X %",

                        "hum_low" to "Độ ẩm thấp hơn X %",

                        "no_motion" to "Không có người trong X phút",

                        "motion" to "Phát hiện chuyển động",

                        "dark" to "Trời tối (cảm biến ánh sáng)",

                        "time" to "Trong khung giờ cụ thể"

                    )



                    var condExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(

                        expanded = condExpanded,

                        onExpandedChange = { condExpanded = !condExpanded }

                    ) {

                        OutlinedTextField(

                            value = conditions.find { it.first == condition }?.second ?: condition,

                            onValueChange = {},

                            readOnly = true,

                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = condExpanded) },

                            modifier = Modifier.menuAnchor().fillMaxWidth()

                        )

                        ExposedDropdownMenu(

                            expanded = condExpanded,

                            onDismissRequest = { condExpanded = false }

                        ) {

                            conditions.forEach { (key, label) ->

                                DropdownMenuItem(

                                    text = { Text(label) },

                                    onClick = {

                                        condition = key

                                        condExpanded = false

                                    }

                                )

                            }

                        }

                    }



                    if (condition in listOf("temp_high", "temp_low", "hum_high", "hum_low", "no_motion")) {

                        OutlinedTextField(

                            value = threshold,

                            onValueChange = { threshold = it },

                            label = { Text("Ngưỡng giá trị (X)") },

                            singleLine = true,

                            modifier = Modifier.fillMaxWidth()

                        )

                    }



                    if (condition == "time") {

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                            OutlinedTextField(

                                value = fromTime,

                                onValueChange = { fromTime = it },

                                label = { Text("Từ giờ") },

                                modifier = Modifier.weight(1f)

                            )

                            OutlinedTextField(

                                value = toTime,

                                onValueChange = { toTime = it },

                                label = { Text("Đến giờ") },

                                modifier = Modifier.weight(1f)

                            )

                        }

                    }



                    Text("HÀNH ĐỘNG · THÌ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuroraAccentEnd)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        val devices = listOf("fan" to "Quạt", "light" to "Đèn phòng", "yard" to "Đèn sân", "curtain" to "Rèm", "door" to "Cửa")

                        var devExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(

                            expanded = devExpanded,

                            onExpandedChange = { devExpanded = !devExpanded },

                            modifier = Modifier.weight(1.2f)

                        ) {

                            OutlinedTextField(

                                value = devices.find { it.first == actionDevice }?.second ?: actionDevice,

                                onValueChange = {},

                                readOnly = true,

                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = devExpanded) },

                                modifier = Modifier.menuAnchor()

                            )

                            ExposedDropdownMenu(

                                expanded = devExpanded,

                                onDismissRequest = { devExpanded = false }

                            ) {

                                devices.forEach { (key, label) ->

                                    DropdownMenuItem(

                                        text = { Text(label) },

                                        onClick = {

                                            actionDevice = key

                                            devExpanded = false

                                        }

                                    )

                                }

                            }

                        }



                        val actions = listOf("on" to "Bật/Mở", "off" to "Tắt/Đóng")

                        var actExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(

                            expanded = actExpanded,

                            onExpandedChange = { actExpanded = !actExpanded },

                            modifier = Modifier.weight(1f)

                        ) {

                            OutlinedTextField(

                                value = actions.find { it.first == actionValue }?.second ?: actionValue,

                                onValueChange = {},

                                readOnly = true,

                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actExpanded) },

                                modifier = Modifier.menuAnchor()

                            )

                            ExposedDropdownMenu(

                                expanded = actExpanded,

                                onDismissRequest = { actExpanded = false }

                            ) {

                                actions.forEach { (key, label) ->

                                    DropdownMenuItem(

                                        text = { Text(label) },

                                        onClick = {

                                            actionValue = key

                                            actExpanded = false

                                        }

                                    )

                                }

                            }

                        }

                    }



                    Text("CÁCH THỰC HIỆN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuroraAccentEnd)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        FilterChip(

                            selected = mode == "auto",

                            onClick = { mode = "auto" },

                            label = { Text("Tự động làm luôn") }

                        )

                        FilterChip(

                            selected = mode == "ask",

                            onClick = { mode = "ask" },

                            label = { Text("Hỏi tôi trước") }

                        )

                    }

                }

            },

            confirmButton = {

                Button(

                    onClick = {

                        val newRule = AutoRuleItem(

                            id = editing?.id ?: "rule-${System.currentTimeMillis()}",

                            name = ruleName.trim().ifEmpty { "Quy tắc tự động" },

                            condition = condition,

                            threshold = threshold.toDoubleOrNull() ?: 30.0,

                            actions = listOf(AutoAction(type = "device", device = actionDevice, value = actionValue)),

                            mode = mode,

                            from = fromTime,

                            to = toTime,

                            enabled = editing?.enabled ?: true

                        )

                        automationViewModel.saveRule(newRule)

                    },

                    colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart)

                ) {

                    Text("Lưu quy tắc")

                }

            },

            dismissButton = {

                TextButton(onClick = { automationViewModel.closeRuleDialog() }) {

                    Text("Hủy")

                }

            }

        )

    }



    if (ruleToDelete != null) {

        AlertDialog(

            onDismissRequest = { ruleToDelete = null },

            title = {

                Text(

                    text = "Xác nhận xóa quy tắc",

                    fontWeight = FontWeight.Bold,

                    fontSize = 17.sp

                )

            },

            text = {

                Text(

                    text = "Bạn có chắc chắn muốn xóa quy tắc \"${ruleToDelete?.name}\" không? Thao tác này không thể hoàn tác.",

                    fontSize = 14.sp

                )

            },

            confirmButton = {

                Button(

                    onClick = {

                        val id = ruleToDelete?.id

                        ruleToDelete = null

                        if (!id.isNullOrBlank()) {

                            automationViewModel.deleteRule(id)

                        }

                    },

                    colors = ButtonDefaults.buttonColors(containerColor = StateError)

                ) {

                    Text("Xác nhận xóa", color = Color.White)

                }

            },

            dismissButton = {

                TextButton(onClick = { ruleToDelete = null }) {

                    Text("Hủy")

                }

            }

        )

    }

}



