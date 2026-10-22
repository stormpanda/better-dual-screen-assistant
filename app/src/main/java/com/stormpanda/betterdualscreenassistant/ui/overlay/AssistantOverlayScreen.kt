package com.stormpanda.betterdualscreenassistant.ui.overlay

import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.system.HardwareTelemetryState
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.system.TelemetryMonitor
import com.stormpanda.betterdualscreenassistant.ui.components.HardwareInfoComponentCard
import com.stormpanda.betterdualscreenassistant.ui.components.HardwareRingGauge
import com.stormpanda.betterdualscreenassistant.ui.components.HorizontalSlidingBar
import com.stormpanda.betterdualscreenassistant.ui.components.SliderKind
import com.stormpanda.betterdualscreenassistant.ui.components.TccButton
import com.stormpanda.betterdualscreenassistant.ui.components.TccDialogOption
import com.stormpanda.betterdualscreenassistant.ui.components.TccOptionDialog
import com.stormpanda.betterdualscreenassistant.ui.components.TccRadioCheckIndicator
import com.stormpanda.betterdualscreenassistant.ui.statusbar.StatusBarRow
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.MegingiardAccentPresets
import com.stormpanda.betterdualscreenassistant.ui.theme.TccTheme
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ActiveDialog {
    NONE,
    FOCUS_LOCK,
    PERFORMANCE_MODE,
    FAN_CONTROL,
    CONTROLLER_STYLE,
    THEME_MODE,
    CLOSE_BOTTOM_TIP
}

