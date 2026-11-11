package com.stormpanda.betterdualscreenassistant.ui.overlay

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import android.view.KeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.ui.components.TccDialogCheckboxRow
import com.stormpanda.betterdualscreenassistant.ui.components.TccDialogFpsSelectorRow
import com.stormpanda.betterdualscreenassistant.ui.components.TccDialogOption
import com.stormpanda.betterdualscreenassistant.ui.components.TccDialogSegmentedRow
import com.stormpanda.betterdualscreenassistant.ui.components.TccDialogToggleRow
import com.stormpanda.betterdualscreenassistant.ui.components.TccOptionDialog
import com.stormpanda.betterdualscreenassistant.ui.components.TccRadioCheckIndicator
import com.stormpanda.betterdualscreenassistant.ui.components.dialogDividerPadding
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalOverlayViewport
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccLookSpec
import com.stormpanda.betterdualscreenassistant.ui.theme.MegingiardAccentPresets
import com.stormpanda.betterdualscreenassistant.ui.theme.TccUiLook
import com.stormpanda.betterdualscreenassistant.ui.theme.TccUiLookPreference
import com.stormpanda.betterdualscreenassistant.ui.theme.aynDp
import com.stormpanda.betterdualscreenassistant.ui.theme.aynSp
import com.stormpanda.betterdualscreenassistant.ui.theme.resolveResponsiveDialogWidthDp
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import com.stormpanda.betterdualscreenassistant.ui.theme.tccTextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class ActiveDialog {
    NONE,
    FOCUS_LOCK,
    PERFORMANCE_MODE,
    FAN_CONTROL,
    CONTROLLER_STYLE,
    THEME_MODE,
    TRIGGER_MODE,
    SCREENSHOT,
    SCREEN_RECORD,
    CLOSE_BOTTOM_TIP
}

@Composable
private fun DialogFooterDivider() {
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = lookSpec.dialogDividerPadding())
            .height(1.dp)
            .background(colors.fourth)
    )
}

