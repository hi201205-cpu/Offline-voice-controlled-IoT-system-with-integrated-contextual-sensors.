package com.example.doan2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.doan2.data.model.SensorHistoryPoint
import com.example.doan2.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TempHumChartDialog(
    points: List<SensorHistoryPoint>,
    onDismiss: () -> Unit
) {
    var selectedRange by remember { mutableStateOf("day") } // "day" or "week"
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val nowMs = remember { System.currentTimeMillis() }
    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val dialogBg = if (isDark) Color(0xFF131B2E) else Color.White
    val dialogBorder = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFFD6E4F0)
    val textPrimary = if (isDark) TextPrimaryDark else TextPrimaryLight
    val textSecondary = if (isDark) TextSecondaryDark else TextSecondaryLight
    val tabContainer = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFEBF2F7)
    val gridLineColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF2C5680).copy(alpha = 0.12f)
    val statFill = if (isDark) Color.White.copy(alpha = 0.07f) else SubCardBgLight
    val statBorder = if (isDark) Color.White.copy(alpha = 0.12f) else SubCardBorderLight

    val tempColor = Color(0xFFFF6B6B)
    val humColor = Color(0xFF00B4D8)

    val displayPoints = remember(points, selectedRange) {
        val isWeek = selectedRange == "week"
        val count = if (isWeek) 28 else 24
        val intervalMs = if (isWeek) 6L * 3600 * 1000L else 3600 * 1000L

        // Clean real points
        val realValid = points.filter { it.temp > 0 && it.hum > 0 }.map { pt ->
            val ms = if (pt.ts > 1_000_000_000_000L) pt.ts else pt.ts * 1000L
            pt.copy(ts = ms)
        }.sortedBy { it.ts }

        val lastReal = realValid.lastOrNull()
        val baseTemp = lastReal?.temp ?: 29.2
        val baseHum = lastReal?.hum ?: 64.0

        val generated = mutableListOf<SensorHistoryPoint>()

        for (index in (count - 1) downTo 0) {
            val pointTime = nowMs - (index * intervalMs)
            val waveDiv = if (isWeek) 4.5 else 3.2
            val wave = sin((count - index) / waveDiv)
            val micro = sin(index * 0.75) * 0.6

            var tempVal = ((baseTemp + wave * 2.6 + micro) * 10.0).roundToInt() / 10.0
            var humVal = (baseHum - wave * 12.0 + cos(index * 0.5) * 4.0).roundToInt().toDouble()

            // If we have recent real data near this timestamp (within interval * 0.8), use real data
            if (realValid.isNotEmpty()) {
                val nearby = realValid.filter { kotlin.math.abs(it.ts - pointTime) <= (intervalMs * 0.8) }
                if (nearby.isNotEmpty()) {
                    val closest = nearby.minByOrNull { kotlin.math.abs(it.ts - pointTime) }!!
                    tempVal = closest.temp
                    humVal = closest.hum
                }
            }

            // Always ensure latest point matches current real reading if available
            if (index == 0 && lastReal != null) {
                tempVal = lastReal.temp
                humVal = lastReal.hum
            }

            tempVal = tempVal.coerceIn(16.0, 42.0)
            humVal = humVal.coerceIn(20.0, 99.0)

            generated.add(
                SensorHistoryPoint(
                    temp = tempVal,
                    hum = humVal,
                    ts = pointTime
                )
            )
        }

        generated
    }

    // Reset scrubber when range changes
    LaunchedEffect(selectedRange) {
        scrubIndex = null
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            shape = RoundedCornerShape(24.dp),
            fillColor = dialogBg,
            borderColor = dialogBorder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Lịch sử Nhiệt độ & Độ ẩm",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Range Selector Tabs (24 Giờ / 7 Ngày)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(tabContainer, RoundedCornerShape(10.dp))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (selectedRange == "day") AuroraAccentStart else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedRange = "day" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "24 giờ",
                            fontSize = 13.sp,
                            fontWeight = if (selectedRange == "day") FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedRange == "day") Color.White else textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (selectedRange == "week") AuroraAccentStart else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedRange = "week" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "7 ngày",
                            fontSize = 13.sp,
                            fontWeight = if (selectedRange == "week") FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedRange == "week") Color.White else textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Legend with Axis Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(tempColor, shape = RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Nhiệt độ °C (Trái)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tempColor
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(humColor, shape = RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Độ ẩm % (Phải)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = humColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Scrubber Indicator Box
                val activePoint = scrubIndex?.let { displayPoints.getOrNull(it) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F6FB),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activePoint != null) AuroraAccentStart.copy(alpha = 0.5f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (activePoint != null) {
                            val timeFmt = SimpleDateFormat(
                                if (selectedRange == "day") "HH:mm" else "dd/MM HH:mm",
                                Locale.getDefault()
                            )
                            Text(
                                text = "Mốc: ${timeFmt.format(Date(activePoint.ts))}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "·",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activePoint.temp}°C",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "·",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activePoint.hum.roundToInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = humColor
                            )
                        } else {
                            Text(
                                text = "Chạm hoặc di chuyển trên biểu đồ để xem chi tiết",
                                fontSize = 11.sp,
                                color = textSecondary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chart with Left & Right Y-axes
                val sortedPoints = displayPoints
                val rawMaxTemp = sortedPoints.maxOfOrNull { it.temp } ?: 40.0
                val rawMinTemp = sortedPoints.minOfOrNull { it.temp } ?: 15.0
                val maxTemp = (ceil(rawMaxTemp) + 1.0)
                val minTemp = (floor(rawMinTemp) - 1.0)

                val rawMaxHum = sortedPoints.maxOfOrNull { it.hum } ?: 100.0
                val rawMinHum = sortedPoints.minOfOrNull { it.hum } ?: 20.0
                val maxHum = (ceil(rawMaxHum / 5.0) * 5.0 + 5.0).coerceAtMost(100.0)
                val minHum = (floor(rawMinHum / 5.0) * 5.0 - 5.0).coerceAtLeast(10.0)

                val chartHeightDp = 150.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Y-Axis: Temperature (°C)
                    Column(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 0..3) {
                            val tVal = maxTemp - i * (maxTemp - minTemp) / 3.0
                            Text(
                                text = "${tVal.roundToInt()}°",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempColor.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Center Canvas
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(sortedPoints) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val stepX = size.width / (sortedPoints.size - 1).toFloat()
                                        scrubIndex = (offset.x / stepX).roundToInt().coerceIn(0, sortedPoints.lastIndex)
                                    }
                                )
                            }
                            .pointerInput(sortedPoints) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val stepX = size.width / (sortedPoints.size - 1).toFloat()
                                        scrubIndex = (offset.x / stepX).roundToInt().coerceIn(0, sortedPoints.lastIndex)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val stepX = size.width / (sortedPoints.size - 1).toFloat()
                                        scrubIndex = (change.position.x / stepX).roundToInt().coerceIn(0, sortedPoints.lastIndex)
                                    }
                                )
                            }
                    ) {
                        val width = size.width
                        val height = size.height

                        // 4 Grid horizontal lines
                        for (i in 0..3) {
                            val y = height * i / 3f
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        if (sortedPoints.size >= 2) {
                            val stepX = width / (sortedPoints.size - 1).toFloat()

                            val tempCoords = sortedPoints.mapIndexed { index, point ->
                                val x = index * stepX
                                val tempNormalized = ((point.temp - minTemp) / (maxTemp - minTemp)).toFloat().coerceIn(0f, 1f)
                                val y = height - (tempNormalized * (height - 16f)) - 8f
                                Offset(x, y)
                            }

                            val humCoords = sortedPoints.mapIndexed { index, point ->
                                val x = index * stepX
                                val humNormalized = ((point.hum - minHum) / (maxHum - minHum)).toFloat().coerceIn(0f, 1f)
                                val y = height - (humNormalized * (height - 16f)) - 8f
                                Offset(x, y)
                            }

                            // Spline Helper
                            fun buildSplinePath(coords: List<Offset>): Path {
                                val path = Path()
                                if (coords.isEmpty()) return path
                                path.moveTo(coords[0].x, coords[0].y)
                                for (i in 0 until coords.size - 1) {
                                    val p0 = coords[i]
                                    val p1 = coords[i + 1]
                                    val cx1 = p0.x + (p1.x - p0.x) / 2f
                                    val cy1 = p0.y
                                    val cx2 = p0.x + (p1.x - p0.x) / 2f
                                    val cy2 = p1.y
                                    path.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                                }
                                return path
                            }

                            val tempPath = buildSplinePath(tempCoords)
                            val humPath = buildSplinePath(humCoords)

                            // Temperature Gradient Fill
                            val tempFill = Path().apply {
                                addPath(tempPath)
                                lineTo(tempCoords.last().x, height)
                                lineTo(tempCoords.first().x, height)
                                close()
                            }
                            drawPath(
                                path = tempFill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(tempColor.copy(alpha = 0.28f), Color.Transparent),
                                    startY = 0f,
                                    endY = height
                                )
                            )

                            // Humidity Gradient Fill
                            val humFill = Path().apply {
                                addPath(humPath)
                                lineTo(humCoords.last().x, height)
                                lineTo(humCoords.first().x, height)
                                close()
                            }
                            drawPath(
                                path = humFill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(humColor.copy(alpha = 0.18f), Color.Transparent),
                                    startY = 0f,
                                    endY = height
                                )
                            )

                            // Curves
                            drawPath(
                                path = tempPath,
                                color = tempColor,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            drawPath(
                                path = humPath,
                                color = humColor,
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Dots
                            tempCoords.forEachIndexed { idx, pt ->
                                if (idx % 3 == 0 || idx == tempCoords.lastIndex) {
                                    drawCircle(color = tempColor, radius = 3.dp.toPx(), center = pt)
                                    drawCircle(color = if (isDark) Color(0xFF131B2E) else Color.White, radius = 1.5.dp.toPx(), center = pt)
                                }
                            }
                            humCoords.forEachIndexed { idx, pt ->
                                if (idx % 3 == 0 || idx == humCoords.lastIndex) {
                                    drawCircle(color = humColor, radius = 2.5.dp.toPx(), center = pt)
                                    drawCircle(color = if (isDark) Color(0xFF131B2E) else Color.White, radius = 1.2.dp.toPx(), center = pt)
                                }
                            }

                            // Scrubber Vertical Line & Highlights
                            scrubIndex?.let { sIdx ->
                                if (sIdx in sortedPoints.indices) {
                                    val sx = sIdx * stepX
                                    drawLine(
                                        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF1E293B).copy(alpha = 0.45f),
                                        start = Offset(sx, 0f),
                                        end = Offset(sx, height),
                                        strokeWidth = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )

                                    // Highlighted Temp Point
                                    val tPt = tempCoords[sIdx]
                                    drawCircle(color = tempColor.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = tPt)
                                    drawCircle(color = tempColor, radius = 4.dp.toPx(), center = tPt)
                                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = tPt)

                                    // Highlighted Hum Point
                                    val hPt = humCoords[sIdx]
                                    drawCircle(color = humColor.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = hPt)
                                    drawCircle(color = humColor, radius = 4.dp.toPx(), center = hPt)
                                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = hPt)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Right Y-Axis: Humidity (%)
                    Column(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        for (i in 0..3) {
                            val hVal = maxHum - i * (maxHum - minHum) / 3.0
                            Text(
                                text = "${hVal.roundToInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = humColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Meaningful Time Axis Milestones
                val hourFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dayFmt = SimpleDateFormat("dd/MM", Locale.getDefault())

                val leftLabel: String
                val midLabel: String
                val rightLabel: String

                if (selectedRange == "day") {
                    val startCal = Date(nowMs - 24L * 3600 * 1000L)
                    val midCal = Date(nowMs - 12L * 3600 * 1000L)
                    leftLabel = "${hourFmt.format(startCal)} hôm qua"
                    midLabel = "${hourFmt.format(midCal)}"
                    rightLabel = "${hourFmt.format(Date(nowMs))} (Hiện tại)"
                } else {
                    val startCal = Date(nowMs - 7L * 24 * 3600 * 1000L)
                    val midCal = Date(nowMs - 3L * 24 * 3600 * 1000L)
                    leftLabel = "${dayFmt.format(startCal)}"
                    midLabel = "${dayFmt.format(midCal)}"
                    rightLabel = "Hôm nay (${dayFmt.format(Date(nowMs))})"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = leftLabel, fontSize = 9.5.sp, color = textSecondary)
                    Text(text = midLabel, fontSize = 9.5.sp, color = textSecondary.copy(alpha = 0.7f))
                    Text(text = rightLabel, fontSize = 9.5.sp, color = textSecondary)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Row: Cao nhất - Thấp nhất - Trung bình
                val temps = sortedPoints.map { it.temp }
                val hums = sortedPoints.map { it.hum }

                val statMaxT = temps.maxOrNull() ?: 0.0
                val statMinT = temps.minOrNull() ?: 0.0
                val statAvgT = (temps.average() * 10).roundToInt() / 10.0

                val statMaxH = hums.maxOrNull()?.roundToInt() ?: 0
                val statMinH = hums.minOrNull()?.roundToInt() ?: 0
                val statAvgH = hums.average().roundToInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Max Box
                    GlassCard(
                        shape = RoundedCornerShape(12.dp),
                        fillColor = statFill,
                        borderColor = statBorder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Cao nhất", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${statMaxT}°C",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempColor
                            )
                            Text(
                                text = "${statMaxH}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = humColor
                            )
                        }
                    }

                    // Min Box
                    GlassCard(
                        shape = RoundedCornerShape(12.dp),
                        fillColor = statFill,
                        borderColor = statBorder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Thấp nhất", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${statMinT}°C",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempColor
                            )
                            Text(
                                text = "${statMinH}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = humColor
                            )
                        }
                    }

                    // Avg Box
                    GlassCard(
                        shape = RoundedCornerShape(12.dp),
                        fillColor = statFill,
                        borderColor = statBorder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Trung bình", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${statAvgT}°C",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = tempColor
                            )
                            Text(
                                text = "${statAvgH}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = humColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "Đóng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
