package com.stormpanda.betterdualscreenassistant.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.stormpanda.betterdualscreenassistant.R

private const val LUMINANCE_CONTRAST_THRESHOLD = 0.45f
private val DEFAULT_CONTRAST_DARK_TEXT = Color(0xFF121212)
const val DEFAULT_MEGINGIARD_ACCENT_ARGB: Int = 0xFFCC0000.toInt()

fun Color.contrastingContentColor(
    dark: Color = DEFAULT_CONTRAST_DARK_TEXT,
    light: Color = Color.White
): Color = if (luminance() > LUMINANCE_CONTRAST_THRESHOLD) dark else light

/**
 * Accent color presets adapted from Megingiard's `AppPalettes.accentPresets` (plus the default crimson).
 */
val MegingiardAccentPresets: List<Color> = listOf(
    Color(0xFFCC0000), // Megingiard Default Crimson
    Color(0xFFFF5252), // Red
    Color(0xFFFF7043), // Deep Orange
    Color(0xFFFFA726), // Orange
    Color(0xFFFFCA28), // Amber
    Color(0xFF66BB6A), // Green
    Color(0xFF26A69A), // Teal
    Color(0xFF29B6F6), // Light Blue
    Color(0xFF42A5F5), // Blue
    Color(0xFF7E57C2), // Deep Purple
    Color(0xFFEC407A)  // Pink
)

enum class AppThemeOption(
    val id: Int,
    val displayName: String,
    val shortTileLabel: String,
    val isMegingiardStyle: Boolean,
    val supportsCustomAccent: Boolean = false,
    val megingiardIpcName: String? = null,
    val previewBackground: Color = Color.Black,
    val previewSurface: Color = Color(0xFF161616),
    val previewAccent: Color = Color(0xFF8E84FF)
) {
    STOCK_SYSTEM(
        id = 1,
        displayName = "Follow System (Stock)",
        shortTileLabel = "Follow System",
        isMegingiardStyle = false,
        previewBackground = Color.Black,
        previewSurface = Color(0xFF161616),
        previewAccent = Color(0xFF8E84FF)
    ),
    STOCK_LIGHT(
        id = 3,
        displayName = "Light (Stock)",
        shortTileLabel = "Light",
        isMegingiardStyle = false,
        previewBackground = Color(0xFFDEDBDB),
        previewSurface = Color(0xFFE7E5E5),
        previewAccent = Color(0xFF8E84FF)
    ),
    STOCK_DARK(
        id = 4,
        displayName = "Dark (Stock)",
        shortTileLabel = "Dark",
        isMegingiardStyle = false,
        previewBackground = Color.Black,
        previewSurface = Color(0xFF161616),
        previewAccent = Color(0xFF8E84FF)
    ),
    SYNC_MEGINGIARD(
        id = 100,
        displayName = "Sync with Megingiard",
        shortTileLabel = "Megingiard Sync",
        isMegingiardStyle = true,
        previewBackground = Color(0xFF040C08),
        previewSurface = Color(0xFF06140C),
        previewAccent = Color(0xFFE5B842)
    ),
    MEGINGIARD_DARK(
        id = 101,
        displayName = "Megingiard Dark",
        shortTileLabel = "MG Dark",
        isMegingiardStyle = true,
        supportsCustomAccent = true,
        megingiardIpcName = "DARK",
        previewBackground = Color(0xFF121212),
        previewSurface = Color(0xFF1C1C1E),
        previewAccent = Color(0xFFCC0000)
    ),
    MEGINGIARD_DARK_OLED(
        id = 102,
        displayName = "Megingiard Dark OLED",
        shortTileLabel = "MG OLED",
        isMegingiardStyle = true,
        supportsCustomAccent = true,
        megingiardIpcName = "DARK_OLED",
        previewBackground = Color.Black,
        previewSurface = Color(0xFF161618),
        previewAccent = Color(0xFFCC0000)
    ),
    MEGINGIARD(
        id = 103,
        displayName = "Megingiard (Norse Forest)",
        shortTileLabel = "Megingiard",
        isMegingiardStyle = true,
        megingiardIpcName = "MEGINGIARD",
        previewBackground = Color(0xFF040C08),
        previewSurface = Color(0xFF06140C),
        previewAccent = Color(0xFFE5B842)
    ),
    MJOLNIR(
        id = 104,
        displayName = "Mjölnir Steel",
        shortTileLabel = "Mjölnir",
        isMegingiardStyle = true,
        megingiardIpcName = "MJOLNIR",
        previewBackground = Color(0xFF101418),
        previewSurface = Color(0xFF161E26),
        previewAccent = Color(0xFF00E5FF)
    ),
    VALHALLA(
        id = 105,
        displayName = "Valhalla Sunset",
        shortTileLabel = "Valhalla",
        isMegingiardStyle = true,
        megingiardIpcName = "VALHALLA",
        previewBackground = Color(0xFF140E0A),
        previewSurface = Color(0xFF1E1610),
        previewAccent = Color(0xFFFFA726)
    ),
    AURORA(
        id = 106,
        displayName = "Aurora Borealis",
        shortTileLabel = "Aurora",
        isMegingiardStyle = true,
        megingiardIpcName = "AURORA",
        previewBackground = Color(0xFF0A0A14),
        previewSurface = Color(0xFF121222),
        previewAccent = Color(0xFF00F5D4)
    ),
    RETRO_PHOSPHOR(
        id = 107,
        displayName = "Retro Phosphor",
        shortTileLabel = "Retro Phosphor",
        isMegingiardStyle = true,
        megingiardIpcName = "RETRO_PHOSPHOR",
        previewBackground = Color(0xFF141712),
        previewSurface = Color(0xFF1C2219),
        previewAccent = Color(0xFF8BAC0F)
    ),
    ROYAL_ASGARD(
        id = 108,
        displayName = "Royal Asgard",
        shortTileLabel = "Royal Asgard",
        isMegingiardStyle = true,
        megingiardIpcName = "ROYAL_ASGARD",
        previewBackground = Color.Black,
        previewSurface = Color(0xFF12110E),
        previewAccent = Color(0xFFFFD700)
    );

    companion object {
        fun fromId(id: Int): AppThemeOption {
            if (id == 2) return STOCK_DARK
            return entries.firstOrNull { it.id == id } ?: STOCK_DARK
        }

        fun fromMegingiardIpcName(name: String?): AppThemeOption {
            if (name.isNullOrBlank()) return MEGINGIARD
            return entries.firstOrNull { it.megingiardIpcName == name } ?: MEGINGIARD
        }
    }
}

