package com.stormpanda.betterdualscreenassistant

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stormpanda.betterdualscreenassistant.plugin.BdsaPluginContract
import com.stormpanda.betterdualscreenassistant.plugin.PluginIconLoader
import com.stormpanda.betterdualscreenassistant.plugin.PluginRegistry
import com.stormpanda.betterdualscreenassistant.plugin.PluginTileDefinition
import com.stormpanda.betterdualscreenassistant.plugin.RegisteredPlugin
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.AppLanguage
import com.stormpanda.betterdualscreenassistant.system.LocaleHelper
import com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.QuickSettingCategory
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.ui.components.PixelCornerCutShape
import com.stormpanda.betterdualscreenassistant.ui.components.tccTileBackground
import com.stormpanda.betterdualscreenassistant.ui.components.tccTileIconTint
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccLookSpec
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccUiLook
import com.stormpanda.betterdualscreenassistant.ui.theme.MegingiardAccentPresets
import com.stormpanda.betterdualscreenassistant.ui.theme.TccColorPalette
import com.stormpanda.betterdualscreenassistant.ui.theme.TccTheme
import com.stormpanda.betterdualscreenassistant.ui.theme.TccUiLook
import com.stormpanda.betterdualscreenassistant.ui.theme.TccUiLookPreference
import com.stormpanda.betterdualscreenassistant.ui.theme.contrastingContentColor
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import com.stormpanda.betterdualscreenassistant.ui.theme.tccFontFamily
import com.stormpanda.betterdualscreenassistant.ui.theme.tccTextStyle
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppPalettes
import com.stormpanda.megingiard.ui.BumperDirection
import com.stormpanda.megingiard.ui.DialogToastManager
import com.stormpanda.megingiard.ui.DialogToastPill
import com.stormpanda.megingiard.ui.GamepadActionCard
import com.stormpanda.megingiard.ui.GamepadCardRow
import com.stormpanda.megingiard.ui.GamepadCategoryTile
import com.stormpanda.megingiard.ui.GamepadChoiceCard
import com.stormpanda.megingiard.ui.GamepadColorPaletteCard
import com.stormpanda.megingiard.ui.GamepadColorSwatch
import com.stormpanda.megingiard.ui.GamepadDeck
import com.stormpanda.megingiard.ui.GamepadFocusCard
import com.stormpanda.megingiard.ui.GamepadInfoBox
import com.stormpanda.megingiard.ui.GamepadPill
import com.stormpanda.megingiard.ui.GamepadSectionHeader
import com.stormpanda.megingiard.ui.GamepadSliderCard
import com.stormpanda.megingiard.ui.GamepadToggleCard
import com.stormpanda.megingiard.ui.GamepadTwoPaneScaffold
import com.stormpanda.megingiard.ui.GamepadTwoStepConfirmCard
import com.stormpanda.megingiard.ui.LocalAppColors
import com.stormpanda.megingiard.ui.LocalBreadcrumbConsumer
import com.stormpanda.megingiard.ui.LocalDeckBackInterceptor
import com.stormpanda.megingiard.ui.LocalDeckCardRegistry
import com.stormpanda.megingiard.ui.LocalFirstContentRequester
import com.stormpanda.megingiard.ui.LocalLastFocusedDeckTracker
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.launchUrlOnPrimaryDisplay
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.update.AppReleaseInfo
import com.stormpanda.megingiard.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAndRemoveTask()
                }
            }
        )
        UpdateManager.init(this)
        UpdateManager.checkForUpdates(force = false)
        ensureOverlayStateForDisplay()

        setContent {
            val context = LocalContext.current
            val repo = remember {
                SystemSettingsRepository.getInstance(context.applicationContext).also {
                    it.register()
                }
            }
            val themeModeId by repo.themeMode.collectAsState()
            val uiLookMode by repo.uiLookMode.collectAsState()
            val retroidReadabilityBoost by repo.retroidReadabilityBoost.collectAsState()
            val customAccentArgb by repo.customAccentArgb.collectAsState()
            val syncedMegingiardOption by repo.syncedMegingiardOption.collectAsState()
            val syncedMegingiardAccentArgb by repo.syncedMegingiardAccentArgb.collectAsState()
            val promotedToManager by repo.promotedToManager.collectAsState()

            LaunchedEffect(promotedToManager) {
                title = getString(
                    if (promotedToManager) R.string.app_name_manager else R.string.app_name
                )
            }

            val selectedTheme = AppThemeOption.fromId(themeModeId)
            val effectiveOption = if (selectedTheme == AppThemeOption.SYNC_MEGINGIARD) {
                syncedMegingiardOption
            } else {
                selectedTheme
            }
            val effectiveAccentArgb = if (selectedTheme == AppThemeOption.SYNC_MEGINGIARD) {
                syncedMegingiardAccentArgb ?: customAccentArgb
            } else {
                customAccentArgb
            }

            TccTheme(
                themeMode = themeModeId,
                uiLookMode = uiLookMode,
                retroidReadabilityBoost = retroidReadabilityBoost,
                customAccentArgb = customAccentArgb,
                syncedMegingiardOption = syncedMegingiardOption,
                syncedMegingiardAccentArgb = syncedMegingiardAccentArgb
            ) {
                val tccColors = LocalTccColors.current
                val appColors = remember(effectiveOption, effectiveAccentArgb, tccColors) {
                    resolveMegingiardAppColors(
                        option = effectiveOption,
                        accentArgb = effectiveAccentArgb,
                        tccColors = tccColors
                    )
                }

                CompositionLocalProvider(LocalAppColors provides appColors) {
                    MaterialTheme(
                        colorScheme = colorSchemeFor(appColors),
                        typography = megingiardTypography
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(appColors.appBackground)
                                .statusBarsPadding()
                        ) {
                            BetterDualScreenSettingsScreen(repo = repo)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SystemSettingsRepository.getInstance(applicationContext)
            .setMainActivityStarted(System.identityHashCode(this), started = true)
        ensureOverlayStateForDisplay()
    }

    override fun onResume() {
        super.onResume()
        val repo = SystemSettingsRepository.getInstance(applicationContext)
        repo.setSettingsUiActive(System.identityHashCode(this), active = true)
        repo.refreshMegingiardSyncedTheme()
        repo.refreshClusterTuneInstalled()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                SystemSettingsRepository.getInstance(applicationContext)
                    .setSettingsUiActive(System.identityHashCode(this), active = true)
            }
        } else {
            SystemSettingsRepository.getInstance(applicationContext).setSettingsUiActive(
                activityToken = System.identityHashCode(this),
                active = false,
                flushIfInactive = !isChangingConfigurations
            )
        }
    }

    override fun onStop() {
        val repo = SystemSettingsRepository.getInstance(applicationContext)
        val token = System.identityHashCode(this)
        val flush = !isChangingConfigurations
        repo.setSettingsUiActive(
            activityToken = token,
            active = false,
            flushIfInactive = flush
        )
        repo.setMainActivityStarted(
            activityToken = token,
            started = false,
            flushIfStopped = flush
        )
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && isDeviceInteractive()) {
            finishAndRemoveTask()
        }
    }

    private fun isDeviceInteractive(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ensureOverlayStateForDisplay()
    }

    private fun ensureOverlayStateForDisplay() {
        val currentDisplayId = try {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } catch (_: UnsupportedOperationException) {
            Display.DEFAULT_DISPLAY
        }
        if (currentDisplayId != DeviceProfile.current(this).bottomDisplayId) {
            AssistantForegroundService.startService(
                this,
                AssistantForegroundService.ACTION_SHOW_OVERLAY
            )
        } else {
            AssistantForegroundService.startService(this)
        }
    }

    private var pendingUnhandledButtonBDown = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            PrimaryOverlayInputBridge.notifyTouchInput()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val handled = PrimaryOverlayInputBridge.processGenericMotionEvent(ev) { action, keyCode ->
            dispatchKeyEvent(KeyEvent(action, keyCode))
        }
        return if (handled) true else super.dispatchGenericMotionEvent(ev)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            PrimaryOverlayInputBridge.notifyKeyInput()
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    PrimaryOverlayInputBridge.sendBumper(BumperDirection.PREV)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
                    return true
                }
            }
        }

        val handled = super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (handled) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    pendingUnhandledButtonBDown = false
                }
                return true
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        pendingUnhandledButtonBDown = true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    val shouldTriggerBack = pendingUnhandledButtonBDown && !event.isCanceled
                    pendingUnhandledButtonBDown = false
                    if (shouldTriggerBack) {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
            return true
        }
        if (!handled && event.action == KeyEvent.ACTION_DOWN && isNavigationOrActionKey(event.keyCode)) {
            PrimaryOverlayInputBridge.sendFocusRecovery(event.keyCode)
            return true
        }
        return handled
    }

    override fun onDestroy() {
        val repo = SystemSettingsRepository.getInstance(applicationContext)
        val token = System.identityHashCode(this)
        val flush = !isChangingConfigurations
        repo.setSettingsUiActive(
            activityToken = token,
            active = false,
            flushIfInactive = flush
        )
        repo.setMainActivityStarted(
            activityToken = token,
            started = false,
            flushIfStopped = flush
        )
        pendingUnhandledButtonBDown = false
        PrimaryOverlayInputBridge.resetJoystickState(preserveInputMode = isChangingConfigurations)
        super.onDestroy()
    }

    private fun isNavigationOrActionKey(keyCode: Int): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER -> true
            else -> false
        }
}

private fun resolveMegingiardAppColors(
    option: AppThemeOption,
    accentArgb: Int,
    tccColors: TccColorPalette
): AppColors {
    val megingiardMode = when (option) {
        AppThemeOption.MEGINGIARD_DARK -> ThemeMode.DARK
        AppThemeOption.MEGINGIARD_DARK_OLED -> ThemeMode.DARK_OLED
        AppThemeOption.MEGINGIARD -> ThemeMode.MEGINGIARD
        AppThemeOption.MJOLNIR -> ThemeMode.MJOLNIR
        AppThemeOption.VALHALLA -> ThemeMode.VALHALLA
        AppThemeOption.AURORA -> ThemeMode.AURORA
        AppThemeOption.RETRO_PHOSPHOR -> ThemeMode.RETRO_PHOSPHOR
        AppThemeOption.ROYAL_ASGARD -> ThemeMode.ROYAL_ASGARD
        else -> null
    }
    if (megingiardMode != null) {
        return paletteFor(
            mode = megingiardMode,
            userAccent = if (megingiardMode.supportsCustomAccent) Color(accentArgb) else null
        )
    }

    return AppColors(
        appBackground = tccColors.primaryBackground,
        surface = tccColors.panelBackground,
        surfaceVariant = tccColors.panelBackgroundSelected,
        onSurface = tccColors.primary,
        onSurfaceSecondary = tccColors.secondary,
        accent = tccColors.accent,
        onAccent = tccColors.onAccent,
        divider = tccColors.primary.copy(alpha = 0.10f),
        error = if (tccColors.isDark) tccColors.error else Color(0xFFD32F2F),
        sectionHeaderColor = tccColors.accent,
        settingsSeparator = tccColors.primary.copy(alpha = 0.12f)
    )
}

private enum class LauncherCategory(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector
) {
    GENERAL(R.string.settings_cat_general, Icons.Rounded.Tune),
    QUICK_TOGGLES(R.string.settings_cat_quick_toggles, Icons.Rounded.GridView),
    APPEARANCE(R.string.settings_cat_appearance, Icons.Rounded.Palette),
    PLUGINS(R.string.settings_cat_plugins, Icons.Rounded.Extension),
    UPDATES(R.string.settings_cat_updates, Icons.Rounded.SystemUpdate),
    DIAGNOSTICS(R.string.settings_cat_diagnostics, Icons.Rounded.HealthAndSafety)
}

private enum class LauncherSubPage(
    val parentCategory: LauncherCategory,
    @param:StringRes val breadcrumbTitleRes: Int
) {
    CUSTOM_ACCENT(LauncherCategory.APPEARANCE, R.string.settings_subpage_custom_color),
    ENABLE_DISABLE_TOGGLES(LauncherCategory.QUICK_TOGGLES, R.string.settings_subpage_enable_disable),
    LICENSES_OVERVIEW(LauncherCategory.GENERAL, R.string.settings_subpage_licenses),
    LICENSE_APP(LauncherCategory.GENERAL, R.string.settings_subpage_license_app),
    LICENSE_FUSION_PIXEL(LauncherCategory.GENERAL, R.string.settings_subpage_license_fusion_pixel),
    LICENSE_DOSIYAGI(LauncherCategory.GENERAL, R.string.settings_subpage_license_dosiyagi),
    LICENSE_ANDROIDX(LauncherCategory.GENERAL, R.string.settings_subpage_license_androidx),
    LICENSE_KOTLIN(LauncherCategory.GENERAL, R.string.settings_subpage_license_kotlin)
}

private enum class ThemeSourceOption(
    @param:StringRes val displayNameRes: Int
) {
    STOCK(R.string.theme_source_stock),
    SYNC_MEGINGIARD(R.string.theme_source_sync_megingiard),
    CUSTOM_PALETTE(R.string.theme_source_custom_palette)
}

private val StockThemeOptions = listOf(
    AppThemeOption.STOCK_SYSTEM,
    AppThemeOption.STOCK_LIGHT,
    AppThemeOption.STOCK_DARK
)

