package com.stormpanda.betterdualscreenassistant.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.system.HardwareTelemetryState
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import java.util.Locale

/**
 * MSI Afterburner / RivaTuner style hardware telemetry tab.
 * Fits perfectly on the AYN Thor bottom screen in a balanced 2-column layout:
 * - Top: Quick summary status chips (FPS, Temp, Fan, Power)
 * - Left Column: GPU (Adreno 740 clock & load) + Memory (RAM & ZRAM)
 * - Right Column: CPU (Snapdragon 8 Gen 2 & 3 clusters) + Power & Discharge
 */
@Composable
fun HardwareMonitorTab(
    telemetry: HardwareTelemetryState,
    tempFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Summary Quick Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryChip(
                label = "FPS",
                value = "${telemetry.currentFps}",
                accentColor = Color(0xFF00E5FF),
                modifier = Modifier.weight(1f)
            )
            SummaryChip(
                label = "TEMP",
                value = formatCpuTemperature(telemetry.cpuTempCelsius, tempFahrenheit),
                accentColor = if (telemetry.cpuTempCelsius >= 75f) colors.error else Color(0xFFFF9100),
                modifier = Modifier.weight(1f)
            )
            SummaryChip(
                label = "FAN",
                value = "${telemetry.fanSpeedPercent}%",
                accentColor = Color(0xFF76CA40),
                modifier = Modifier.weight(1f)
            )
            SummaryChip(
                label = "POWER",
                value = String.format(Locale.US, "%.1f W", telemetry.powerWatts),
                accentColor = Color(0xFFE040FB),
                modifier = Modifier.weight(1f)
            )
        }

        // 2-Column Split: Left = GPU & Memory, Right = CPU & Power
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GPU Card
                TelemetryCard(
                    title = "GPU — ADRENO 740",
                    modifier = Modifier.weight(1f)
                ) {
                    val gpuFreqText = if (telemetry.gpuCurFreqMhz > 0f) {
                        "${telemetry.gpuCurFreqMhz.toInt()} / ${telemetry.gpuMaxFreqMhz.toInt()} MHz"
                    } else {
                        "Idle"
                    }
                    TelemetryMetricBar(
                        name = "Core Load",
                        detail = gpuFreqText,
                        percentage = telemetry.gpuBusyPercent,
                        barColor = Color(0xFF00E5FF)
                    )
                }

                // RAM & ZRAM Card
                TelemetryCard(
                    title = "MEMORY — RAM & ZRAM",
                    modifier = Modifier.weight(1f)
                ) {
                    val ramDetailText = String.format(
                        Locale.US,
                        "%.1f / %.1f GB",
                        telemetry.ramUsedGb,
                        telemetry.ramTotalGb
                    )
                    TelemetryMetricBar(
                        name = "Physical RAM",
                        detail = ramDetailText,
                        percentage = telemetry.ramPercent,
                        barColor = Color(0xFF76CA40)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val swapDetailText = if (telemetry.swapTotalGb > 0f) {
                        String.format(
                            Locale.US,
                            "%.1f / %.1f GB",
                            telemetry.swapUsedGb,
                            telemetry.swapTotalGb
                        )
                    } else {
                        "Disabled"
                    }
                    TelemetryMetricBar(
                        name = "ZRAM Swap",
                        detail = swapDetailText,
                        percentage = telemetry.swapPercent,
                        barColor = Color(0xFF81C784)
                    )
                }

                // Power Card
                TelemetryCard(
                    title = "POWER & DISCHARGE",
                    modifier = Modifier.weight(1f)
                ) {
                    val pwrDetailText = if (telemetry.batteryVoltageMv > 0f) {
                        String.format(
                            Locale.US,
                            "%.1fV @ %dmA",
                            telemetry.batteryVoltageMv / 1000f,
                            telemetry.batteryCurrentMa
                        )
                    } else {
                        ""
                    }
                    val pwrPct = ((telemetry.powerWatts / 25f) * 100f).toInt().coerceIn(0, 100)
                    TelemetryMetricBar(
                        name = "System Power",
                        detail = pwrDetailText,
                        percentage = pwrPct,
                        barColor = Color(0xFFE040FB),
                        overrideValueText = String.format(Locale.US, "%.1f W", telemetry.powerWatts)
                    )
                }
            }

            // Right Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // CPU Card (Expanded to show all clusters)
                TelemetryCard(
                    title = "CPU — SNAPDRAGON 8 GEN 2",
                    modifier = Modifier.fillMaxHeight()
                ) {
                    TelemetryMetricBar(
                        name = "CPU Total Usage",
                        detail = String.format(Locale.US, "%.2f GHz", telemetry.cpuCurFreqGhz),
                        percentage = telemetry.cpuTotalUsagePercent,
                        barColor = if (colors.isMegingiardStyle) colors.accent else Color(0xFFEE188C)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val silverFreqText = if (telemetry.cpuSilverCurFreqGhz > 0f) {
                        String.format(Locale.US, "%.2f GHz", telemetry.cpuSilverCurFreqGhz)
                    } else {
                        "2.02 GHz"
                    }
                    TelemetryMetricBar(
                        name = "Silver (E-Cores 0-2)",
                        detail = silverFreqText,
                        percentage = telemetry.cpuSilverUsagePercent,
                        barColor = Color(0xFF4DD0E1)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val goldFreqText = if (telemetry.cpuGoldCurFreqGhz > 0f) {
                        String.format(Locale.US, "%.2f GHz", telemetry.cpuGoldCurFreqGhz)
                    } else {
                        "2.80 GHz"
                    }
                    TelemetryMetricBar(
                        name = "Gold (P-Cores 3-6)",
                        detail = goldFreqText,
                        percentage = telemetry.cpuGoldUsagePercent,
                        barColor = Color(0xFFFFB74D)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val primeFreqText = if (telemetry.cpuPrimeCurFreqGhz > 0f) {
                        String.format(Locale.US, "%.2f GHz", telemetry.cpuPrimeCurFreqGhz)
                    } else {
                        "3.19 GHz"
                    }
                    TelemetryMetricBar(
                        name = "Prime X3 (Core 7)",
                        detail = primeFreqText,
                        percentage = telemetry.cpuPrimeUsagePercent,
                        barColor = Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panelBackground)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = colors.secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalTccColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panelBackground)
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = title,
                color = colors.secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun TelemetryMetricBar(
    name: String,
    detail: String,
    percentage: Int,
    barColor: Color,
    overrideValueText: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = (percentage.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "telemetry_progress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                color = colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        color = colors.secondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    text = overrideValueText ?: "$percentage%",
                    color = barColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}