@Composable
internal fun AssistantOverlayDialogHost(
    activeDialog: ActiveDialog,
    profile: DeviceProfile,
    repository: SystemSettingsRepository,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onCaptureScreenshotRequest: (Int) -> Unit,
    onStartScreenRecordingRequest: (Int) -> Unit
) {
    var pendingDismissJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(activeDialog) {
        pendingDismissJob?.cancel()
        pendingDismissJob = null
        onDispose {
            pendingDismissJob?.cancel()
            pendingDismissJob = null
        }
    }

    if (activeDialog == ActiveDialog.NONE) return

    fun dismissAfterSelection() {
        pendingDismissJob?.cancel()
        pendingDismissJob = coroutineScope.launch {
            delay(300L)
            onDismiss()
        }
    }

    when (activeDialog) {
        ActiveDialog.NONE -> Unit

        ActiveDialog.CLOSE_BOTTOM_TIP -> {
            CloseScreenTipOverlay(
                tipBodyText = profile.closeBottomTipText,
                onCancel = onDismiss,
                onConfirm = { noMore ->
                    if (noMore) {
                        repository.setNoMoreCloseBottomScreenTip(true)
                    }
                    onDismiss()
                    coroutineScope.launch {
                        delay(100L)
                        repository.setDualScreenDisplayMode(1)
                    }
                }
            )
        }

        ActiveDialog.FOCUS_LOCK -> {
            val focusLock by repository.screenFocusLock.collectAsState()
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
                titleIconRes = R.drawable.ic_screen_focus_lock_mode,
                options = options,
                selectedId = focusLock,
                onSelectOption = { selected ->
                    repository.setScreenFocusLock(selected)
                    dismissAfterSelection()
                },
                onDismiss = onDismiss
            )
        }

        ActiveDialog.PERFORMANCE_MODE -> {
            val performanceMode by repository.performanceMode.collectAsState()
            val fanMode by repository.fanMode.collectAsState()
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
                titleIconRes = R.drawable.ic_qs_performance,
                options = options,
                selectedId = performanceMode,
                onSelectOption = { selected ->
                    repository.setPerformanceMode(selected)
                    if (selected == 2 && fanMode == 0) {
                        repository.setFanMode(4)
                    }
                    dismissAfterSelection()
                },
                onDismiss = onDismiss
            )
        }

        ActiveDialog.FAN_CONTROL -> {
            val performanceMode by repository.performanceMode.collectAsState()
            val fanMode by repository.fanMode.collectAsState()
            val fanSpeed by repository.fanSpeed.collectAsState()
            val options = buildList {
                if (performanceMode != 2) {
                    add(
                        TccDialogOption(
                            id = 0,
                            label = stringResource(R.string.text_fan_control_off),
                            iconRes = R.drawable.ic_ban_on_use
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
                titleIconRes = R.drawable.ic_qs_fan_control,
                options = options,
                selectedId = fanMode,
                showCustomFanSlider = true,
                customFanSpeed = fanSpeed,
                onCustomFanSpeedChange = { speed -> repository.setCustomFanSpeed(speed) },
                onSelectOption = { selected ->
                    repository.setFanMode(selected)
                    if (selected != 6) {
                        dismissAfterSelection()
                    }
                },
                onDismiss = onDismiss
            )
        }

        ActiveDialog.CONTROLLER_STYLE -> {
            val controllerStyle by repository.controllerStyle.collectAsState()
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
                    label = profile.controllerStyleOptionLabel,
                    iconRes = R.drawable.ic_controller_odin_style
                )
            )
            TccOptionDialog(
                title = stringResource(R.string.text_handle_style_title),
                titleIconRes = R.drawable.ic_qs_controller_style,
                options = options,
                selectedId = controllerStyle,
                onSelectOption = { selected ->
                    repository.setControllerStyle(selected)
                    dismissAfterSelection()
                },
                onDismiss = onDismiss
            )
        }

        ActiveDialog.THEME_MODE -> {
            LaunchedEffect(Unit) {
                repository.refreshMegingiardSyncedTheme()
            }
            val themeMode by repository.themeMode.collectAsState()
            val uiLookMode by repository.uiLookMode.collectAsState()
            val retroidReadabilityBoost by repository.retroidReadabilityBoost.collectAsState()
            val customAccentArgb by repository.customAccentArgb.collectAsState()
            val syncedMegingiardOption by repository.syncedMegingiardOption.collectAsState()
            val syncedMegingiardAccentArgb by repository.syncedMegingiardAccentArgb.collectAsState()
            val isMegingiardAvailable by repository.isMegingiardProviderAvailable.collectAsState()
            val injectOsTheme by repository.injectOsTheme.collectAsState()
            val isRetroidLookActive = TccUiLookPreference.fromId(uiLookMode)
                .resolve(profile) == TccUiLook.RETROID

            val rawSelectedThemeOption = AppThemeOption.fromId(themeMode)
            val selectedThemeOption = if (rawSelectedThemeOption == AppThemeOption.SYNC_MEGINGIARD && !isMegingiardAvailable) {
                syncedMegingiardOption
            } else {
                rawSelectedThemeOption
            }
            val customAccentColor = Color(customAccentArgb)
            val syncedAccentColor = syncedMegingiardAccentArgb?.let { Color(it) }
                ?: syncedMegingiardOption.previewAccent
            val visibleThemeOptions = AppThemeOption.entries.filter { opt ->
                opt != AppThemeOption.SYNC_MEGINGIARD || isMegingiardAvailable
            }
            val options = visibleThemeOptions.map { opt ->
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
                titleIconRes = R.drawable.ic_qs_theme_mode,
                options = options,
                selectedId = selectedThemeOption.id,
                accentPresets = if (selectedThemeOption.supportsCustomAccent) {
                    MegingiardAccentPresets
                } else {
                    emptyList()
                },
                selectedAccentArgb = customAccentArgb,
                onSelectAccentColor = { color -> repository.setCustomAccentArgb(color.toArgb()) },
                showOsThemeToggle = true,
                injectOsThemeEnabled = injectOsTheme,
                onToggleInjectOsTheme = { enabled -> repository.setInjectOsTheme(enabled) },
                onSelectOption = { selected ->
                    repository.setThemeMode(selected)
                    val newlySelected = AppThemeOption.fromId(selected)
                    if (!newlySelected.supportsCustomAccent && !injectOsTheme) {
                        dismissAfterSelection()
                    }
                },
                onDismiss = onDismiss,
                footerContent = {
                    DialogFooterDivider()
                    TccDialogSegmentedRow(
                        label = "Overlay UI Look",
                        segments = listOf(
                            TccUiLookPreference.AUTO.id to TccUiLookPreference.AUTO.shortLabel,
                            TccUiLookPreference.AYN.id to TccUiLookPreference.AYN.shortLabel,
                            TccUiLookPreference.RETROID.id to TccUiLookPreference.RETROID.shortLabel
                        ),
                        selectedId = uiLookMode,
                        onSelectId = { repository.setUiLookMode(it) }
                    )
                    DialogFooterDivider()
                    TccDialogCheckboxRow(
                        label = "Improve Retroid Readability",
                        checked = retroidReadabilityBoost,
                        enabled = isRetroidLookActive,
                        onCheckedChange = { repository.setRetroidReadabilityBoost(it) }
                    )
                }
            )
        }

        ActiveDialog.TRIGGER_MODE -> {
            val triggerMode by repository.triggerMode.collectAsState()
            val options = listOf(
                TccDialogOption(id = 1, label = "Analog Only", iconRes = R.drawable.ic_qs_trigger_mode),
                TccDialogOption(id = 0, label = "Digital Only", iconRes = R.drawable.ic_qs_trigger_mode),
                TccDialogOption(id = 2, label = "Both (Analog + Digital)", iconRes = R.drawable.ic_qs_trigger_mode)
            )
            TccOptionDialog(
                title = "L2 / R2 Trigger Mode",
                titleIconRes = R.drawable.ic_qs_trigger_mode,
                options = options,
                selectedId = triggerMode,
                onSelectOption = { selected ->
                    repository.setTriggerMode(selected)
                    dismissAfterSelection()
                },
                onDismiss = onDismiss
            )
        }

        ActiveDialog.SCREENSHOT -> {
            val options = screenTargetDialogOptions()
            TccOptionDialog(
                title = "Capture Screenshot",
                titleIconRes = R.drawable.ic_qs_screenshot,
                options = options,
                selectedId = -1,
                onSelectOption = { selected ->
                    onDismiss()
                    onCaptureScreenshotRequest(selected)
                },
                onDismiss = onDismiss
            )
        }

        ActiveDialog.SCREEN_RECORD -> {
            val screenRecordInternalAudio by repository.screenRecordInternalAudio.collectAsState()
            val screenRecordMicAudio by repository.screenRecordMicAudio.collectAsState()
            val screenRecordFps by repository.screenRecordFps.collectAsState()
            TccOptionDialog(
                title = "Record Screen",
                titleIconRes = R.drawable.ic_qs_screen_record,
                options = screenTargetDialogOptions(),
                selectedId = -1,
                onSelectOption = { selected ->
                    onDismiss()
                    onStartScreenRecordingRequest(selected)
                },
                onDismiss = onDismiss,
                footerContent = {
                    TccDialogToggleRow(
                        label = "Internal Game Audio",
                        checked = screenRecordInternalAudio,
                        onCheckedChange = { repository.setScreenRecordInternalAudio(it) }
                    )
                    DialogFooterDivider()
                    TccDialogToggleRow(
                        label = "Microphone",
                        checked = screenRecordMicAudio,
                        onCheckedChange = { repository.setScreenRecordMicAudio(it) }
                    )
                    DialogFooterDivider()
                    TccDialogFpsSelectorRow(
                        selectedFps = screenRecordFps,
                        onSelectFps = { repository.setScreenRecordFps(it) }
                    )
                }
            )
        }
    }
}

