package com.stormpanda.betterdualscreenassistant

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.TccColorPalette
import com.stormpanda.betterdualscreenassistant.ui.theme.TccTheme
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.ui.AppColors
import com.stormpanda.megingiard.ui.AppPalettes
import com.stormpanda.megingiard.ui.BumperDirection
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
import com.stormpanda.megingiard.ui.LocalDeckBackInterceptor
import com.stormpanda.megingiard.ui.PrimaryOverlayInputBridge
import com.stormpanda.megingiard.ui.colorSchemeFor
import com.stormpanda.megingiard.ui.cycle
import com.stormpanda.megingiard.ui.firstDeckItem
import com.stormpanda.megingiard.ui.megingiardTypography
import com.stormpanda.megingiard.ui.paletteFor
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
        AssistantForegroundService.startService(this)

        setContent {
            val context = LocalContext.current
            val repo = remember {
                AssistantForegroundService.instance?.repository
                    ?: SystemSettingsRepository(context.applicationContext)
            }
            val themeModeId by repo.themeMode.collectAsState()
            val customAccentArgb by repo.customAccentArgb.collectAsState()
            val syncedMegingiardOption by repo.syncedMegingiardOption.collectAsState()
            val syncedMegingiardAccentArgb by repo.syncedMegingiardAccentArgb.collectAsState()

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

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val handled = PrimaryOverlayInputBridge.processGenericMotionEvent(ev) { action, keyCode ->
            dispatchKeyEvent(KeyEvent(action, keyCode))
        }
        return if (handled) true else super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
        if (!handled) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    onBackPressedDispatcher.onBackPressed()
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN && isNavigationOrActionKey(event.keyCode)) {
                PrimaryOverlayInputBridge.sendFocusRecovery(event.keyCode)
                return true
            }
        }
        return handled
    }

    override fun onDestroy() {
        PrimaryOverlayInputBridge.resetJoystickState()
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
        sectionHeaderColor = tccColors.accent,
        settingsSeparator = tccColors.primary.copy(alpha = 0.12f)
    )
}

private enum class LauncherCategory(
    val title: String,
    val icon: ImageVector
) {
    GENERAL("General", Icons.Rounded.Tune),
    QUICK_TOGGLES("Quick Toggles", Icons.Rounded.GridView),
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    DIAGNOSTICS("Diagnostics", Icons.Rounded.HealthAndSafety)
}

private enum class LauncherSubPage(
    val parentCategory: LauncherCategory
) {
    CUSTOM_ACCENT(LauncherCategory.APPEARANCE),
    ENABLE_DISABLE_TOGGLES(LauncherCategory.QUICK_TOGGLES)
}