@Immutable
data class TccColorPalette(
    val isDark: Boolean,
    val isMegingiardStyle: Boolean,
    val primaryBackground: Color,
    val panelBackground: Color,
    val panelBackgroundSelected: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val fourth: Color,
    val fifth: Color,
    val dialogBackground: Color,
    val accent: Color,
    val onAccent: Color,
    val sliderFill: Color,
    val lightning: Color,
    val error: Color = Color(0xFFF64646)
)

val LightTccPalette = TccColorPalette(
    isDark = false,
    isMegingiardStyle = false,
    primaryBackground = Color(0xFFDEDBDB),
    panelBackground = Color(0xFFE7E5E5),
    panelBackgroundSelected = Color(0xFFD5D5D5),
    primary = Color(0xFF434343),
    secondary = Color(0xFF787777),
    tertiary = Color(0xFFD0D0D0),
    fourth = Color(0xFFEBEBEB),
    fifth = Color(0xFFFDFDFD),
    dialogBackground = Color(0xFFE7E7E7),
    accent = Color(0xFF8E84FF),
    onAccent = Color.White,
    sliderFill = Color(0xFFD0D0D0),
    lightning = Color(0xFF04AD29)
)

val PureBlackTccPalette = TccColorPalette(
    isDark = true,
    isMegingiardStyle = false,
    primaryBackground = Color(0xFF000000),
    panelBackground = Color(0xFF161616),
    panelBackgroundSelected = Color(0xFF3A3A3A),
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFFE0E0E0),
    tertiary = Color(0xFFB0B0B0),
    fourth = Color(0xFF282828),
    fifth = Color(0xFF282828),
    dialogBackground = Color(0xFF1E1E1E),
    accent = Color(0xFF8E84FF),
    onAccent = Color.White,
    sliderFill = Color(0xFFB0B0B0),
    lightning = Color(0xFF03C82E)
)