private val MegingiardThemeOptions = listOf(
    AppThemeOption.MEGINGIARD_DARK,
    AppThemeOption.MEGINGIARD_DARK_OLED,
    AppThemeOption.MEGINGIARD,
    AppThemeOption.MJOLNIR,
    AppThemeOption.VALHALLA,
    AppThemeOption.AURORA,
    AppThemeOption.RETRO_PHOSPHOR,
    AppThemeOption.ROYAL_ASGARD
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BetterDualScreenSettingsScreen(repo: SystemSettingsRepository) {
    val context = LocalContext.current
    val profile = remember(context) { DeviceProfile.current(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isOverlayDark = LocalTccColors.current.isDark

    val themeModeId by repo.themeMode.collectAsState()
    val uiLookMode by repo.uiLookMode.collectAsState()
    val retroidReadabilityBoost by repo.retroidReadabilityBoost.collectAsState()
    val selectedTheme = AppThemeOption.fromId(themeModeId)
    val customAccentArgb by repo.customAccentArgb.collectAsState()
    val injectSystemTheme by repo.injectOsTheme.collectAsState()
    val syncedMegingiardOption by repo.syncedMegingiardOption.collectAsState()
    val showCloseBottomScreenTip by repo.showCloseBottomScreenTip.collectAsState()
    val rememberLastTab by repo.rememberLastTab.collectAsState()
    val rememberLastQuickTogglePage by repo.rememberLastQuickTogglePage.collectAsState()
    val showLedBrightnessSlider by repo.showLedBrightnessSlider.collectAsState()
    val showSwapScreensButton by repo.showSwapScreensButton.collectAsState()
    val showEditButton by repo.showEditButton.collectAsState()
    val accurateGameFps by repo.accurateGameFps.collectAsState()
    val cpuFreqScalingMode by repo.cpuFreqScalingMode.collectAsState()
    val tempFahrenheit by repo.tempFahrenheit.collectAsState()
    val screenshotTargetMode by repo.screenshotTargetMode.collectAsState()
    val promotedToManager by repo.promotedToManager.collectAsState()
    val isMegingiardAvailable by repo.isMegingiardProviderAvailable.collectAsState()
    val isClusterTuneInstalled by repo.isClusterTuneInstalled.collectAsState()
    val quickTileOrder by repo.quickTileOrder.collectAsState()
    val unifiedQuickTileOrder by repo.unifiedQuickTileOrder.collectAsState()
    val disabledQuickTiles by repo.disabledQuickTiles.collectAsState()
    val pluginRegistry = remember(context) { PluginRegistry.getInstance(context) }
    val registeredPlugins by pluginRegistry.plugins.collectAsState()
    val enabledPluginTileKeys by pluginRegistry.enabledPluginTileKeys.collectAsState()

    val autoUpdateCheckEnabled by UpdateManager.autoUpdateCheckEnabled.collectAsStateWithLifecycle()
    val updateAvailable by UpdateManager.updateAvailable.collectAsStateWithLifecycle()
    val latestReleaseInfo by UpdateManager.latestReleaseInfo.collectAsStateWithLifecycle()
    val isCheckingUpdates by UpdateManager.isChecking.collectAsStateWithLifecycle()
    val updateCheckError by UpdateManager.checkError.collectAsStateWithLifecycle()
    var hasTriggeredManualCheck by remember { mutableStateOf(false) }

    var selectedCategory by rememberSaveable { mutableStateOf(LauncherCategory.GENERAL) }
    var subPageStack by rememberSaveable { mutableStateOf<List<LauncherSubPage>>(emptyList()) }
    var savedDeckFocusKeys by rememberSaveable(
        stateSaver = listSaver<Map<Int, String>, Any>(
            save = { map -> map.entries.flatMap { (depth, key) -> listOf(depth, key) } },
            restore = { flat ->
                buildMap {
                    var i = 0
                    while (i + 1 < flat.size) {
                        val depth = flat[i] as? Int
                        val key = flat[i + 1] as? String
                        if (depth != null && key != null) {
                            put(depth, key)
                        }
                        i += 2
                    }
                }
            }
        )
    ) { mutableStateOf(emptyMap()) }
    val currentSubPage = subPageStack.lastOrNull()
    var isVisualReorderGridActive by remember { mutableStateOf(false) }

    val helperConnected by PrivilegedBridge.isHelperConnected.collectAsState()
    var pServerAvailable by remember { mutableStateOf(false) }
    var stockDisabled by remember { mutableStateOf(true) }
    var isRefreshingStatus by remember { mutableStateOf(false) }
    var customSwatchColorArgb by rememberSaveable { mutableIntStateOf(customAccentArgb) }

    LaunchedEffect(customAccentArgb) {
        if (Color(customAccentArgb) !in MegingiardAccentPresets) {
            customSwatchColorArgb = customAccentArgb
        }
    }

    suspend fun refreshStatus() {
        withContext(Dispatchers.IO) {
            val pAvail = PServerClient.isAvailable()
            val sEnabled = PServerClient.isStockAssistantEnabled(context)
            withContext(Dispatchers.Main) {
                pServerAvailable = pAvail
                stockDisabled = !sEnabled
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repo.refreshAll()
                pluginRegistry.refreshPlugins()
                scope.launch { refreshStatus() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val categoryList = remember {
        LauncherCategory.entries.filter { it != LauncherCategory.DIAGNOSTICS || BuildConfig.DEBUG }
    }
    LaunchedEffect(categoryList) {
        if (selectedCategory !in categoryList) {
            selectedCategory = LauncherCategory.GENERAL
        }
    }
    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            if (!isVisualReorderGridActive) {
                selectedCategory = categoryList.cycle(selectedCategory, direction)
                subPageStack = emptyList()
            }
        }
    }

    val colors = LocalAppColors.current
    val activeToast by DialogToastManager.currentToast.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.surfaceVariant.copy(alpha = 0.8f))
                .padding(horizontal = 16.dp)
                .focusProperties {
                    canFocus = false
                    enter = { FocusRequester.Cancel }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                        }
                )
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                        }
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = stringResource(
                    if (promotedToManager) R.string.app_wordmark_manager else R.string.app_wordmark_assistant
                ),
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            DialogToastPill(
                toast = activeToast,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Image(
                painter = painterResource(R.drawable.support_me_on_kofi_dark),
                contentDescription = stringResource(R.string.settings_support_app),
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .focusProperties { canFocus = false }
                    .clickable {
                        launchUrlOnPrimaryDisplay(context, "https://ko-fi.com/stormpanda")
                    }
            )
        }

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusGroup()
        ) {
            GamepadTwoPaneScaffold(
                scrollableDeck = false,
                isCustomBackActive = subPageStack.isNotEmpty(),
                onCustomBack = {
                    subPageStack = subPageStack.dropLast(1)
                },
                navigationKey = subPageStack,
                categoryKey = selectedCategory,
                savedFocusKeys = savedDeckFocusKeys,
                onRecordFocusedKey = { depth, key ->
                    if (key is String && savedDeckFocusKeys[depth] != key) {
                        savedDeckFocusKeys = savedDeckFocusKeys + (depth to key)
                    }
                },
                onRemoveFocusedKey = { depth ->
                    if (depth in savedDeckFocusKeys) {
                        savedDeckFocusKeys = savedDeckFocusKeys - depth
                    }
                },
                sidebarContent = {
                    categoryList.forEach { category ->
                        GamepadCategoryTile(
                            title = stringResource(category.titleRes),
                            icon = category.icon,
                            selected = (currentSubPage?.parentCategory ?: selectedCategory) == category,
                            onClick = {
                                selectedCategory = category
                                subPageStack = emptyList()
                            }
                        )
                    }
                },
                content = {
                    AnimatedContent(
                        targetState = subPageStack,
                        transitionSpec = {
                            val direction = if (targetState.size < initialState.size) -1 else 1
                            slideInHorizontally { width -> width * direction }.togetherWith(
                                slideOutHorizontally { width -> -width * direction }
                            )
                        },
                        label = "LauncherSubPageAnimation"
                    ) { stack ->
                        val isTargetPage = stack == subPageStack
                        val exitingFirstRequester = remember { FocusRequester() }
                        val pageContent: @Composable () -> Unit = {
                            val subPage = stack.lastOrNull()
                            when (subPage) {
                                null -> {
                                GamepadDeck(title = stringResource(selectedCategory.titleRes)) {
                                    when (selectedCategory) {
                                        LauncherCategory.GENERAL -> {
                                            GeneralSettingsTabContent(
                                                profile = profile,
                                                updateAvailable = updateAvailable,
                                                latestReleaseInfo = latestReleaseInfo,
                                                stockDisabled = stockDisabled,
                                                showCloseBottomScreenTip = showCloseBottomScreenTip,
                                                rememberLastTab = rememberLastTab,
                                                rememberLastQuickTogglePage = rememberLastQuickTogglePage,
                                                showEditButton = showEditButton,
                                                showSwapScreensButton = showSwapScreensButton,
                                                showLedBrightnessSlider = showLedBrightnessSlider,
                                                accurateGameFps = accurateGameFps,
                                                cpuFreqScalingMode = cpuFreqScalingMode,
                                                tempFahrenheit = tempFahrenheit,
                                                screenshotTargetMode = screenshotTargetMode,
                                                promotedToManager = promotedToManager,
                                                onToggleReplaceStock = { replaceStock ->
                                                    stockDisabled = replaceStock
                                                    scope.launch(Dispatchers.IO) {
                                                        PServerClient.setStockAssistantEnabled(!replaceStock, context)
                                                        refreshStatus()
                                                    }
                                                },
                                                onToggleCloseBottomScreenTip = { enabled ->
                                                    repo.setNoMoreCloseBottomScreenTip(!enabled)
                                                },
                                                onToggleRememberLastTab = { enabled ->
                                                    repo.setRememberLastTab(enabled)
                                                },
                                                onToggleRememberLastQuickTogglePage = { enabled ->
                                                    repo.setRememberLastQuickTogglePage(enabled)
                                                },
                                                onToggleShowEditButton = { enabled ->
                                                    repo.setShowEditButton(enabled)
                                                },
                                                onToggleShowSwapScreensButton = { enabled ->
                                                    repo.setShowSwapScreensButton(enabled)
                                                },
                                                onToggleShowLedBrightnessSlider = { enabled ->
                                                    repo.setShowLedBrightnessSlider(enabled)
                                                },
                                                onToggleAccurateGameFps = { enabled ->
                                                    repo.setAccurateGameFps(enabled)
                                                },
                                                onSetCpuFreqScalingMode = { scalingMode ->
                                                    repo.setCpuFreqScalingMode(scalingMode)
                                                },
                                                onSetTempFahrenheit = { fahrenheit ->
                                                    repo.setTempFahrenheit(fahrenheit)
                                                },
                                                onSetScreenshotTargetMode = { mode ->
                                                    repo.setScreenshotTargetMode(mode)
                                                },
                                                onTogglePromotedToManager = { promoted ->
                                                    repo.setPromotedToManager(promoted)
                                                    DialogToastManager.show(
                                                        context.getString(
                                                            if (promoted) {
                                                                R.string.settings_toast_promoted
                                                            } else {
                                                                R.string.settings_toast_demoted
                                                            }
                                                        )
                                                    )
                                                },
                                                onOpenLicenses = {
                                                    subPageStack = listOf(LauncherSubPage.LICENSES_OVERVIEW)
                                                }
                                            )
                                        }

                                        LauncherCategory.QUICK_TOGGLES -> {
                                            QuickTogglesSettingsTabContent(
                                                profile = profile,
                                                isClusterTuneInstalled = isClusterTuneInstalled,
                                                unifiedQuickTileOrder = unifiedQuickTileOrder,
                                                disabledQuickTiles = disabledQuickTiles,
                                                registeredPlugins = registeredPlugins,
                                                enabledPluginTileKeys = enabledPluginTileKeys,
                                                onMoveActiveTile = { fromIndex, toIndex, activeKeys ->
                                                    repo.moveActiveUnifiedQuickTile(
                                                        fromActiveIndex = fromIndex,
                                                        toActiveIndex = toIndex,
                                                        activeOrderedKeys = activeKeys
                                                    )
                                                },
                                                onOpenEnableDisableSubPage = {
                                                    subPageStack = listOf(LauncherSubPage.ENABLE_DISABLE_TOGGLES)
                                                },
                                                onResetDefaults = {
                                                    repo.resetQuickTilesToDefault()
                                                },
                                                onGridActiveChanged = { active ->
                                                    isVisualReorderGridActive = active
                                                }
                                            )
                                        }

                                        LauncherCategory.APPEARANCE -> {
                                            AppearanceSettingsTabContent(
                                                profile = profile,
                                                selectedTheme = selectedTheme,
                                                uiLookMode = uiLookMode,
                                                retroidReadabilityBoost = retroidReadabilityBoost,
                                                customAccentArgb = customAccentArgb,
                                                customSwatchColorArgb = customSwatchColorArgb,
                                                injectSystemTheme = injectSystemTheme,
                                                syncedMegingiardOption = syncedMegingiardOption,
                                                isMegingiardAvailable = isMegingiardAvailable,
                                                promotedToManager = promotedToManager,
                                                onSelectTheme = { repo.setThemeMode(it.id) },
                                                onSetUiLookMode = { repo.setUiLookMode(it) },
                                                onToggleRetroidReadabilityBoost = {
                                                    repo.setRetroidReadabilityBoost(it)
                                                },
                                                onSelectAccentColor = { repo.setCustomAccentArgb(it) },
                                                onOpenCustomAccent = {
                                                    subPageStack = listOf(LauncherSubPage.CUSTOM_ACCENT)
                                                },
                                                onToggleInjectSystemTheme = { repo.setInjectOsTheme(it) }
                                            )
                                        }

                                        LauncherCategory.PLUGINS -> {
                                            PluginsSettingsTabContent(
                                                profile = profile,
                                                plugins = registeredPlugins,
                                                onTogglePluginEnabled = { pkg, enabled ->
                                                    pluginRegistry.setPluginEnabled(pkg, enabled)
                                                    val activePluginCount = pluginRegistry.activePluginTilesMap(profile).size
                                                    repo.ensureAtLeastOneTileEnabled(activePluginCount)
                                                },
                                                onRescanPlugins = {
                                                    pluginRegistry.refreshPlugins()
                                                    val count = pluginRegistry.plugins.value.size
                                                    DialogToastManager.show(
                                                        context.resources.getQuantityString(
                                                            R.plurals.settings_plugins_rescan_toast,
                                                            count,
                                                            count
                                                        )
                                                    )
                                                }
                                            )
                                        }

                                        LauncherCategory.UPDATES -> {
                                            UpdatesSettingsTabContent(
                                                autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                                                updateAvailable = updateAvailable,
                                                latestReleaseInfo = latestReleaseInfo,
                                                isCheckingUpdates = isCheckingUpdates,
                                                updateCheckError = updateCheckError,
                                                hasTriggeredManualCheck = hasTriggeredManualCheck,
                                                promotedToManager = promotedToManager,
                                                onTriggerManualCheck = {
                                                    hasTriggeredManualCheck = true
                                                    UpdateManager.checkForUpdates(
                                                        force = true,
                                                        currentVersion = BuildConfig.VERSION_NAME
                                                    )
                                                }
                                            )
                                        }

                                        LauncherCategory.DIAGNOSTICS -> {
                                            if (BuildConfig.DEBUG) {
                                                DiagnosticsSettingsTabContent(
                                                    profile = profile,
                                                    helperConnected = helperConnected,
                                                    pServerAvailable = pServerAvailable,
                                                    stockDisabled = stockDisabled,
                                                    isMegingiardAvailable = isMegingiardAvailable,
                                                    syncedMegingiardOption = syncedMegingiardOption,
                                                    isRefreshing = isRefreshingStatus,
                                                    onReconnectAndRefresh = {
                                                        if (isRefreshingStatus) return@DiagnosticsSettingsTabContent
                                                        isRefreshingStatus = true
                                                        scope.launch(Dispatchers.IO) {
                                                            PrivilegedBridge.ensureInitialized(context)
                                                            PServerClient.ensurePermissionsGranted(context)
                                                            repo.refreshAll()
                                                            refreshStatus()
                                                            withContext(Dispatchers.Main) {
                                                                isRefreshingStatus = false
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            LauncherSubPage.CUSTOM_ACCENT -> {
                                GamepadDeck(
                                    breadcrumbs = listOf(
                                        stringResource(LauncherCategory.APPEARANCE.titleRes),
                                        stringResource(LauncherSubPage.CUSTOM_ACCENT.breadcrumbTitleRes)
                                    )
                                ) {
                                    CustomAccentSubPageContent(
                                        initialColor = Color(customSwatchColorArgb),
                                        onSaveColor = { newColor ->
                                            val argb = newColor.toArgb()
                                            customSwatchColorArgb = argb
                                            repo.setCustomAccentArgb(argb)
                                            subPageStack = subPageStack.dropLast(1)
                                        }
                                    )
                                }
                            }

                            LauncherSubPage.ENABLE_DISABLE_TOGGLES -> {
                                GamepadDeck(
                                    breadcrumbs = listOf(
                                        stringResource(LauncherCategory.QUICK_TOGGLES.titleRes),
                                        stringResource(LauncherSubPage.ENABLE_DISABLE_TOGGLES.breadcrumbTitleRes)
                                    )
                                ) {
                                    EnableDisableTogglesSubPageContent(
                                        profile = profile,
                                        isClusterTuneInstalled = isClusterTuneInstalled,
                                        quickTileOrder = quickTileOrder,
                                        disabledQuickTiles = disabledQuickTiles,
                                        registeredPlugins = registeredPlugins,
                                        enabledPluginTileKeys = enabledPluginTileKeys,
                                        promotedToManager = promotedToManager,
                                        onSetTileEnabled = { tile, enabled ->
                                            val activePluginCount = pluginRegistry.activePluginTilesMap(profile).size
                                            repo.setQuickTileEnabled(tile, enabled, activePluginCount)
                                        },
                                        onSetPluginTileEnabled = { tileKey, enabled ->
                                            if (pluginRegistry.setPluginTileEnabled(tileKey, enabled, isDarkTheme = isOverlayDark) && enabled) {
                                                repo.ensureQuickTileKeyInOrder(tileKey)
                                            }
                                            val activePluginCount = pluginRegistry.activePluginTilesMap(profile).size
                                            repo.ensureAtLeastOneTileEnabled(activePluginCount)
                                        }
                                    )
                                }
                            }

                            LauncherSubPage.LICENSES_OVERVIEW -> {
                                GamepadDeck(
                                    breadcrumbs = listOf(
                                        stringResource(LauncherCategory.GENERAL.titleRes),
                                        stringResource(LauncherSubPage.LICENSES_OVERVIEW.breadcrumbTitleRes)
                                    )
                                ) {
                                    LicensesOverviewSubPageContent(
                                        onOpenLicenseDetail = { detailSubPage ->
                                            subPageStack = subPageStack + detailSubPage
                                        }
                                    )
                                }
                            }

                            LauncherSubPage.LICENSE_APP,
                            LauncherSubPage.LICENSE_FUSION_PIXEL,
                            LauncherSubPage.LICENSE_DOSIYAGI,
                            LauncherSubPage.LICENSE_ANDROIDX,
                            LauncherSubPage.LICENSE_KOTLIN -> {
                                GamepadDeck(
                                    breadcrumbs = listOf(
                                        stringResource(LauncherCategory.GENERAL.titleRes),
                                        stringResource(LauncherSubPage.LICENSES_OVERVIEW.breadcrumbTitleRes),
                                        stringResource(subPage.breadcrumbTitleRes)
                                    ),
                                    scrollable = false
                                ) {
                                    LicenseDetailSubPageContent(subPage = subPage)
                                }
                            }
                        }
                        }

                        if (isTargetPage) {
                            pageContent()
                        } else {
                            CompositionLocalProvider(
                                LocalFirstContentRequester provides exitingFirstRequester,
                                LocalDeckCardRegistry provides null,
                                LocalLastFocusedDeckTracker provides null,
                                LocalBreadcrumbConsumer provides null
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusProperties {
                                            canFocus = false
                                            enter = { FocusRequester.Cancel }
                                        }
                                        .focusGroup()
                                ) {
                                    pageContent()
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun GeneralSettingsTabContent(
    profile: DeviceProfile,
    updateAvailable: Boolean,
    latestReleaseInfo: AppReleaseInfo?,
    stockDisabled: Boolean,
    showCloseBottomScreenTip: Boolean,
    rememberLastTab: Boolean,
    rememberLastQuickTogglePage: Boolean,
    showEditButton: Boolean,
    showSwapScreensButton: Boolean,
    showLedBrightnessSlider: Boolean,
    accurateGameFps: Boolean,
    cpuFreqScalingMode: Boolean,
    tempFahrenheit: Boolean,
    screenshotTargetMode: Int,
    promotedToManager: Boolean,
    onToggleReplaceStock: (Boolean) -> Unit,
    onToggleCloseBottomScreenTip: (Boolean) -> Unit,
    onToggleRememberLastTab: (Boolean) -> Unit,
    onToggleRememberLastQuickTogglePage: (Boolean) -> Unit,
    onToggleShowEditButton: (Boolean) -> Unit,
    onToggleShowSwapScreensButton: (Boolean) -> Unit,
    onToggleShowLedBrightnessSlider: (Boolean) -> Unit,
    onToggleAccurateGameFps: (Boolean) -> Unit,
    onSetCpuFreqScalingMode: (Boolean) -> Unit,
    onSetTempFahrenheit: (Boolean) -> Unit,
    onSetScreenshotTargetMode: (Int) -> Unit,
    onTogglePromotedToManager: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit = {}
) {
    val context = LocalContext.current
    val appDisplayName = stringResource(
        if (promotedToManager) R.string.app_name_manager else R.string.app_name
    )

    if (updateAvailable && latestReleaseInfo != null) {
        GamepadActionCard(
            title = stringResource(R.string.settings_update_available_banner, latestReleaseInfo.tagName),
            description = stringResource(R.string.settings_update_available_banner_desc),
            icon = Icons.Rounded.SystemUpdate,
            onClick = {
                launchUrlOnPrimaryDisplay(context, latestReleaseInfo.htmlUrl)
            },
            modifier = Modifier.firstDeckItem()
        )
    }

    GamepadToggleCard(
        title = stringResource(R.string.settings_replace_stock_title),
        description = if (stockDisabled) {
            stringResource(profile.replaceStockDescActiveRes, appDisplayName)
        } else {
            stringResource(profile.replaceStockDescInactiveRes)
        },
        checked = stockDisabled,
        icon = Icons.Rounded.Layers,
        onCheckedChange = onToggleReplaceStock,
        modifier = Modifier.firstDeckItem(isFirst = !updateAvailable || latestReleaseInfo == null)
    )

    if (AppLanguage.hasSelectableTranslations) {
        val configuration = LocalConfiguration.current
        val languages = AppLanguage.entries
        var selectedLanguage by remember(context, configuration) {
            mutableStateOf(LocaleHelper.currentLanguage(context))
        }
        GamepadChoiceCard(
            title = stringResource(R.string.settings_language),
            description = stringResource(R.string.settings_language_desc),
            selectedText = selectedLanguage.displayLabel(context),
            icon = Icons.Rounded.Language,
            itemKey = "settings_language",
            onPrevious = {
                val prev = languages.cycle(selectedLanguage, BumperDirection.PREV)
                selectedLanguage = prev
                LocaleHelper.applyLanguage(context, prev)
            },
            onNext = {
                val next = languages.cycle(selectedLanguage, BumperDirection.NEXT)
                selectedLanguage = next
                LocaleHelper.applyLanguage(context, next)
            }
        )
    }

    GamepadToggleCard(
        title = stringResource(R.string.settings_accurate_game_fps_title),
        description = stringResource(R.string.settings_accurate_game_fps_desc),
        checked = accurateGameFps,
        icon = Icons.Rounded.Speed,
        onCheckedChange = onToggleAccurateGameFps
    )

    GamepadChoiceCard(
        title = stringResource(R.string.settings_cpu_max_speed_mode_title),
        description = stringResource(R.string.settings_cpu_max_speed_mode_desc),
        selectedText = stringResource(
            if (cpuFreqScalingMode) {
                R.string.settings_cpu_max_speed_perf_limit
            } else {
                R.string.settings_cpu_max_speed_hardware_max
            }
        ),
        icon = Icons.Rounded.DeveloperBoard,
        onPrevious = { onSetCpuFreqScalingMode(!cpuFreqScalingMode) },
        onNext = { onSetCpuFreqScalingMode(!cpuFreqScalingMode) }
    )

    GamepadChoiceCard(
        title = stringResource(R.string.settings_temp_unit_title),
        description = stringResource(R.string.settings_temp_unit_desc),
        selectedText = stringResource(
            if (tempFahrenheit) {
                R.string.settings_temp_unit_fahrenheit
            } else {
                R.string.settings_temp_unit_celsius
            }
        ),
        icon = Icons.Rounded.Thermostat,
        onPrevious = { onSetTempFahrenheit(!tempFahrenheit) },
        onNext = { onSetTempFahrenheit(!tempFahrenheit) }
    )

    val screenshotTargetModes = listOf(
        SystemSettingsRepository.SCREENSHOT_TARGET_ASK to stringResource(R.string.settings_screenshot_ask_every_time),
        SystemSettingsRepository.SCREENSHOT_TARGET_TOP to stringResource(R.string.settings_screenshot_top),
        SystemSettingsRepository.SCREENSHOT_TARGET_BOTTOM to stringResource(R.string.settings_screenshot_bottom),
        SystemSettingsRepository.SCREENSHOT_TARGET_BOTH to stringResource(R.string.settings_screenshot_both)
    )
    val currentScreenshotIndex = screenshotTargetModes
        .indexOfFirst { it.first == screenshotTargetMode }
        .coerceAtLeast(0)
    GamepadChoiceCard(
        title = stringResource(R.string.settings_screenshot_display_title),
        description = stringResource(R.string.settings_screenshot_display_desc),
        selectedText = screenshotTargetModes[currentScreenshotIndex].second,
        icon = Icons.Rounded.Screenshot,
        onPrevious = {
            val prevIdx = (currentScreenshotIndex - 1 + screenshotTargetModes.size) % screenshotTargetModes.size
            onSetScreenshotTargetMode(screenshotTargetModes[prevIdx].first)
        },
        onNext = {
            val nextIdx = (currentScreenshotIndex + 1) % screenshotTargetModes.size
            onSetScreenshotTargetMode(screenshotTargetModes[nextIdx].first)
        }
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_show_edit_button_title),
        description = stringResource(R.string.settings_show_edit_button_desc),
        checked = showEditButton,
        icon = Icons.Rounded.Edit,
        onCheckedChange = onToggleShowEditButton
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_remember_last_tab_title),
        description = stringResource(
            if (promotedToManager) {
                R.string.settings_remember_last_tab_desc_manager
            } else {
                R.string.settings_remember_last_tab_desc_assistant
            }
        ),
        checked = rememberLastTab,
        icon = Icons.Rounded.Tab,
        onCheckedChange = onToggleRememberLastTab
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_remember_last_qs_page_title),
        description = stringResource(
            if (promotedToManager) {
                R.string.settings_remember_last_qs_page_desc_manager
            } else {
                R.string.settings_remember_last_qs_page_desc_assistant
            }
        ),
        checked = rememberLastQuickTogglePage,
        icon = Icons.Rounded.GridView,
        onCheckedChange = onToggleRememberLastQuickTogglePage
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_show_swap_screens_title),
        description = stringResource(R.string.settings_show_swap_screens_desc),
        checked = showSwapScreensButton,
        icon = Icons.Rounded.Sync,
        onCheckedChange = onToggleShowSwapScreensButton
    )

    if (profile.supportsAmbientLight) {
        GamepadToggleCard(
            title = stringResource(R.string.settings_show_led_slider_title),
            description = stringResource(R.string.settings_show_led_slider_desc),
            checked = showLedBrightnessSlider,
            icon = Icons.Rounded.Tune,
            onCheckedChange = onToggleShowLedBrightnessSlider
        )
    }

    GamepadToggleCard(
        title = stringResource(R.string.settings_confirm_close_bottom_title),
        description = stringResource(R.string.settings_confirm_close_bottom_desc),
        checked = showCloseBottomScreenTip,
        icon = Icons.Rounded.NotificationsActive,
        onCheckedChange = onToggleCloseBottomScreenTip
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_promote_manager_title),
        description = stringResource(
            if (promotedToManager) {
                R.string.settings_promote_manager_desc_promoted
            } else {
                R.string.settings_promote_manager_desc_unpromoted
            }
        ),
        checked = promotedToManager,
        icon = Icons.Rounded.WorkspacePremium,
        onCheckedChange = onTogglePromotedToManager
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_licenses_title),
        description = stringResource(R.string.settings_licenses_desc),
        icon = Icons.Rounded.Gavel,
        actionText = stringResource(R.string.settings_action_view),
        itemKey = "licenses_and_notices",
        onClick = onOpenLicenses
    )
}

private data class ReorderTileItem(
    val key: String,
    val title: String,
    val gridTitle: String,
    val iconRes: Int,
    val iconBitmap: ImageBitmap? = null
)

@Composable
private fun QuickTogglesSettingsTabContent(
    profile: DeviceProfile,
    isClusterTuneInstalled: Boolean,
    unifiedQuickTileOrder: List<String>,
    disabledQuickTiles: Set<QuickSettingTileId>,
    registeredPlugins: List<RegisteredPlugin>,
    enabledPluginTileKeys: Set<String>,
    onMoveActiveTile: (fromIndex: Int, toIndex: Int, activeKeys: List<String>) -> Unit,
    onOpenEnableDisableSubPage: () -> Unit,
    onResetDefaults: () -> Unit,
    onGridActiveChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val supportedBuiltIns = remember(profile, isClusterTuneInstalled) {
        QuickSettingTileId.supportedEntries(profile, isClusterTuneInstalled)
    }
    val supportedBuiltInSet = remember(supportedBuiltIns) { supportedBuiltIns.toSet() }

    val availablePluginTiles = remember(registeredPlugins, profile) {
        buildList {
            for (plugin in registeredPlugins) {
                if (!plugin.isApiCompatible) continue
                for (tile in plugin.supportedTilesOn(profile)) {
                    add(plugin to tile)
                }
            }
        }
    }
    val activePluginTilesByKey = remember(availablePluginTiles, enabledPluginTileKeys) {
        buildMap {
            for ((plugin, tile) in availablePluginTiles) {
                if (plugin.isPluginEnabled && tile.tileKey in enabledPluginTileKeys) {
                    put(tile.tileKey, plugin to tile)
                }
            }
        }
    }

    val desiredPluginIconRequests = remember(activePluginTilesByKey, isRetroidLook) {
        buildMap {
            for ((key, pair) in activePluginTilesByKey) {
                val (plugin, tileDef) = pair
                val desiredRes = if (isRetroidLook && tileDef.pixelIconResId != 0) {
                    tileDef.pixelIconResId
                } else {
                    tileDef.iconResId
                }
                if (desiredRes != 0) {
                    put(key, Triple(plugin.packageName, plugin.versionCode, desiredRes))
                }
            }
        }
    }
    val initialCachedPluginIcons = remember(desiredPluginIconRequests) {
        buildMap {
            for ((key, req) in desiredPluginIconRequests) {
                PluginIconLoader.getCachedIconBitmap(req.first, req.second, req.third)?.let {
                    put(key, it)
                }
            }
        }
    }
    val pluginIconBitmaps by produceState(
        initialValue = initialCachedPluginIcons,
        desiredPluginIconRequests
    ) {
        val allCached = desiredPluginIconRequests.values.all { req ->
            PluginIconLoader.isCached(req.first, req.second, req.third)
        }
        if (allCached) {
            value = initialCachedPluginIcons
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            buildMap {
                for ((key, req) in desiredPluginIconRequests) {
                    PluginIconLoader.loadIconBitmap(
                        context = context,
                        packageName = req.first,
                        versionCode = req.second,
                        resId = req.third
                    )?.let { put(key, it) }
                }
            }
        }
    }

    val activeTiles: List<ReorderTileItem> = remember(
        unifiedQuickTileOrder,
        disabledQuickTiles,
        supportedBuiltInSet,
        activePluginTilesByKey,
        pluginIconBitmaps
    ) {
        val orderedKeys = unifiedQuickTileOrder.toMutableList()
        for (pluginKey in activePluginTilesByKey.keys) {
            if (pluginKey !in orderedKeys) orderedKeys.add(pluginKey)
        }
        buildList {
            for (key in orderedKeys) {
                if (BdsaPluginContract.isPluginTileKey(key)) {
                    val (_, tileDef) = activePluginTilesByKey[key] ?: continue
                    add(
                        ReorderTileItem(
                            key = tileDef.tileKey,
                            title = tileDef.listTitle,
                            gridTitle = tileDef.gridTitle,
                            iconRes = R.drawable.ic_qs_plugin_default,
                            iconBitmap = pluginIconBitmaps[key]
                        )
                    )
                } else {
                    val builtIn = QuickSettingTileId.fromKey(key) ?: continue
                    if (builtIn in supportedBuiltInSet && builtIn !in disabledQuickTiles) {
                        add(
                            ReorderTileItem(
                                key = builtIn.key,
                                title = builtIn.title(context),
                                gridTitle = builtIn.gridTitle(context),
                                iconRes = builtIn.iconRes
                            )
                        )
                    }
                }
            }
        }
    }

    val totalCount = supportedBuiltIns.size + availablePluginTiles.size
    val activeCount = activeTiles.size
    val supportedDefaultOrder = remember(profile, isClusterTuneInstalled) {
        QuickSettingTileId.defaultOrder.filter { it.isSupportedOn(profile, isClusterTuneInstalled) }
    }
    val supportedDefaultDisabled = remember(profile, isClusterTuneInstalled) {
        QuickSettingTileId.defaultDisabled
            .filter { it.isSupportedOn(profile, isClusterTuneInstalled) }
            .toSet()
    }
    val supportedDisabled = remember(disabledQuickTiles, supportedBuiltInSet) {
        disabledQuickTiles.intersect(supportedBuiltInSet)
    }
    val currentBuiltInOrder = remember(unifiedQuickTileOrder, supportedBuiltInSet) {
        unifiedQuickTileOrder.mapNotNull { QuickSettingTileId.fromKey(it) }
            .filter { it in supportedBuiltInSet }
    }
    val hasActivePluginTiles = activePluginTilesByKey.isNotEmpty()
    val isModified = remember(
        currentBuiltInOrder,
        supportedDisabled,
        supportedDefaultOrder,
        supportedDefaultDisabled,
        hasActivePluginTiles
    ) {
        currentBuiltInOrder != supportedDefaultOrder ||
            supportedDisabled != supportedDefaultDisabled ||
            hasActivePluginTiles
    }

    QuickTogglesVisualReorderCard(
        activeTiles = activeTiles,
        onMoveActiveTile = { fromIndex, toIndex ->
            onMoveActiveTile(fromIndex, toIndex, activeTiles.map { it.key })
        },
        onGridActiveChanged = onGridActiveChanged,
        modifier = Modifier.firstDeckItem()
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_qs_enable_disable_title),
        description = stringResource(
            R.string.settings_qs_enable_disable_desc,
            QuickSettingCategory.STOCK_AYN.titleFor(context, profile)
        ),
        icon = Icons.Rounded.Checklist,
        trailingContent = {
            GamepadPill(
                text = pluralStringResource(
                    R.plurals.settings_qs_active_count_pill,
                    activeCount,
                    activeCount,
                    totalCount
                ),
                isAccent = supportedDisabled != supportedDefaultDisabled || hasActivePluginTiles
            )
        },
        onClick = onOpenEnableDisableSubPage
    )

    GamepadTwoStepConfirmCard(
        title = stringResource(R.string.settings_qs_reset_title),
        confirmTitle = stringResource(R.string.settings_qs_reset_confirm_title),
        description = stringResource(
            if (isModified) {
                R.string.settings_qs_reset_desc_modified
            } else {
                R.string.settings_qs_reset_desc_default
            }
        ),
        confirmDescription = stringResource(R.string.settings_qs_reset_confirm_desc),
        actionText = stringResource(R.string.settings_action_reset),
        confirmActionText = stringResource(R.string.settings_action_confirm_reset),
        icon = Icons.Rounded.RestartAlt,
        enabled = isModified,
        isDestructive = true,
        onConfirm = onResetDefaults
    )
}

@Composable
private fun QuickTogglesVisualReorderCard(
    activeTiles: List<ReorderTileItem>,
    onMoveActiveTile: (fromIndex: Int, toIndex: Int) -> Unit,
    onGridActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val tccColors = LocalTccColors.current
    val coroutineScope = rememberCoroutineScope()
    val latestOnMoveActiveTile by rememberUpdatedState(onMoveActiveTile)
    val latestActiveTiles by rememberUpdatedState(activeTiles)

    var isGridActive by remember { mutableStateOf(false) }
    var cursorIndex by remember { mutableIntStateOf(0) }
    var isGrabbed by remember { mutableStateOf(false) }
    var hasCardFocus by remember { mutableStateOf(false) }

    val pages = remember(activeTiles) { activeTiles.chunked(8) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(activeTiles.size) {
        if (activeTiles.isEmpty()) {
            cursorIndex = 0
            isGrabbed = false
        } else if (cursorIndex > activeTiles.lastIndex) {
            cursorIndex = activeTiles.lastIndex
        }
    }

    LaunchedEffect(isGridActive) {
        onGridActiveChanged(isGridActive)
        if (!isGridActive) {
            isGrabbed = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onGridActiveChanged(false)
        }
    }

    LaunchedEffect(cursorIndex, pageCount, isGridActive) {
        if (isGridActive && activeTiles.isNotEmpty()) {
            val targetPage = (cursorIndex / 8).coerceIn(0, pageCount - 1)
            if (pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(isGridActive, pageCount, activeTiles.size) {
        if (isGridActive) {
            PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
                when (direction) {
                    BumperDirection.PREV -> {
                        if (pagerState.currentPage > 0) {
                            val newPage = pagerState.currentPage - 1
                            val targetIdx = (newPage * 8 + (cursorIndex % 8)).coerceAtMost(latestActiveTiles.lastIndex)
                            if (isGrabbed) {
                                latestOnMoveActiveTile(cursorIndex, targetIdx)
                            }
                            cursorIndex = targetIdx
                        }
                    }
                    BumperDirection.NEXT -> {
                        if (pagerState.currentPage < pageCount - 1) {
                            val newPage = pagerState.currentPage + 1
                            val targetIdx = (newPage * 8 + (cursorIndex % 8)).coerceAtMost(latestActiveTiles.lastIndex)
                            if (isGrabbed) {
                                latestOnMoveActiveTile(cursorIndex, targetIdx)
                            }
                            cursorIndex = targetIdx
                        }
                    }
                }
            }
        }
    }

    val deckBackInterceptor = LocalDeckBackInterceptor.current
    val handleBackStep: () -> Boolean = remember(isGridActive, isGrabbed) {
        {
            if (isGrabbed) {
                isGrabbed = false
                true
            } else if (isGridActive) {
                isGridActive = false
                true
            } else {
                false
            }
        }
    }

    DisposableEffect(deckBackInterceptor, handleBackStep, isGridActive) {
        if (isGridActive) {
            deckBackInterceptor?.invoke(handleBackStep, true)
        }
        onDispose {
            deckBackInterceptor?.invoke(handleBackStep, false)
        }
    }

    BackHandler(enabled = isGridActive) {
        handleBackStep()
    }

    val currentTile = activeTiles.getOrNull(cursorIndex)
    val subtitle = when {
        !isGridActive -> stringResource(R.string.settings_qs_reorder_idle)
        isGrabbed && currentTile != null ->
            stringResource(R.string.settings_qs_reorder_moving, currentTile.title)
        currentTile != null ->
            stringResource(R.string.settings_qs_reorder_selected, cursorIndex + 1, currentTile.title)
        else -> stringResource(R.string.settings_qs_reorder_fallback)
    }
    val pillText = when {
        isGrabbed -> stringResource(R.string.settings_qs_pill_moving, cursorIndex + 1)
        isGridActive -> stringResource(R.string.settings_qs_pill_slot, cursorIndex + 1)
        else -> stringResource(R.string.settings_qs_pill_reorder)
    }

    GamepadFocusCard(
        onClick = {
            if (!isGridActive) {
                isGridActive = true
                isGrabbed = false
            } else {
                isGrabbed = !isGrabbed
            }
        },
        onTouchClick = {
            if (isGridActive) {
                isGridActive = false
                isGrabbed = false
            }
        },
        modifier = modifier,
        itemKey = "quick_toggles_visual_reorder",
        isAdjusting = isGridActive,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
        onFocusChanged = { focused ->
            hasCardFocus = focused
            if (!focused) {
                isGridActive = false
                isGrabbed = false
            }
        },
        onCustomKeyEvent = { keyEvent ->
            if (!isGridActive) return@GamepadFocusCard false
            val keyCode = keyEvent.nativeKeyEvent.keyCode
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (cursorIndex > 0) {
                            val target = cursorIndex - 1
                            if (isGrabbed) {
                                onMoveActiveTile(cursorIndex, target)
                            }
                            cursorIndex = target
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (cursorIndex < activeTiles.lastIndex) {
                            val target = cursorIndex + 1
                            if (isGrabbed) {
                                onMoveActiveTile(cursorIndex, target)
                            }
                            cursorIndex = target
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val inPageIndex = cursorIndex % 8
                        if (inPageIndex >= 4) {
                            val target = (cursorIndex - 4).coerceAtLeast(0)
                            if (isGrabbed) {
                                onMoveActiveTile(cursorIndex, target)
                            }
                            cursorIndex = target
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val inPageIndex = cursorIndex % 8
                        val pageStart = (cursorIndex / 8) * 8
                        val pageEnd = (pageStart + 7).coerceAtMost(activeTiles.lastIndex)
                        if (inPageIndex < 4 && pageStart + 4 <= pageEnd) {
                            val target = (cursorIndex + 4).coerceAtMost(pageEnd)
                            if (isGrabbed) {
                                onMoveActiveTile(cursorIndex, target)
                            }
                            cursorIndex = target
                        }
                        true
                    }

                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        isGrabbed = !isGrabbed
                        true
                    }

                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        handleBackStep()
                        true
                    }

                    else -> false
                }
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> true
                    else -> false
                }
            } else {
                false
            }
        }
    ) { isEffectivelyFocused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GamepadCardRow(
                title = stringResource(R.string.settings_qs_layout_title),
                description = subtitle,
                icon = Icons.Rounded.GridView,
                isFocused = isEffectivelyFocused || isGridActive,
                alwaysShowFullDescription = true,
                trailingContent = {
                    GamepadPill(
                        text = pillText,
                        isAccent = isGrabbed,
                        isHighlighted = isGridActive && !isGrabbed
                    )
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Compact QuickSettingsPanel replica (2x4 paged grid + bottom page indicator)
            val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
            val lookSpec = LocalTccLookSpec.current
            val boost = isRetroidLook && lookSpec.retroidReadabilityBoost
            val previewHeight = if (boost) {
                if (pageCount > 1) 196.dp else 182.dp
            } else {
                if (pageCount > 1) 174.dp else 160.dp
            }
            val previewShape: Shape = RoundedCornerShape(16.dp)
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1f)
            ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(previewShape)
                    .background(tccColors.panelBackground)
                    .border(
                        width = if (isGridActive) 1.5.dp else 1.dp,
                        color = if (isGridActive) {
                            appColors.accent.copy(alpha = 0.65f)
                        } else if (isRetroidLook) {
                            tccColors.fourth
                        } else {
                            appColors.onSurface.copy(alpha = 0.10f)
                        },
                        shape = previewShape
                    )
                    .padding(top = 12.dp, bottom = if (pageCount > 1) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .focusProperties { canFocus = false }
                ) { pageIndex ->
                    val pageItems = pages.getOrNull(pageIndex).orEmpty()
                    val pageOffset = pageIndex * 8
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                    ) {
                        for (rowIndex in 0 until 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (colIndex in 0 until 4) {
                                    val inPageIdx = rowIndex * 4 + colIndex
                                    val tile = pageItems.getOrNull(inPageIdx)
                                    val globalIdx = pageOffset + inPageIdx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (tile != null) {
                                            val isCursorHere = isGridActive && cursorIndex == globalIdx
                                            val isTileGrabbed = isCursorHere && isGrabbed
                                            ReorderableTccTile(
                                                tile = tile,
                                                positionIndex = globalIdx,
                                                isCursorSelected = isCursorHere,
                                                isGrabbed = isTileGrabbed,
                                                onClick = {
                                                    if (!isGridActive) {
                                                        isGridActive = true
                                                        cursorIndex = globalIdx
                                                        isGrabbed = true
                                                    } else if (!isGrabbed) {
                                                        cursorIndex = globalIdx
                                                        isGrabbed = true
                                                    } else if (cursorIndex == globalIdx) {
                                                        isGrabbed = false
                                                        if (!hasCardFocus || !PrimaryOverlayInputBridge.isGamepadInputActive) {
                                                            isGridActive = false
                                                        }
                                                    } else {
                                                        onMoveActiveTile(cursorIndex, globalIdx)
                                                        cursorIndex = globalIdx
                                                        isGrabbed = false
                                                        if (!hasCardFocus || !PrimaryOverlayInputBridge.isGamepadInputActive) {
                                                            isGridActive = false
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pageCount > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pageCount) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(if (isRetroidLook) RectangleShape else CircleShape)
                                        .background(if (isSelected) tccColors.accent else tccColors.secondary)
                                        .focusProperties { canFocus = false }
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * Compact interactive replica of `TccButton` used inside `QuickTogglesVisualReorderCard`,
 * scaled (`35dp` circle + `18dp` icon + `23dp` title) so all Quick Toggles cards fit above
 * the fold while preserving slot index badges and cursor/grabbed highlight states.
 */
@Composable
private fun ReorderableTccTile(
    tile: ReorderTileItem,
    positionIndex: Int,
    isCursorSelected: Boolean,
    isGrabbed: Boolean,
    onClick: () -> Unit
) {
    val tccColors = LocalTccColors.current
    val appColors = LocalAppColors.current
    val lookSpec = LocalTccLookSpec.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val boost = lookSpec.retroidReadabilityBoost
    val pixelFont = tccFontFamily()
    val labelStyle = tccTextStyle()
    val tileShape: Shape = if (isRetroidLook) PixelCornerCutShape(5.dp) else CircleShape
    val slotContainerShape: Shape = RoundedCornerShape(8.dp)
    val badgeShape: Shape = if (isRetroidLook) PixelCornerCutShape(2.dp) else RoundedCornerShape(5.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isGrabbed) 1.06f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tileGrabScale"
    )

    val iconTint = tccTileIconTint(
        colors = tccColors,
        isRetroidLook = isRetroidLook,
        selected = isGrabbed,
        highlighted = isCursorSelected
    )
    val textColor = if (isCursorSelected || isGrabbed) tccColors.primary else tccColors.secondary

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(slotContainerShape)
            .background(
                when {
                    isGrabbed -> appColors.accent.copy(alpha = 0.18f)
                    isCursorSelected -> tccColors.panelBackgroundSelected
                    else -> Color.Transparent
                }
            )
            .then(
                if (isGrabbed) {
                    Modifier.border(2.dp, appColors.accent, slotContainerShape)
                } else if (isCursorSelected) {
                    Modifier.border(1.5.dp, appColors.accent.copy(alpha = 0.85f), slotContainerShape)
                } else {
                    Modifier
                }
            )
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(35.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(tileShape)
                        .tccTileBackground(
                            colors = tccColors,
                            isRetroidLook = isRetroidLook,
                            selected = isGrabbed
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val tilePainter = if (tile.iconBitmap != null) {
                        remember(tile.iconBitmap) { BitmapPainter(tile.iconBitmap) }
                    } else {
                        painterResource(id = tccDrawable(tile.iconRes))
                    }
                    Image(
                        painter = tilePainter,
                        contentDescription = tile.title,
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Compact slot position badge (#1..#N) at top-right of the circle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(badgeShape)
                        .background(
                            if (isGrabbed || isCursorSelected) {
                                appColors.accent
                            } else {
                                appColors.surfaceVariant.copy(alpha = 0.92f)
                            }
                        )
                        .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${positionIndex + 1}",
                        color = if (isGrabbed || isCursorSelected) {
                            appColors.onAccent
                        } else {
                            appColors.onSurfaceSecondary
                        },
                        fontSize = if (boost) 12.sp else 8.5.sp,
                        lineHeight = if (boost) 13.sp else 9.5.sp,
                        fontFamily = pixelFont,
                        fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            val tileLabel = tile.gridTitle
            val hasCjk = remember(tileLabel) {
                tileLabel.codePoints().anyMatch { cp ->
                    Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                }
            }
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(if (boost) 34.dp else 25.dp)
                    .padding(horizontal = 1.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = tileLabel,
                    color = textColor,
                    fontSize = if (hasCjk) {
                        if (boost) 12.sp else 9.sp
                    } else {
                        if (boost) 14.5.sp else 10.sp
                    },
                    lineHeight = if (hasCjk) {
                        if (boost) 14.sp else 11.sp
                    } else {
                        if (boost) 16.sp else 11.sp
                    },
                    style = labelStyle,
                    fontWeight = if (!isRetroidLook && (isCursorSelected || isGrabbed)) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EnableDisableTogglesSubPageContent(
    profile: DeviceProfile,
    isClusterTuneInstalled: Boolean,
    quickTileOrder: List<QuickSettingTileId>,
    disabledQuickTiles: Set<QuickSettingTileId>,
    registeredPlugins: List<RegisteredPlugin>,
    enabledPluginTileKeys: Set<String>,
    promotedToManager: Boolean,
    onSetTileEnabled: (QuickSettingTileId, Boolean) -> Unit,
    onSetPluginTileEnabled: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val supportedTiles = remember(profile, isClusterTuneInstalled) {
        QuickSettingTileId.supportedEntries(profile, isClusterTuneInstalled)
    }
    val supportedSet = remember(supportedTiles) { supportedTiles.toSet() }
    val availablePluginTiles = remember(registeredPlugins, profile) {
        buildList {
            for (plugin in registeredPlugins) {
                if (!plugin.isApiCompatible) continue
                for (tile in plugin.supportedTilesOn(profile)) {
                    add(plugin to tile)
                }
            }
        }
    }
    val enabledPluginCount = availablePluginTiles.count { (plugin, tile) ->
        plugin.isPluginEnabled && tile.tileKey in enabledPluginTileKeys
    }
    val totalCount = supportedTiles.size + availablePluginTiles.size
    val enabledCount = supportedTiles.count { it !in disabledQuickTiles } + enabledPluginCount

    GamepadInfoBox(
        text = pluralStringResource(
            R.plurals.settings_qs_visibility_header,
            enabledCount,
            enabledCount,
            totalCount
        ),
        description = stringResource(
            R.string.settings_qs_visibility_desc,
            QuickSettingCategory.STOCK_AYN.titleFor(context, profile)
        )
    )

    var isFirstItem = true
    QuickSettingCategory.entries.forEach { category ->
        val categoryTiles = remember(quickTileOrder, category, supportedSet) {
            quickTileOrder.filter { it in supportedSet && it.category == category }
        }
        val activeInCategory = categoryTiles.count { it !in disabledQuickTiles }

        GamepadSectionHeader(
            text = stringResource(
                R.string.settings_qs_category_count_header,
                category.titleFor(context, profile),
                activeInCategory,
                categoryTiles.size
            )
        )

        categoryTiles.forEach { tile ->
            val isEnabled = tile !in disabledQuickTiles
            val canToggle = !isEnabled || enabledCount > 1
            val itemModifier = if (isFirstItem && canToggle) {
                isFirstItem = false
                Modifier.firstDeckItem()
            } else {
                Modifier
            }
            val tileDescription = tile.descriptionFor(context, promotedToManager, profile)
            GamepadToggleCard(
                title = tile.title(context),
                description = if (!canToggle) {
                    stringResource(R.string.settings_qs_min_one_required, tileDescription)
                } else {
                    tileDescription
                },
                checked = isEnabled,
                enabled = canToggle,
                itemKey = "toggle_visibility_${tile.key}",
                onCheckedChange = { nextEnabled ->
                    onSetTileEnabled(tile, nextEnabled)
                },
                modifier = itemModifier
            )
        }
    }

    // Separate third-party plugins section at the very bottom, clearly marked as use at your own risk.
    GamepadSectionHeader(
        text = stringResource(
            R.string.settings_qs_third_party_header,
            enabledPluginCount,
            availablePluginTiles.size
        )
    )

    GamepadInfoBox(
        text = stringResource(R.string.settings_qs_third_party_warning_title),
        description = stringResource(R.string.settings_qs_third_party_warning_desc),
        icon = Icons.Rounded.WarningAmber,
        iconTint = appColors.error
    )

    if (availablePluginTiles.isEmpty()) {
        GamepadInfoBox(
            text = stringResource(R.string.settings_qs_third_party_empty_title),
            description = stringResource(R.string.settings_qs_third_party_empty_desc),
            icon = Icons.Rounded.Extension
        )
    } else {
        availablePluginTiles.forEach { (plugin, tile) ->
            val isTileEnabled = plugin.isPluginEnabled && (tile.tileKey in enabledPluginTileKeys)
            val canToggle = plugin.isPluginEnabled && (!isTileEnabled || enabledCount > 1)
            val description = when {
                !plugin.isPluginEnabled -> stringResource(
                    R.string.settings_qs_third_party_plugin_disabled_desc,
                    plugin.appName
                )
                tile.description.isNotBlank() -> stringResource(
                    R.string.settings_qs_third_party_tile_desc_format,
                    plugin.appName,
                    tile.description
                )
                else -> plugin.appName
            }
            GamepadToggleCard(
                title = tile.listTitle,
                description = if (plugin.isPluginEnabled && !canToggle) {
                    stringResource(R.string.settings_qs_min_one_required, description)
                } else {
                    description
                },
                icon = Icons.Rounded.Extension,
                checked = isTileEnabled,
                enabled = canToggle,
                itemKey = "toggle_visibility_${tile.tileKey}",
                onCheckedChange = { nextEnabled ->
                    onSetPluginTileEnabled(tile.tileKey, nextEnabled)
                }
            )
        }
    }
}

@Composable
private fun PluginsSettingsTabContent(
    profile: DeviceProfile,
    plugins: List<RegisteredPlugin>,
    onTogglePluginEnabled: (packageName: String, enabled: Boolean) -> Unit,
    onRescanPlugins: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    GamepadInfoBox(
        text = stringResource(R.string.settings_plugins_info_title),
        description = stringResource(R.string.settings_plugins_info_desc),
        icon = Icons.Rounded.WarningAmber,
        iconTint = appColors.error
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_plugins_refresh_title),
        description = stringResource(R.string.settings_plugins_refresh_desc),
        icon = Icons.Rounded.Refresh,
        actionText = stringResource(R.string.settings_plugins_action_rescan),
        itemKey = "plugins_rescan",
        onClick = onRescanPlugins,
        modifier = Modifier.firstDeckItem()
    )

    if (plugins.isEmpty()) {
        GamepadInfoBox(
            text = stringResource(R.string.settings_plugins_empty_title),
            description = stringResource(R.string.settings_plugins_empty_desc),
            icon = Icons.Rounded.Extension
        )
        return
    }

    GamepadSectionHeader(
        text = stringResource(R.string.settings_plugins_section_installed, plugins.size)
    )

    plugins.forEach { plugin ->
        val supportedTiles = remember(plugin, profile) { plugin.supportedTilesOn(profile) }
        val metaParts = buildList {
            if (plugin.versionName.isNotBlank()) {
                add(stringResource(R.string.settings_plugins_meta_version, plugin.versionName))
            }
            if (plugin.author.isNotBlank()) {
                add(stringResource(R.string.settings_plugins_meta_by_author, plugin.author))
            }
            add(plugin.packageName)
        }
        val tileSummary = if (supportedTiles.isEmpty()) {
            stringResource(R.string.settings_plugins_meta_no_supported_tiles, profile.displayName)
        } else {
            pluralStringResource(
                R.plurals.settings_plugins_meta_tiles_count,
                supportedTiles.size,
                supportedTiles.size,
                supportedTiles.joinToString(", ") { it.listTitle }
            )
        }
        val bodyDesc = plugin.description.ifBlank {
            stringResource(R.string.settings_plugins_no_description)
        }
        val fullDesc = "$bodyDesc\n${metaParts.joinToString(" • ")} • $tileSummary"

        if (!plugin.isApiCompatible) {
            GamepadInfoBox(
                text = "${plugin.appName} — ${stringResource(R.string.settings_plugins_incompatible_title, plugin.apiVersion)}",
                description = "${stringResource(R.string.settings_plugins_incompatible_desc, plugin.apiVersion, BdsaPluginContract.API_VERSION)}\n$fullDesc",
                icon = Icons.Rounded.WarningAmber,
                iconTint = appColors.error
            )
        } else {
            GamepadToggleCard(
                title = plugin.appName,
                description = fullDesc,
                checked = plugin.isPluginEnabled,
                icon = Icons.Rounded.Extension,
                itemKey = "plugin_toggle_${plugin.packageName}",
                onCheckedChange = { nextEnabled ->
                    onTogglePluginEnabled(plugin.packageName, nextEnabled)
                }
            )
        }

        val settingsComponent = plugin.settingsActivity
        if (settingsComponent != null && plugin.isApiCompatible) {
            GamepadActionCard(
                title = stringResource(R.string.settings_plugins_configure_title, plugin.appName),
                description = stringResource(R.string.settings_plugins_configure_desc),
                icon = Icons.Rounded.Tune,
                actionText = stringResource(R.string.settings_plugins_action_configure),
                itemKey = "plugin_configure_${plugin.packageName}",
                onClick = {
                    launchPluginSettingsOnPrimaryDisplay(context, settingsComponent)
                }
            )
        }

        GamepadActionCard(
            title = stringResource(R.string.settings_plugins_app_info_title, plugin.appName),
            description = stringResource(R.string.settings_plugins_app_info_desc, plugin.packageName),
            icon = Icons.Rounded.Info,
            actionText = stringResource(R.string.settings_plugins_action_app_info),
            itemKey = "plugin_app_info_${plugin.packageName}",
            onClick = {
                openAppDetailsOnPrimaryDisplay(context, plugin.packageName)
            }
        )
    }
}

private fun launchPluginSettingsOnPrimaryDisplay(
    context: Context,
    componentName: ComponentName
) {
    try {
        val intent = Intent().apply {
            component = componentName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        context.startActivity(intent, options.toBundle())
    } catch (t: Throwable) {
        Log.w("MainActivity", "Failed to launch plugin settings $componentName", t)
        DialogToastManager.show(context.getString(R.string.settings_plugins_configure_failed))
    }
}

private fun openAppDetailsOnPrimaryDisplay(
    context: Context,
    packageName: String
) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        context.startActivity(intent, options.toBundle())
    } catch (_: Throwable) {
    }
}

@Composable
private fun AppearanceSettingsTabContent(
    profile: DeviceProfile,
    selectedTheme: AppThemeOption,
    uiLookMode: Int,
    retroidReadabilityBoost: Boolean,
    customAccentArgb: Int,
    customSwatchColorArgb: Int,
    injectSystemTheme: Boolean,
    syncedMegingiardOption: AppThemeOption,
    isMegingiardAvailable: Boolean,
    promotedToManager: Boolean,
    onSelectTheme: (AppThemeOption) -> Unit,
    onSetUiLookMode: (Int) -> Unit,
    onToggleRetroidReadabilityBoost: (Boolean) -> Unit,
    onSelectAccentColor: (Int) -> Unit,
    onOpenCustomAccent: () -> Unit,
    onToggleInjectSystemTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val stockVendorName = profile.vendorName
    val selectedUiLookPref = TccUiLookPreference.fromId(uiLookMode)
    val isRetroidLookActive = selectedUiLookPref.resolve(profile) == TccUiLook.RETROID
    val uiLookOptions = TccUiLookPreference.entries

    val availableSources = remember(isMegingiardAvailable) {
        if (isMegingiardAvailable) {
            listOf(
                ThemeSourceOption.STOCK,
                ThemeSourceOption.SYNC_MEGINGIARD,
                ThemeSourceOption.CUSTOM_PALETTE
            )
        } else {
            listOf(
                ThemeSourceOption.STOCK,
                ThemeSourceOption.CUSTOM_PALETTE
            )
        }
    }

    val activeSource = when {
        selectedTheme == AppThemeOption.SYNC_MEGINGIARD && isMegingiardAvailable -> ThemeSourceOption.SYNC_MEGINGIARD
        selectedTheme.isMegingiardStyle -> ThemeSourceOption.CUSTOM_PALETTE
        else -> ThemeSourceOption.STOCK
    }

    val effectiveSelectedTheme = if (selectedTheme == AppThemeOption.SYNC_MEGINGIARD && !isMegingiardAvailable) {
        syncedMegingiardOption
    } else {
        selectedTheme
    }

    var lastStockOption by rememberSaveable {
        mutableStateOf(if (!selectedTheme.isMegingiardStyle) selectedTheme else AppThemeOption.STOCK_DARK)
    }
    var lastCustomOption by rememberSaveable {
        mutableStateOf(
            if (selectedTheme.isMegingiardStyle && selectedTheme != AppThemeOption.SYNC_MEGINGIARD) {
                selectedTheme
            } else {
                AppThemeOption.MEGINGIARD_DARK
            }
        )
    }

    LaunchedEffect(selectedTheme) {
        if (!selectedTheme.isMegingiardStyle) {
            lastStockOption = selectedTheme
        } else if (selectedTheme != AppThemeOption.SYNC_MEGINGIARD) {
            lastCustomOption = selectedTheme
        }
    }

    fun applyThemeSource(source: ThemeSourceOption) {
        when (source) {
            ThemeSourceOption.STOCK -> onSelectTheme(lastStockOption)
            ThemeSourceOption.SYNC_MEGINGIARD -> onSelectTheme(AppThemeOption.SYNC_MEGINGIARD)
            ThemeSourceOption.CUSTOM_PALETTE -> onSelectTheme(lastCustomOption)
        }
    }

    GamepadChoiceCard(
        title = stringResource(R.string.settings_theme_source_title),
        description = stringResource(
            if (isMegingiardAvailable) {
                R.string.settings_theme_source_desc_with_sync
            } else {
                R.string.settings_theme_source_desc_no_sync
            }
        ),
        selectedText = stringResource(activeSource.displayNameRes),
        icon = Icons.Rounded.Palette,
        onPrevious = {
            applyThemeSource(availableSources.cycle(activeSource, BumperDirection.PREV))
        },
        onNext = {
            applyThemeSource(availableSources.cycle(activeSource, BumperDirection.NEXT))
        },
        modifier = Modifier.firstDeckItem()
    )

    when (activeSource) {
        ThemeSourceOption.STOCK -> {
            GamepadChoiceCard(
                title = stringResource(R.string.settings_stock_theme_title),
                description = stringResource(R.string.settings_stock_theme_desc, stockVendorName),
                selectedText = stringResource(effectiveSelectedTheme.displayNameRes),
                icon = Icons.Rounded.ColorLens,
                onPrevious = {
                    onSelectTheme(StockThemeOptions.cycle(effectiveSelectedTheme, BumperDirection.PREV))
                },
                onNext = {
                    onSelectTheme(StockThemeOptions.cycle(effectiveSelectedTheme, BumperDirection.NEXT))
                }
            )
        }

        ThemeSourceOption.SYNC_MEGINGIARD -> {
            GamepadActionCard(
                title = stringResource(R.string.settings_megingiard_sync_title),
                description = stringResource(
                    R.string.settings_megingiard_sync_desc,
                    stringResource(syncedMegingiardOption.displayNameRes)
                ),
                icon = Icons.Rounded.Sync,
                trailingContent = {
                    GamepadPill(
                        text = stringResource(R.string.settings_megingiard_sync_pill),
                        isAccent = true
                    )
                },
                onClick = {}
            )
        }

        ThemeSourceOption.CUSTOM_PALETTE -> {
            GamepadChoiceCard(
                title = stringResource(R.string.settings_custom_palette_title),
                description = stringResource(
                    if (promotedToManager) {
                        R.string.settings_custom_palette_desc_manager
                    } else {
                        R.string.settings_custom_palette_desc_assistant
                    }
                ),
                selectedText = stringResource(effectiveSelectedTheme.displayNameRes),
                icon = Icons.Rounded.ColorLens,
                onPrevious = {
                    onSelectTheme(MegingiardThemeOptions.cycle(effectiveSelectedTheme, BumperDirection.PREV))
                },
                onNext = {
                    onSelectTheme(MegingiardThemeOptions.cycle(effectiveSelectedTheme, BumperDirection.NEXT))
                }
            )
        }
    }

    if (effectiveSelectedTheme.supportsCustomAccent && activeSource != ThemeSourceOption.SYNC_MEGINGIARD) {
        val accentColor = Color(customAccentArgb)
        val customSwatchColor = Color(customSwatchColorArgb)
        val isCustomAccent =
            customAccentArgb == customSwatchColorArgb && accentColor !in MegingiardAccentPresets

        GamepadColorPaletteCard(
            title = stringResource(R.string.settings_accent_color_title),
            description = stringResource(
                R.string.settings_accent_color_desc,
                stringResource(effectiveSelectedTheme.displayNameRes)
            ),
            icon = Icons.Rounded.FormatColorFill,
            paletteColors = MegingiardAccentPresets,
            selectedColor = accentColor,
            onColorSelected = { onSelectAccentColor(it.toArgb()) }
        )

        GamepadActionCard(
            title = stringResource(R.string.settings_custom_color_title),
            description = stringResource(R.string.settings_custom_color_desc),
            icon = Icons.Rounded.Colorize,
            actionLeadingContent = {
                GamepadColorSwatch(
                    color = customSwatchColor,
                    isSelected = isCustomAccent
                )
            },
            onClick = onOpenCustomAccent
        )
    }

    GamepadChoiceCard(
        title = stringResource(R.string.settings_ui_look_title),
        description = stringResource(R.string.settings_ui_look_desc),
        selectedText = selectedUiLookPref.displayNameFor(context, profile),
        icon = Icons.Rounded.Layers,
        onPrevious = {
            onSetUiLookMode(uiLookOptions.cycle(selectedUiLookPref, BumperDirection.PREV).id)
        },
        onNext = {
            onSetUiLookMode(uiLookOptions.cycle(selectedUiLookPref, BumperDirection.NEXT).id)
        }
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_retroid_readability_title),
        description = stringResource(R.string.settings_retroid_readability_desc),
        checked = retroidReadabilityBoost,
        enabled = isRetroidLookActive,
        icon = Icons.Rounded.Tune,
        onCheckedChange = onToggleRetroidReadabilityBoost
    )

    GamepadToggleCard(
        title = stringResource(R.string.settings_inject_os_theme_title),
        description = stringResource(R.string.settings_inject_os_theme_desc),
        checked = injectSystemTheme,
        icon = Icons.Rounded.SettingsSuggest,
        onCheckedChange = onToggleInjectSystemTheme
    )
}

@Composable
private fun CustomAccentSubPageContent(
    initialColor: Color,
    onSaveColor: (Color) -> Unit
) {
    val initHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[0]) }
    var sat by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[1]) }
    var bri by rememberSaveable(initialColor) { mutableFloatStateOf(initHsv[2]) }

    val workingColor by remember {
        derivedStateOf {
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
        }
    }

    val hueGradient = remember {
        Brush.horizontalGradient(colors = AppPalettes.hueSpectrum)
    }
    val saturationGradient = remember(hue, bri) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0f, bri))),
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, bri)))
            )
        )
    }
    val brightnessGradient = remember(hue, sat) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Black,
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, 1f)))
            )
        )
    }

    val colors = LocalAppColors.current
    val hasChanges = remember(workingColor, initialColor) {
        workingColor.toArgb() != initialColor.toArgb()
    }
    val workingHex = remember(workingColor) {
        String.format("#%06X", 0xFFFFFF and workingColor.toArgb())
    }
    val initialHex = remember(initialColor) {
        String.format("#%06X", 0xFFFFFF and initialColor.toArgb())
    }

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_hue_title),
        description = stringResource(R.string.settings_color_hue_desc),
        value = hue,
        valueRange = 0f..360f,
        onValueChange = { hue = it },
        valueLabel = "${hue.roundToInt()}°",
        step = 5f,
        fineStep = 1f,
        trackBrush = hueGradient,
        thumbColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
        modifier = Modifier.firstDeckItem()
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_saturation_title),
        description = stringResource(R.string.settings_color_saturation_desc),
        value = sat,
        valueRange = 0f..1f,
        onValueChange = { sat = it },
        valueLabel = "${(sat * 100).roundToInt()}%",
        step = 0.02f,
        fineStep = 0.01f,
        trackBrush = saturationGradient,
        thumbColor = workingColor
    )

    GamepadSliderCard(
        title = stringResource(R.string.settings_color_brightness_title),
        description = stringResource(R.string.settings_color_brightness_desc),
        value = bri,
        valueRange = 0f..1f,
        onValueChange = { bri = it },
        valueLabel = "${(bri * 100).roundToInt()}%",
        step = 0.02f,
        fineStep = 0.01f,
        trackBrush = brightnessGradient,
        thumbColor = workingColor
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_color_undo_title),
        description = if (hasChanges) {
            stringResource(R.string.settings_color_undo_desc_changed, workingHex, initialHex)
        } else {
            stringResource(R.string.settings_color_undo_desc_unchanged, workingHex)
        },
        icon = Icons.AutoMirrored.Rounded.Undo,
        actionLeadingContent = {
            if (hasChanges) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GamepadColorSwatch(color = workingColor, isSelected = true)
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = colors.onSurfaceSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    GamepadColorSwatch(color = initialColor, isSelected = false)
                }
            } else {
                GamepadColorSwatch(color = initialColor, isSelected = true)
            }
        },
        actionText = if (hasChanges) stringResource(R.string.settings_color_action_undo) else null,
        enabled = hasChanges,
        onClick = {
            hue = initHsv[0]
            sat = initHsv[1]
            bri = initHsv[2]
        }
    )

    GamepadActionCard(
        title = stringResource(R.string.settings_color_save_title),
        description = stringResource(R.string.settings_color_save_desc, workingHex),
        icon = Icons.Rounded.Save,
        actionText = stringResource(R.string.gamepad_action_save),
        onClick = { onSaveColor(workingColor) }
    )
}