private enum class ThemeSourceOption(
    val displayName: String
) {
    STOCK("Stock AYN"),
    SYNC_MEGINGIARD("Sync with Megingiard"),
    MEGINGIARD_PALETTE("Megingiard Palette")
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

@Composable
private fun BetterDualScreenSettingsScreen(repo: SystemSettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeModeId by repo.themeMode.collectAsState()
    val selectedTheme = AppThemeOption.fromId(themeModeId)
    val customAccentArgb by repo.customAccentArgb.collectAsState()
    val injectSystemTheme by repo.injectOsTheme.collectAsState()
    val syncedMegingiardOption by repo.syncedMegingiardOption.collectAsState()
    val showCloseBottomScreenTip by repo.showCloseBottomScreenTip.collectAsState()
    val rememberLastTab by repo.rememberLastTab.collectAsState()
    val isMegingiardAvailable by repo.isMegingiardProviderAvailable.collectAsState()
    val quickTileOrder by repo.quickTileOrder.collectAsState()
    val disabledQuickTiles by repo.disabledQuickTiles.collectAsState()

    var selectedCategory by rememberSaveable { mutableStateOf(LauncherCategory.GENERAL) }
    var subPageStack by rememberSaveable { mutableStateOf<List<LauncherSubPage>>(emptyList()) }
    val currentSubPage = subPageStack.lastOrNull()
    var isVisualReorderGridActive by remember { mutableStateOf(false) }

    val helperConnected by PrivilegedBridge.isHelperConnected.collectAsState()
    var pServerAvailable by remember { mutableStateOf(false) }
    var stockDisabled by remember { mutableStateOf(true) }
    var isRefreshingStatus by remember { mutableStateOf(false) }
    var customSwatchColorArgb by rememberSaveable { mutableIntStateOf(customAccentArgb) }

    LaunchedEffect(customAccentArgb) {
        if (Color(customAccentArgb) !in AppPalettes.accentPresets) {
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

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repo.refreshAll()
                scope.launch { refreshStatus() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val categoryList = remember { LauncherCategory.entries }
    LaunchedEffect(Unit) {
        PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
            if (!isVisualReorderGridActive) {
                selectedCategory = categoryList.cycle(selectedCategory, direction)
                subPageStack = emptyList()
            }
        }
    }

    GamepadTwoPaneScaffold(
        scrollableDeck = false,
        isCustomBackActive = subPageStack.isNotEmpty(),
        onCustomBack = {
            subPageStack = subPageStack.dropLast(1)
        },
        navigationKey = subPageStack,
        sidebarContent = {
            LauncherCategory.entries.forEach { category ->
                GamepadCategoryTile(
                    title = category.title,
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
                val subPage = stack.lastOrNull()
                when (subPage) {
                    null -> {
                        GamepadDeck(title = selectedCategory.title) {
                            when (selectedCategory) {
                                LauncherCategory.GENERAL -> {
                                    GeneralSettingsTabContent(
                                        stockDisabled = stockDisabled,
                                        showCloseBottomScreenTip = showCloseBottomScreenTip,
                                        rememberLastTab = rememberLastTab,
                                        onToggleReplaceStock = { replaceStock ->
                                            stockDisabled = replaceStock
                                            scope.launch(Dispatchers.IO) {
                                                PServerClient.setStockAssistantEnabled(!replaceStock, context)
                                                refreshStatus()
                                            }
                                        },
                                        onOpenOverlay = {
                                            AssistantForegroundService.startService(
                                                context,
                                                AssistantForegroundService.ACTION_SHOW_OVERLAY
                                            )
                                        },
                                        onToggleCloseBottomScreenTip = { enabled ->
                                            repo.setNoMoreCloseBottomScreenTip(!enabled)
                                        },
                                        onToggleRememberLastTab = { enabled ->
                                            repo.setRememberLastTab(enabled)
                                        }
                                    )
                                }

                                LauncherCategory.QUICK_TOGGLES -> {
                                    QuickTogglesSettingsTabContent(
                                        quickTileOrder = quickTileOrder,
                                        disabledQuickTiles = disabledQuickTiles,
                                        onMoveActiveTile = { fromIndex, toIndex ->
                                            repo.moveActiveQuickTile(fromIndex, toIndex)
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
                                        selectedTheme = selectedTheme,
                                        customAccentArgb = customAccentArgb,
                                        customSwatchColorArgb = customSwatchColorArgb,
                                        injectSystemTheme = injectSystemTheme,
                                        syncedMegingiardOption = syncedMegingiardOption,
                                        isMegingiardAvailable = isMegingiardAvailable,
                                        onSelectTheme = { repo.setThemeMode(it.id) },
                                        onSelectAccentColor = { repo.setCustomAccentArgb(it) },
                                        onOpenCustomAccent = {
                                            subPageStack = listOf(LauncherSubPage.CUSTOM_ACCENT)
                                        },
                                        onToggleInjectSystemTheme = { repo.setInjectOsTheme(it) }
                                    )
                                }

                                LauncherCategory.DIAGNOSTICS -> {
                                    DiagnosticsSettingsTabContent(
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

                    LauncherSubPage.CUSTOM_ACCENT -> {
                        GamepadDeck(
                            breadcrumbs = listOf(
                                LauncherCategory.APPEARANCE.title,
                                "Custom Color"
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
                                LauncherCategory.QUICK_TOGGLES.title,
                                "Enable / Disable"
                            )
                        ) {
                            EnableDisableTogglesSubPageContent(
                                quickTileOrder = quickTileOrder,
                                disabledQuickTiles = disabledQuickTiles,
                                onSetTileEnabled = { tile, enabled ->
                                    repo.setQuickTileEnabled(tile, enabled)
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun GeneralSettingsTabContent(
    stockDisabled: Boolean,
    showCloseBottomScreenTip: Boolean,
    rememberLastTab: Boolean,
    onToggleReplaceStock: (Boolean) -> Unit,
    onOpenOverlay: () -> Unit,
    onToggleCloseBottomScreenTip: (Boolean) -> Unit,
    onToggleRememberLastTab: (Boolean) -> Unit
) {
    GamepadToggleCard(
        title = "Replace Stock Assistant",
        description = if (stockDisabled) {
            "Stock DualScreenAssistant is suppressed; physical AYN button opens Better Dual Screen Assistant"
        } else {
            "Stock DualScreenAssistant is currently active; enable to take over the physical AYN button"
        },
        checked = stockDisabled,
        icon = Icons.Rounded.Layers,
        onCheckedChange = onToggleReplaceStock,
        modifier = Modifier.firstDeckItem()
    )

    GamepadActionCard(
        title = "Open Assistant Overlay",
        description = "Launch the Dual Screen Assistant overlay on the bottom screen (displayId = 4)",
        icon = Icons.AutoMirrored.Rounded.OpenInNew,
        actionText = "Open",
        onClick = onOpenOverlay
    )

    GamepadToggleCard(
        title = "Remember Last Open Tab",
        description = "Keep the last open overlay tab (Mode or Settings) in memory when reopening the assistant",
        checked = rememberLastTab,
        icon = Icons.Rounded.Tab,
        onCheckedChange = onToggleRememberLastTab
    )

    GamepadToggleCard(
        title = "Confirm Before Turning Off Bottom Screen",
        description = "Show the wake-up reminder dialog before blanking the bottom screen",
        checked = showCloseBottomScreenTip,
        icon = Icons.Rounded.NotificationsActive,
        onCheckedChange = onToggleCloseBottomScreenTip
    )
}

@Composable
private fun QuickTogglesSettingsTabContent(
    quickTileOrder: List<QuickSettingTileId>,
    disabledQuickTiles: Set<QuickSettingTileId>,
    onMoveActiveTile: (fromIndex: Int, toIndex: Int) -> Unit,
    onOpenEnableDisableSubPage: () -> Unit,
    onResetDefaults: () -> Unit,
    onGridActiveChanged: (Boolean) -> Unit
) {
    val activeTiles = remember(quickTileOrder, disabledQuickTiles) {
        quickTileOrder.filter { it !in disabledQuickTiles }
    }
    val totalCount = QuickSettingTileId.entries.size
    val activeCount = activeTiles.size
    val isModified = remember(quickTileOrder, disabledQuickTiles) {
        quickTileOrder != QuickSettingTileId.defaultOrder || disabledQuickTiles.isNotEmpty()
    }

    QuickTogglesVisualReorderCard(
        activeTiles = activeTiles,
        onMoveActiveTile = onMoveActiveTile,
        onGridActiveChanged = onGridActiveChanged,
        modifier = Modifier.firstDeckItem()
    )

    GamepadActionCard(
        title = "Enable / Disable Toggles",
        description = "Choose which quick setting toggles appear in the assistant overlay",
        icon = Icons.Rounded.Checklist,
        trailingContent = {
            GamepadPill(
                text = "$activeCount / $totalCount Active",
                isAccent = disabledQuickTiles.isNotEmpty()
            )
        },
        onClick = onOpenEnableDisableSubPage
    )

    GamepadTwoStepConfirmCard(
        title = "Reset Quick Toggles",
        confirmTitle = "Confirm Reset to Default",
        description = if (isModified) {
            "Restore default stock toggle order and re-enable all $totalCount toggles"
        } else {
            "All toggles are currently in their default order and enabled"
        },
        confirmDescription = "Press A again to reset toggle order and visibility, or B to cancel",
        actionText = "Reset",
        confirmActionText = "Confirm Reset",
        icon = Icons.Rounded.RestartAlt,
        enabled = isModified,
        isDestructive = true,
        onConfirm = onResetDefaults
    )
}

@Composable
private fun QuickTogglesVisualReorderCard(
    activeTiles: List<QuickSettingTileId>,
    onMoveActiveTile: (fromIndex: Int, toIndex: Int) -> Unit,
    onGridActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val tccColors = LocalTccColors.current
    val coroutineScope = rememberCoroutineScope()

    var isGridActive by remember { mutableStateOf(false) }
    var cursorIndex by remember { mutableIntStateOf(0) }
    var isGrabbed by remember { mutableStateOf(false) }

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

    LaunchedEffect(isGridActive, pageCount) {
        if (isGridActive) {
            PrimaryOverlayInputBridge.bumperEvents.collect { direction ->
                when (direction) {
                    BumperDirection.PREV -> {
                        if (pagerState.currentPage > 0) {
                            val newPage = pagerState.currentPage - 1
                            val targetIdx = (newPage * 8 + (cursorIndex % 8)).coerceAtMost(activeTiles.lastIndex)
                            if (isGrabbed) {
                                onMoveActiveTile(cursorIndex, targetIdx)
                            }
                            cursorIndex = targetIdx
                        }
                    }
                    BumperDirection.NEXT -> {
                        if (pagerState.currentPage < pageCount - 1) {
                            val newPage = pagerState.currentPage + 1
                            val targetIdx = (newPage * 8 + (cursorIndex % 8)).coerceAtMost(activeTiles.lastIndex)
                            if (isGrabbed) {
                                onMoveActiveTile(cursorIndex, targetIdx)
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
        !isGridActive -> "Press A to enter the Quick Settings grid, or tap any two tiles to swap positions"
        isGrabbed && currentTile != null -> "Moving \"${currentTile.title}\" — use D-pad to reposition, then press A or B to drop"
        currentTile != null -> "Selected #${cursorIndex + 1} \"${currentTile.title}\" — press A to grab & move, or B to exit grid"
        else -> "Use D-pad to select a toggle, press A to grab & move, or B to exit"
    }
    val pillText = when {
        isGrabbed -> "Moving #${cursorIndex + 1}"
        isGridActive -> "Slot #${cursorIndex + 1}"
        else -> "Reorder"
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
        modifier = modifier,
        itemKey = "quick_toggles_visual_reorder",
        isAdjusting = isGridActive,
        onFocusChanged = { focused ->
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
    ) { isCardFocused ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GamepadCardRow(
                title = "Quick Settings Layout",
                description = subtitle,
                icon = Icons.Rounded.GridView,
                isFocused = isCardFocused || isGridActive,
                alwaysShowFullDescription = true,
                trailingContent = {
                    GamepadPill(
                        text = pillText,
                        isAccent = isGrabbed,
                        isHighlighted = isGridActive && !isGrabbed
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Authentic QuickSettingsPanel replica (2x4 paged grid + bottom page indicator)
            Column(
                modifier = Modifier
                    .width(470.dp)
                    .height(246.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tccColors.panelBackground)
                    .border(
                        width = if (isGridActive) 1.5.dp else 1.dp,
                        color = if (isGridActive) {
                            appColors.accent.copy(alpha = 0.65f)
                        } else {
                            appColors.onSurface.copy(alpha = 0.10f)
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.91f)
                        .padding(horizontal = 10.dp)
                ) { pageIndex ->
                    val pageItems = pages.getOrNull(pageIndex).orEmpty()
                    val pageOffset = pageIndex * 8
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
                                                    } else {
                                                        onMoveActiveTile(cursorIndex, globalIdx)
                                                        cursorIndex = globalIdx
                                                        isGrabbed = false
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

                // Bottom 9%: ViewPagerIndicator matching QuickSettingsPanel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.09f),
                    contentAlignment = Alignment.Center
                ) {
                    if (pageCount > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pageCount) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) tccColors.accent else tccColors.secondary)
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

/**
 * Interactive replica of `TccButton` used inside `QuickTogglesVisualReorderCard`,
 * preserving exact 50.5dp circle + 26dp icon + 29dp title dimensions while adding
 * a slot index badge and cursor/grabbed highlight states.
 */
@Composable
private fun ReorderableTccTile(
    tile: QuickSettingTileId,
    positionIndex: Int,
    isCursorSelected: Boolean,
    isGrabbed: Boolean,
    onClick: () -> Unit
) {
    val tccColors = LocalTccColors.current
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isGrabbed) 1.07f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tileGrabScale"
    )

    val iconTint = if (isGrabbed) {
        if (tccColors.isMegingiardStyle) {
            tccColors.onAccent
        } else if (tccColors.isDark) {
            Color.White
        } else {
            tccColors.primary
        }
    } else if (isCursorSelected) {
        tccColors.primary
    } else {
        tccColors.secondary
    }
    val textColor = if (isCursorSelected || isGrabbed) tccColors.primary else tccColors.secondary

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isGrabbed -> appColors.accent.copy(alpha = 0.18f)
                    isCursorSelected -> tccColors.panelBackgroundSelected
                    else -> Color.Transparent
                }
            )
            .then(
                if (isGrabbed) {
                    Modifier.border(2.dp, appColors.accent, RoundedCornerShape(10.dp))
                } else if (isCursorSelected) {
                    Modifier.border(1.5.dp, appColors.accent.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(72.5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier.size(50.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.5.dp)
                        .clip(CircleShape)
                        .then(
                            if (isGrabbed) {
                                if (tccColors.isMegingiardStyle) {
                                    Modifier.background(tccColors.accent)
                                } else if (tccColors.isDark) {
                                    Modifier
                                        .background(Color(0xFF272727))
                                        .background(
                                            Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.5f to Color(0x1A9E21BE),
                                                1.0f to Color(0x66AB24CE)
                                            )
                                        )
                                } else {
                                    Modifier.background(Color(0xFFD0D0D0))
                                }
                            } else {
                                Modifier.background(tccColors.fifth)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = tccDrawable(tile.iconRes)),
                        contentDescription = tile.title,
                        colorFilter = ColorFilter.tint(iconTint),
                        modifier = Modifier.size(26.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Compact slot position badge (#1..#N) at top-right of the circle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isGrabbed || isCursorSelected) {
                                appColors.accent
                            } else {
                                appColors.surfaceVariant.copy(alpha = 0.92f)
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${positionIndex + 1}",
                        color = if (isGrabbed || isCursorSelected) {
                            appColors.onAccent
                        } else {
                            appColors.onSurfaceSecondary
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(72.5.dp)
                    .height(29.dp)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tile.title,
                    color = textColor,
                    fontSize = 11.sp,
                    lineHeight = 12.5.sp,
                    fontWeight = if (isCursorSelected || isGrabbed) FontWeight.SemiBold else FontWeight.Normal,
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
    quickTileOrder: List<QuickSettingTileId>,
    disabledQuickTiles: Set<QuickSettingTileId>,
    onSetTileEnabled: (QuickSettingTileId, Boolean) -> Unit
) {
    val totalCount = QuickSettingTileId.entries.size
    val enabledCount = totalCount - disabledQuickTiles.size

    GamepadInfoBox(
        text = "Toggle Visibility ($enabledCount of $totalCount Active)",
        description = "Disabled toggles are hidden from the bottom-screen Quick Settings panel. At least 1 toggle must remain enabled."
    )

    quickTileOrder.forEachIndexed { index, tile ->
        val isEnabled = tile !in disabledQuickTiles
        val canToggle = !isEnabled || enabledCount > 1
        GamepadToggleCard(
            title = tile.title,
            description = if (!canToggle) {
                "${tile.description} (At least 1 toggle must stay enabled)"
            } else {
                tile.description
            },
            checked = isEnabled,
            enabled = canToggle,
            itemKey = "toggle_visibility_${tile.key}",
            onCheckedChange = { nextEnabled ->
                onSetTileEnabled(tile, nextEnabled)
            },
            modifier = if (index == 0) Modifier.firstDeckItem() else Modifier
        )
    }
}

@Composable
private fun AppearanceSettingsTabContent(
    selectedTheme: AppThemeOption,
    customAccentArgb: Int,
    customSwatchColorArgb: Int,
    injectSystemTheme: Boolean,
    syncedMegingiardOption: AppThemeOption,
    isMegingiardAvailable: Boolean,
    onSelectTheme: (AppThemeOption) -> Unit,
    onSelectAccentColor: (Int) -> Unit,
    onOpenCustomAccent: () -> Unit,
    onToggleInjectSystemTheme: (Boolean) -> Unit
) {
    val activeSource = when {
        selectedTheme == AppThemeOption.SYNC_MEGINGIARD -> ThemeSourceOption.SYNC_MEGINGIARD
        selectedTheme.isMegingiardStyle -> ThemeSourceOption.MEGINGIARD_PALETTE
        else -> ThemeSourceOption.STOCK
    }

    var lastStockOption by rememberSaveable {
        mutableStateOf(if (!selectedTheme.isMegingiardStyle) selectedTheme else AppThemeOption.STOCK_DARK)
    }
    var lastMegingiardOption by rememberSaveable {
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
            lastMegingiardOption = selectedTheme
        }
    }

    fun applyThemeSource(source: ThemeSourceOption) {
        when (source) {
            ThemeSourceOption.STOCK -> onSelectTheme(lastStockOption)
            ThemeSourceOption.SYNC_MEGINGIARD -> onSelectTheme(AppThemeOption.SYNC_MEGINGIARD)
            ThemeSourceOption.MEGINGIARD_PALETTE -> onSelectTheme(lastMegingiardOption)
        }
    }

    GamepadChoiceCard(
        title = "Theme Source",
        description = "Choose between Stock AYN themes, live Megingiard sync, or standalone Megingiard palettes",
        selectedText = activeSource.displayName,
        icon = Icons.Rounded.Palette,
        onPrevious = {
            applyThemeSource(ThemeSourceOption.entries.cycle(activeSource, BumperDirection.PREV))
        },
        onNext = {
            applyThemeSource(ThemeSourceOption.entries.cycle(activeSource, BumperDirection.NEXT))
        },
        modifier = Modifier.firstDeckItem()
    )

    when (activeSource) {
        ThemeSourceOption.STOCK -> {
            GamepadChoiceCard(
                title = "Stock Theme Mode",
                description = "Select the built-in AYN Dual Screen Assistant appearance",
                selectedText = selectedTheme.displayName,
                icon = Icons.Rounded.ColorLens,
                onPrevious = {
                    onSelectTheme(StockThemeOptions.cycle(selectedTheme, BumperDirection.PREV))
                },
                onNext = {
                    onSelectTheme(StockThemeOptions.cycle(selectedTheme, BumperDirection.NEXT))
                }
            )
        }

        ThemeSourceOption.SYNC_MEGINGIARD -> {
            GamepadActionCard(
                title = "Live Megingiard Theme Sync",
                description = if (isMegingiardAvailable) {
                    "Synchronized with Megingiard (${syncedMegingiardOption.displayName})"
                } else {
                    "Megingiard provider not detected; using ${syncedMegingiardOption.displayName} fallback"
                },
                icon = Icons.Rounded.Sync,
                trailingContent = {
                    GamepadPill(
                        text = if (isMegingiardAvailable) "SYNCED" else "FALLBACK",
                        isAccent = isMegingiardAvailable
                    )
                },
                onClick = {}
            )
        }

        ThemeSourceOption.MEGINGIARD_PALETTE -> {
            GamepadChoiceCard(
                title = "Megingiard Palette",
                description = "Color scheme for launcher & assistant overlay surfaces",
                selectedText = selectedTheme.displayName,
                icon = Icons.Rounded.ColorLens,
                onPrevious = {
                    onSelectTheme(MegingiardThemeOptions.cycle(selectedTheme, BumperDirection.PREV))
                },
                onNext = {
                    onSelectTheme(MegingiardThemeOptions.cycle(selectedTheme, BumperDirection.NEXT))
                }
            )
        }
    }

    if (selectedTheme.supportsCustomAccent) {
        val accentColor = Color(customAccentArgb)
        val customSwatchColor = Color(customSwatchColorArgb)
        val isCustomAccent =
            customAccentArgb == customSwatchColorArgb && accentColor !in AppPalettes.accentPresets

        GamepadColorPaletteCard(
            title = "Accent Color",
            description = "Choose a preset highlight color for ${selectedTheme.displayName}",
            icon = Icons.Rounded.FormatColorFill,
            paletteColors = AppPalettes.accentPresets,
            selectedColor = accentColor,
            onColorSelected = { onSelectAccentColor(it.toArgb()) }
        )

        GamepadActionCard(
            title = "Custom Color",
            description = "Fine-tune Hue, Saturation, and Brightness for a custom accent color",
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

    GamepadToggleCard(
        title = "Inject Theme Colors into OS",
        description = "Push active theme palette to Android SystemUI and Settings app via theme_customization_overlay_packages",
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
        title = "Hue",
        description = "Base color angle on the color wheel",
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
        title = "Saturation",
        description = "Color intensity from neutral gray to pure vivid color",
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
        title = "Brightness",
        description = "Lightness of the accent color",
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
        title = "Undo Color Changes",
        description = if (hasChanges) {
            "Revert from $workingHex back to $initialHex"
        } else {
            "No unsaved color adjustments ($workingHex)"
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
        actionText = if (hasChanges) "Undo" else null,
        enabled = hasChanges,
        onClick = {
            hue = initHsv[0]
            sat = initHsv[1]
            bri = initHsv[2]
        }
    )

    GamepadActionCard(
        title = "Save Color",
        description = "Apply $workingHex as the custom accent color",
        icon = Icons.Rounded.Save,
        actionText = "Save",
        onClick = { onSaveColor(workingColor) }
    )
}

@Composable
private fun DiagnosticsSettingsTabContent(
    helperConnected: Boolean,
    pServerAvailable: Boolean,
    stockDisabled: Boolean,
    isMegingiardAvailable: Boolean,
    syncedMegingiardOption: AppThemeOption,
    isRefreshing: Boolean,
    onReconnectAndRefresh: () -> Unit
) {
    GamepadActionCard(
        title = "Privileged Helper (UID 0)",
        description = "In-memory root Binder daemon handling display blanking, brightness, and uninstall recovery",
        icon = Icons.Rounded.Security,
        trailingContent = {
            GamepadPill(
                text = if (helperConnected) "CONNECTED" else "DISCONNECTED",
                isAccent = helperConnected
            )
        },
        onClick = onReconnectAndRefresh,
        modifier = Modifier.firstDeckItem()
    )

    GamepadActionCard(
        title = "PServerBinder Root Service",
        description = "AYN Thor firmware root command execution service in ServiceManager",
        icon = Icons.Rounded.DeveloperBoard,
        trailingContent = {
            GamepadPill(
                text = if (pServerAvailable) "AVAILABLE" else "UNAVAILABLE",
                isAccent = pServerAvailable
            )
        },
        onClick = onReconnectAndRefresh
    )

    GamepadActionCard(
        title = "Stock DualScreenAssistant Process",
        description = "Ephemeral SIGSTOP suppression of com.odin.dualscreen.assistant",
        icon = Icons.Rounded.Layers,
        trailingContent = {
            GamepadPill(
                text = if (stockDisabled) "SUPPRESSED" else "ACTIVE",
                isAccent = stockDisabled
            )
        },
        onClick = onReconnectAndRefresh
    )

    GamepadActionCard(
        title = "Megingiard Theme Provider",
        description = if (isMegingiardAvailable) {
            "Connected to Megingiard theme provider (${syncedMegingiardOption.displayName})"
        } else {
            "Megingiard ContentProvider not detected on this device"
        },
        icon = Icons.Rounded.Hub,
        trailingContent = {
            GamepadPill(
                text = if (isMegingiardAvailable) "DETECTED" else "NOT FOUND",
                isAccent = isMegingiardAvailable
            )
        },
        onClick = onReconnectAndRefresh
    )

    GamepadActionCard(
        title = "Reconnect Privileged Bridge",
        description = "Re-establish UID 0 helper connection, re-grant permissions, and refresh status",
        icon = Icons.Rounded.Refresh,
        actionText = if (isRefreshing) "Refreshing..." else "Reconnect",
        enabled = !isRefreshing,
        onClick = onReconnectAndRefresh
    )

    GamepadSectionHeader(text = "DEVICE & BUILD")

    GamepadInfoBox(
        text = "AYN Thor Display Topology",
        description = "Top Screen (displayId = 0): 1920×1080 • Bottom Screen (displayId = 4): 1240×1080 (369 dpi)"
    )

    GamepadActionCard(
        title = "Application Version",
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
}
