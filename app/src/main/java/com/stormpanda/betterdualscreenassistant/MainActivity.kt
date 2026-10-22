package com.stormpanda.betterdualscreenassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.ui.components.TccRadioCheckIndicator
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.MegingiardAccentPresets
import com.stormpanda.betterdualscreenassistant.ui.theme.TccTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AssistantForegroundService.startService(this)
        setContent {
            val context = LocalContext.current
            val repository = remember {
                SystemSettingsRepository(context.applicationContext)
            }
            DisposableEffect(repository) {
                repository.register()
                onDispose {
                    repository.unregister()
                }
            }
            val themeMode by repository.themeMode.collectAsState()
            val customAccentArgb by repository.customAccentArgb.collectAsState()
            val syncedOption by repository.syncedMegingiardOption.collectAsState()
            val syncedAccentArgb by repository.syncedMegingiardAccentArgb.collectAsState()

            TccTheme(
                themeMode = themeMode,
                customAccentArgb = customAccentArgb,
                syncedMegingiardOption = syncedOption,
                syncedMegingiardAccentArgb = syncedAccentArgb
            ) {
                LauncherDashboardScreen(repository = repository)
            }
        }
    }
}

@Composable
private fun LauncherDashboardScreen(
    repository: SystemSettingsRepository
) {
    val context = LocalContext.current
    val colors = LocalTccColors.current
    val scope = rememberCoroutineScope()

    val themeMode by repository.themeMode.collectAsState()
    val customAccentArgb by repository.customAccentArgb.collectAsState()
    val syncedOption by repository.syncedMegingiardOption.collectAsState()
    val syncedAccentArgb by repository.syncedMegingiardAccentArgb.collectAsState()
    val injectOsTheme by repository.injectOsTheme.collectAsState()
    val selectedThemeOption = AppThemeOption.fromId(themeMode)

    var pServerAvailable by remember { mutableStateOf(false) }
    var helperConnected by remember { mutableStateOf(false) }
    var serviceRunning by remember { mutableStateOf(false) }
    var stockReplaced by remember { mutableStateOf(false) }
    var isTogglingStock by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withContext(Dispatchers.IO) {
                val pAvail = PServerClient.isAvailable()
                val hConn = PrivilegedBridge.isHelperConnected.value
                val sRun = AssistantForegroundService.instance != null
                val stockEnabled = PServerClient.isStockAssistantEnabled(context)
                pServerAvailable = pAvail
                helperConnected = hConn
                serviceRunning = sRun
                stockReplaced = !stockEnabled
            }
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.primaryBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Better DualScreenAssistant",
            color = colors.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Drop-in replacement for the AYN Thor DualScreenAssistant with 1:1 feature parity, Megingiard theming, and low-latency PServerBinder root bridge.",
            color = colors.secondary,
            fontSize = 14.sp
        )

        // Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panelBackground)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "System Status",
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            StatusIndicatorRow(
                label = "PServerBinder Root Service",
                active = pServerAvailable,
                statusText = if (pServerAvailable) "Connected" else "Unavailable"
            )
            StatusIndicatorRow(
                label = "Privileged app_process Bridge",
                active = helperConnected,
                statusText = if (helperConnected) "Active (UID 0)" else "Connecting..."
            )
            StatusIndicatorRow(
                label = "Overlay Foreground Service",
                active = serviceRunning,
                statusText = if (serviceRunning) "Running" else "Starting..."
            )
        }

        // Takeover Toggle Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panelBackground)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replace Stock DualScreenAssistant",
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (stockReplaced) {
                        "Active: Stock com.odin.dualscreen.assistant is disabled. Pressing the TCC button opens Better DualScreenAssistant."
                    } else {
                        "Inactive: Stock com.odin.dualscreen.assistant is still enabled. Toggle on to take over the TCC button."
                    },
                    color = colors.secondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = stockReplaced,
                enabled = !isTogglingStock,
                onCheckedChange = { replace ->
                    isTogglingStock = true
                    scope.launch(Dispatchers.IO) {
                        PServerClient.setStockAssistantEnabled(!replace, context)
                        val nowEnabled = PServerClient.isStockAssistantEnabled(context)
                        withContext(Dispatchers.Main) {
                            stockReplaced = !nowEnabled
                            isTogglingStock = false
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                    checkedTrackColor = colors.accent
                )
            )
        }

        // Appearance & Theme Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panelBackground)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Appearance & Theme",
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose between the original stock themes, Megingiard themes, or live synchronization with Megingiard.",
                color = colors.secondary,
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.fifth)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inject Theme Colors into OS",
                        color = colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Applies the active Megingiard palette to Android 13 SystemUI, Quick Settings, Settings, and Material You apps. Selecting a Stock theme or toggling off reverts to the stock OS theme.",
                        color = colors.secondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = injectOsTheme,
                    onCheckedChange = { enabled ->
                        repository.setInjectOsTheme(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                        checkedTrackColor = colors.accent
                    )
                )
            }

            val customAccentColor = Color(customAccentArgb)
            val syncedAccentColor = syncedAccentArgb?.let { Color(it) } ?: syncedOption.previewAccent

            AppThemeOption.entries.forEach { opt ->
                val isSelected = selectedThemeOption == opt
                val (surfaceColor, accentColor) = when (opt) {
                    AppThemeOption.SYNC_MEGINGIARD -> syncedOption.previewSurface to syncedAccentColor
                    AppThemeOption.MEGINGIARD_DARK, AppThemeOption.MEGINGIARD_DARK_OLED ->
                        opt.previewSurface to customAccentColor
                    else -> opt.previewSurface to opt.previewAccent
                }
                val labelText = if (opt == AppThemeOption.SYNC_MEGINGIARD) {
                    "${opt.displayName} (${syncedOption.shortTileLabel})"
                } else {
                    opt.displayName
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.fifth else Color.Transparent)
                        .clickable { repository.setThemeMode(opt.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(surfaceColor)
                                .border(1.5.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = labelText,
                            color = colors.primary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    TccRadioCheckIndicator(
                        selected = isSelected,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (selectedThemeOption.supportsCustomAccent) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Accent Color",
                    color = colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MegingiardAccentPresets.forEach { preset ->
                        val isSelectedSwatch = customAccentColor == preset
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .then(
                                    if (isSelectedSwatch) {
                                        Modifier.border(2.5.dp, colors.primary, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { repository.setCustomAccentArgb(preset.toArgb()) }
                        )
                    }
                }
            }
        }

        // Quick Actions Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panelBackground)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Controls",
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    label = "Show Overlay on Bottom Screen",
                    backgroundColor = colors.accent,
                    textColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                    onClick = {
                        AssistantForegroundService.startService(
                            context,
                            AssistantForegroundService.ACTION_SHOW_OVERLAY
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    label = "Hide Overlay",
                    backgroundColor = colors.fifth,
                    textColor = colors.primary,
                    onClick = {
                        AssistantForegroundService.startService(
                            context,
                            AssistantForegroundService.ACTION_HIDE_OVERLAY
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            ActionButton(
                label = "Re-Grant Permissions & Reconnect Root Bridge",
                backgroundColor = colors.fifth,
                textColor = colors.primary,
                onClick = {
                    PrivilegedBridge.ensureInitialized(context)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusIndicatorRow(
    label: String,
    active: Boolean,
    statusText: String
) {
    val colors = LocalTccColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.secondary,
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.lightning else Color(0xFFE34646))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