@Composable
fun AssistantOverlayScreen(
    repository: SystemSettingsRepository,
    telemetryMonitor: TelemetryMonitor,
    activeTabFlow: StateFlow<Int>,
    onSelectTab: (Int) -> Unit,
    onOpenRecentsRequest: () -> Unit,
    onCleanMemoryRequest: () -> Unit,
    onDismissOverlayRequest: () -> Unit
) {
    val themeMode by repository.themeMode.collectAsState()
    val customAccentArgb by repository.customAccentArgb.collectAsState()
    val syncedMegingiardOption by repository.syncedMegingiardOption.collectAsState()
    val syncedMegingiardAccentArgb by repository.syncedMegingiardAccentArgb.collectAsState()
    val injectOsTheme by repository.injectOsTheme.collectAsState()
    val dualScreenMode by repository.dualScreenDisplayMode.collectAsState()
    val focusLock by repository.screenFocusLock.collectAsState()
    val performanceMode by repository.performanceMode.collectAsState()
    val fanMode by repository.fanMode.collectAsState()
    val fanSpeed by repository.fanSpeed.collectAsState()
    val controllerStyle by repository.controllerStyle.collectAsState()
    val chargingSeparation by repository.chargingSeparation.collectAsState()
    val vibrateOn by repository.vibrateOn.collectAsState()
    val ambientLight by repository.ambientLightEnabled.collectAsState()
    val forceLandscape by repository.forceLandscape.collectAsState()
    val refreshRate by repository.refreshRate.collectAsState()

    val dualBrightness by repository.dualBrightness.collectAsState()
    val topBrightness by repository.topBrightness.collectAsState()
    val bottomBrightness by repository.bottomBrightness.collectAsState()
    val dualVolume by repository.dualVolumePercent.collectAsState()
    val primaryVolume by repository.primaryVolumePercent.collectAsState()
    val secondaryVolume by repository.secondaryVolumePercent.collectAsState()

    val telemetry by telemetryMonitor.state.collectAsState()

    val activeTab by activeTabFlow.collectAsState() // 0 = Mode, 1 = Settings
    var activeDialog by remember { mutableStateOf(ActiveDialog.NONE) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(activeTab) {
        if (activeTab == 1) {
            telemetryMonitor.start()
        } else {
            telemetryMonitor.stop()
        }
        onDispose {
            telemetryMonitor.stop()
        }
    }

    TccTheme(
        themeMode = themeMode,
        customAccentArgb = customAccentArgb,
        syncedMegingiardOption = syncedMegingiardOption,
        syncedMegingiardAccentArgb = syncedMegingiardAccentArgb
    ) {
        val colors = LocalTccColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.primaryBackground)
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                        (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B)
                    ) {
                        if (activeDialog != ActiveDialog.NONE) {
                            activeDialog = ActiveDialog.NONE
                            true
                        } else {
                            onDismissOverlayRequest()
                            true
                        }
                    } else {
                        false
                    }
                }
        ) {
            // Replicates main_layout.xml:
            // 1. StatusBar (marginTop = 15dp, paddingHorizontal = 15dp)
            // 2. MainView (weight(1f), paddingHorizontal = 15dp, marginBottom = 5dp)
            // 3. NavigationBar (paddingHorizontal = 15dp, marginBottom = 15dp)
            Column(modifier = Modifier.fillMaxSize()) {
                StatusBarRow(
                    onTimeClick = onCleanMemoryRequest
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 15.dp, end = 15.dp, bottom = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeTab == 0) {
                        ScreenControlTab(
                            dualScreenMode = dualScreenMode,
                            dualBrightness = dualBrightness,
                            topBrightness = topBrightness,
                            bottomBrightness = bottomBrightness,
                            dualVolume = dualVolume,
                            primaryVolume = primaryVolume,
                            secondaryVolume = secondaryVolume,
                            onSelectDisplayMode = { mode ->
                                if (mode == 1 && dualScreenMode != 1) {
                                    // Replicate ScreenModeControlPanel.java line 286:
                                    // Tapping "Only top screen" shows the Tip dialog if enabled
                                    if (repository.shouldShowCloseBottomScreenTip()) {
                                        activeDialog = ActiveDialog.CLOSE_BOTTOM_TIP
                                    } else {
                                        repository.setDualScreenDisplayMode(1)
                                    }
                                } else {
                                    repository.setDualScreenDisplayMode(mode)
                                }
                            },
                            onDualBrightnessChange = { pct, temp ->
                                repository.setDualScreenBrightnessPercent(pct, temp)
                            },
                            onTopBrightnessChange = { pct, temp ->
                                repository.setDisplayBrightnessPercent(0, pct, temp)
                            },
                            onBottomBrightnessChange = { pct, temp ->
                                repository.setDisplayBrightnessPercent(4, pct, temp)
                            },
                            onDualVolumeChange = { pct ->
                                repository.setDualVolumePercent(pct)
                            },
                            onPrimaryVolumeChange = { pct ->
                                repository.setPrimaryVolumePercent(pct)
                            },
                            onSecondaryVolumeChange = { pct ->
                                repository.setSecondaryVolumePercent(pct)
                            }
                        )
                    } else {
                        SystemSettingsTab(
                            telemetry = telemetry,
                            refreshRate = refreshRate,
                            dualScreenMode = dualScreenMode,
                            focusLock = focusLock,
                            performanceMode = performanceMode,
                            fanMode = fanMode,
                            controllerStyle = controllerStyle,
                            chargingSeparation = chargingSeparation,
                            vibrateOn = vibrateOn,
                            ambientLight = ambientLight,
                            forceLandscape = forceLandscape,
                            themeMode = themeMode,
                            onToggleRefreshRate = { repository.toggleRefreshRate() },
                            onOpenDialog = { dialog -> activeDialog = dialog },
                            onToggleChargingSeparation = { repository.toggleChargingSeparation() },
                            onToggleVibrate = { repository.toggleVibrate() },
                            onCleanMemory = onCleanMemoryRequest,
                            onToggleAmbientLight = { repository.toggleAmbientLight() },
                            onToggleForceLandscape = { repository.toggleForceLandscape() }
                        )
                    }
                }

                NavigationBarSection(
                    activeTab = activeTab,
                    onSelectTab = onSelectTab,
                    onOpenRecents = onOpenRecentsRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp, start = 15.dp, end = 15.dp, bottom = 15.dp)
                )
            }

            // Modal Option Dialogs
            when (activeDialog) {
                ActiveDialog.NONE -> Unit

                ActiveDialog.CLOSE_BOTTOM_TIP -> {
                    CloseScreenTipOverlay(
                        themeMode = themeMode,
                        onCancel = { activeDialog = ActiveDialog.NONE },
                        onConfirm = { noMore ->
                            if (noMore) {
                                repository.setNoMoreCloseBottomScreenTip(true)
                            }
                            activeDialog = ActiveDialog.NONE
                            coroutineScope.launch {
                                delay(100L)
                                repository.setDualScreenDisplayMode(1)
                            }
                        }
                    )
                }

                ActiveDialog.FOCUS_LOCK -> {
                    val options = listOf(
                        TccDialogOption(
                            id = 0,
                            label = stringResource(R.string.key_focus_mode_touch_switchable),
                            iconRes = R.drawable.ic_screen_focus_lock_switchable
                        ),
                        TccDialogOption(
                            id = 1,
                            label = stringResource(R.string.key_focus_mode_lock_on_upper_screen),
                            iconRes = R.drawable.ic_screen_focus_lock_upper
                        ),
                        TccDialogOption(
                            id = 2,
                            label = stringResource(R.string.key_focus_mode_lock_on_lower_screen),
                            iconRes = R.drawable.ic_screen_focus_lock_lower
                        )
                    )
                    TccOptionDialog(
                        title = stringResource(R.string.key_focus_mode_title_text),
                        options = options,
                        selectedId = focusLock,
                        onSelectOption = { selected ->
                            repository.setScreenFocusLock(selected)
                            coroutineScope.launch {
                                delay(300L)
                                activeDialog = ActiveDialog.NONE
                            }
                        },
                        onDismiss = { activeDialog = ActiveDialog.NONE }
                    )
                }

                ActiveDialog.PERFORMANCE_MODE -> {
                    val options = listOf(
                        TccDialogOption(
                            id = 0,
                            label = stringResource(R.string.text_performance_mode_normal),
                            iconRes = R.drawable.ic_normal_mode
                        ),
                        TccDialogOption(
                            id = 1,
                            label = stringResource(R.string.text_performance_mode_performance),
                            iconRes = R.drawable.ic_performance_mode
                        ),
                        TccDialogOption(
                            id = 2,
                            label = stringResource(R.string.text_performance_mode_height_performance),
                            iconRes = R.drawable.ic_height_performance_mode
                        )
                    )
                    TccOptionDialog(
                        title = stringResource(R.string.text_performance_mode_title),
                        options = options,
                        selectedId = performanceMode,
                        onSelectOption = { selected ->
                            repository.setPerformanceMode(selected)
                            if (selected == 2 && fanMode == 0) {
                                repository.setFanMode(4)
                            }
                            coroutineScope.launch {
                                delay(300L)
                                activeDialog = ActiveDialog.NONE
                            }
                        },
                        onDismiss = { activeDialog = ActiveDialog.NONE }
                    )
                }

                ActiveDialog.FAN_CONTROL -> {
                    val options = buildList {
                        if (performanceMode != 2) {
                            add(
                                TccDialogOption(
                                    id = 0,
                                    label = stringResource(R.string.text_fan_control_off),
                                    iconRes = R.drawable.ic_qs_fan_control
                                )
                            )
                        }
                        add(
                            TccDialogOption(
                                id = 1,
                                label = stringResource(R.string.text_fan_control_low_speed),
                                iconRes = R.drawable.ic_fan_quiet_mode
                            )
                        )
                        add(
                            TccDialogOption(
                                id = 4,
                                label = stringResource(R.string.text_fan_control_smart),
                                iconRes = R.drawable.ic_fan_smart_mode
                            )
                        )
                        add(
                            TccDialogOption(
                                id = 5,
                                label = stringResource(R.string.text_fan_control_medium_speed),
                                iconRes = R.drawable.ic_fan_sport_mode
                            )
                        )
                        add(
                            TccDialogOption(
                                id = 6,
                                label = stringResource(R.string.text_fan_control_custom),
                                iconRes = R.drawable.ic_fan_custom_mode
                            )
                        )
                    }
                    TccOptionDialog(
                        title = stringResource(R.string.text_fan_control_title),
                        options = options,
                        selectedId = fanMode,
                        showCustomFanSlider = true,
                        customFanSpeed = fanSpeed,
                        onCustomFanSpeedChange = { speed ->
                            repository.setCustomFanSpeed(speed)
                        },
                        onSelectOption = { selected ->
                            repository.setFanMode(selected)
                            if (selected != 6) {
                                coroutineScope.launch {
                                    delay(300L)
                                    activeDialog = ActiveDialog.NONE
                                }
                            }
                        },
                        onDismiss = { activeDialog = ActiveDialog.NONE }
                    )
                }

                ActiveDialog.CONTROLLER_STYLE -> {
                    val options = listOf(
                        TccDialogOption(
                            id = 2,
                            label = stringResource(R.string.fan_option_disable),
                            iconRes = R.drawable.ic_ban_on_use
                        ),
                        TccDialogOption(
                            id = 0,
                            label = stringResource(R.string.fan_option_xbox_style),
                            iconRes = R.drawable.ic_controller__xbox_style
                        ),
                        TccDialogOption(
                            id = 1,
                            label = stringResource(R.string.fan_option_odin_style),
                            iconRes = R.drawable.ic_controller_odin_style
                        )
                    )
                    TccOptionDialog(
                        title = stringResource(R.string.text_handle_style_title),
                        options = options,
                        selectedId = controllerStyle,
                        onSelectOption = { selected ->
                            repository.setControllerStyle(selected)
                            coroutineScope.launch {
                                delay(300L)
                                activeDialog = ActiveDialog.NONE
                            }
                        },
                        onDismiss = { activeDialog = ActiveDialog.NONE }
                    )
                }

                ActiveDialog.THEME_MODE -> {
                    val selectedThemeOption = AppThemeOption.fromId(themeMode)
                    val customAccentColor = Color(customAccentArgb)
                    val syncedAccentColor = syncedMegingiardAccentArgb?.let { Color(it) }
                        ?: syncedMegingiardOption.previewAccent
                    val options = AppThemeOption.entries.map { opt ->
                        when (opt) {
                            AppThemeOption.STOCK_SYSTEM -> TccDialogOption(
                                id = opt.id,
                                label = "${stringResource(R.string.text_system_theme)} (Stock)",
                                iconRes = R.drawable.ic_qs_system_theme,
                                preserveIconColors = true
                            )
                            AppThemeOption.STOCK_LIGHT -> TccDialogOption(
                                id = opt.id,
                                label = "${stringResource(R.string.text_light_theme)} (Stock)",
                                iconRes = R.drawable.ic_qs_light_theme,
                                preserveIconColors = true
                            )
                            AppThemeOption.STOCK_DARK -> TccDialogOption(
                                id = opt.id,
                                label = "${stringResource(R.string.text_dark_theme)} (Stock)",
                                iconRes = R.drawable.ic_qs_dark_theme,
                                preserveIconColors = true
                            )
                            AppThemeOption.SYNC_MEGINGIARD -> TccDialogOption(
                                id = opt.id,
                                label = "${opt.displayName} (${syncedMegingiardOption.shortTileLabel})",
                                iconRes = R.drawable.ic_qs_theme_mode,
                                swatchColors = syncedMegingiardOption.previewSurface to syncedAccentColor
                            )
                            AppThemeOption.MEGINGIARD_DARK, AppThemeOption.MEGINGIARD_DARK_OLED -> TccDialogOption(
                                id = opt.id,
                                label = opt.displayName,
                                iconRes = R.drawable.ic_qs_theme_mode,
                                swatchColors = opt.previewSurface to customAccentColor
                            )
                            else -> TccDialogOption(
                                id = opt.id,
                                label = opt.displayName,
                                iconRes = R.drawable.ic_qs_theme_mode,
                                swatchColors = opt.previewSurface to opt.previewAccent
                            )
                        }
                    }
                    TccOptionDialog(
                        title = stringResource(R.string.text_theme_mode_title),
                        options = options,
                        selectedId = selectedThemeOption.id,
                        accentPresets = if (selectedThemeOption.supportsCustomAccent) {
                            MegingiardAccentPresets
                        } else {
                            emptyList()
                        },
                        selectedAccentArgb = customAccentArgb,
                        onSelectAccentColor = { color ->
                            repository.setCustomAccentArgb(color.toArgb())
                        },
                        showOsThemeToggle = true,
                        injectOsThemeEnabled = injectOsTheme,
                        onToggleInjectOsTheme = { enabled ->
                            repository.setInjectOsTheme(enabled)
                        },
                        onSelectOption = { selected ->
                            repository.setThemeMode(selected)
                            val newlySelected = AppThemeOption.fromId(selected)
                            if (!newlySelected.supportsCustomAccent && !injectOsTheme) {
                                coroutineScope.launch {
                                    delay(300L)
                                    activeDialog = ActiveDialog.NONE
                                }
                            }
                        },
                        onDismiss = { activeDialog = ActiveDialog.NONE }
                    )
                }
            }
        }
    }
}

