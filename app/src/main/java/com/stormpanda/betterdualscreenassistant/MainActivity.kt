package com.stormpanda.betterdualscreenassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DashboardCategory(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val iconRes: Int
) {
    GENERAL(
        title = "General",
        subtitle = "Takeover & overlay",
        iconRes = R.drawable.ic_dual_screen_display
    ),
    APPEARANCE(
        title = "Appearance",
        subtitle = "Themes & OS colors",
        iconRes = R.drawable.ic_qs_theme_mode
    ),
    DIAGNOSTICS(
        title = "System & Diagnostics",
        subtitle = "Bridge & device info",
        iconRes = R.drawable.ic_system_set
    )
}

private enum class ThemeSourceMode(val label: String) {
    SYNC_MEGINGIARD("Sync with Megingiard"),
    MEGINGIARD("Megingiard Themes"),
    STOCK("Stock Themes")
}

private val StandaloneMegingiardOptions = listOf(
    AppThemeOption.MEGINGIARD_DARK,
    AppThemeOption.MEGINGIARD_DARK_OLED,
    AppThemeOption.MEGINGIARD,
    AppThemeOption.MJOLNIR,
    AppThemeOption.VALHALLA,
    AppThemeOption.AURORA,
    AppThemeOption.RETRO_PHOSPHOR,
    AppThemeOption.ROYAL_ASGARD
)

private val StockThemeOptions = listOf(
    AppThemeOption.STOCK_SYSTEM,
    AppThemeOption.STOCK_LIGHT,
    AppThemeOption.STOCK_DARK
)

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

    var selectedCategory by rememberSaveable { mutableStateOf(DashboardCategory.GENERAL) }

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

    val allBridgesHealthy = pServerAvailable && helperConnected && serviceRunning

    val onToggleStockReplacement: (Boolean) -> Unit = { replace ->
        if (!isTogglingStock) {
            isTogglingStock = true
            scope.launch(Dispatchers.IO) {
                PServerClient.setStockAssistantEnabled(!replace, context)
                val nowEnabled = PServerClient.isStockAssistantEnabled(context)
                withContext(Dispatchers.Main) {
                    stockReplaced = !nowEnabled
                    isTogglingStock = false
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.primaryBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Category Sidebar
        CategorySidebar(
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            allBridgesHealthy = allBridgesHealthy,
            stockReplaced = stockReplaced,
            onOpenOverlay = {
                AssistantForegroundService.startService(
                    context,
                    AssistantForegroundService.ACTION_SHOW_OVERLAY
                )
            },
            modifier = Modifier
                .width(244.dp)
                .fillMaxHeight()
        )

        // Right Column: Selected Category Settings Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "CategoryPaneTransition"
            ) { category ->
                when (category) {
                    DashboardCategory.GENERAL -> GeneralSettingsPane(
                        repository = repository,
                        pServerAvailable = pServerAvailable,
                        helperConnected = helperConnected,
                        serviceRunning = serviceRunning,
                        stockReplaced = stockReplaced,
                        isTogglingStock = isTogglingStock,
                        onToggleStockReplacement = onToggleStockReplacement
                    )
                    DashboardCategory.APPEARANCE -> AppearanceSettingsPane(
                        repository = repository
                    )
                    DashboardCategory.DIAGNOSTICS -> DiagnosticsSettingsPane(
                        repository = repository,
                        pServerAvailable = pServerAvailable,
                        helperConnected = helperConnected,
                        serviceRunning = serviceRunning,
                        stockReplaced = stockReplaced
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySidebar(
    selectedCategory: DashboardCategory,
    onSelectCategory: (DashboardCategory) -> Unit,
    allBridgesHealthy: Boolean,
    stockReplaced: Boolean,
    onOpenOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val statusColor = when {
        !allBridgesHealthy -> Color(0xFFE34646)
        stockReplaced -> colors.lightning
        else -> colors.secondary
    }
    val statusText = when {
        !allBridgesHealthy -> "Bridge Initializing"
        stockReplaced -> "Takeover Active"
        else -> "Stock Mode"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panelBackground)
            .border(1.dp, colors.fourth, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // App Identity Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.16f))
                        .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = tccDrawable(R.drawable.ic_dual_screen_display)),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.accent),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Better DualScreen",
                        color = colors.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onSelectCategory(DashboardCategory.DIAGNOSTICS)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            color = colors.secondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.fourth)
            )

            // Category Navigation Items
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DashboardCategory.entries.forEach { category ->
                    val isSelected = selectedCategory == category
                    SidebarCategoryItem(
                        category = category,
                        selected = isSelected,
                        onClick = { onSelectCategory(category) }
                    )
                }
            }
        }

        // Quick Bottom Action
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.fourth)
            )
            ActionButton(
                label = "Open Overlay",
                backgroundColor = colors.accent,
                textColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                onClick = onOpenOverlay,
                modifier = Modifier.fillMaxWidth(),
                height = 40.dp
            )
        }
    }
}