private fun buildMegingiardPalette(
    appBackground: Color,
    panelSurface: Color,
    elevatedSurface: Color,
    pressedSurface: Color,
    onSurface: Color,
    onSurfaceSecondary: Color,
    accent: Color,
    error: Color
): TccColorPalette {
    val resolvedOnAccent = accent.contrastingContentColor(
        dark = appBackground,
        light = Color.White
    )
    return TccColorPalette(
        isDark = true,
        isMegingiardStyle = true,
        primaryBackground = appBackground,
        panelBackground = panelSurface,
        panelBackgroundSelected = pressedSurface,
        primary = onSurface,
        secondary = onSurfaceSecondary,
        tertiary = onSurfaceSecondary,
        fourth = elevatedSurface,
        fifth = elevatedSurface,
        dialogBackground = elevatedSurface,
        accent = accent,
        onAccent = resolvedOnAccent,
        sliderFill = accent,
        lightning = accent,
        error = error
    )
}

private fun megingiardDarkPalette(accent: Color): TccColorPalette = buildMegingiardPalette(
    appBackground = Color(0xFF121212),
    panelSurface = Color(0xFF1C1C1E),
    elevatedSurface = Color(0xFF2C2C2E),
    pressedSurface = Color(0xFF48484A),
    onSurface = Color.White,
    onSurfaceSecondary = Color.White.copy(alpha = 0.6f),
    accent = accent,
    error = Color(0xFFCF6679)
)

private fun megingiardDarkOledPalette(accent: Color): TccColorPalette {
    val oledText = Color(0xFFE3E3E8)
    // Uses surfaceVariant (#161618) for borderless panel cards so they remain distinct on pitch-black (#000000)
    return buildMegingiardPalette(
        appBackground = Color.Black,
        panelSurface = Color(0xFF161618),
        elevatedSurface = Color(0xFF26262A),
        pressedSurface = Color(0xFF323235),
        onSurface = oledText,
        onSurfaceSecondary = oledText.copy(alpha = 0.6f),
        accent = accent,
        error = Color(0xFFCF6679)
    )
}

private val MegingiardNorseForestPalette = buildMegingiardPalette(
    appBackground = Color(0xFF040C08),
    panelSurface = Color(0xFF06140C),
    elevatedSurface = Color(0xFF0B2015),
    pressedSurface = Color(0xFF102B1D),
    onSurface = Color(0xFFE2EBE5),
    onSurfaceSecondary = Color(0xFF7BA68C),
    accent = Color(0xFFE5B842),
    error = Color(0xFFD94336)
)

private val MjolnirSteelPalette = buildMegingiardPalette(
    appBackground = Color(0xFF101418),
    panelSurface = Color(0xFF161E26),
    elevatedSurface = Color(0xFF1F2B36),
    pressedSurface = Color(0xFF2A3A48),
    onSurface = Color(0xFFE2EBF2),
    onSurfaceSecondary = Color(0xFF8FA3B5),
    accent = Color(0xFF00E5FF),
    error = Color(0xFFE53935)
)

private val ValhallaSunsetPalette = buildMegingiardPalette(
    appBackground = Color(0xFF140E0A),
    panelSurface = Color(0xFF1E1610),
    elevatedSurface = Color(0xFF2B2018),
    pressedSurface = Color(0xFF3B2C21),
    onSurface = Color(0xFFF4E8D1),
    onSurfaceSecondary = Color(0xFFBCAAA4),
    accent = Color(0xFFFFA726),
    error = Color(0xFFD84315)
)

private val AuroraBorealisPalette = buildMegingiardPalette(
    appBackground = Color(0xFF0A0A14),
    panelSurface = Color(0xFF121222),
    elevatedSurface = Color(0xFF1B1B32),
    pressedSurface = Color(0xFF262645),
    onSurface = Color(0xFFE6E6FA),
    onSurfaceSecondary = Color(0xFF9FA8DA),
    accent = Color(0xFF00F5D4),
    error = Color(0xFFFF5252)
)

private val RetroPhosphorPalette = buildMegingiardPalette(
    appBackground = Color(0xFF141712),
    panelSurface = Color(0xFF1C2219),
    elevatedSurface = Color(0xFF263022),
    pressedSurface = Color(0xFF33402E),
    onSurface = Color(0xFFC0D890),
    onSurfaceSecondary = Color(0xFF708850),
    accent = Color(0xFF8BAC0F),
    error = Color(0xFFE57373)
)