/**
 * Replicates `screen_control_layout.xml`, `screen_mode_control_panel.xml`, and `sliding_control_panel_layout.xml`:
 * - Top 50% (`0.5` guideline, `marginTop = 8.dp`):
 *   - Single rounded card (`20.dp` radius, `colors.panelBackground`) with 3 mode buttons:
 *     `Dual-screen display`, `Only top screen`, `Only bottom screen`.
 * - Bottom 50% (`marginTop = 10.dp`):
 *   - Split vertically 50% / 50% into two side-by-side rounded cards (`20.dp` radius, `colors.panelBackground`):
 *     - Left card: 3 brightness sliders (`Dual Screen`, `Top Screen`, `Bottom Screen`) from `6%` to `94%` height.
 *     - Right card: 3 volume sliders (`Dual Screen`, `Top Screen`, `Bottom Screen`) from `6%` to `94%` height.
 */
@Composable
private fun ScreenControlTab(
    dualScreenMode: Int,
    dualBrightness: Int,
    topBrightness: Int,
    bottomBrightness: Int,
    dualVolume: Int,
    primaryVolume: Int,
    secondaryVolume: Int,
    onSelectDisplayMode: (Int) -> Unit,
    onDualBrightnessChange: (Int, Boolean) -> Unit,
    onTopBrightnessChange: (Int, Boolean) -> Unit,
    onBottomBrightnessChange: (Int, Boolean) -> Unit,
    onDualVolumeChange: (Int) -> Unit,
    onPrimaryVolumeChange: (Int) -> Unit,
    onSecondaryVolumeChange: (Int) -> Unit
) {
    val colors = LocalTccColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top 50%: ScreenModeControlPanel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.panelBackground)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TccButton(
                label = stringResource(R.string.dualscreen_asynchronous_display),
                iconRes = R.drawable.ic_dual_screen_display,
                selected = dualScreenMode == 0,
                onClick = { onSelectDisplayMode(0) },
                modifier = Modifier.padding(15.dp)
            )
            TccButton(
                label = stringResource(R.string.only_theupper_screen_displayed),
                iconRes = R.drawable.ic_just_screen_above,
                selected = dualScreenMode == 1,
                onClick = { onSelectDisplayMode(1) },
                modifier = Modifier.padding(15.dp)
            )
            TccButton(
                label = stringResource(R.string.only_following_screen_displayed),
                iconRes = R.drawable.ic_just_screen_below,
                selected = dualScreenMode == 2,
                onClick = { onSelectDisplayMode(2) },
                modifier = Modifier.padding(15.dp)
            )
        }

        // Bottom 50%: SlidingControlPanel (Brightness on Left 50%, Volume on Right 50%)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(top = 10.dp)
        ) {
            // Left 50%: ScreenControlPanel (3 brightness sliders, 6%..94% vertical guidelines)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.panelBackground)
                    .padding(horizontal = 10.dp)
            ) {
                Spacer(modifier = Modifier.weight(0.06f))
                HorizontalSlidingBar(
                    label = stringResource(R.string.sliding_dual_screen),
                    progress = dualBrightness,
                    kind = SliderKind.BRIGHTNESS,
                    onProgressChange = onDualBrightnessChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.293f)
                )
                HorizontalSlidingBar(
                    label = stringResource(R.string.sliding_main_screen),
                    progress = topBrightness,
                    kind = SliderKind.BRIGHTNESS,
                    onProgressChange = onTopBrightnessChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.293f)
                )
                HorizontalSlidingBar(
                    label = stringResource(R.string.sliding_secondary_screen),
                    progress = bottomBrightness,
                    kind = SliderKind.BRIGHTNESS,
                    onProgressChange = onBottomBrightnessChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.293f)
                )
                Spacer(modifier = Modifier.weight(0.06f))
            }

            // Right 50%: VolumeControlPanel (3 volume sliders, 6%..94% vertical guidelines)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.panelBackground)
                    .padding(horizontal = 10.dp)
            ) {
                Spacer(modifier = Modifier.weight(0.06f))
                HorizontalSlidingBar(
                    label = stringResource(R.string.sliding_dual_screen),
                    progress = dualVolume,
                    kind = SliderKind.VOLUME,
                    onProgressChange = { pct, _ -> onDualVolumeChange(pct) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.293f)
                )
                HorizontalSlidingBar(
                    label = stringResource(R.string.sliding_main_screen),
                    progress = primaryVolume,
                    kind = SliderKind.VOLUME,
                    onProgressChange = { pct, _ -> onPrimaryVolumeChange(pct) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.293f)
                )
                HorizontalSlidingBar(
                    label = stringResource(R.string.sliding_secondary_screen),
                    progress = secondaryVolume,
                    kind = SliderKind.VOLUME,
                    onProgressChange = { pct, _ -> onSecondaryVolumeChange(pct) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.293f)
                )
                Spacer(modifier = Modifier.weight(0.06f))
            }
        }
    }
}