@Composable
private fun SidebarCategoryItem(
    category: DashboardCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTccColors.current
    val bgColor = if (selected) colors.fifth else Color.Transparent
    val borderColor = if (selected) colors.accent.copy(alpha = 0.45f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) colors.accent else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Image(
            painter = painterResource(id = tccDrawable(category.iconRes)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (selected) colors.accent else colors.secondary),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                color = colors.primary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category.subtitle,
                color = colors.secondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GeneralSettingsPane(
    repository: SystemSettingsRepository,
    pServerAvailable: Boolean,
    helperConnected: Boolean,
    serviceRunning: Boolean,
    stockReplaced: Boolean,
    isTogglingStock: Boolean,
    onToggleStockReplacement: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalTccColors.current
    val showCloseScreenTip by repository.showCloseBottomScreenTip.collectAsState()
    var bridgeDetailsExpanded by rememberSaveable { mutableStateOf(false) }

    val allHealthy = pServerAvailable && helperConnected && serviceRunning
    val healthLabel = when {
        !allHealthy -> "Needs Attention"
        stockReplaced -> "Ready • UID 0 Active"
        else -> "Standby • Stock Active"
    }
    val healthDotColor = when {
        !allHealthy -> Color(0xFFE34646)
        stockReplaced -> colors.lightning
        else -> colors.secondary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaneSectionHeader(
            title = "General",
            subtitle = "Manage hardware button takeover, bottom-screen overlay controls, and confirmation prompts."
        )

        // Unified Hero Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(
                    width = 1.dp,
                    color = if (stockReplaced) colors.accent.copy(alpha = 0.35f) else colors.fourth,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: Health Pill (expandable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.fifth)
                        .clickable { bridgeDetailsExpanded = !bridgeDetailsExpanded }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(healthDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = healthLabel,
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (bridgeDetailsExpanded) "Hide Details" else "Details",
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isTogglingStock) {
                    Text(
                        text = "Applying...",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Takeover Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replace Stock DualScreenAssistant",
                        color = colors.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (stockReplaced) {
                            "Pressing the physical AYN button opens Better DualScreenAssistant on the bottom display. Long-pressing blanks the bottom screen."
                        } else {
                            "Stock DualScreenAssistant is currently handling the AYN button. Enable takeover to switch to Better DualScreenAssistant."
                        },
                        color = colors.secondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = stockReplaced,
                    enabled = !isTogglingStock,
                    onCheckedChange = onToggleStockReplacement,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                        checkedTrackColor = colors.accent
                    )
                )
            }

            // Expandable Bridge Details & Reconnect
            AnimatedVisibility(
                visible = bridgeDetailsExpanded || !allHealthy,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.fifth)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusIndicatorRow(
                        label = "PServerBinder Root Service",
                        active = pServerAvailable,
                        statusText = if (pServerAvailable) "Connected" else "Unavailable"
                    )
                    StatusIndicatorRow(
                        label = "Privileged Helper Bridge",
                        active = helperConnected,
                        statusText = if (helperConnected) "Active (UID 0)" else "Connecting..."
                    )
                    StatusIndicatorRow(
                        label = "Overlay Foreground Service",
                        active = serviceRunning,
                        statusText = if (serviceRunning) "Running" else "Starting..."
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ActionButton(
                        label = "Reconnect Root Bridge & Re-Grant Permissions",
                        backgroundColor = colors.panelBackground,
                        textColor = colors.primary,
                        onClick = { PrivilegedBridge.ensureInitialized(context) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 38.dp
                    )
                }
            }

            // Action Buttons inside Hero Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButton(
                    label = "Open Overlay on Bottom Screen",
                    backgroundColor = colors.accent,
                    textColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                    onClick = {
                        AssistantForegroundService.startService(
                            context,
                            AssistantForegroundService.ACTION_SHOW_OVERLAY
                        )
                    },
                    modifier = Modifier.weight(1.3f)
                )
                ActionButton(
                    label = "Quick Settings Tab",
                    backgroundColor = colors.fifth,
                    textColor = colors.primary,
                    onClick = {
                        AssistantForegroundService.startService(
                            context,
                            AssistantForegroundService.ACTION_SHOW_SETTINGS
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "Hide Overlay",
                    backgroundColor = colors.fifth,
                    textColor = colors.secondary,
                    onClick = {
                        AssistantForegroundService.startService(
                            context,
                            AssistantForegroundService.ACTION_HIDE_OVERLAY
                        )
                    },
                    modifier = Modifier.weight(0.8f)
                )
            }
        }

        // Overlay Preferences Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(1.dp, colors.fourth, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Overlay Behavior",
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.fifth)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirm Before Turning Off Bottom Screen",
                        color = colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Show a confirmation dialog with instructions on how to wake the display when selecting 'Just Upper Screen' in the overlay.",
                        color = colors.secondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Switch(
                    checked = showCloseScreenTip,
                    onCheckedChange = { enabled ->
                        repository.setNoMoreCloseBottomScreenTip(!enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                        checkedTrackColor = colors.accent
                    )
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsPane(
    repository: SystemSettingsRepository
) {
    val colors = LocalTccColors.current
    val themeMode by repository.themeMode.collectAsState()
    val customAccentArgb by repository.customAccentArgb.collectAsState()
    val syncedOption by repository.syncedMegingiardOption.collectAsState()
    val syncedAccentArgb by repository.syncedMegingiardAccentArgb.collectAsState()
    val isMegingiardAvailable by repository.isMegingiardProviderAvailable.collectAsState()
    val injectOsTheme by repository.injectOsTheme.collectAsState()

    val selectedThemeOption = AppThemeOption.fromId(themeMode)
    val customAccentColor = Color(customAccentArgb)
    val syncedAccentColor = syncedAccentArgb?.let { Color(it) } ?: syncedOption.previewAccent

    var lastMegingiardOptionId by rememberSaveable {
        mutableStateOf(
            if (selectedThemeOption in StandaloneMegingiardOptions) {
                selectedThemeOption.id
            } else {
                AppThemeOption.MEGINGIARD.id
            }
        )
    }
    var lastStockOptionId by rememberSaveable {
        mutableStateOf(
            if (selectedThemeOption in StockThemeOptions) {
                selectedThemeOption.id
            } else {
                AppThemeOption.STOCK_DARK.id
            }
        )
    }

    LaunchedEffect(selectedThemeOption) {
        if (selectedThemeOption in StandaloneMegingiardOptions) {
            lastMegingiardOptionId = selectedThemeOption.id
        } else if (selectedThemeOption in StockThemeOptions) {
            lastStockOptionId = selectedThemeOption.id
        }
    }

    val activeSourceMode = when (selectedThemeOption) {
        AppThemeOption.SYNC_MEGINGIARD -> ThemeSourceMode.SYNC_MEGINGIARD
        in StockThemeOptions -> ThemeSourceMode.STOCK
        else -> ThemeSourceMode.MEGINGIARD
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaneSectionHeader(
            title = "Appearance & Theme",
            subtitle = "Customize the assistant palette, synchronize live with Megingiard, or inject dynamic colors across Android 13."
        )

        // Theme Mode & Palettes Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(1.dp, colors.fourth, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 3-Way Mode Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.fifth)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeSourceMode.entries.forEach { mode ->
                    val isSelected = activeSourceMode == mode
                    val tabBg = if (isSelected) colors.accent else Color.Transparent
                    val tabText = if (isSelected) {
                        if (colors.isMegingiardStyle) colors.onAccent else Color.White
                    } else {
                        colors.primary
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(tabBg)
                            .clickable {
                                when (mode) {
                                    ThemeSourceMode.SYNC_MEGINGIARD -> {
                                        repository.setThemeMode(AppThemeOption.SYNC_MEGINGIARD.id)
                                    }
                                    ThemeSourceMode.MEGINGIARD -> {
                                        repository.setThemeMode(lastMegingiardOptionId)
                                    }
                                    ThemeSourceMode.STOCK -> {
                                        repository.setThemeMode(lastStockOptionId)
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = tabText,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Mode-Specific Content
            when (activeSourceMode) {
                ThemeSourceMode.SYNC_MEGINGIARD -> {
                    MegingiardSyncLiveCard(
                        syncedOption = syncedOption,
                        syncedAccentColor = syncedAccentColor,
                        isProviderAvailable = isMegingiardAvailable,
                        onRefreshSync = { repository.refreshMegingiardSyncedTheme() }
                    )
                }

                ThemeSourceMode.MEGINGIARD -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StandaloneMegingiardOptions.chunked(2).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowOptions.forEach { opt ->
                                    val isSelected = selectedThemeOption == opt
                                    val previewAccent = if (opt.supportsCustomAccent) {
                                        customAccentColor
                                    } else {
                                        opt.previewAccent
                                    }
                                    ThemePalettePreviewCard(
                                        title = opt.displayName,
                                        badgeText = if (opt.supportsCustomAccent) "Custom Accent" else null,
                                        backgroundColor = opt.previewBackground,
                                        surfaceColor = opt.previewSurface,
                                        accentColor = previewAccent,
                                        selected = isSelected,
                                        onClick = { repository.setThemeMode(opt.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowOptions.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        if (selectedThemeOption.supportsCustomAccent) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.fifth)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Custom Accent Color",
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MegingiardAccentPresets.forEach { preset ->
                                        val isSelectedSwatch = customAccentColor == preset
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(preset)
                                                .then(
                                                    if (isSelectedSwatch) {
                                                        Modifier.border(2.5.dp, colors.primary, CircleShape)
                                                    } else {
                                                        Modifier.border(
                                                            1.dp,
                                                            Color.White.copy(alpha = 0.18f),
                                                            CircleShape
                                                        )
                                                    }
                                                )
                                                .clickable {
                                                    repository.setCustomAccentArgb(preset.toArgb())
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelectedSwatch) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (colors.isMegingiardStyle) colors.onAccent else Color.White
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ThemeSourceMode.STOCK -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StockThemeOptions.forEach { opt ->
                            val isSelected = selectedThemeOption == opt
                            ThemePalettePreviewCard(
                                title = opt.shortTileLabel,
                                badgeText = "Stock",
                                backgroundColor = opt.previewBackground,
                                surfaceColor = opt.previewSurface,
                                accentColor = opt.previewAccent,
                                selected = isSelected,
                                onClick = { repository.setThemeMode(opt.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // OS Theme Injection Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(1.dp, colors.fourth, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Inject Theme Colors into OS",
                            color = colors.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (injectOsTheme) colors.accent.copy(alpha = 0.18f) else colors.fifth
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (injectOsTheme) "Active" else "Off",
                                color = if (injectOsTheme) colors.accent else colors.secondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Applies the active Megingiard palette to Android 13 SystemUI, Quick Settings, Settings, and Material You apps. Selecting 'Follow System (Stock)' or turning this off restores your original OS theme.",
                        color = colors.secondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

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
        }
    }
}

@Composable
private fun MegingiardSyncLiveCard(
    syncedOption: AppThemeOption,
    syncedAccentColor: Color,
    isProviderAvailable: Boolean,
    onRefreshSync: () -> Unit
) {
    val colors = LocalTccColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.fifth)
            .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isProviderAvailable) colors.lightning else colors.secondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isProviderAvailable) {
                            "Live Sync Connected"
                        } else {
                            "Megingiard Provider Not Detected (Using Fallback)"
                        },
                        color = colors.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = syncedOption.displayName,
                    color = colors.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            ActionButton(
                label = "Refresh Sync",
                backgroundColor = colors.panelBackground,
                textColor = colors.primary,
                onClick = onRefreshSync,
                height = 36.dp
            )
        }

        // Visual 3-Swatch Preview Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorSwatchPreviewPill(
                label = "Background",
                color = syncedOption.previewBackground,
                borderColor = colors.primary.copy(alpha = 0.15f),
                modifier = Modifier.weight(1f)
            )
            ColorSwatchPreviewPill(
                label = "Panel Surface",
                color = syncedOption.previewSurface,
                borderColor = colors.primary.copy(alpha = 0.15f),
                modifier = Modifier.weight(1f)
            )
            ColorSwatchPreviewPill(
                label = "Accent",
                color = syncedAccentColor,
                borderColor = syncedAccentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ColorSwatchPreviewPill(
    label: String,
    color: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.panelBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, borderColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = colors.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ThemePalettePreviewCard(
    title: String,
    badgeText: String?,
    backgroundColor: Color,
    surfaceColor: Color,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val cardBorder = if (selected) colors.accent else colors.fourth
    val cardBg = if (selected) colors.fifth else colors.primaryBackground.copy(alpha = 0.45f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(if (selected) 1.5.dp else 1.dp, cardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Mini Layered Surface & Accent Swatch
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(5.dp))
                        .background(surfaceColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        color = if (selected) colors.accent else colors.secondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        TccRadioCheckIndicator(
            selected = selected,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DiagnosticsSettingsPane(
    repository: SystemSettingsRepository,
    pServerAvailable: Boolean,
    helperConnected: Boolean,
    serviceRunning: Boolean,
    stockReplaced: Boolean
) {
    val context = LocalContext.current
    val colors = LocalTccColors.current
    val scope = rememberCoroutineScope()
    val themeMode by repository.themeMode.collectAsState()
    val selectedTheme = AppThemeOption.fromId(themeMode)
    var reconnectFeedback by remember { mutableStateOf(false) }

    val versionInfo = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName ?: "1.0"} (${pInfo.longVersionCode})"
        } catch (_: Throwable) {
            "1.0"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaneSectionHeader(
            title = "System & Diagnostics",
            subtitle = "Inspect privileged root bridge connections, re-grant system permissions, and view hardware topology."
        )

        // Privileged Services & Health Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(1.dp, colors.fourth, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Privileged Bridge & Service Health",
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.fifth)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                StatusIndicatorRow(
                    label = "Stock DualScreenAssistant Process",
                    active = stockReplaced,
                    statusText = if (stockReplaced) "Suppressed (Frozen)" else "Running (Stock Active)"
                )
            }

            ActionButton(
                label = if (reconnectFeedback) {
                    "Permissions Re-Granted & Bridge Refreshed"
                } else {
                    "Re-Grant Permissions & Reconnect Root Bridge"
                },
                backgroundColor = if (reconnectFeedback) colors.accent else colors.fifth,
                textColor = if (reconnectFeedback) {
                    if (colors.isMegingiardStyle) colors.onAccent else Color.White
                } else {
                    colors.primary
                },
                onClick = {
                    PrivilegedBridge.ensureInitialized(context)
                    reconnectFeedback = true
                    scope.launch {
                        delay(1800L)
                        reconnectFeedback = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Device & Application Info Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panelBackground)
                .border(1.dp, colors.fourth, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Application & Display Topology",
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            InfoKeyValueRow(label = "App Version", value = versionInfo)
            InfoKeyValueRow(label = "Package Name", value = context.packageName)
            InfoKeyValueRow(label = "Primary Display (Top)", value = "Display 0 • 1920×1080 (369 dpi)")
            InfoKeyValueRow(label = "Secondary Display (Bottom)", value = "Display 4 • 1240×1080 (369 dpi)")
            InfoKeyValueRow(label = "Active Theme", value = selectedTheme.displayName)
        }
    }
}

@Composable
private fun PaneSectionHeader(
    title: String,
    subtitle: String
) {
    val colors = LocalTccColors.current
    Column(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = colors.primary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = colors.secondary,
            fontSize = 13.sp
        )
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
            fontSize = 13.sp
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InfoKeyValueRow(
    label: String,
    value: String
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
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