private fun screenTargetDialogOptions(): List<TccDialogOption> = listOf(
    TccDialogOption(id = 0, label = "Top Screen", iconRes = R.drawable.ic_just_screen_above),
    TccDialogOption(id = 1, label = "Bottom Screen", iconRes = R.drawable.ic_just_screen_below),
    TccDialogOption(id = 2, label = "Both Screens", iconRes = R.drawable.ic_dual_screen_display)
)

@Composable
fun CloseScreenTipOverlay(
    tipBodyText: String? = null,
    onCancel: () -> Unit,
    onConfirm: (noMoreTip: Boolean) -> Unit
) {
    var noMoreTip by remember { mutableStateOf(false) }
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val isRetroid = lookSpec.isRetroid
    val boost = lookSpec.retroidReadabilityBoost
    val dialogSpec = lookSpec.dialog
    val viewport = LocalOverlayViewport.current
    val labelStyle = tccTextStyle()
    val resolvedBodyText = tipBodyText ?: stringResource(R.string.tip_content_text)

    val tipDialogWidth = resolveResponsiveDialogWidthDp(
        preferredWidth = if (isRetroid) (if (boost) 556.dp else 520.dp) else aynDp(420f),
        viewportWidth = viewport.widthDp,
        maxViewportWidthFraction = 0.80f
    )
    val sectionGap = if (isRetroid) 18.dp else aynDp(14f)
    val buttonFontSize = if (isRetroid) (if (boost) 25.sp else 18.sp) else aynSp(15f)
    val confirmFocusRequester = remember { FocusRequester() }
    var isCheckboxFocused by remember { mutableStateOf(false) }
    var isCancelFocused by remember { mutableStateOf(false) }
    var isConfirmFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { confirmFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dialogSpec.backdropDimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(tipDialogWidth)
                .clip(dialogSpec.dialogShape)
                .background(colors.dialogBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(top = if (isRetroid) 24.dp else aynDp(18f)),
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
                    modifier = Modifier.size(if (isRetroid) 28.dp else aynDp(22f))
                )
                Spacer(modifier = Modifier.width(if (isRetroid) 10.dp else aynDp(8f)))
                Text(
                    text = stringResource(R.string.tip_title_text),
                    color = colors.primary,
                    fontSize = if (isRetroid) (if (boost) 30.sp else 22.sp) else aynSp(17f),
                    style = labelStyle,
                    fontWeight = if (isRetroid) FontWeight.Normal else FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            Text(
                text = resolvedBodyText,
                color = colors.primary,
                fontSize = if (isRetroid) (if (boost) 24.sp else 17.sp) else aynSp(14f),
                lineHeight = if (isRetroid) (if (boost) 30.sp else 22.sp) else aynSp(18f),
                style = labelStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = if (isRetroid) 30.dp else aynDp(24f))
            )

            Spacer(modifier = Modifier.height(sectionGap))

            Row(
                modifier = Modifier
                    .background(if (isCheckboxFocused) colors.panelBackgroundSelected else Color.Transparent)
                    .onFocusChanged { isCheckboxFocused = it.isFocused }
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A
                        ) {
                            noMoreTip = !noMoreTip
                            true
                        } else {
                            false
                        }
                    }
                    .clickable { noMoreTip = !noMoreTip }
                    .padding(
                        horizontal = if (isRetroid) 16.dp else aynDp(16f),
                        vertical = if (isRetroid) 6.dp else aynDp(6f)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TccRadioCheckIndicator(
                    selected = noMoreTip,
                    modifier = Modifier.size(if (isRetroid) 28.dp else aynDp(18f))
                )
                Spacer(modifier = Modifier.width(if (isRetroid) 10.dp else aynDp(8f)))
                Text(
                    text = stringResource(R.string.tip_dialog_cb_text_no_more_tip),
                    color = colors.secondary,
                    fontSize = dialogSpec.toggleFontSize,
                    style = labelStyle
                )
            }

            Spacer(modifier = Modifier.height(if (isRetroid) 16.dp else aynDp(12f)))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .then(
                        if (isRetroid) {
                            Modifier.background(colors.fourth)
                        } else {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0x88E4E4ED),
                                        Color.Transparent
                                    )
                                )
                            )
                        }
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isRetroid) 58.dp else dialogSpec.rowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isCancelFocused) colors.panelBackgroundSelected else Color.Transparent)
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A
                            ) {
                                onCancel()
                                true
                            } else {
                                false
                            }
                        }
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = colors.primary,
                        fontSize = buttonFontSize,
                        style = labelStyle,
                        fontWeight = if (isRetroid) FontWeight.Normal else FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .then(
                            if (isRetroid) {
                                Modifier.background(colors.fourth)
                            } else {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0x88E4E4ED),
                                            Color.Transparent
                                        )
                                    )
                                )
                            }
                        )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isConfirmFocused) colors.panelBackgroundSelected else Color.Transparent)
                        .focusRequester(confirmFocusRequester)
                        .onFocusChanged { isConfirmFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A
                            ) {
                                onConfirm(noMoreTip)
                                true
                            } else {
                                false
                            }
                        }
                        .clickable { onConfirm(noMoreTip) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = colors.accent,
                        fontSize = buttonFontSize,
                        style = labelStyle,
                        fontWeight = if (isRetroid) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }
        }
    }
}
