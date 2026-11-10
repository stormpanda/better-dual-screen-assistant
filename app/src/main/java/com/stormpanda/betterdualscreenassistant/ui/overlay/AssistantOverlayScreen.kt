package com.stormpanda.betterdualscreenassistant.ui.overlay

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.res.ResourcesCompat
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.HardwareTelemetryState
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.system.TelemetryMonitor
import com.stormpanda.betterdualscreenassistant.ui.components.HardwareInfoComponentCard
import com.stormpanda.betterdualscreenassistant.ui.components.HardwareRingGauge
import com.stormpanda.betterdualscreenassistant.ui.components.HorizontalSlidingBar
import com.stormpanda.betterdualscreenassistant.ui.components.SliderKind
import com.stormpanda.betterdualscreenassistant.ui.components.TccButton
import com.stormpanda.betterdualscreenassistant.ui.components.TccTileVariant
import com.stormpanda.betterdualscreenassistant.ui.statusbar.StatusBarRow
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalOverlayViewport
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccLookSpec
import com.stormpanda.betterdualscreenassistant.ui.theme.OverlayViewport
import com.stormpanda.betterdualscreenassistant.ui.theme.TccTheme
import com.stormpanda.betterdualscreenassistant.ui.theme.aynDp
import com.stormpanda.betterdualscreenassistant.ui.theme.retroidContentOnAccent
import com.stormpanda.betterdualscreenassistant.ui.theme.tccTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun formatRecordingElapsedSeconds(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600L
    val mins = (clamped % 3600L) / 60L
    val secs = clamped % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@Composable
fun AssistantOverlayScreen(
    repository: SystemSettingsRepository,
    telemetryMonitor: TelemetryMonitor,
    activeTabFlow: StateFlow<Int>,
    onSelectTab: (Int) -> Unit,
    onOpenRecentsRequest: () -> Unit,
    onOpenEditRequest: () -> Unit = {},
    onCleanMemoryRequest: () -> Unit,
    onClearAllTasksRequest: () -> Unit = {},
    onSwapScreensRequest: () -> Unit = {},
    onToggleTouchLockRequest: () -> Unit = {},
    onCaptureScreenshotRequest: (Int) -> Unit = {},
    onStartScreenRecordingRequest: (Int) -> Unit = {},
    onStopScreenRecordingRequest: () -> Unit = {},
    onDismissOverlayRequest: () -> Unit
) {
    val context = LocalContext.current
    val profile = remember(context) { DeviceProfile.current(context) }
    val themeMode by repository.themeMode.collectAsState()
    val uiLookMode by repository.uiLookMode.collectAsState()
    val customAccentArgb by repository.customAccentArgb.collectAsState()
    val syncedMegingiardOption by repository.syncedMegingiardOption.collectAsState()
    val syncedMegingiardAccentArgb by repository.syncedMegingiardAccentArgb.collectAsState()
    val showEditButton by repository.showEditButton.collectAsState()
    val screenRecordingState by repository.screenRecordingState.collectAsState()
    val telemetry by telemetryMonitor.state.collectAsState()
    val activeTab by activeTabFlow.collectAsState() // 0 = Mode, 1 = Settings

    var recordingElapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(screenRecordingState.isRecording, screenRecordingState.startedElapsedRealtimeMs) {
        if (!screenRecordingState.isRecording) {
            recordingElapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            val elapsedMs = (SystemClock.elapsedRealtime() - screenRecordingState.startedElapsedRealtimeMs)
                .coerceAtLeast(0L)
            recordingElapsedSeconds = elapsedMs / 1000L
            delay(500L)
        }
    }
    val recordingElapsedText = remember(recordingElapsedSeconds) {
        formatRecordingElapsedSeconds(recordingElapsedSeconds)
    }

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
        uiLookMode = uiLookMode,
        customAccentArgb = customAccentArgb,
        syncedMegingiardOption = syncedMegingiardOption,
        syncedMegingiardAccentArgb = syncedMegingiardAccentArgb
    ) {
        OverlayViewport {
            val colors = LocalTccColors.current
            val lookSpec = LocalTccLookSpec.current
            val viewport = LocalOverlayViewport.current
            val navSpec = lookSpec.navBar
            val sidePad = lookSpec.outerHorizontalPadding

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
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                val maxContentWidth = viewport.heightDp * lookSpec.maxContentAspectRatio
                val contentWidthModifier = if (viewport.widthDp > maxContentWidth) {
                    Modifier
                        .width(maxContentWidth)
                        .fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                }

                Column(modifier = contentWidthModifier) {
                    StatusBarRow(
                        onTimeClick = onCleanMemoryRequest,
                        isRecording = screenRecordingState.isRecording,
                        recordingElapsedText = recordingElapsedText,
                        onStopRecordingClick = onStopScreenRecordingRequest
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(
                                start = sidePad,
                                end = sidePad,
                                bottom = lookSpec.contentBottomPadding
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeTab == 0) {
                            ScreenControlTab(
                                profile = profile,
                                repository = repository,
                                onSwapScreens = onSwapScreensRequest,
                                onShowCloseBottomTip = { activeDialog = ActiveDialog.CLOSE_BOTTOM_TIP }
                            )
                        } else {
                            SystemSettingsTab(
                                profile = profile,
                                repository = repository,
                                telemetry = telemetry,
                                recordingElapsedText = recordingElapsedText,
                                onOpenDialog = { dialog -> activeDialog = dialog },
                                onStopScreenRecording = onStopScreenRecordingRequest,
                                onCleanMemory = onCleanMemoryRequest,
                                onClearAllTasks = onClearAllTasksRequest,
                                onToggleTouchLock = onToggleTouchLockRequest
                            )
                        }
                    }

                    NavigationBarSection(
                        activeTab = activeTab,
                        showEditButton = showEditButton,
                        onSelectTab = onSelectTab,
                        onOpenRecents = onOpenRecentsRequest,
                        onOpenEdit = onOpenEditRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (navSpec.fixedOuterHeight != null) {
                                    Modifier.height(navSpec.fixedOuterHeight)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(
                                top = navSpec.outerTopPadding,
                                start = sidePad,
                                end = sidePad,
                                bottom = navSpec.outerBottomPadding
                            )
                    )
                }

                AssistantOverlayDialogHost(
                    activeDialog = activeDialog,
                    profile = profile,
                    repository = repository,
                    coroutineScope = coroutineScope,
                    onDismiss = { activeDialog = ActiveDialog.NONE },
                    onCaptureScreenshotRequest = onCaptureScreenshotRequest,
                    onStartScreenRecordingRequest = onStartScreenRecordingRequest
                )
            }
        }
    }
}