private data class QuickSettingTileModel(
    val label: String,
    @DrawableRes val iconRes: Int,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Replicates `system_set_layout.xml`, `frame_rate_panel_layout.xml`, `quick_settings_panel_layout.xml`,
 * and `hardware_info_panel_layout_2.xml`:
 * - Top 70% (`guideline11 = 0.7`):
 *   - Left 40% (`guideline12 = 0.4`): `FrameRatePanel` (`120 FPS MODE` pill + huge live FPS + `CURRENT FPS`)
 *   - Right 60%: `QuickSettingsPanel` (2x4 paged tiles + `#8E84FF` dot indicator)
 * - Bottom 30%:
 *   - `0%..20%`: `CpuTempInfoComponent` (`TEMP` card)
 *   - `20%..40%`: `FanInfoComponent` (`FAN` card)
 *   - `40%..100%`: `HardwareInfoDisplayView` (`CPU`, `GPU`, `PWR`, `RAM` colored ring gauges)
 */
@Composable
private fun SystemSettingsTab(
    telemetry: HardwareTelemetryState,
    refreshRate: Int,
    dualScreenMode: Int,
    focusLock: Int,
    performanceMode: Int,
    fanMode: Int,
    controllerStyle: Int,
    chargingSeparation: Boolean,
    vibrateOn: Boolean,
    ambientLight: Boolean,
    forceLandscape: Boolean,
    themeMode: Int,
    onToggleRefreshRate: () -> Unit,
    onOpenDialog: (ActiveDialog) -> Unit,
    onToggleChargingSeparation: () -> Unit,
    onToggleVibrate: () -> Unit,
    onCleanMemory: () -> Unit,
    onToggleAmbientLight: () -> Unit,
    onToggleForceLandscape: () -> Unit
) {
    val colors = LocalTccColors.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Build dynamic Quick Settings tile list matching QuickSettingsPanel.java line 79
    val tiles = buildList {
        if (dualScreenMode == 0) {
            val (focusLabel, focusSelected) = when (focusLock) {
                1 -> stringResource(R.string.key_focus_mode_lock_on_upper_screen) to true
                2 -> stringResource(R.string.key_focus_mode_lock_on_lower_screen) to true
                else -> stringResource(R.string.key_focus_mode_touch_switchable) to false
            }
            add(
                QuickSettingTileModel(
                    label = focusLabel,
                    iconRes = R.drawable.ic_screen_focus_lock_mode,
                    selected = focusSelected,
                    onClick = { onOpenDialog(ActiveDialog.FOCUS_LOCK) }
                )
            )
        }

        val (perfLabel, perfSelected) = when (performanceMode) {
            1 -> stringResource(R.string.text_performance_mode_performance) to true
            2 -> stringResource(R.string.text_performance_mode_height_performance) to true
            else -> stringResource(R.string.text_performance_mode_normal) to false
        }
        add(
            QuickSettingTileModel(
                label = perfLabel,
                iconRes = R.drawable.ic_qs_performance,
                selected = perfSelected,
                onClick = { onOpenDialog(ActiveDialog.PERFORMANCE_MODE) }
            )
        )

        val (fanLabel, fanSelected) = when (fanMode) {
            1 -> stringResource(R.string.text_fan_control_low_speed) to true
            4 -> stringResource(R.string.text_fan_control_smart) to true
            5 -> stringResource(R.string.text_fan_control_medium_speed) to true
            6 -> stringResource(R.string.text_fan_control_custom) to true
            else -> stringResource(R.string.text_fan_control_off) to false
        }
        add(
            QuickSettingTileModel(
                label = fanLabel,
                iconRes = R.drawable.ic_qs_fan_control,
                selected = fanSelected,
                onClick = { onOpenDialog(ActiveDialog.FAN_CONTROL) }
            )
        )

        add(
            QuickSettingTileModel(
                label = stringResource(R.string.text_clear_memory_title),
                iconRes = R.drawable.ic_qs_clean,
                selected = false,
                onClick = onCleanMemory
            )
        )

        val (ctrlLabel, ctrlSelected) = when (controllerStyle) {
            0 -> stringResource(R.string.fan_option_xbox_style) to true
            1 -> stringResource(R.string.fan_option_odin_style) to true
            else -> stringResource(R.string.fan_option_disable) to false
        }
        add(
            QuickSettingTileModel(
                label = ctrlLabel,
                iconRes = R.drawable.ic_qs_controller_style,
                selected = ctrlSelected,
                onClick = { onOpenDialog(ActiveDialog.CONTROLLER_STYLE) }
            )
        )

        add(
            QuickSettingTileModel(
                label = stringResource(R.string.text_charging_separation_title),
                iconRes = R.drawable.ic_qs_charging_separation,
                selected = chargingSeparation,
                onClick = onToggleChargingSeparation
            )
        )

        add(
            QuickSettingTileModel(
                label = stringResource(R.string.text_vibration_open_title),
                iconRes = R.drawable.ic_qs_vibrate,
                selected = vibrateOn,
                onClick = onToggleVibrate
            )
        )

        add(
            QuickSettingTileModel(
                label = stringResource(R.string.text_led_lights_open_title),
                iconRes = R.drawable.ic_qs_ambient_light,
                selected = ambientLight,
                onClick = onToggleAmbientLight
            )
        )

        add(
            QuickSettingTileModel(
                label = stringResource(R.string.force_landscape_mode_text),
                iconRes = R.drawable.ic_landscape,
                selected = forceLandscape,
                onClick = onToggleForceLandscape
            )
        )

        val currentThemeOpt = AppThemeOption.fromId(themeMode)
        val (themeLabel, themeSelected) = when (currentThemeOpt) {
            AppThemeOption.STOCK_SYSTEM -> stringResource(R.string.text_system_theme) to false
            AppThemeOption.STOCK_LIGHT -> stringResource(R.string.text_light_theme) to false
            AppThemeOption.STOCK_DARK -> stringResource(R.string.text_dark_theme) to true
            else -> currentThemeOpt.shortTileLabel to true
        }
        add(
            QuickSettingTileModel(
                label = themeLabel,
                iconRes = R.drawable.ic_qs_theme_mode,
                selected = themeSelected,
                onClick = { onOpenDialog(ActiveDialog.THEME_MODE) }
            )
        )
    }

    val pages = remember(tiles) { tiles.chunked(8) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            if (pagerState.currentPage < 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Top 70%: FrameRatePanel (40%) + QuickSettingsPanel (60%)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
                .padding(vertical = 5.dp)
        ) {
            // Left 40%: FrameRatePanel
            FrameRatePanelCard(
                refreshRate = refreshRate,
                currentFps = telemetry.currentFps,
                onToggleRefreshRate = onToggleRefreshRate,
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(end = 5.dp)
            )

            // Right 60%: QuickSettingsPanel
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(start = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.panelBackground),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.93f)
                        .padding(horizontal = 10.dp)
                ) { pageIndex ->
                    val pageItems = pages.getOrNull(pageIndex).orEmpty()
                    QuickSettingsGridPage(items = pageItems)
                }

                // Bottom 7%: ViewPagerIndicator (selected = accent #8E84FF, unselected = secondary #E0E0E0)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.07f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.accent else colors.secondary)
                            )
                        }
                    }
                }
            }
        }

        // Bottom 30%: HardwareInfoPanel (TEMP 20%, FAN 20%, 4 Rings 60%)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f)
                .padding(top = 5.dp)
        ) {
            val tempInt = telemetry.cpuTempCelsius.roundToInt()
            val tempTint = if (colors.isMegingiardStyle) {
                when {
                    tempInt >= 86 -> colors.error
                    tempInt >= 71 -> colors.error.copy(alpha = 0.85f)
                    tempInt >= 51 -> colors.accent
                    else -> colors.secondary
                }
            } else {
                when {
                    tempInt >= 86 -> Color(0xFFF64646)
                    tempInt >= 71 -> Color(0xFFF67474)
                    tempInt >= 51 -> Color(0xFFF6A6A6)
                    else -> Color(0xFF818184)
                }
            }
            val fanTint = if (colors.isMegingiardStyle) colors.secondary else Color(0xFF818184)

            HardwareInfoComponentCard(
                title = "TEMP",
                valueWithUnit = "${tempInt}℃",
                iconRes = R.drawable.ic_temp,
                iconTint = tempTint,
                iconSizeRate = 0.20f,
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxHeight()
                    .padding(end = 7.5.dp)
            )

            HardwareInfoComponentCard(
                title = "FAN",
                valueWithUnit = "${telemetry.fanSpeedPercent}%",
                iconRes = R.drawable.ic_fan_normal,
                iconTint = fanTint,
                iconSizeRate = 0.17f,
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxHeight()
                    .padding(start = 2.5.dp, end = 5.dp)
            )

            Row(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(start = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.panelBackground)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HardwareRingGauge(
                    title = "CPU",
                    valueText = formatRingValue(telemetry.cpuCurFreqGhz),
                    unitText = telemetry.cpuUnit,
                    value = telemetry.cpuCurFreqGhz,
                    minValue = 0f,
                    maxValue = telemetry.cpuMaxFreqGhz,
                    ringColor = if (colors.isMegingiardStyle) colors.accent else Color(0xFFEE188C),
                    modifier = Modifier.fillMaxHeight()
                )

                HardwareRingGauge(
                    title = "GPU",
                    valueText = formatRingValue(telemetry.gpuCurFreqMhz),
                    unitText = telemetry.gpuUnit,
                    value = telemetry.gpuCurFreqMhz,
                    minValue = 0f,
                    maxValue = telemetry.gpuMaxFreqMhz,
                    ringColor = if (colors.isMegingiardStyle) colors.accent else Color(0xFF70E7FF),
                    modifier = Modifier.fillMaxHeight()
                )

                HardwareRingGauge(
                    title = "PWR",
                    valueText = formatRingValue(telemetry.powerWatts),
                    unitText = "W",
                    value = telemetry.powerWatts,
                    minValue = -20f,
                    maxValue = 25f,
                    ringColor = if (colors.isMegingiardStyle) colors.accent else Color(0xFFE26E01),
                    bidirectional = true,
                    modifier = Modifier.fillMaxHeight()
                )

                HardwareRingGauge(
                    title = "RAM",
                    valueText = formatRingValue(telemetry.ramUsedGb),
                    unitText = "GB",
                    value = telemetry.ramUsedGb,
                    minValue = 0f,
                    maxValue = telemetry.ramTotalGb,
                    ringColor = if (colors.isMegingiardStyle) colors.accent else Color(0xFF76CA40),
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

/**
 * Formats ring gauge numeric values using the exact decimal logic of `B0.d.setNumberOfDecimalPlacesByProgress`:
 * - `0` when `< 0.01f`
 * - `%.2f` when `<= 9.99f`
 * - `%.1f` when `<= 99.9f`
 * - `%.0f` when `>= 100f`
 */
private fun formatRingValue(value: Float): String {
    val absVal = abs(value)
    if (absVal < 0.01f) return "0"
    val raw = when {
        absVal < 10f -> String.format(Locale.US, "%.2f", value)
        absVal < 100f -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.0f", value)
    }
    val truncated = if (raw.length > 5) raw.substring(0, 5) else raw
    return if (truncated.endsWith(".")) truncated.dropLast(1) else truncated
}

/**
 * Replicates `frame_rate_panel_layout.xml`, `FrameRateModeView.java`, and `CurrentFrameRateView.java`:
 * - Top 30% (`0%..30%`): Pill button `"120 FPS MODE"` / `"60 FPS MODE"` (`bg_120_frame_rate_mode` / `bg_60_frame_rate_mode`)
 * - Middle 40% (`30%..70%`): Huge bold live FPS number + baseline-aligned `"FPS"`
 * - Bottom 30% (`70%..100%`): `"CURRENT FPS"` in `colors.tertiary`
 */
@Composable
private fun FrameRatePanelCard(
    refreshRate: Int,
    currentFps: Int,
    onToggleRefreshRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isHighRate = refreshRate >= 90

    val pillGradient = if (isHighRate) {
        if (colors.isDark) {
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.5f to Color(0x1A9E21BE),
                1.0f to Color(0x66AB24CE)
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color(0x00D11AFF),
                1.0f to Color(0xFFE066FF)
            )
        }
    } else {
        if (colors.isDark) {
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.5f to Color(0x2970E7FF),
                1.0f to Color(0x6770E7FF)
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color(0x0070E7FF),
                1.0f to Color(0xFF1BBDDE)
            )
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panelBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 0%..30%: FrameRateModeView pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            val pillTextColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(25.dp))
                    .then(
                        if (colors.isMegingiardStyle) {
                            Modifier.background(colors.accent)
                        } else {
                            Modifier
                                .background(if (colors.isDark) Color(0xFF272727) else Color(0xFFD5D5D5))
                                .background(pillGradient)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleRefreshRate
                    )
                    .padding(horizontal = 15.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.frame_rate_mode_text, refreshRate),
                    color = pillTextColor,
                    fontSize = 16.sp,
                    fontWeight = if (colors.isMegingiardStyle) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        // 30%..70%: CurrentFrameRateView (exact Canvas drawing from CurrentFrameRateView.java)
        val primaryArgb = colors.primary.toArgb()
        val padPx = with(LocalDensity.current) { 5.dp.toPx() }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        ) {
            val h = size.height
            val numberStr = currentFps.coerceIn(0, refreshRate).toString()
            val unitStr = "FPS"

            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = h * 0.75f
                style = Paint.Style.FILL_AND_STROKE
                typeface = Typeface.DEFAULT_BOLD
                color = primaryArgb
            }
            val fm = numberPaint.fontMetrics
            val baselineY = (h * 0.5f) + ((abs(fm.ascent) - fm.descent) * 0.5f) + padPx
            val numberWidth = numberPaint.measureText(numberStr)

            val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = h * 0.20f
                style = Paint.Style.FILL
                typeface = Typeface.DEFAULT
                color = primaryArgb
            }
            val unitWidth = unitPaint.measureText(unitStr)
            val totalWidth = numberWidth + padPx * 2f + unitWidth
            val startX = (size.width - totalWidth) * 0.5f

            drawContext.canvas.nativeCanvas.apply {
                drawText(numberStr, startX, baselineY, numberPaint)
                drawText(unitStr, startX + numberWidth + padPx * 2f, baselineY, unitPaint)
            }
        }

        // 70%..100%: "CURRENT FPS"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.current_frame_rate_tip),
                color = colors.tertiary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun QuickSettingsGridPage(
    items: List<QuickSettingTileModel>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        for (rowIndex in 0 until 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (colIndex in 0 until 4) {
                    val itemIndex = rowIndex * 4 + colIndex
                    val item = items.getOrNull(itemIndex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item != null) {
                            TccButton(
                                label = item.label,
                                iconRes = item.iconRes,
                                selected = item.selected,
                                onClick = item.onClick
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Replicates `navigation_bar_layout.xml` and `NavigationBar.java`:
 * - Rounded card (`20.dp` radius, `colors.panelBackground`)
 * - 3 `TccButton`s (`Mode`, `Task`, `Settings`) spaced between `15%` and `85%` horizontal guidelines (`70%` width).
 */
@Composable
private fun NavigationBarSection(
    activeTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenRecents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panelBackground)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.70f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TccButton(
                label = stringResource(R.string.navigation_screen_control_text),
                iconRes = R.drawable.ic_screen_control,
                selected = activeTab == 0,
                onClick = { onSelectTab(0) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            TccButton(
                label = stringResource(R.string.navigation_task_manage_text),
                iconRes = R.drawable.ic_task_manage,
                selected = false,
                onClick = onOpenRecents,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            TccButton(
                label = stringResource(R.string.navigation_system_set_text),
                iconRes = R.drawable.ic_system_set,
                selected = activeTab == 1,
                onClick = { onSelectTab(1) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * Replicates `com.odin.dualscreen.assistant.dialog.a` (`tip_dialog_layout.xml`), shown when selecting
 * "Only top screen" unless "Do not show again" has been checked.
 */
@Composable
fun CloseScreenTipOverlay(
    themeMode: Int,
    onCancel: () -> Unit,
    onConfirm: (noMoreTip: Boolean) -> Unit
) {
    var noMoreTip by remember { mutableStateOf(false) }
    val colors = LocalTccColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.dialogBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = tccDrawable(R.drawable.ic_tip)),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.secondary),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tip_title_text),
                    color = colors.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.tip_content_text),
                color = colors.primary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .clickable { noMoreTip = !noMoreTip }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TccRadioCheckIndicator(
                    selected = noMoreTip,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tip_dialog_cb_text_no_more_tip),
                    color = colors.secondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x88E4E4ED),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = colors.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x88E4E4ED),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onConfirm(noMoreTip) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = colors.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