private val RoyalAsgardPalette = buildMegingiardPalette(
    appBackground = Color.Black,
    panelSurface = Color(0xFF12110E),
    elevatedSurface = Color(0xFF1C1A16),
    pressedSurface = Color(0xFF2A2721),
    onSurface = Color(0xFFF7F3E9),
    onSurfaceSecondary = Color(0xFFC5BCA8),
    accent = Color(0xFFFFD700),
    error = Color(0xFFD32F2F)
)

val LocalTccColors = staticCompositionLocalOf { PureBlackTccPalette }

fun resolveTccPalette(
    option: AppThemeOption,
    systemDark: Boolean,
    customAccentArgb: Int = DEFAULT_MEGINGIARD_ACCENT_ARGB,
    syncedOption: AppThemeOption = AppThemeOption.MEGINGIARD,
    syncedAccentArgb: Int? = null
): TccColorPalette {
    val effectiveOption = if (option == AppThemeOption.SYNC_MEGINGIARD) {
        syncedOption
    } else {
        option
    }
    val effectiveAccent = if (option == AppThemeOption.SYNC_MEGINGIARD && syncedAccentArgb != null) {
        Color(syncedAccentArgb)
    } else {
        Color(customAccentArgb)
    }

    return when (effectiveOption) {
        AppThemeOption.STOCK_SYSTEM -> if (systemDark) PureBlackTccPalette else LightTccPalette
        AppThemeOption.STOCK_LIGHT -> LightTccPalette
        AppThemeOption.STOCK_DARK -> PureBlackTccPalette
        AppThemeOption.MEGINGIARD_DARK -> megingiardDarkPalette(effectiveAccent)
        AppThemeOption.MEGINGIARD_DARK_OLED -> megingiardDarkOledPalette(effectiveAccent)
        AppThemeOption.MEGINGIARD, AppThemeOption.SYNC_MEGINGIARD -> MegingiardNorseForestPalette
        AppThemeOption.MJOLNIR -> MjolnirSteelPalette
        AppThemeOption.VALHALLA -> ValhallaSunsetPalette
        AppThemeOption.AURORA -> AuroraBorealisPalette
        AppThemeOption.RETRO_PHOSPHOR -> RetroPhosphorPalette
        AppThemeOption.ROYAL_ASGARD -> RoyalAsgardPalette
    }
}

/**
 * Resolves the TCC or Megingiard theme palette based on `themeMode` ID (`AppThemeOption.id`).
 */
@Composable
fun TccTheme(
    themeMode: Int,
    customAccentArgb: Int = DEFAULT_MEGINGIARD_ACCENT_ARGB,
    syncedMegingiardOption: AppThemeOption = AppThemeOption.MEGINGIARD,
    syncedMegingiardAccentArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val option = AppThemeOption.fromId(themeMode)
    val palette = resolveTccPalette(
        option = option,
        systemDark = systemDark,
        customAccentArgb = customAccentArgb,
        syncedOption = syncedMegingiardOption,
        syncedAccentArgb = syncedMegingiardAccentArgb
    )

    CompositionLocalProvider(LocalTccColors provides palette) {
        content()
    }
}

/**
 * Maps a light drawable resource to its `_black` dark-mode equivalent when a dark/Megingiard
 * theme is active, matching `r0.C0469a.c(int)` from the original DualScreenAssistant.
 */