/**
 * Unified, look-driven Mode tab (`ScreenModeControlPanel` + `Brightness` & `Volume` slider cards)
 * adapting smoothly across arbitrary aspect ratios and slider counts (3 or 4 with Stick LED).
 */
@Composable
private fun ScreenControlTab(
    profile: DeviceProfile,
    repository: SystemSettingsRepository,
    onSwapScreens: () -> Unit,
    onShowCloseBottomTip: () -> Unit
) {
    val dualScreenMode by repository.dualScreenDisplayMode.collectAsState()
    val dualBrightness by repository.dualBrightness.collectAsState()
    val topBrightness by repository.topBrightness.collectAsState()
    val bottomBrightness by repository.bottomBrightness.collectAsState()
    val isDualBrightnessActive by repository.isDualBrightnessActive.collectAsState()
    val showSwapScreensButton by repository.showSwapScreensButton.collectAsState()
    val showLedBrightnessSlider by repository.showLedBrightnessSlider.collectAsState()
    val ledBrightnessPercent by repository.ledBrightnessPercent.collectAsState()
    val ambientLightEnabled by repository.ambientLightEnabled.collectAsState()
    val dualVolume by repository.dualVolumePercent.collectAsState()
    val primaryVolume by repository.primaryVolumePercent.collectAsState()
    val secondaryVolume by repository.secondaryVolumePercent.collectAsState()
    val isDualVolumeActive by repository.isDualVolumeActive.collectAsState()

    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val reserveFourSliderLowerHeight = !lookSpec.isRetroid
    val modeSpec = lookSpec.modeTab
    val cardShape = lookSpec.panelCardShape
    val showFourthBrightnessSlider = profile.supportsAmbientLight && showLedBrightnessSlider

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val referenceThreeSliderCardHeight = (maxHeight * 0.5f) - aynDp(10f)
        val dynamicSliderHeight = referenceThreeSliderCardHeight * 0.2933f
        val dynamicCardVerticalPad = referenceThreeSliderCardHeight * 0.06f
        val reservedBottomCardsHeight = (dynamicSliderHeight * 4f) + (dynamicCardVerticalPad * 2f)

        val brightnessSliderHeight = when {
            reserveFourSliderLowerHeight -> dynamicSliderHeight
            showFourthBrightnessSlider -> modeSpec.fourSliderRowHeight
            else -> modeSpec.threeSliderRowHeight
        }
        val volumeSliderHeight = if (reserveFourSliderLowerHeight) {
            dynamicSliderHeight
        } else {
            modeSpec.threeSliderRowHeight
        }
        val cardVerticalPad = if (reserveFourSliderLowerHeight) {
            dynamicCardVerticalPad
        } else {
            modeSpec.fixedCardVerticalPadding
        }

        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (reserveFourSliderLowerHeight) 1f else modeSpec.upperCardWeight)
                    .padding(top = modeSpec.upperCardTopPadding)
                    .clip(cardShape)
                    .background(colors.panelBackground)
                    .padding(vertical = modeSpec.upperCardInnerVerticalPadding),
                contentAlignment = Alignment.Center
            ) {
                val modeItemCount = if (showSwapScreensButton) 4 else 3
                val maxModeLabelWidth = ((maxWidth / modeItemCount) - (modeSpec.modeItemHorizontalPadding * 2))
                    .coerceAtLeast(72.dp)
                val itemModifier = Modifier.padding(
                    horizontal = modeSpec.modeItemHorizontalPadding,
                    vertical = modeSpec.modeItemVerticalPadding
                )
                val selectMode: (Int) -> Unit = { mode ->
                    if (mode == 1 && dualScreenMode != 1 && repository.shouldShowCloseBottomScreenTip()) {
                        onShowCloseBottomTip()
                    } else {
                        repository.setDualScreenDisplayMode(mode)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TccButton(
                        label = stringResource(R.string.dualscreen_asynchronous_display),
                        iconRes = R.drawable.ic_dual_screen_display,
                        selected = dualScreenMode == 0,
                        onClick = { selectMode(0) },
                        variant = TccTileVariant.MODE,
                        maxLabelWidth = maxModeLabelWidth,
                        modifier = itemModifier
                    )
                    TccButton(
                        label = stringResource(R.string.only_theupper_screen_displayed),
                        iconRes = R.drawable.ic_just_screen_above,
                        selected = dualScreenMode == 1,
                        onClick = { selectMode(1) },
                        variant = TccTileVariant.MODE,
                        maxLabelWidth = maxModeLabelWidth,
                        modifier = itemModifier
                    )
                    TccButton(
                        label = stringResource(R.string.only_following_screen_displayed),
                        iconRes = R.drawable.ic_just_screen_below,
                        selected = dualScreenMode == 2,
                        onClick = { selectMode(2) },
                        variant = TccTileVariant.MODE,
                        maxLabelWidth = maxModeLabelWidth,
                        modifier = itemModifier
                    )
                    if (showSwapScreensButton) {
                        TccButton(
                            label = stringResource(R.string.swap_screens_mode_text),
                            iconRes = R.drawable.ic_qs_swap_screens,
                            selected = false,
                            onClick = onSwapScreens,
                            variant = TccTileVariant.MODE,
                            maxLabelWidth = maxModeLabelWidth,
                            modifier = itemModifier
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(modeSpec.middleGap))

            Row(
                modifier = if (reserveFourSliderLowerHeight) {
                    Modifier
                        .fillMaxWidth()
                        .height(reservedBottomCardsHeight)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(modeSpec.lowerRowWeight)
                        .padding(bottom = modeSpec.lowerRowBottomPadding)
                }
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = modeSpec.cardHalfGap)
                        .clip(cardShape)
                        .background(colors.panelBackground)
                        .padding(
                            horizontal = modeSpec.cardHorizontalPadding,
                            vertical = cardVerticalPad
                        ),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    HorizontalSlidingBar(
                        label = stringResource(R.string.sliding_dual_screen),
                        progress = dualBrightness,
                        kind = SliderKind.BRIGHTNESS,
                        onProgressChange = { pct, temp -> repository.setDualScreenBrightnessPercent(pct, temp) },
                        isFillDimmed = !isDualBrightnessActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(brightnessSliderHeight)
                    )
                    HorizontalSlidingBar(
                        label = stringResource(R.string.sliding_main_screen),
                        progress = topBrightness,
                        kind = SliderKind.BRIGHTNESS,
                        onProgressChange = { pct, temp -> repository.setDisplayBrightnessPercent(0, pct, temp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(brightnessSliderHeight)
                    )
                    HorizontalSlidingBar(
                        label = stringResource(R.string.sliding_secondary_screen),
                        progress = bottomBrightness,
                        kind = SliderKind.BRIGHTNESS,
                        onProgressChange = { pct, temp ->
                            repository.setDisplayBrightnessPercent(profile.bottomDisplayId, pct, temp)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(brightnessSliderHeight)
                    )
                    if (showFourthBrightnessSlider) {
                        HorizontalSlidingBar(
                            label = stringResource(R.string.sliding_stick_led),
                            progress = ledBrightnessPercent,
                            kind = SliderKind.LED_BRIGHTNESS,
                            onProgressChange = { pct, _ -> repository.setLedBrightnessPercent(pct) },
                            isFillDimmed = !ambientLightEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(brightnessSliderHeight)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = modeSpec.cardHalfGap)
                        .clip(cardShape)
                        .background(colors.panelBackground)
                        .padding(
                            horizontal = modeSpec.cardHorizontalPadding,
                            vertical = cardVerticalPad
                        ),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    HorizontalSlidingBar(
                        label = stringResource(R.string.sliding_dual_screen),
                        progress = dualVolume,
                        kind = SliderKind.VOLUME,
                        onProgressChange = { pct, _ -> repository.setDualVolumePercent(pct) },
                        isFillDimmed = !isDualVolumeActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(volumeSliderHeight)
                    )
                    HorizontalSlidingBar(
                        label = stringResource(R.string.sliding_main_screen),
                        progress = primaryVolume,
                        kind = SliderKind.VOLUME,
                        onProgressChange = { pct, _ -> repository.setPrimaryVolumePercent(pct) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(volumeSliderHeight)
                    )
                    HorizontalSlidingBar(
                        label = stringResource(R.string.sliding_secondary_screen),
                        progress = secondaryVolume,
                        kind = SliderKind.VOLUME,
                        onProgressChange = { pct, _ -> repository.setSecondaryVolumePercent(pct) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(volumeSliderHeight)
                    )
                }
            }
        }
    }
}

private data class QuickSettingTileModel(
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun buildQuickSettingTileModels(
    profile: DeviceProfile,
    repository: SystemSettingsRepository,
    recordingElapsedText: String,
    onOpenDialog: (ActiveDialog) -> Unit,
    onStopScreenRecording: () -> Unit,
    onCleanMemory: () -> Unit,
    onClearAllTasks: () -> Unit,
    onToggleTouchLock: () -> Unit
): List<QuickSettingTileModel> {
    val dualScreenMode by repository.dualScreenDisplayMode.collectAsState()
    val focusLock by repository.screenFocusLock.collectAsState()
    val performanceMode by repository.performanceMode.collectAsState()
    val fanMode by repository.fanMode.collectAsState()
    val controllerStyle by repository.controllerStyle.collectAsState()
    val chargingSeparation by repository.chargingSeparation.collectAsState()
    val vibrateOn by repository.vibrateOn.collectAsState()
    val ambientLight by repository.ambientLightEnabled.collectAsState()
    val forceLandscape by repository.forceLandscape.collectAsState()
    val themeMode by repository.themeMode.collectAsState()
    val isMegingiardAvailable by repository.isMegingiardProviderAvailable.collectAsState()
    val syncedMegingiardOption by repository.syncedMegingiardOption.collectAsState()
    val wifiEnabled by repository.wifiEnabled.collectAsState()
    val bluetoothEnabled by repository.bluetoothEnabled.collectAsState()
    val dndEnabled by repository.dndEnabled.collectAsState()
    val airplaneModeEnabled by repository.airplaneModeEnabled.collectAsState()
    val touchLockEnabled by repository.touchLockEnabled.collectAsState()
    val triggerMode by repository.triggerMode.collectAsState()
    val chargeLimit80 by repository.chargeLimit80.collectAsState()
    val gamepadMouseMode by repository.gamepadMouseMode.collectAsState()
    val keyMapperEnabled by repository.keyMapperEnabled.collectAsState()
    val keepScreenOn by repository.keepScreenOn.collectAsState()
    val preventAccidentalHome by repository.preventAccidentalHome.collectAsState()
    val screenRecordingState by repository.screenRecordingState.collectAsState()
    val quickTileOrder by repository.quickTileOrder.collectAsState()
    val disabledQuickTiles by repository.disabledQuickTiles.collectAsState()

    val isRecordingScreen = screenRecordingState.isRecording

    return buildList {
        for (tileId in quickTileOrder) {
            if (!tileId.isSupportedOn(profile) || tileId in disabledQuickTiles) continue
            when (tileId) {
                QuickSettingTileId.FOCUS_LOCK -> if (dualScreenMode == 0) {
                    val (focusLabel, focusSelected) = when (focusLock) {
                        1 -> stringResource(R.string.key_focus_mode_lock_on_upper_screen) to true
                        2 -> stringResource(R.string.key_focus_mode_lock_on_lower_screen) to true
                        else -> stringResource(R.string.key_focus_mode_touch_switchable) to false
                    }
                    add(QuickSettingTileModel(focusLabel, R.drawable.ic_screen_focus_lock_mode, focusSelected) {
                        onOpenDialog(ActiveDialog.FOCUS_LOCK)
                    })
                }
                QuickSettingTileId.PERFORMANCE_MODE -> {
                    val (perfLabel, perfSelected) = when (performanceMode) {
                        1 -> stringResource(R.string.text_performance_mode_performance) to true
                        2 -> stringResource(R.string.text_performance_mode_height_performance) to true
                        else -> stringResource(R.string.text_performance_mode_normal) to false
                    }
                    add(QuickSettingTileModel(perfLabel, R.drawable.ic_qs_performance, perfSelected) {
                        onOpenDialog(ActiveDialog.PERFORMANCE_MODE)
                    })
                }
                QuickSettingTileId.FAN_CONTROL -> {
                    val (fanLabel, fanSelected) = when (fanMode) {
                        1 -> stringResource(R.string.text_fan_control_low_speed) to true
                        4 -> stringResource(R.string.text_fan_control_smart) to true
                        5 -> stringResource(R.string.text_fan_control_medium_speed) to true
                        6 -> stringResource(R.string.text_fan_control_custom) to true
                        else -> stringResource(R.string.text_fan_control_off) to false
                    }
                    add(QuickSettingTileModel(fanLabel, R.drawable.ic_qs_fan_control, fanSelected) {
                        onOpenDialog(ActiveDialog.FAN_CONTROL)
                    })
                }
                QuickSettingTileId.CLEAR_MEMORY -> add(
                    QuickSettingTileModel(stringResource(R.string.text_clear_memory_title), R.drawable.ic_qs_clean, false, onCleanMemory)
                )
                QuickSettingTileId.CONTROLLER_STYLE -> {
                    val (ctrlLabel, ctrlSelected) = when (controllerStyle) {
                        0 -> stringResource(R.string.fan_option_xbox_style) to true
                        1 -> profile.controllerStyleOptionLabel to true
                        else -> stringResource(R.string.fan_option_disable) to false
                    }
                    add(QuickSettingTileModel(ctrlLabel, R.drawable.ic_qs_controller_style, ctrlSelected) {
                        onOpenDialog(ActiveDialog.CONTROLLER_STYLE)
                    })
                }
                QuickSettingTileId.CHARGING_SEPARATION -> add(
                    QuickSettingTileModel(
                        stringResource(R.string.text_charging_separation_title),
                        R.drawable.ic_qs_charging_separation,
                        chargingSeparation
                    ) { repository.toggleChargingSeparation() }
                )
                QuickSettingTileId.VIBRATION -> add(
                    QuickSettingTileModel(
                        stringResource(R.string.text_vibration_open_title),
                        R.drawable.ic_qs_vibrate,
                        vibrateOn
                    ) { repository.toggleVibrate() }
                )
                QuickSettingTileId.AMBIENT_LIGHT -> add(
                    QuickSettingTileModel(
                        stringResource(R.string.text_led_lights_open_title),
                        R.drawable.ic_qs_ambient_light,
                        ambientLight
                    ) { repository.toggleAmbientLight() }
                )
                QuickSettingTileId.FORCE_LANDSCAPE -> add(
                    QuickSettingTileModel(
                        stringResource(R.string.force_landscape_mode_text),
                        R.drawable.ic_landscape,
                        forceLandscape
                    ) { repository.toggleForceLandscape() }
                )
                QuickSettingTileId.THEME_MODE -> {
                    val rawThemeOpt = AppThemeOption.fromId(themeMode)
                    val currentThemeOpt = if (rawThemeOpt == AppThemeOption.SYNC_MEGINGIARD && !isMegingiardAvailable) {
                        syncedMegingiardOption
                    } else {
                        rawThemeOpt
                    }
                    val (themeLabel, themeSelected) = when (currentThemeOpt) {
                        AppThemeOption.STOCK_SYSTEM -> stringResource(R.string.text_system_theme) to false
                        AppThemeOption.STOCK_LIGHT -> stringResource(R.string.text_light_theme) to false
                        AppThemeOption.STOCK_DARK -> stringResource(R.string.text_dark_theme) to true
                        else -> currentThemeOpt.shortTileLabel to true
                    }
                    add(QuickSettingTileModel(themeLabel, R.drawable.ic_qs_theme_mode, themeSelected) {
                        onOpenDialog(ActiveDialog.THEME_MODE)
                    })
                }
                QuickSettingTileId.WIFI -> add(
                    QuickSettingTileModel("Wi-Fi", R.drawable.ic_qs_wifi, wifiEnabled) { repository.toggleWifi() }
                )
                QuickSettingTileId.BLUETOOTH -> add(
                    QuickSettingTileModel("Bluetooth", R.drawable.ic_bluetooth, bluetoothEnabled) { repository.toggleBluetooth() }
                )
                QuickSettingTileId.DO_NOT_DISTURB -> add(
                    QuickSettingTileModel("Do Not Disturb", R.drawable.ic_qs_dnd, dndEnabled) { repository.toggleDnd() }
                )
                QuickSettingTileId.AIRPLANE_MODE -> add(
                    QuickSettingTileModel("Airplane Mode", R.drawable.ic_qs_airplane, airplaneModeEnabled) { repository.toggleAirplaneMode() }
                )
                QuickSettingTileId.TOUCH_LOCK -> add(
                    QuickSettingTileModel(
                        if (touchLockEnabled) "Touch Locked" else "Touch Lock",
                        R.drawable.ic_qs_touch_lock,
                        touchLockEnabled,
                        onToggleTouchLock
                    )
                )
                QuickSettingTileId.SCREENSHOT -> add(
                    QuickSettingTileModel("Screenshot", R.drawable.ic_qs_screenshot, false) {
                        onOpenDialog(ActiveDialog.SCREENSHOT)
                    }
                )
                QuickSettingTileId.SCREEN_RECORD -> {
                    val tileLabel = if (isRecordingScreen) {
                        "Stop (${recordingElapsedText.ifEmpty { "00:00" }})"
                    } else {
                        "Screen Record"
                    }
                    add(QuickSettingTileModel(tileLabel, R.drawable.ic_qs_screen_record, isRecordingScreen) {
                        if (isRecordingScreen) onStopScreenRecording() else onOpenDialog(ActiveDialog.SCREEN_RECORD)
                    })
                }
                QuickSettingTileId.TRIGGER_MODE -> {
                    val triggerLabel = when (triggerMode) {
                        1 -> "Trigger: Analog"
                        0 -> "Trigger: Digital"
                        else -> "Trigger: Both"
                    }
                    add(QuickSettingTileModel(triggerLabel, R.drawable.ic_qs_trigger_mode, triggerMode != 2) {
                        onOpenDialog(ActiveDialog.TRIGGER_MODE)
                    })
                }
                QuickSettingTileId.CHARGE_LIMIT_80 -> add(
                    QuickSettingTileModel("80% Charge Limit", R.drawable.ic_qs_charge_limit, chargeLimit80) {
                        repository.toggleChargeLimit80()
                    }
                )
                QuickSettingTileId.GAMEPAD_MOUSE_MODE -> add(
                    QuickSettingTileModel("Mouse Mode", R.drawable.ic_qs_mouse_mode, gamepadMouseMode) {
                        repository.toggleGamepadMouseMode()
                    }
                )
                QuickSettingTileId.KEY_MAPPER -> add(
                    QuickSettingTileModel("Key Mapper", R.drawable.ic_qs_key_mapper, keyMapperEnabled) {
                        repository.toggleKeyMapper()
                    }
                )
                QuickSettingTileId.KEEP_SCREEN_ON -> add(
                    QuickSettingTileModel("Keep Screen On", R.drawable.ic_qs_keep_awake, keepScreenOn) {
                        repository.toggleKeepScreenOn()
                    }
                )
                QuickSettingTileId.PREVENT_ACCIDENTAL_HOME -> add(
                    QuickSettingTileModel("Home Lock", R.drawable.ic_qs_prevent_home, preventAccidentalHome) {
                        repository.togglePreventAccidentalHome()
                    }
                )
                QuickSettingTileId.CLEAR_ALL_TASKS -> add(
                    QuickSettingTileModel("Clear All Tasks", R.drawable.ic_qs_clear_tasks, false, onClearAllTasks)
                )
            }
        }
    }
}

@Composable
private fun SystemSettingsTab(
    profile: DeviceProfile,
    repository: SystemSettingsRepository,
    telemetry: HardwareTelemetryState,
    recordingElapsedText: String,
    onOpenDialog: (ActiveDialog) -> Unit,
    onStopScreenRecording: () -> Unit,
    onCleanMemory: () -> Unit,
    onClearAllTasks: () -> Unit,
    onToggleTouchLock: () -> Unit
) {
    val refreshRate by repository.refreshRate.collectAsState()
    val tempFahrenheit by repository.tempFahrenheit.collectAsState()
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val settingsSpec = lookSpec.settingsTab
    val viewport = LocalOverlayViewport.current
    val cardShape = lookSpec.panelCardShape
    val coroutineScope = rememberCoroutineScope()

    val tiles = buildQuickSettingTileModels(
        profile = profile,
        repository = repository,
        recordingElapsedText = recordingElapsedText,
        onOpenDialog = onOpenDialog,
        onStopScreenRecording = onStopScreenRecording,
        onCleanMemory = onCleanMemory,
        onClearAllTasks = onClearAllTasks,
        onToggleTouchLock = onToggleTouchLock
    )

    val pages = remember(tiles) { tiles.chunked(8) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
        }
    }

    val aspectFrac = viewport.aspectFraction()
    val leftColWeight = settingsSpec.narrowLeftColWeight +
        (settingsSpec.wideLeftColWeight - settingsSpec.narrowLeftColWeight) * aspectFrac
    val rightColWeight = 1f - leftColWeight
    val cardHalfGap = settingsSpec.cardHalfGap

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
                            if (pagerState.currentPage < pageCount - 1) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(settingsSpec.topRowWeight)
                .padding(
                    top = settingsSpec.topRowTopPadding,
                    bottom = settingsSpec.topRowBottomPadding
                )
        ) {
            FrameRatePanelCard(
                refreshRate = refreshRate,
                currentFps = telemetry.currentFps,
                onToggleRefreshRate = { repository.toggleRefreshRate() },
                modifier = Modifier
                    .weight(leftColWeight)
                    .fillMaxHeight()
                    .padding(end = cardHalfGap)
            )

            Column(
                modifier = Modifier
                    .weight(rightColWeight)
                    .fillMaxHeight()
                    .padding(start = cardHalfGap)
                    .clip(cardShape)
                    .background(colors.panelBackground),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.92f)
                        .padding(settingsSpec.pagerPadding)
                ) { pageIndex ->
                    val pageItems = pages.getOrNull(pageIndex).orEmpty()
                    QuickSettingsGridPage(items = pageItems)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.08f),
                    contentAlignment = Alignment.Center
                ) {
                    if (pageCount > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(settingsSpec.indicatorDotSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pageCount) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(settingsSpec.indicatorDotSize)
                                        .clip(settingsSpec.indicatorShape)
                                        .background(if (isSelected) colors.accent else colors.secondary)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(settingsSpec.bottomRowWeight)
                .padding(top = settingsSpec.bottomRowTopPadding, bottom = 0.dp)
        ) {
            val tempCelsiusInt = telemetry.cpuTempCelsius.roundToInt()
            val tempTint = when {
                colors.isMegingiardStyle -> when {
                    tempCelsiusInt >= 86 -> colors.error
                    tempCelsiusInt >= 71 -> colors.error.copy(alpha = 0.85f)
                    tempCelsiusInt >= 51 -> colors.accent
                    else -> colors.secondary
                }
                lookSpec.isRetroid -> when {
                    tempCelsiusInt >= 86 -> Color(0xFFF64646)
                    tempCelsiusInt >= 71 -> Color(0xFFF67474)
                    else -> if (colors.isDark) Color(0xFFDFDFDF) else colors.primary
                }
                else -> when {
                    tempCelsiusInt >= 86 -> Color(0xFFF64646)
                    tempCelsiusInt >= 71 -> Color(0xFFF67474)
                    tempCelsiusInt >= 51 -> Color(0xFFF6A6A6)
                    else -> Color(0xFF818184)
                }
            }
            val fanTint = when {
                colors.isMegingiardStyle -> colors.secondary
                lookSpec.isRetroid -> if (colors.isDark) Color(0xFFDFDFDF) else colors.primary
                else -> Color(0xFF818184)
            }
            val rawTempText = formatCpuTemperature(telemetry.cpuTempCelsius, tempFahrenheit)
            val displayTempText = if (lookSpec.isRetroid) {
                rawTempText.replace("℃", "°C").replace("℉", "°F")
            } else {
                rawTempText
            }

            Row(
                modifier = Modifier
                    .weight(leftColWeight)
                    .fillMaxHeight()
                    .padding(end = cardHalfGap),
                horizontalArrangement = Arrangement.spacedBy(settingsSpec.tempFanGap)
            ) {
                HardwareInfoComponentCard(
                    title = "TEMP",
                    valueWithUnit = displayTempText,
                    iconRes = R.drawable.ic_temp,
                    iconTint = tempTint,
                    iconSizeRate = 0.20f,
                    onClick = { repository.toggleTempFahrenheit() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                HardwareInfoComponentCard(
                    title = "FAN",
                    valueWithUnit = "${telemetry.fanSpeedPercent}%",
                    iconRes = R.drawable.ic_fan_normal,
                    iconTint = fanTint,
                    iconSizeRate = 0.19f,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .weight(rightColWeight)
                    .fillMaxHeight()
                    .padding(start = cardHalfGap)
                    .clip(cardShape)
                    .background(colors.panelBackground)
                    .padding(
                        horizontal = settingsSpec.ringsHorizontalPadding,
                        vertical = settingsSpec.ringsVerticalPadding
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cpuRingColor = when {
                    colors.isMegingiardStyle -> colors.accent
                    lookSpec.isRetroid -> Color(0xFFB125E3)
                    else -> Color(0xFFEE188C)
                }
                val gpuRingColor = when {
                    colors.isMegingiardStyle -> colors.accent
                    lookSpec.isRetroid -> Color(0xFF373FC6)
                    else -> Color(0xFF70E7FF)
                }
                val pwrRingColor = when {
                    colors.isMegingiardStyle -> colors.accent
                    lookSpec.isRetroid -> Color(0xFFE46D00)
                    else -> Color(0xFFE26E01)
                }
                val ramRingColor = when {
                    colors.isMegingiardStyle -> colors.accent
                    lookSpec.isRetroid -> Color(0xFF86D100)
                    else -> Color(0xFF76CA40)
                }

                HardwareRingGauge(
                    title = "CPU",
                    valueText = formatRingValue(telemetry.cpuCurFreqGhz),
                    unitText = telemetry.cpuUnit,
                    value = telemetry.cpuCurFreqGhz,
                    minValue = 0f,
                    maxValue = telemetry.cpuMaxFreqGhz,
                    ringColor = cpuRingColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                HardwareRingGauge(
                    title = "GPU",
                    valueText = formatRingValue(telemetry.gpuCurFreqMhz),
                    unitText = telemetry.gpuUnit,
                    value = telemetry.gpuCurFreqMhz,
                    minValue = 0f,
                    maxValue = telemetry.gpuMaxFreqMhz,
                    ringColor = gpuRingColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                HardwareRingGauge(
                    title = "PWR",
                    valueText = formatRingValue(telemetry.powerWatts),
                    unitText = "W",
                    value = telemetry.powerWatts,
                    minValue = -20f,
                    maxValue = 25f,
                    ringColor = pwrRingColor,
                    bidirectional = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                HardwareRingGauge(
                    title = "RAM",
                    valueText = formatRingValue(telemetry.ramUsedGb),
                    unitText = "GB",
                    value = telemetry.ramUsedGb,
                    minValue = 0f,
                    maxValue = telemetry.ramTotalGb,
                    ringColor = ramRingColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

internal fun formatCpuTemperature(cpuTempCelsius: Float, useFahrenheit: Boolean): String {
    return if (useFahrenheit) {
        val fahrenheit = ((cpuTempCelsius * 9f / 5f) + 32f).roundToInt()
        "${fahrenheit}℉"
    } else {
        "${cpuTempCelsius.roundToInt()}℃"
    }
}

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

@Composable
private fun FrameRatePanelCard(
    refreshRate: Int,
    currentFps: Int,
    onToggleRefreshRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val isRetroid = lookSpec.isRetroid
    val settingsSpec = lookSpec.settingsTab
    val labelStyle = tccTextStyle()

    val pixelDisplayTypeface = remember(context, isRetroid) {
        if (isRetroid) {
            runCatching { ResourcesCompat.getFont(context, R.font.tcc_pixel_display) }.getOrNull()
        } else {
            null
        }
    }
    val pixelLabelTypeface = remember(context, isRetroid) {
        if (isRetroid) {
            runCatching { ResourcesCompat.getFont(context, R.font.tcc_pixel_label) }.getOrNull()
        } else {
            null
        }
    }
    val isHighRate = refreshRate >= 90
    var isFocused by remember { mutableStateOf(false) }

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
            .clip(lookSpec.panelCardShape)
            .background(if (isFocused) colors.panelBackgroundSelected else colors.panelBackground)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                    (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onToggleRefreshRate()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isRetroid) 0.34f else 0.30f),
            contentAlignment = Alignment.Center
        ) {
            val retroidPillBg = when {
                colors.isMegingiardStyle -> if (isHighRate) colors.accent else colors.fifth
                isHighRate -> Color(0xFFFFB816)
                else -> Color(0xFF70E7FF)
            }
            val pillTextColor = when {
                isRetroid -> when {
                    colors.isMegingiardStyle && !isHighRate -> colors.primary
                    colors.isMegingiardStyle -> colors.retroidContentOnAccent()
                    else -> Color.Black
                }
                colors.isMegingiardStyle -> colors.onAccent
                else -> Color.White
            }
            val preferredBadgeW = settingsSpec.fpsBadgePreferredWidth
            val preferredBadgeH = settingsSpec.fpsBadgeHeight
            val badgeSizeModifier = if (preferredBadgeW != null && preferredBadgeH != null) {
                Modifier
                    .width(min(preferredBadgeW, (maxWidth - 20.dp).coerceAtLeast(120.dp)))
                    .height(preferredBadgeH)
            } else {
                Modifier
            }

            Box(
                modifier = badgeSizeModifier
                    .clip(settingsSpec.fpsBadgeShape)
                    .then(
                        when {
                            isRetroid -> Modifier.background(retroidPillBg)
                            colors.isMegingiardStyle -> Modifier.background(colors.accent)
                            else -> Modifier
                                .background(if (colors.isDark) Color(0xFF272727) else Color(0xFFD5D5D5))
                                .background(pillGradient)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleRefreshRate
                    )
                    .padding(
                        horizontal = settingsSpec.fpsBadgeHorizontalPadding,
                        vertical = settingsSpec.fpsBadgeVerticalPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.frame_rate_mode_text, refreshRate),
                    color = pillTextColor,
                    fontSize = settingsSpec.fpsBadgeFontSize,
                    style = labelStyle,
                    fontWeight = if (!isRetroid && colors.isMegingiardStyle) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1
                )
            }
        }

        val primaryArgb = colors.primary.toArgb()
        val padPx = with(LocalDensity.current) { (if (isRetroid) 5.dp else aynDp(5f)).toPx() }
        val numberPaint = remember(isRetroid, pixelDisplayTypeface) {
            if (isRetroid) {
                Paint().apply {
                    isAntiAlias = false
                    style = Paint.Style.FILL
                    typeface = pixelDisplayTypeface ?: Typeface.MONOSPACE
                }
            } else {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL_AND_STROKE
                    typeface = Typeface.DEFAULT_BOLD
                }
            }
        }
        val unitPaint = remember(isRetroid, pixelLabelTypeface) {
            if (isRetroid) {
                Paint().apply {
                    isAntiAlias = false
                    style = Paint.Style.FILL
                    typeface = pixelLabelTypeface ?: Typeface.MONOSPACE
                }
            } else {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    typeface = Typeface.DEFAULT
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isRetroid) 0.38f else 0.40f)
        ) {
            val h = size.height
            val numberStr = currentFps.coerceIn(0, refreshRate).toString()
            val unitStr = "FPS"

            numberPaint.textSize = h * (if (isRetroid) 0.76f else 0.75f)
            numberPaint.color = primaryArgb
            val fm = numberPaint.fontMetrics
            val baselineY = (h * 0.5f) + ((abs(fm.ascent) - fm.descent) * 0.5f) +
                (if (!isRetroid) padPx else 0f)
            val numberWidth = numberPaint.measureText(numberStr)

            unitPaint.textSize = h * (if (isRetroid) 0.18f else 0.20f)
            unitPaint.color = primaryArgb
            val unitWidth = unitPaint.measureText(unitStr)
            val totalWidth = numberWidth + padPx * 2f + unitWidth
            val startX = (size.width - totalWidth) * 0.5f

            drawContext.canvas.nativeCanvas.apply {
                drawText(numberStr, startX, baselineY, numberPaint)
                drawText(unitStr, startX + numberWidth + padPx * 2f, baselineY, unitPaint)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isRetroid) 0.28f else 0.30f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.current_frame_rate_tip),
                color = colors.tertiary,
                fontSize = settingsSpec.fpsTipFontSize,
                style = labelStyle,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun QuickSettingsGridPage(
    items: List<QuickSettingTileModel>
) {
    val lookSpec = LocalTccLookSpec.current
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
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item != null) {
                            val cellAllowance = if (lookSpec.isRetroid) {
                                (maxWidth - 4.dp).coerceAtLeast(80.dp)
                            } else {
                                maxWidth.coerceAtLeast(80.dp)
                            }
                            TccButton(
                                label = item.label,
                                iconRes = item.iconRes,
                                selected = item.selected,
                                onClick = item.onClick,
                                variant = TccTileVariant.QUICK_SETTING,
                                maxLabelWidth = cellAllowance
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationBarSection(
    activeTab: Int,
    showEditButton: Boolean,
    onSelectTab: (Int) -> Unit,
    onOpenRecents: () -> Unit,
    onOpenEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val navSpec = lookSpec.navBar
    val viewport = LocalOverlayViewport.current
    val aspectFrac = viewport.aspectFraction()

    val rowFraction = if (showEditButton) {
        navSpec.narrowRowFraction4 + (navSpec.wideRowFraction4 - navSpec.narrowRowFraction4) * aspectFrac
    } else {
        navSpec.narrowRowFraction3 + (navSpec.wideRowFraction3 - navSpec.narrowRowFraction3) * aspectFrac
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(lookSpec.panelCardShape)
            .background(colors.panelBackground)
            .padding(vertical = navSpec.innerVerticalPadding),
        contentAlignment = Alignment.Center
    ) {
        val navItemCount = if (showEditButton) 4 else 3
        val maxNavLabelWidth = (((maxWidth * rowFraction) / navItemCount) - (navSpec.itemHorizontalPadding * 2))
            .coerceAtLeast(64.dp)
        val itemModifier = Modifier.padding(horizontal = navSpec.itemHorizontalPadding)

        Row(
            modifier = Modifier.fillMaxWidth(rowFraction),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TccButton(
                label = stringResource(R.string.navigation_screen_control_text),
                iconRes = R.drawable.ic_screen_control,
                selected = activeTab == 0,
                onClick = { onSelectTab(0) },
                variant = TccTileVariant.NAV_BAR,
                maxLabelWidth = maxNavLabelWidth,
                modifier = itemModifier
            )

            TccButton(
                label = stringResource(R.string.navigation_task_manage_text),
                iconRes = R.drawable.ic_task_manage,
                selected = false,
                onClick = onOpenRecents,
                variant = TccTileVariant.NAV_BAR,
                maxLabelWidth = maxNavLabelWidth,
                modifier = itemModifier
            )

            TccButton(
                label = stringResource(R.string.navigation_system_set_text),
                iconRes = R.drawable.ic_system_set,
                selected = activeTab == 1,
                onClick = { onSelectTab(1) },
                variant = TccTileVariant.NAV_BAR,
                maxLabelWidth = maxNavLabelWidth,
                modifier = itemModifier
            )

            if (showEditButton) {
                TccButton(
                    label = stringResource(R.string.navigation_edit_text),
                    iconRes = R.drawable.ic_nav_edit,
                    selected = false,
                    onClick = onOpenEdit,
                    variant = TccTileVariant.NAV_BAR,
                    maxLabelWidth = maxNavLabelWidth,
                    modifier = itemModifier
                )
            }
        }
    }
}