@Composable
private fun UpdatesSettingsTabContent(
    autoUpdateCheckEnabled: Boolean,
    updateAvailable: Boolean,
    latestReleaseInfo: AppReleaseInfo?,
    isCheckingUpdates: Boolean,
    updateCheckError: String?,
    hasTriggeredManualCheck: Boolean,
    promotedToManager: Boolean,
    onTriggerManualCheck: () -> Unit
) {
    val context = LocalContext.current

    // 1. Automatic Update Check Toggle
    GamepadToggleCard(
        title = stringResource(R.string.settings_auto_update_check),
        description = stringResource(R.string.help_settings_auto_update_desc),
        checked = autoUpdateCheckEnabled,
        icon = Icons.Rounded.Update,
        onCheckedChange = { UpdateManager.setAutoUpdateCheckEnabled(it) },
        modifier = Modifier.firstDeckItem()
    )

    // 2. Manual Check for Updates Button
    val releaseUrl =
        latestReleaseInfo?.htmlUrl?.takeIf { it.isNotBlank() }
            ?: UpdateManager.BDSA_GITHUB_RELEASES_FALLBACK_URL

    val checkActionText =
        when {
            isCheckingUpdates -> stringResource(R.string.gamepad_action_checking)
            updateAvailable -> stringResource(R.string.settings_update_now_btn)
            !hasTriggeredManualCheck -> null
            updateCheckError != null -> stringResource(R.string.settings_check_failed)
            else -> stringResource(R.string.settings_up_to_date)
        }

    GamepadActionCard(
        title = stringResource(R.string.settings_check_for_updates),
        description =
            if (updateAvailable) {
                stringResource(R.string.settings_update_available_tag, latestReleaseInfo?.tagName ?: "")
            } else {
                stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME)
            },
        icon = Icons.Rounded.Refresh,
        actionLeadingContent =
            if (checkActionText != null) {
                {
                    GamepadPill(
                        text = checkActionText,
                        isHighlighted = isCheckingUpdates,
                        isAccent = updateAvailable && !isCheckingUpdates,
                        isDestructive = !isCheckingUpdates && !updateAvailable && updateCheckError != null
                    )
                }
            } else {
                null
            },
        onClick = {
            when {
                isCheckingUpdates -> return@GamepadActionCard
                updateAvailable -> launchUrlOnPrimaryDisplay(context, releaseUrl)
                else -> onTriggerManualCheck()
            }
        }
    )

    // 3. Add to Obtainium Button
    GamepadActionCard(
        title = stringResource(R.string.settings_add_to_obtainium),
        description = stringResource(
            if (promotedToManager) {
                R.string.help_settings_add_to_obtainium_manager_desc
            } else {
                R.string.help_settings_add_to_obtainium_desc
            }
        ),
        icon = Icons.Rounded.Download,
        onClick = {
            if (!launchUrlOnPrimaryDisplay(context, "obtainium://add/${UpdateManager.BDSA_OBTAINIUM_REPO_URL}")) {
                AppLog.w("MainActivity", "Obtainium deep link failed, falling back to browser")
                launchUrlOnPrimaryDisplay(context, UpdateManager.BDSA_OBTAINIUM_FALLBACK_URL)
            }
        }
    )
}