@Composable
@DrawableRes
fun tccDrawable(@DrawableRes lightResId: Int): Int {
    val isDark = LocalTccColors.current.isDark
    if (!isDark) return lightResId
    return when (lightResId) {
        R.drawable.bg_120_frame_rate_mode -> R.drawable.bg_120_frame_rate_mode_black
        R.drawable.bg_60_frame_rate_mode -> R.drawable.bg_60_frame_rate_mode_black
        R.drawable.bg_horizontal_split_line -> R.drawable.bg_horizontal_split_line_black
        R.drawable.bg_vertical_split_line -> R.drawable.bg_vertical_split_line_black
        R.drawable.ic_app_manage -> R.drawable.ic_app_manage_black
        R.drawable.ic_ban_on_use -> R.drawable.ic_ban_on_use_black
        R.drawable.ic_bluetooth -> R.drawable.ic_bluetooth_black
        R.drawable.ic_brightness_0 -> R.drawable.ic_brightness_0_black
        R.drawable.ic_brightness_1 -> R.drawable.ic_brightness_1_black
        R.drawable.ic_brightness_2 -> R.drawable.ic_brightness_2_black
        R.drawable.ic_close_screen -> R.drawable.ic_close_screen_black
        R.drawable.ic_controller__xbox_style -> R.drawable.ic_controller__xbox_style_black
        R.drawable.ic_controller_odin_style -> R.drawable.ic_controller_odin_style_black
        R.drawable.ic_dual_screen_display -> R.drawable.ic_dual_screen_display_black
        R.drawable.ic_fan_custom_mode -> R.drawable.ic_fan_custom_mode_black
        R.drawable.ic_fan_custom_progress -> R.drawable.ic_fan_custom_progress_black
        R.drawable.ic_fan_normal -> R.drawable.ic_fan_normal_black
        R.drawable.ic_fan_quiet_mode -> R.drawable.ic_fan_quiet_mode_black
        R.drawable.ic_fan_smart_mode -> R.drawable.ic_fan_smart_mode_black
        R.drawable.ic_fan_sport_mode -> R.drawable.ic_fan_sport_mode_black
        R.drawable.ic_headset -> R.drawable.ic_headset_black
        R.drawable.ic_height_performance_mode -> R.drawable.ic_height_performance_mode_black
        R.drawable.ic_just_screen_above -> R.drawable.ic_just_screen_above_black
        R.drawable.ic_just_screen_below -> R.drawable.ic_just_screen_below_black
        R.drawable.ic_landscape -> R.drawable.ic_landscape_black
        R.drawable.ic_normal_mode -> R.drawable.ic_normal_mode_black
        R.drawable.ic_performance_mode -> R.drawable.ic_performance_mode_black
        R.drawable.ic_qs_ambient_light -> R.drawable.ic_qs_ambient_light_black
        R.drawable.ic_qs_charging_separation -> R.drawable.ic_qs_charging_separation_black
        R.drawable.ic_qs_clean -> R.drawable.ic_qs_clean_black
        R.drawable.ic_qs_controller_style -> R.drawable.ic_qs_controller_style_black
        R.drawable.ic_qs_fan_control -> R.drawable.ic_qs_fan_control_black
        R.drawable.ic_qs_keyboard -> R.drawable.ic_qs_keyboard_black
        R.drawable.ic_qs_performance -> R.drawable.ic_qs_performance_black
        R.drawable.ic_qs_theme_mode -> R.drawable.ic_qs_theme_mode_black
        R.drawable.ic_qs_vibrate -> R.drawable.ic_qs_vibrate_black
        R.drawable.ic_screen_control -> R.drawable.ic_screen_control_black
        R.drawable.ic_screen_focus_lock_lower -> R.drawable.ic_screen_focus_lock_lower_black
        R.drawable.ic_screen_focus_lock_mode -> R.drawable.ic_screen_focus_lock_mode_black
        R.drawable.ic_screen_focus_lock_switchable -> R.drawable.ic_screen_focus_lock_switchable_black
        R.drawable.ic_screen_focus_lock_upper -> R.drawable.ic_screen_focus_lock_upper_black
        R.drawable.ic_status_wifi_0 -> R.drawable.ic_status_wifi_0_black
        R.drawable.ic_status_wifi_1 -> R.drawable.ic_status_wifi_1_black
        R.drawable.ic_status_wifi_2 -> R.drawable.ic_status_wifi_2_black
        R.drawable.ic_status_wifi_3 -> R.drawable.ic_status_wifi_3_black
        R.drawable.ic_system_set -> R.drawable.ic_system_set_black
        R.drawable.ic_task_manage -> R.drawable.ic_task_manage_black
        R.drawable.ic_temp -> R.drawable.ic_temp_black
        R.drawable.ic_tip -> R.drawable.ic_tip_black
        R.drawable.ic_volume_0 -> R.drawable.ic_volume_0_black
        R.drawable.ic_volume_1 -> R.drawable.ic_volume_1_black
        R.drawable.ic_volume_2 -> R.drawable.ic_volume_2_black
        R.drawable.ic_volume_3 -> R.drawable.ic_volume_3_black
        else -> lightResId
    }
}
