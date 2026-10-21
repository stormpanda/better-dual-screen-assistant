package com.maikthomalla.betterdualscreenassistant.ui.statusbar

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maikthomalla.betterdualscreenassistant.R
import com.maikthomalla.betterdualscreenassistant.ui.theme.LocalTccColors
import com.maikthomalla.betterdualscreenassistant.ui.theme.tccDrawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replicates `StatusBar` (`status_bar.xml`):
 * `marginTop = 15dp`, `paddingHorizontal = 15dp`.
 * Left: `TimeView` (tapping triggers memory cleanup just like the stock app).
 * Right: `BluetoothView`, `InternetSpeedView`, `WifiView`, and `BatteryView`.
 */
@Composable
fun StatusBarRow(
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalTccColors.current

    var timeText by remember { mutableStateOf(formatCurrentTime(context)) }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var wifiSignalLevel by remember { mutableIntStateOf(-1) }
    var isNetworkConnected by remember { mutableStateOf(false) }
    var netSpeedText by remember { mutableStateOf("0.0KB/S") }
    var isBluetoothConnected by remember { mutableStateOf(checkBluetoothConnected(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        timeText = formatCurrentTime(context)
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        if (level >= 0 && scale > 0) {
                            batteryLevel = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                        }
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL ||
                            plugged != 0
                    }
                    WifiManager.RSSI_CHANGED_ACTION,
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        wifiSignalLevel = readWifiSignalLevel(context)
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED,
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        isBluetoothConnected = checkBluetoothConnected(context)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val stickyBattery = context.registerReceiver(receiver, filter)
        if (stickyBattery != null) {
            val level = stickyBattery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyBattery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) {
                batteryLevel = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            }
            val status = stickyBattery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = stickyBattery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0
        }
        wifiSignalLevel = readWifiSignalLevel(context)
        isBluetoothConnected = checkBluetoothConnected(context)

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkConnected = true
                wifiSignalLevel = readWifiSignalLevel(context)
            }

            override fun onLost(network: Network) {
                isNetworkConnected = checkNetworkConnected(context)
                wifiSignalLevel = readWifiSignalLevel(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isNetworkConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                wifiSignalLevel = readWifiSignalLevel(context)
            }
        }
        isNetworkConnected = checkNetworkConnected(context)
        try {
            cm?.registerDefaultNetworkCallback(netCallback)
        } catch (_: Throwable) {
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
            try {
                cm?.unregisterNetworkCallback(netCallback)
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(isNetworkConnected) {
        if (!isNetworkConnected) return@LaunchedEffect
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTime = System.currentTimeMillis()
        while (isActive) {
            delay(2000L)
            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTime = System.currentTimeMillis()
            val dtSec = ((nowTime - lastTime) / 1000f).coerceAtLeast(0.1f)
            val diffBytes = (nowRx - lastRx).coerceAtLeast(0L)
            lastRx = nowRx
            lastTime = nowTime
            val kbPerSec = (diffBytes / 1024f) / dtSec
            netSpeedText = if (kbPerSec >= 1000f) {
                String.format(Locale.US, "%.1fMB/S", kbPerSec / 1024f)
            } else {
                String.format(Locale.US, "%.1fKB/S", kbPerSec)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 15.dp, start = 15.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = timeText,
            color = colors.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTimeClick
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isBluetoothConnected) {
                Image(
                    painter = painterResource(id = tccDrawable(R.drawable.ic_bluetooth)),
                    contentDescription = "Bluetooth",
                    colorFilter = ColorFilter.tint(colors.primary),
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isNetworkConnected) {
                Text(
                    text = netSpeedText,
                    color = colors.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (wifiSignalLevel in 0..3) {
                val wifiRes = when (wifiSignalLevel) {
                    0 -> R.drawable.ic_status_wifi_0
                    1 -> R.drawable.ic_status_wifi_1
                    2 -> R.drawable.ic_status_wifi_2
                    else -> R.drawable.ic_status_wifi_3
                }
                Image(
                    painter = painterResource(id = tccDrawable(wifiRes)),
                    contentDescription = "Wi-Fi",
                    modifier = Modifier.size(17.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryIconCanvas(
                    level = batteryLevel,
                    isCharging = isCharging,
                    modifier = Modifier.size(width = 25.dp, height = 14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$batteryLevel%",
                    color = colors.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BatteryIconCanvas(
    level: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    Canvas(modifier = modifier) {
        // Replicates BatteryView.a rotated 90 degrees clockwise:
        // Left nub, outer body frame in primaryColor, fill from right to left in secondaryColor
        // (interpolated towards red below 50%), and green lightning bolt on top when charging.
        val strokeW = size.height * 0.10f
        val nubWidth = size.width * 0.07f
        val nubHeight = size.height * 0.45f
        val bodyLeft = nubWidth + strokeW * 0.5f
        val bodyWidth = size.width - bodyLeft - strokeW * 0.5f
        val bodyHeight = size.height * 0.86f
        val top = (size.height - bodyHeight) / 2f
        val corner = CornerRadius(2f, 2f)

        // Left nub
        drawRoundRect(
            color = colors.primary,
            topLeft = Offset(0f, (size.height - nubHeight) / 2f),
            size = Size(nubWidth, nubHeight),
            cornerRadius = corner
        )

        // Body outline
        drawRoundRect(
            color = colors.primary,
            topLeft = Offset(bodyLeft, top),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = corner,
            style = Stroke(width = strokeW)
        )

        // Fill (anchored to the right of the body, matching 90° clockwise rotation of bottom-up fill)
        val pad = strokeW * 1.4f
        val maxFillW = (bodyWidth - pad * 2f).coerceAtLeast(0f)
        val fillW = maxFillW * (level.coerceIn(0, 100) / 100f)
        val fillColor = if (level > 50) {
            colors.secondary
        } else {
            Color(0xFFE34646)
        }
        if (fillW > 0f) {
            drawRect(
                color = fillColor,
                topLeft = Offset(bodyLeft + bodyWidth - pad - fillW, top + pad),
                size = Size(fillW, (bodyHeight - pad * 2f).coerceAtLeast(0f))
            )
        }

        // Lightning bolt in colors.lightning (#00FF00)
        if (isCharging) {
            val cx = bodyLeft + bodyWidth / 2f
            val cy = size.height / 2f
            val boltW = bodyWidth * 0.70f
            val boltH = bodyHeight * 0.65f
            val boltPath = Path().apply {
                moveTo(cx + boltW * 0.35f, cy - boltH * 0.25f)
                lineTo(cx, cy - boltH * 0.05f)
                lineTo(cx, cy - boltH * 0.35f)
                lineTo(cx - boltW * 0.35f, cy + boltH * 0.25f)
                lineTo(cx, cy + boltH * 0.05f)
                lineTo(cx, cy + boltH * 0.35f)
                close()
            }
            drawPath(path = boltPath, color = colors.lightning)
        }
    }
}

private fun formatCurrentTime(context: Context): String {
    val is24 = DateFormat.is24HourFormat(context)
    val pattern = if (is24) "HH:mm" else "h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}

private fun readWifiSignalLevel(context: Context): Int {
    return try {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        if (wm == null || !wm.isWifiEnabled) return -1
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm?.activeNetwork ?: return -1
        val caps = cm.getNetworkCapabilities(active) ?: return -1
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return -1
        val wifiInfo = (caps.transportInfo as? WifiInfo) ?: @Suppress("DEPRECATION") wm.connectionInfo
        val rssi = wifiInfo?.rssi ?: return -1
        if (rssi <= -127) return -1
        wm.calculateSignalLevel(rssi).coerceIn(0, 3)
    } catch (_: Throwable) {
        -1
    }
}

private fun checkNetworkConnected(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) {
        false
    }
}

private fun checkBluetoothConnected(context: Context): Boolean {
    return try {
        val bm = context.getSystemService(BluetoothManager::class.java) ?: return false
        val adapter = bm.adapter ?: return false
        if (!adapter.isEnabled) return false
        val profiles = intArrayOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            BluetoothProfile.GATT,
            4 // HID_DEVICE / INPUT_DEVICE
        )
        profiles.any { profile ->
            try {
                adapter.getProfileConnectionState(profile) == BluetoothAdapter.STATE_CONNECTED
            } catch (_: Throwable) {
                false
            }
        }
    } catch (_: Throwable) {
        false
    }
}