@Composable
private fun LicensesOverviewSubPageContent(
    onOpenLicenseDetail: (LauncherSubPage) -> Unit
) {
    GamepadSectionHeader(text = stringResource(R.string.licenses_section_app))

    GamepadActionCard(
        title = stringResource(R.string.app_name),
        description = stringResource(R.string.licenses_app_desc),
        icon = Icons.Rounded.Gavel,
        trailingContent = {
            GamepadPill(
                text = stringResource(R.string.licenses_app_pill),
                isAccent = true
            )
        },
        itemKey = "license_entry_app",
        modifier = Modifier.firstDeckItem(),
        onClick = { onOpenLicenseDetail(LauncherSubPage.LICENSE_APP) }
    )

    GamepadSectionHeader(text = stringResource(R.string.licenses_section_fonts))

    GamepadActionCard(
        title = stringResource(R.string.licenses_fusion_pixel_title),
        description = stringResource(R.string.licenses_fusion_pixel_desc),
        icon = Icons.Rounded.TextFields,
        actionText = stringResource(R.string.licenses_fusion_pixel_action),
        itemKey = "license_entry_fusion_pixel",
        onClick = { onOpenLicenseDetail(LauncherSubPage.LICENSE_FUSION_PIXEL) }
    )

    GamepadActionCard(
        title = stringResource(R.string.licenses_dosiyagi_title),
        description = stringResource(R.string.licenses_dosiyagi_desc),
        icon = Icons.Rounded.TextFields,
        actionText = stringResource(R.string.licenses_dosiyagi_action),
        itemKey = "license_entry_dosiyagi",
        onClick = { onOpenLicenseDetail(LauncherSubPage.LICENSE_DOSIYAGI) }
    )

    GamepadSectionHeader(text = stringResource(R.string.licenses_section_libraries))

    GamepadActionCard(
        title = stringResource(R.string.licenses_androidx_title),
        description = stringResource(R.string.licenses_androidx_desc),
        icon = Icons.Rounded.Layers,
        actionText = stringResource(R.string.licenses_apache_action),
        itemKey = "license_entry_androidx",
        onClick = { onOpenLicenseDetail(LauncherSubPage.LICENSE_ANDROIDX) }
    )

    GamepadActionCard(
        title = stringResource(R.string.licenses_kotlin_title),
        description = stringResource(R.string.licenses_kotlin_desc),
        icon = Icons.Rounded.DeveloperBoard,
        actionText = stringResource(R.string.licenses_apache_action),
        itemKey = "license_entry_kotlin",
        onClick = { onOpenLicenseDetail(LauncherSubPage.LICENSE_KOTLIN) }
    )
}

private data class LicenseDetailSpec(
    val documentTitle: String,
    val scopeBannerSummary: String,
    val linkActionLabel: String,
    val linkUrl: String,
    val fullBodyText: String
)

private fun cleanLicenseDocumentText(rawText: String): String =
    rawText
        .lineSequence()
        .map { line ->
            when {
                line.startsWith("### ") -> line.removePrefix("### ").trim()
                line.startsWith("## ") -> line.removePrefix("## ").trim()
                line.startsWith("# ") -> line.removePrefix("# ").trim()
                else -> line.trimEnd()
            }
        }
        .joinToString("\n")
        .trim()

private fun extractThirdPartySection(rawNotices: String, marker: String): String {
    val afterMarker = rawNotices.substringAfter(marker, missingDelimiterValue = "")
    if (afterMarker.isEmpty()) return ""
    return afterMarker.substringBefore("=== SECTION:").trim()
}

@Composable
private fun ColumnScope.LicenseDetailSubPageContent(subPage: LauncherSubPage) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val detailSpec = remember(context, subPage) {
        when (subPage) {
            LauncherSubPage.LICENSE_APP -> {
                val appLicenseRaw = runCatching {
                    context.assets.open("APP_LICENSE.txt").bufferedReader().use { it.readText() }
                }.getOrDefault(
                    "Better Dual Screen Assistant Source-Available License v1.1\n\n" +
                        "Copyright (c) 2026 Maik Thomalla / Stormpanda\n\n" +
                        "Free for personal, non-commercial use. See repository LICENSE for full terms."
                )
                LicenseDetailSpec(
                    documentTitle = context.getString(R.string.licenses_detail_app_title),
                    scopeBannerSummary = context.getString(R.string.licenses_detail_app_scope),
                    linkActionLabel = context.getString(R.string.licenses_link_github),
                    linkUrl = "https://github.com/Stormpanda/better-dual-screen-assistant/blob/main/LICENSE",
                    fullBodyText = cleanLicenseDocumentText(appLicenseRaw)
                )
            }

            LauncherSubPage.LICENSE_FUSION_PIXEL -> {
                val noticesRaw = runCatching {
                    context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
                }.getOrDefault("")
                val oflText = extractThirdPartySection(noticesRaw, "=== SECTION: SIL_OFL_1_1 ===")
                    .ifEmpty { "SIL Open Font License, Version 1.1" }
                LicenseDetailSpec(
                    documentTitle = context.getString(R.string.licenses_detail_fusion_pixel_title),
                    scopeBannerSummary = context.getString(R.string.licenses_detail_fusion_pixel_scope),
                    linkActionLabel = context.getString(R.string.licenses_link_github),
                    linkUrl = "https://github.com/TakWolf/fusion-pixel-font",
                    fullBodyText = "Copyright (c) 2022, TakWolf (https://takwolf.com), with Reserved Font Name \"Fusion Pixel\"\n" +
                        "Source: https://github.com/TakWolf/fusion-pixel-font\n\n" +
                        cleanLicenseDocumentText(oflText)
                )
            }

            LauncherSubPage.LICENSE_DOSIYAGI -> {
                val noticesRaw = runCatching {
                    context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
                }.getOrDefault("")
                val mitText = extractThirdPartySection(noticesRaw, "=== SECTION: DOSIYAGI_MIT ===")
                    .ifEmpty { "MIT License — Copyright (c) 2022 Damheo Lee (DOSIyagiBoldface font only)" }
                LicenseDetailSpec(
                    documentTitle = context.getString(R.string.licenses_detail_dosiyagi_title),
                    scopeBannerSummary = context.getString(R.string.licenses_detail_dosiyagi_scope),
                    linkActionLabel = context.getString(R.string.licenses_link_github),
                    linkUrl = "https://github.com/leedheo/DOSIyagi",
                    fullBodyText = cleanLicenseDocumentText(mitText)
                )
            }

            LauncherSubPage.LICENSE_ANDROIDX -> {
                LicenseDetailSpec(
                    documentTitle = context.getString(R.string.licenses_detail_androidx_title),
                    scopeBannerSummary = context.getString(R.string.licenses_detail_androidx_scope),
                    linkActionLabel = context.getString(R.string.licenses_link_apache),
                    linkUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    fullBodyText = "AndroidX & Jetpack Compose Runtime Libraries\n" +
                        "Copyright © The Android Open Source Project\n\n" +
                        "Included packages:\n" +
                        "• androidx.core:core-ktx\n" +
                        "• androidx.activity:activity-compose\n" +
                        "• androidx.lifecycle:*\n" +
                        "• androidx.savedstate:savedstate-ktx\n" +
                        "• androidx.compose.ui:*\n" +
                        "• androidx.compose.foundation:foundation\n" +
                        "• androidx.compose.material3:material3\n" +
                        "• androidx.compose.material:material-icons-extended\n\n" +
                        "Upstream Source: https://android.googlesource.com/platform/frameworks/support\n" +
                        "License: Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0)"
                )
            }

            LauncherSubPage.LICENSE_KOTLIN -> {
                LicenseDetailSpec(
                    documentTitle = context.getString(R.string.licenses_detail_kotlin_title),
                    scopeBannerSummary = context.getString(R.string.licenses_detail_kotlin_scope),
                    linkActionLabel = context.getString(R.string.licenses_link_apache),
                    linkUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    fullBodyText = "Kotlin Standard Library & Kotlinx Coroutines\n" +
                        "Copyright © 2010–2025 JetBrains s.r.o. and Kotlin Programming Language contributors\n\n" +
                        "Included packages:\n" +
                        "• org.jetbrains.kotlin:kotlin-stdlib\n" +
                        "• org.jetbrains.kotlinx:kotlinx-coroutines-android\n\n" +
                        "Upstream Sources:\n" +
                        "• https://github.com/JetBrains/kotlin\n" +
                        "• https://github.com/Kotlin/kotlinx.coroutines\n\n" +
                        "License: Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0)"
                )
            }

            else -> {
                LicenseDetailSpec(
                    documentTitle = context.getString(R.string.licenses_detail_default_title),
                    scopeBannerSummary = "",
                    linkActionLabel = context.getString(R.string.licenses_link_open),
                    linkUrl = "https://github.com/Stormpanda/better-dual-screen-assistant",
                    fullBodyText = ""
                )
            }
        }
    }

    val scrollState = rememberScrollState(0)
    val docFocusRequester = remember(subPage) { FocusRequester() }
    val interactionSource = remember(subPage) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val cardRegistry = LocalDeckCardRegistry.current
    val recordLastFocused = LocalLastFocusedDeckTracker.current
    val itemKey = remember(subPage) { "license_doc_pane_${subPage.name}" }

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var heldScrollDir by remember(subPage) { mutableIntStateOf(0) }
    var activeScrollKeyCode by remember(subPage) { mutableIntStateOf(0) }
    var pendingActionKeyCode by remember(subPage) { mutableIntStateOf(0) }

    DisposableEffect(itemKey, docFocusRequester) {
        cardRegistry?.invoke(itemKey, docFocusRequester)
        onDispose {
            cardRegistry?.invoke(itemKey, null)
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            recordLastFocused?.invoke(itemKey, docFocusRequester)
        } else {
            heldScrollDir = 0
            activeScrollKeyCode = 0
            pendingActionKeyCode = 0
        }
    }

    LaunchedEffect(heldScrollDir) {
        if (heldScrollDir != 0) {
            kotlinx.coroutines.delay(140L)
            var lastFrameNanos = 0L
            val speedPxPerSec = with(density) { 440.dp.toPx() }
            while (heldScrollDir != 0) {
                withFrameNanos { now ->
                    if (lastFrameNanos != 0L) {
                        val dtSec = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        scrollState.dispatchRawDelta(heldScrollDir * speedPxPerSec * dtSec)
                    }
                    lastFrameNanos = now
                }
            }
        }
    }

    // Pinned scope & external-link header bar (never scrolls off screen)
    val headerShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.surface.copy(alpha = 0.65f), headerShape)
            .border(1.dp, appColors.onSurfaceSecondary.copy(alpha = 0.18f), headerShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = appColors.accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = detailSpec.scopeBannerSummary,
            color = appColors.onSurfaceSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .focusProperties { canFocus = false }
                .clickable {
                    launchUrlOnPrimaryDisplay(context, detailSpec.linkUrl)
                }
        ) {
            GamepadPill(
                text = if (isFocused) {
                    stringResource(R.string.licenses_link_focused, detailSpec.linkActionLabel)
                } else {
                    detailSpec.linkActionLabel
                },
                isHighlighted = isFocused
            )
        }
    }

    // Single continuous viewport-sized document reader pane (zero split boxes, zero BringIntoView jitter)
    val docShape = RoundedCornerShape(12.dp)
    val docBorderColor = if (isFocused) {
        appColors.accent.copy(alpha = 0.85f)
    } else {
        appColors.onSurfaceSecondary.copy(alpha = 0.18f)
    }
    val docBorderWidth = if (isFocused) 1.5.dp else 1.dp
    val scrollbarTrackColor = appColors.onSurfaceSecondary.copy(alpha = 0.12f)
    val scrollbarThumbColor = if (isFocused) {
        appColors.accent.copy(alpha = 0.75f)
    } else {
        appColors.onSurfaceSecondary.copy(alpha = 0.40f)
    }
    val stepPx = with(density) { 56.dp.toPx() }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onSizeChanged { viewportHeightPx = it.height }
            .clip(docShape)
            .background(appColors.surface.copy(alpha = 0.55f), docShape)
            .border(docBorderWidth, docBorderColor, docShape)
            .firstDeckItem()
            .focusRequester(docFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (activeScrollKeyCode != keyCode) {
                                activeScrollKeyCode = keyCode
                                heldScrollDir = -1
                                coroutineScope.launch {
                                    scrollState.animateScrollBy(-stepPx, tween(90))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (activeScrollKeyCode != keyCode) {
                                activeScrollKeyCode = keyCode
                                heldScrollDir = 1
                                coroutineScope.launch {
                                    scrollState.animateScrollBy(stepPx, tween(90))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_L2,
                        KeyEvent.KEYCODE_PAGE_UP -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                val pageStep = (viewportHeightPx * 0.75f).coerceAtLeast(stepPx * 3f)
                                coroutineScope.launch {
                                    scrollState.animateScrollBy(-pageStep, tween(160))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R2,
                        KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                val pageStep = (viewportHeightPx * 0.75f).coerceAtLeast(stepPx * 3f)
                                coroutineScope.launch {
                                    scrollState.animateScrollBy(pageStep, tween(160))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MOVE_HOME -> {
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                            true
                        }
                        KeyEvent.KEYCODE_MOVE_END -> {
                            coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                pendingActionKeyCode = keyCode
                            }
                            true
                        }
                        else -> false
                    }
                } else if (keyEvent.type == KeyEventType.KeyUp) {
                    when {
                        keyCode == activeScrollKeyCode && keyCode != 0 -> {
                            activeScrollKeyCode = 0
                            heldScrollDir = 0
                            true
                        }
                        keyCode == pendingActionKeyCode && keyCode != 0 -> {
                            pendingActionKeyCode = 0
                            PrimaryOverlayInputBridge.notifyKeyInput()
                            launchUrlOnPrimaryDisplay(context, detailSpec.linkUrl)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable(interactionSource = interactionSource)
            .drawWithContent {
                drawContent()
                val maxScroll = scrollState.maxValue
                if (maxScroll > 0 && size.height > 0f) {
                    val trackPadV = 8.dp.toPx()
                    val trackHeight = (size.height - trackPadV * 2f).coerceAtLeast(0f)
                    if (trackHeight > 16f) {
                        val totalContent = size.height + maxScroll.toFloat()
                        val thumbHeight = ((size.height / totalContent) * trackHeight)
                            .coerceIn(24.dp.toPx().coerceAtMost(trackHeight), trackHeight)
                        val fraction = (scrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
                        val thumbTop = trackPadV + fraction * (trackHeight - thumbHeight)
                        val barWidth = 3.5.dp.toPx()
                        val barX = size.width - barWidth - 5.dp.toPx()
                        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                        drawRoundRect(
                            color = scrollbarTrackColor,
                            topLeft = Offset(barX, trackPadV),
                            size = Size(barWidth, trackHeight),
                            cornerRadius = corner
                        )
                        drawRoundRect(
                            color = scrollbarThumbColor,
                            topLeft = Offset(barX, thumbTop),
                            size = Size(barWidth, thumbHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 18.dp)
        ) {
            Text(
                text = detailSpec.documentTitle,
                color = appColors.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(appColors.divider)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = detailSpec.fullBodyText,
                color = appColors.onSurface.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DiagnosticsSettingsTabContent(
    profile: DeviceProfile,
    helperConnected: Boolean,
    pServerAvailable: Boolean,
    stockDisabled: Boolean,
    isMegingiardAvailable: Boolean,
    syncedMegingiardOption: AppThemeOption,
    isRefreshing: Boolean,
    onReconnectAndRefresh: () -> Unit
) {
    GamepadActionCard(
        title = stringResource(R.string.diag_privileged_helper_title),
        description = stringResource(R.string.diag_privileged_helper_desc),
        icon = Icons.Rounded.Security,
        trailingContent = {
            GamepadPill(
                text = stringResource(
                    if (helperConnected) R.string.diag_status_connected else R.string.diag_status_disconnected
                ),
                isAccent = helperConnected
            )
        },
        onClick = onReconnectAndRefresh,
        modifier = Modifier.firstDeckItem()
    )

    GamepadActionCard(
        title = stringResource(R.string.diag_pserver_title),
        description = stringResource(R.string.diag_pserver_desc, profile.displayName),
        icon = Icons.Rounded.DeveloperBoard,
        trailingContent = {
            GamepadPill(
                text = stringResource(
                    if (pServerAvailable) R.string.diag_status_available else R.string.diag_status_unavailable
                ),
                isAccent = pServerAvailable
            )
        },
        onClick = onReconnectAndRefresh
    )

    GamepadActionCard(
        title = stringResource(R.string.diag_stock_process_title),
        description = stringResource(R.string.diag_stock_process_desc, profile.stockAssistantPackage),
        icon = Icons.Rounded.Layers,
        trailingContent = {
            GamepadPill(
                text = stringResource(
                    if (stockDisabled) R.string.diag_status_suppressed else R.string.diag_status_active
                ),
                isAccent = stockDisabled
            )
        },
        onClick = onReconnectAndRefresh
    )

    if (isMegingiardAvailable) {
        GamepadActionCard(
            title = stringResource(R.string.diag_megingiard_title),
            description = stringResource(
                R.string.diag_megingiard_desc,
                stringResource(syncedMegingiardOption.displayNameRes)
            ),
            icon = Icons.Rounded.Hub,
            trailingContent = {
                GamepadPill(
                    text = stringResource(R.string.diag_status_detected),
                    isAccent = true
                )
            },
            onClick = onReconnectAndRefresh
        )
    }

    GamepadActionCard(
        title = stringResource(R.string.diag_reconnect_title),
        description = stringResource(R.string.diag_reconnect_desc),
        icon = Icons.Rounded.Refresh,
        actionText = stringResource(
            if (isRefreshing) R.string.diag_action_refreshing else R.string.diag_action_reconnect
        ),
        enabled = !isRefreshing,
        onClick = onReconnectAndRefresh
    )

    GamepadSectionHeader(text = stringResource(R.string.diag_section_device_build))

    GamepadInfoBox(
        text = stringResource(R.string.diag_topology_title, profile.displayName),
        description = stringResource(
            R.string.diag_topology_desc,
            profile.topDisplayId,
            profile.bottomDisplayId,
            profile.bottomFallbackWidth,
            profile.bottomFallbackHeight,
            profile.bottomFallbackDensityDpi
        )
    )

    GamepadActionCard(
        title = stringResource(R.string.diag_app_version_title),
        description = BuildConfig.APPLICATION_ID,
        icon = Icons.Rounded.Info,
        trailingContent = {
            GamepadPill(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                isAccent = false
            )
        },
        onClick = {}
    )

    GamepadInfoBox(
        text = stringResource(R.string.diag_font_credits_title),
        description = stringResource(R.string.diag_font_credits_desc)
    )
}
