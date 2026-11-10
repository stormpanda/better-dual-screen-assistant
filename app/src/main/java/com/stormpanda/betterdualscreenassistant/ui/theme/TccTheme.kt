package com.stormpanda.betterdualscreenassistant.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile

private const val LUMINANCE_CONTRAST_THRESHOLD = 0.45f
private val DEFAULT_CONTRAST_DARK_TEXT = Color(0xFF121212)
const val DEFAULT_MEGINGIARD_ACCENT_ARGB: Int = 0xFFCC0000.toInt()
val RetroidPixelOrangeSwatch: Color = Color(0xFFFF760A)
val RetroidPixelTealSwatch: Color = Color(0xFF03DAC5)

enum class TccUiLook {
    AYN,
    RETROID
}

enum class TccUiLookPreference(
    val id: Int,
    val displayName: String,
    val shortLabel: String
) {
    AUTO(0, "Auto", "Auto"),
    AYN(1, "AYN Smooth", "AYN"),
    RETROID(2, "Retroid Pixel", "Retroid");

    fun resolve(profile: DeviceProfile): TccUiLook = when (this) {
        AUTO -> when (profile.defaultUiLookPreferenceId) {
            RETROID.id -> TccUiLook.RETROID
            else -> TccUiLook.AYN
        }
        AYN -> TccUiLook.AYN
        RETROID -> TccUiLook.RETROID
    }

    fun displayNameFor(profile: DeviceProfile): String = when (this) {
        AUTO -> {
            val nativePref = if (profile.defaultUiLookPreferenceId == RETROID.id) RETROID else AYN
            "Auto (${nativePref.displayName})"
        }
        else -> displayName
    }

    companion object {
        fun fromId(id: Int): TccUiLookPreference =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

val LocalTccUiLook = compositionLocalOf { TccUiLook.AYN }

val TccPixelDisplayFontFamily: FontFamily = FontFamily(
    Font(R.font.tcc_pixel_display, FontWeight.Normal),
    Font(R.font.tcc_pixel_display, FontWeight.Medium),
    Font(R.font.tcc_pixel_display, FontWeight.SemiBold),
    Font(R.font.tcc_pixel_display, FontWeight.Bold)
)

val TccPixelLabelFontFamily: FontFamily = FontFamily(
    Font(R.font.tcc_pixel_label, FontWeight.Normal),
    Font(R.font.tcc_pixel_label, FontWeight.Medium),
    Font(R.font.tcc_pixel_label, FontWeight.SemiBold),
    Font(R.font.tcc_pixel_label, FontWeight.Bold)
)

@Composable
fun tccFontFamily(): FontFamily? =
    if (LocalTccUiLook.current == TccUiLook.RETROID) TccPixelLabelFontFamily else null

@Composable
fun tccDisplayFontFamily(): FontFamily? =
    if (LocalTccUiLook.current == TccUiLook.RETROID) TccPixelDisplayFontFamily else null

@Composable
fun tccTextStyle(): TextStyle =
    if (LocalTccUiLook.current == TccUiLook.RETROID) {
        TextStyle(
            fontFamily = TccPixelLabelFontFamily,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    } else {
        TextStyle.Default
    }

@Composable
fun tccDisplayTextStyle(): TextStyle =
    if (LocalTccUiLook.current == TccUiLook.RETROID) {
        TextStyle(
            fontFamily = TccPixelDisplayFontFamily,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    } else {
        TextStyle.Default
    }

fun Color.contrastingContentColor(
    dark: Color = DEFAULT_CONTRAST_DARK_TEXT,
    light: Color = Color.White
): Color = if (luminance() > LUMINANCE_CONTRAST_THRESHOLD) dark else light

fun TccColorPalette.retroidContentOnAccent(): Color =
    if (!isMegingiardStyle) onAccent else if (accent.luminance() > 0.30f) Color.Black else Color.White

/**
 * Accent color presets adapted from Megingiard's `AppPalettes.accentPresets` (plus the default crimson).
 */
val MegingiardAccentPresets: List<Color> = listOf(
    Color(0xFFCC0000), // Megingiard Default Crimson
    Color(0xFFFF5252), // Red
    RetroidPixelOrangeSwatch, // Retroid Pixel Orange (#FF760A)
    Color(0xFFFF7043), // Deep Orange
    Color(0xFFFFA726), // Orange
    Color(0xFFFFCA28), // Amber
    Color(0xFF66BB6A), // Green
    Color(0xFF26A69A), // Teal
    RetroidPixelTealSwatch, // Retroid Pixel Teal (#03DAC5)
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
        displayName = "Dark",
        shortTileLabel = "Dark",
        isMegingiardStyle = true,
        supportsCustomAccent = true,
        megingiardIpcName = "DARK",
        previewBackground = Color(0xFF121212),
        previewSurface = Color(0xFF1C1C1E),
        previewAccent = Color(0xFFCC0000)
    ),
    MEGINGIARD_DARK_OLED(
        id = 102,
        displayName = "Dark OLED",
        shortTileLabel = "Dark OLED",
        isMegingiardStyle = true,
        supportsCustomAccent = true,
        megingiardIpcName = "DARK_OLED",
        previewBackground = Color.Black,
        previewSurface = Color(0xFF161618),
        previewAccent = Color(0xFFCC0000)
    ),
    MEGINGIARD(
        id = 103,
        displayName = "Norse Forest",
        shortTileLabel = "Norse Forest",
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

val RpPixelLightTccPalette = TccColorPalette(
    isDark = false,
    isMegingiardStyle = false,
    primaryBackground = Color(0xFFE6E6E6),
    panelBackground = Color(0xFFFBFBFB),
    panelBackgroundSelected = Color(0xFFD5D5D5),
    primary = Color(0xFF1A1A1A),
    secondary = Color(0xFF6E6E6E),
    tertiary = Color(0xFF848484),
    fourth = Color(0xFFE0E0E0),
    fifth = Color(0xFFEBEBEB),
    dialogBackground = Color(0xFFF5F5F5),
    accent = RetroidPixelOrangeSwatch,
    onAccent = Color.Black,
    sliderFill = Color(0xFFCFCFCF),
    lightning = Color(0xFF04AD29)
)

val RpPixelPureBlackTccPalette = TccColorPalette(
    isDark = true,
    isMegingiardStyle = false,
    primaryBackground = Color(0xFF141414),
    panelBackground = Color(0xFF000000),
    panelBackgroundSelected = Color(0xFF3A3A3A),
    primary = Color(0xFFF5F5F5),
    secondary = Color(0xFF8C8C8C),
    tertiary = Color(0xFF757575),
    fourth = Color(0xFF2E2E2E),
    fifth = Color(0xFF202020),
    dialogBackground = Color(0xFF141414),
    accent = RetroidPixelOrangeSwatch,
    onAccent = Color.Black,
    sliderFill = Color(0xFF303030),
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

val LocalTccColors = compositionLocalOf { PureBlackTccPalette }

fun resolveTccPalette(
    option: AppThemeOption,
    systemDark: Boolean,
    customAccentArgb: Int = DEFAULT_MEGINGIARD_ACCENT_ARGB,
    syncedOption: AppThemeOption = AppThemeOption.MEGINGIARD,
    syncedAccentArgb: Int? = null,
    uiLook: TccUiLook = TccUiLook.AYN
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
    val stockDarkPalette = if (uiLook == TccUiLook.RETROID) RpPixelPureBlackTccPalette else PureBlackTccPalette
    val stockLightPalette = if (uiLook == TccUiLook.RETROID) RpPixelLightTccPalette else LightTccPalette

    return when (effectiveOption) {
        AppThemeOption.STOCK_SYSTEM -> if (systemDark) stockDarkPalette else stockLightPalette
        AppThemeOption.STOCK_LIGHT -> stockLightPalette
        AppThemeOption.STOCK_DARK -> stockDarkPalette
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

private fun Color.toOpaqueColor(backdrop: Color): Color {
    val opaqueBackdrop = backdrop.copy(alpha = 1f)
    return if (alpha >= 0.999f) copy(alpha = 1f) else compositeOver(opaqueBackdrop).copy(alpha = 1f)
}

private fun Color.toOpaqueArgb(backdrop: Color): Int {
    return toOpaqueColor(backdrop).toArgb() or (0xFF shl 24)
}

/**
 * Builds the Android 12–16 dynamic color resource entries for a `FabricatedOverlay` targeting
 * `"android"` so SystemUI, Quick Settings, Settings, and Material You apps adopt the exact
 * [palette] surfaces, text, and accent colors across both Android 13 (`system_neutral{1,2}_*`,
 * `system_accent{1,2,3}_*`) and Android 14–16 (`com.android.systemui:dynamic` semantic tokens).
 */
fun buildOsDynamicColorOverlayEntries(palette: TccColorPalette): Pair<Array<String>, IntArray> {
    val bg = palette.primaryBackground.copy(alpha = 1f)
    val panel = palette.panelBackground.toOpaqueColor(bg)
    val elevated = palette.fourth.toOpaqueColor(bg)
    val pressed = palette.panelBackgroundSelected.toOpaqueColor(bg)
    val textPrimary = palette.primary.toOpaqueColor(bg)
    val textSecondary = palette.secondary.toOpaqueColor(bg)
    val accent = palette.accent.toOpaqueColor(bg)
    val errorColor = palette.error.toOpaqueColor(bg)

    val cardSurface = lerp(panel, elevated, 0.5f)
    val primaryAccent = if (accent.luminance() < 0.12f) {
        lerp(accent, Color.White, 0.24f)
    } else {
        accent
    }
    val activeAccentTile = if (primaryAccent.luminance() < 0.25f) {
        lerp(primaryAccent, Color.White, 0.22f)
    } else {
        primaryAccent
    }
    val secBase = lerp(textSecondary, primaryAccent, 0.45f)
    val tertBase = lerp(textPrimary, primaryAccent, 0.65f)

    // Tonal step helpers (used for both tonal swatches and Android 14–16 semantic tokens)
    val n1_10 = lerp(textPrimary, Color.White, 0.85f)
    val n1_50 = lerp(textPrimary, Color.White, 0.45f)
    val n1_100 = textPrimary
    val n1_200 = lerp(textSecondary, textPrimary, 0.55f)
    val n1_300 = textSecondary
    val n1_400 = lerp(pressed, textSecondary, 0.60f)
    val n1_500 = lerp(pressed, textSecondary, 0.30f)
    val n1_600 = pressed
    val n1_700 = elevated
    val n1_800 = cardSurface
    val n1_900 = bg

    val n2_10 = lerp(textPrimary, Color.White, 0.85f)
    val n2_50 = lerp(textPrimary, Color.White, 0.45f)
    val n2_100 = textPrimary
    val n2_200 = textSecondary
    val n2_300 = lerp(pressed, textSecondary, 0.82f)
    val n2_400 = lerp(pressed, textSecondary, 0.60f)
    val n2_500 = lerp(pressed, textSecondary, 0.32f)
    val n2_600 = pressed
    val n2_700 = elevated
    val n2_800 = panel
    val n2_900 = bg

    // Blend dark accent containers against panel (rather than pure black OLED bg) so Oklab lerp
    // preserves visible chroma for dark containers (700/800/900).
    val accentDarkAnchor = if (panel.luminance() > 0.005f) panel else Color(0xFF141418)
    val a1_10 = lerp(primaryAccent, Color.White, 0.85f)
    val a1_50 = lerp(primaryAccent, Color.White, 0.55f)
    val a1_100 = activeAccentTile
    val a1_200 = primaryAccent
    val a1_300 = lerp(primaryAccent, accentDarkAnchor, 0.16f)
    val a1_400 = lerp(primaryAccent, accentDarkAnchor, 0.30f)
    val a1_500 = lerp(primaryAccent, accentDarkAnchor, 0.44f)
    val a1_600 = lerp(primaryAccent, accentDarkAnchor, 0.56f)
    val a1_700 = lerp(accentDarkAnchor, activeAccentTile, 0.36f)
    val a1_800 = lerp(accentDarkAnchor, primaryAccent, 0.24f)
    val a1_900 = lerp(bg, primaryAccent, 0.14f)

    val a2_10 = lerp(secBase, Color.White, 0.85f)
    val a2_50 = lerp(secBase, Color.White, 0.55f)
    val a2_100 = lerp(secBase, Color.White, 0.25f)
    val a2_200 = secBase
    val a2_300 = lerp(secBase, accentDarkAnchor, 0.18f)
    val a2_400 = lerp(secBase, accentDarkAnchor, 0.35f)
    val a2_500 = lerp(secBase, accentDarkAnchor, 0.50f)
    val a2_600 = lerp(secBase, accentDarkAnchor, 0.62f)
    val a2_700 = lerp(pressed, primaryAccent, 0.25f)
    val a2_800 = lerp(accentDarkAnchor, primaryAccent, 0.18f)
    val a2_900 = lerp(bg, primaryAccent, 0.10f)

    val a3_10 = lerp(tertBase, Color.White, 0.85f)
    val a3_50 = lerp(tertBase, Color.White, 0.55f)
    val a3_100 = lerp(tertBase, Color.White, 0.25f)
    val a3_200 = tertBase
    val a3_300 = lerp(tertBase, accentDarkAnchor, 0.18f)
    val a3_400 = lerp(tertBase, accentDarkAnchor, 0.35f)
    val a3_500 = lerp(tertBase, accentDarkAnchor, 0.50f)
    val a3_600 = lerp(tertBase, accentDarkAnchor, 0.62f)
    val a3_700 = lerp(pressed, tertBase, 0.25f)
    val a3_800 = lerp(accentDarkAnchor, tertBase, 0.18f)
    val a3_900 = lerp(bg, tertBase, 0.10f)

    val entries = LinkedHashMap<String, Int>(240)
    fun putColor(name: String, color: Color) {
        entries["android:color/$name"] = color.toOpaqueArgb(bg)
    }

    // 1. Tonal Palettes (Android 12–16 system_neutral{1,2}_* & system_accent{1,2,3}_*)
    putColor("system_neutral1_0", Color.White)
    putColor("system_neutral1_10", n1_10)
    putColor("system_neutral1_50", n1_50)
    putColor("system_neutral1_100", n1_100)
    putColor("system_neutral1_200", n1_200)
    putColor("system_neutral1_300", n1_300)
    putColor("system_neutral1_400", n1_400)
    putColor("system_neutral1_500", n1_500)
    putColor("system_neutral1_600", n1_600)
    putColor("system_neutral1_700", n1_700)
    putColor("system_neutral1_800", n1_800)
    putColor("system_neutral1_900", n1_900)
    putColor("system_neutral1_1000", Color.Black)

    putColor("system_neutral2_0", Color.White)
    putColor("system_neutral2_10", n2_10)
    putColor("system_neutral2_50", n2_50)
    putColor("system_neutral2_100", n2_100)
    putColor("system_neutral2_200", n2_200)
    putColor("system_neutral2_300", n2_300)
    putColor("system_neutral2_400", n2_400)
    putColor("system_neutral2_500", n2_500)
    putColor("system_neutral2_600", n2_600)
    putColor("system_neutral2_700", n2_700)
    putColor("system_neutral2_800", n2_800)
    putColor("system_neutral2_900", n2_900)
    putColor("system_neutral2_1000", Color.Black)

    putColor("system_accent1_0", Color.White)
    putColor("system_accent1_10", a1_10)
    putColor("system_accent1_50", a1_50)
    putColor("system_accent1_100", a1_100)
    putColor("system_accent1_200", a1_200)
    putColor("system_accent1_300", a1_300)
    putColor("system_accent1_400", a1_400)
    putColor("system_accent1_500", a1_500)
    putColor("system_accent1_600", a1_600)
    putColor("system_accent1_700", a1_700)
    putColor("system_accent1_800", a1_800)
    putColor("system_accent1_900", a1_900)
    putColor("system_accent1_1000", Color.Black)

    putColor("system_accent2_0", Color.White)
    putColor("system_accent2_10", a2_10)
    putColor("system_accent2_50", a2_50)
    putColor("system_accent2_100", a2_100)
    putColor("system_accent2_200", a2_200)
    putColor("system_accent2_300", a2_300)
    putColor("system_accent2_400", a2_400)
    putColor("system_accent2_500", a2_500)
    putColor("system_accent2_600", a2_600)
    putColor("system_accent2_700", a2_700)
    putColor("system_accent2_800", a2_800)
    putColor("system_accent2_900", a2_900)
    putColor("system_accent2_1000", Color.Black)

    putColor("system_accent3_0", Color.White)
    putColor("system_accent3_10", a3_10)
    putColor("system_accent3_50", a3_50)
    putColor("system_accent3_100", a3_100)
    putColor("system_accent3_200", a3_200)
    putColor("system_accent3_300", a3_300)
    putColor("system_accent3_400", a3_400)
    putColor("system_accent3_500", a3_500)
    putColor("system_accent3_600", a3_600)
    putColor("system_accent3_700", a3_700)
    putColor("system_accent3_800", a3_800)
    putColor("system_accent3_900", a3_900)
    putColor("system_accent3_1000", Color.Black)

    // 2. Android 14–16 Semantic Dynamic Color Tokens (com.android.systemui:dynamic)
    val onPrimaryDark = primaryAccent.contrastingContentColor(
        dark = lerp(bg, primaryAccent, 0.16f),
        light = Color.White
    )
    val onSecondaryDark = secBase.contrastingContentColor(
        dark = lerp(bg, secBase, 0.16f),
        light = Color.White
    )
    val onTertiaryDark = tertBase.contrastingContentColor(
        dark = lerp(bg, tertBase, 0.16f),
        light = Color.White
    )
    val onShadeActive = activeAccentTile.contrastingContentColor(
        dark = lerp(bg, Color.Black, 0.5f),
        light = Color.White
    )
    val onShadeActiveVariant = if (activeAccentTile.luminance() > LUMINANCE_CONTRAST_THRESHOLD) {
        lerp(bg, activeAccentTile, 0.25f)
    } else {
        lerp(Color.White, activeAccentTile, 0.20f)
    }
    val onErrorDark = errorColor.contrastingContentColor(
        dark = Color(0xFF3B0000),
        light = Color.White
    )
    val errorContainerDark = lerp(accentDarkAnchor, errorColor, 0.38f)
    val onErrorContainerDark = lerp(errorColor, Color.White, 0.72f)

    // Light-mode semantic tokens
    putColor("system_primary_container_light", a1_100)
    putColor("system_on_primary_container_light", a1_700)
    putColor("system_primary_light", a1_600)
    putColor("system_on_primary_light", Color.White)
    putColor("system_secondary_container_light", a2_100)
    putColor("system_on_secondary_container_light", a2_700)
    putColor("system_secondary_light", a2_600)
    putColor("system_on_secondary_light", Color.White)
    putColor("system_tertiary_container_light", a3_100)
    putColor("system_on_tertiary_container_light", a3_700)
    putColor("system_tertiary_light", a3_600)
    putColor("system_on_tertiary_light", Color.White)
    putColor("system_background_light", n1_10)
    putColor("system_on_background_light", n1_900)
    putColor("system_surface_light", n1_10)
    putColor("system_on_surface_light", n1_900)
    putColor("system_surface_container_low_light", lerp(n1_50, Color.White, 0.25f))
    putColor("system_surface_container_lowest_light", Color.White)
    putColor("system_surface_container_light", n1_50)
    putColor("system_surface_container_high_light", lerp(n1_100, n1_50, 0.5f))
    putColor("system_surface_container_highest_light", n1_100)
    putColor("system_surface_bright_light", n1_10)
    putColor("system_surface_dim_light", lerp(n1_200, n1_100, 0.65f))
    putColor("system_surface_variant_light", n2_100)
    putColor("system_on_surface_variant_light", n2_700)
    putColor("system_outline_light", n2_500)
    putColor("system_outline_variant_light", n2_200)
    putColor("system_error_light", Color(0xFFBA1A1A))
    putColor("system_on_error_light", Color.White)
    putColor("system_error_container_light", Color(0xFFFFDAD6))
    putColor("system_on_error_container_light", Color(0xFF93000A))
    putColor("system_control_activated_light", a1_100)
    putColor("system_control_normal_light", n2_700)
    putColor("system_control_highlight_light", n1_200)
    putColor("system_text_primary_inverse_light", n1_100)
    putColor("system_text_secondary_and_tertiary_inverse_light", n2_200)
    putColor("system_text_primary_inverse_disable_only_light", n1_100)
    putColor("system_text_secondary_and_tertiary_inverse_disabled_light", n1_100)
    putColor("system_text_hint_inverse_light", n1_100)
    putColor("system_palette_key_color_primary_light", primaryAccent)
    putColor("system_palette_key_color_secondary_light", secBase)
    putColor("system_palette_key_color_tertiary_light", tertBase)
    putColor("system_palette_key_color_neutral_light", n1_500)
    putColor("system_palette_key_color_neutral_variant_light", n2_500)

    // Dark-mode semantic tokens
    putColor("system_primary_container_dark", a1_700)
    putColor("system_on_primary_container_dark", lerp(activeAccentTile, Color.White, 0.65f))
    putColor("system_primary_dark", primaryAccent)
    putColor("system_on_primary_dark", onPrimaryDark)
    putColor("system_secondary_container_dark", a2_700)
    putColor("system_on_secondary_container_dark", a2_100)
    putColor("system_secondary_dark", a2_200)
    putColor("system_on_secondary_dark", onSecondaryDark)
    putColor("system_tertiary_container_dark", a3_700)
    putColor("system_on_tertiary_container_dark", a3_100)
    putColor("system_tertiary_dark", a3_200)
    putColor("system_on_tertiary_dark", onTertiaryDark)
    putColor("system_background_dark", bg)
    putColor("system_on_background_dark", textPrimary)
    putColor("system_surface_dark", bg)
    putColor("system_on_surface_dark", textPrimary)
    putColor("system_surface_container_low_dark", panel)
    putColor("system_surface_container_lowest_dark", lerp(bg, Color.Black, 0.35f))
    putColor("system_surface_container_dark", cardSurface)
    putColor("system_surface_container_high_dark", elevated)
    putColor("system_surface_container_highest_dark", pressed)
    putColor("system_surface_bright_dark", lerp(pressed, textSecondary, 0.20f))
    putColor("system_surface_dim_dark", bg)
    putColor("system_surface_variant_dark", elevated)
    putColor("system_on_surface_variant_dark", textSecondary)
    putColor("system_outline_dark", n2_400)
    putColor("system_outline_variant_dark", n2_600)
    putColor("system_error_dark", errorColor)
    putColor("system_on_error_dark", onErrorDark)
    putColor("system_error_container_dark", errorContainerDark)
    putColor("system_on_error_container_dark", onErrorContainerDark)
    putColor("system_control_activated_dark", a1_700)
    putColor("system_control_normal_dark", textSecondary)
    putColor("system_control_highlight_dark", lerp(pressed, textPrimary, 0.25f))
    putColor("system_text_primary_inverse_dark", bg)
    putColor("system_text_secondary_and_tertiary_inverse_dark", panel)
    putColor("system_text_primary_inverse_disable_only_dark", bg)
    putColor("system_text_secondary_and_tertiary_inverse_disabled_dark", bg)
    putColor("system_text_hint_inverse_dark", panel)
    putColor("system_palette_key_color_primary_dark", primaryAccent)
    putColor("system_palette_key_color_secondary_dark", secBase)
    putColor("system_palette_key_color_tertiary_dark", tertBase)
    putColor("system_palette_key_color_neutral_dark", n1_500)
    putColor("system_palette_key_color_neutral_variant_dark", n2_500)

    // Fixed accent tokens
    putColor("system_primary_fixed", a1_100)
    putColor("system_primary_fixed_dim", primaryAccent)
    putColor("system_on_primary_fixed", onShadeActive)
    putColor("system_on_primary_fixed_variant", onShadeActiveVariant)
    putColor("system_secondary_fixed", a2_100)
    putColor("system_secondary_fixed_dim", a2_200)
    putColor("system_on_secondary_fixed", a2_900)
    putColor("system_on_secondary_fixed_variant", a2_700)
    putColor("system_tertiary_fixed", a3_100)
    putColor("system_tertiary_fixed_dim", a3_200)
    putColor("system_on_tertiary_fixed", a3_900)
    putColor("system_on_tertiary_fixed_variant", a3_700)

    // SystemUI Shade, Brand, Clock, App Theme & Widget tokens
    putColor("system_brand_a_dark", primaryAccent)
    putColor("system_brand_a_light", a1_600)
    putColor("system_brand_b_dark", a1_100)
    putColor("system_brand_b_light", a2_500)
    putColor("system_brand_c_dark", a1_300)
    putColor("system_brand_c_light", a1_500)
    putColor("system_brand_d_dark", a3_100)
    putColor("system_brand_d_light", a3_500)
    putColor("system_clock_hour_dark", a2_200)
    putColor("system_clock_hour_light", a2_700)
    putColor("system_clock_minute_dark", a1_100)
    putColor("system_clock_minute_light", a1_600)
    putColor("system_clock_second_dark", a3_100)
    putColor("system_clock_second_light", a3_600)
    putColor("system_on_shade_active_dark", onShadeActive)
    putColor("system_on_shade_active_light", onShadeActive)
    putColor("system_on_shade_active_variant_dark", onShadeActiveVariant)
    putColor("system_on_shade_active_variant_light", onShadeActiveVariant)
    putColor("system_on_shade_inactive_dark", textPrimary)
    putColor("system_on_shade_inactive_light", textPrimary)
    putColor("system_on_shade_inactive_variant_dark", textSecondary)
    putColor("system_on_shade_inactive_variant_light", textSecondary)
    putColor("system_on_theme_app_dark", lerp(activeAccentTile, Color.White, 0.45f))
    putColor("system_on_theme_app_light", a1_700)
    putColor("system_overview_background_dark", panel)
    putColor("system_overview_background_light", n2_200)
    putColor("system_shade_active_dark", activeAccentTile)
    putColor("system_shade_active_light", activeAccentTile)
    putColor("system_shade_disabled_dark", lerp(bg, panel, 0.45f))
    putColor("system_shade_disabled_light", lerp(bg, panel, 0.45f))
    putColor("system_shade_inactive_dark", cardSurface)
    putColor("system_shade_inactive_light", cardSurface)
    putColor("system_theme_app_dark", a1_700)
    putColor("system_theme_app_light", a1_100)
    putColor("system_theme_app_ring_dark", a1_300)
    putColor("system_theme_app_ring_light", a1_300)
    putColor("system_theme_notif_dark", a3_100)
    putColor("system_theme_notif_light", a3_200)
    putColor("system_under_surface_dark", bg)
    putColor("system_under_surface_light", bg)
    putColor("system_weather_temp_dark", primaryAccent)
    putColor("system_weather_temp_light", a1_600)
    putColor("system_widget_background_dark", panel)
    putColor("system_widget_background_light", n1_50)

    // Inverse, Scrim, Shadow & Surface Tint tokens
    putColor("system_inverse_on_surface_light", n1_50)
    putColor("system_inverse_primary_light", primaryAccent)
    putColor("system_inverse_surface_light", cardSurface)
    putColor("system_scrim_light", Color.Black)
    putColor("system_shadow_light", Color.Black)
    putColor("system_surface_tint_light", a1_600)
    putColor("system_inverse_on_surface_dark", panel)
    putColor("system_inverse_primary_dark", a1_600)
    putColor("system_inverse_surface_dark", textPrimary)
    putColor("system_scrim_dark", Color.Black)
    putColor("system_shadow_dark", Color.Black)
    putColor("system_surface_tint_dark", primaryAccent)

    val keys = entries.keys.toTypedArray()
    val values = IntArray(keys.size) { idx -> entries[keys[idx]] ?: 0xFF000000.toInt() }
    return keys to values
}

/**
 * Resolves the TCC or Megingiard theme palette based on `themeMode` ID (`AppThemeOption.id`)
 * and orthogonal UI Look (`uiLookMode`: Auto / AYN / Retroid Pixel).
 */
@Composable
fun TccTheme(
    themeMode: Int,
    uiLookMode: Int = TccUiLookPreference.AUTO.id,
    retroidReadabilityBoost: Boolean = true,
    customAccentArgb: Int = DEFAULT_MEGINGIARD_ACCENT_ARGB,
    syncedMegingiardOption: AppThemeOption = AppThemeOption.MEGINGIARD,
    syncedMegingiardAccentArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val option = AppThemeOption.fromId(themeMode)
    val resolvedUiLook = TccUiLookPreference.fromId(uiLookMode)
        .resolve(DeviceProfile.current(context))
    val lookSpec = lookSpecFor(resolvedUiLook, retroidReadabilityBoost)
    val palette = resolveTccPalette(
        option = option,
        systemDark = systemDark,
        customAccentArgb = customAccentArgb,
        syncedOption = syncedMegingiardOption,
        syncedAccentArgb = syncedMegingiardAccentArgb,
        uiLook = resolvedUiLook
    )

    CompositionLocalProvider(
        LocalTccColors provides palette,
        LocalTccUiLook provides resolvedUiLook,
        LocalTccLookSpec provides lookSpec
    ) {
        content()
    }
}

@DrawableRes
internal fun retroidPixelDrawableOrNull(@DrawableRes resId: Int): Int? = when (resId) {
    R.drawable.ic_ban_on_use -> R.drawable.rp_ic_ban_on_use
    R.drawable.ic_bluetooth -> R.drawable.rp_ic_bluetooth
    R.drawable.ic_brightness_0 -> R.drawable.rp_ic_brightness_0
    R.drawable.ic_brightness_1 -> R.drawable.rp_ic_brightness_1
    R.drawable.ic_brightness_2 -> R.drawable.rp_ic_brightness_2
    R.drawable.ic_controller__xbox_style -> R.drawable.rp_ic_controller_xbox_style
    R.drawable.ic_controller_odin_style -> R.drawable.rp_ic_controller_odin_style
    R.drawable.ic_dual_screen_display -> R.drawable.rp_ic_dual_screen_display
    R.drawable.ic_fan_custom_mode -> R.drawable.rp_ic_fan_custom_mode
    R.drawable.ic_fan_custom_progress -> R.drawable.rp_ic_fan_custom_progress
    R.drawable.ic_fan_normal -> R.drawable.rp_ic_fan_normal
    R.drawable.ic_fan_quiet_mode -> R.drawable.rp_ic_fan_quiet_mode
    R.drawable.ic_fan_smart_mode -> R.drawable.rp_ic_fan_smart_mode
    R.drawable.ic_fan_sport_mode -> R.drawable.rp_ic_fan_sport_mode
    R.drawable.ic_height_performance_mode -> R.drawable.rp_ic_height_performance_mode
    R.drawable.ic_just_screen_above -> R.drawable.rp_ic_just_screen_above
    R.drawable.ic_just_screen_below -> R.drawable.rp_ic_just_screen_below
    R.drawable.ic_landscape -> R.drawable.rp_ic_landscape
    R.drawable.ic_nav_edit -> R.drawable.rp_ic_nav_edit
    R.drawable.ic_normal_mode -> R.drawable.rp_ic_normal_mode
    R.drawable.ic_performance_mode -> R.drawable.rp_ic_performance_mode
    R.drawable.ic_qs_airplane -> R.drawable.rp_ic_qs_airplane
    R.drawable.ic_qs_ambient_light -> R.drawable.rp_ic_qs_ambient_light
    R.drawable.ic_qs_charge_limit -> R.drawable.rp_ic_qs_charge_limit
    R.drawable.ic_qs_charging_separation -> R.drawable.rp_ic_qs_charging_separation
    R.drawable.ic_qs_clean -> R.drawable.rp_ic_qs_clean
    R.drawable.ic_qs_clear_tasks -> R.drawable.rp_ic_qs_clear_tasks
    R.drawable.ic_qs_controller_style -> R.drawable.rp_ic_qs_controller_style
    R.drawable.ic_qs_dark_theme -> R.drawable.rp_ic_qs_dark_theme
    R.drawable.ic_qs_dnd -> R.drawable.rp_ic_qs_dnd
    R.drawable.ic_qs_fan_control -> R.drawable.rp_ic_qs_fan_control
    R.drawable.ic_qs_keep_awake -> R.drawable.rp_ic_qs_keep_awake
    R.drawable.ic_qs_key_mapper -> R.drawable.rp_ic_qs_key_mapper
    R.drawable.ic_qs_light_theme -> R.drawable.rp_ic_qs_light_theme
    R.drawable.ic_qs_mouse_mode -> R.drawable.rp_ic_qs_mouse_mode
    R.drawable.ic_qs_performance -> R.drawable.rp_ic_qs_performance
    R.drawable.ic_qs_prevent_home -> R.drawable.rp_ic_qs_prevent_home
    R.drawable.ic_qs_screen_record -> R.drawable.rp_ic_qs_screen_record
    R.drawable.ic_qs_screenshot -> R.drawable.rp_ic_qs_screenshot
    R.drawable.ic_qs_swap_screens -> R.drawable.rp_ic_qs_swap_screens
    R.drawable.ic_qs_system_theme -> R.drawable.rp_ic_qs_system_theme
    R.drawable.ic_qs_theme_mode -> R.drawable.rp_ic_qs_theme_mode
    R.drawable.ic_qs_touch_lock -> R.drawable.rp_ic_qs_touch_lock
    R.drawable.ic_qs_trigger_mode -> R.drawable.rp_ic_qs_trigger_mode
    R.drawable.ic_qs_vibrate -> R.drawable.rp_ic_qs_vibrate
    R.drawable.ic_qs_wifi -> R.drawable.rp_ic_qs_wifi
    R.drawable.ic_screen_control -> R.drawable.rp_ic_screen_control
    R.drawable.ic_screen_focus_lock_lower -> R.drawable.rp_ic_screen_focus_lock_lower
    R.drawable.ic_screen_focus_lock_mode -> R.drawable.rp_ic_screen_focus_lock_mode
    R.drawable.ic_screen_focus_lock_switchable -> R.drawable.rp_ic_screen_focus_lock_switchable
    R.drawable.ic_screen_focus_lock_upper -> R.drawable.rp_ic_screen_focus_lock_upper
    R.drawable.ic_status_wifi_0 -> R.drawable.rp_ic_status_wifi_0
    R.drawable.ic_status_wifi_1 -> R.drawable.rp_ic_status_wifi_1
    R.drawable.ic_status_wifi_2 -> R.drawable.rp_ic_status_wifi_2
    R.drawable.ic_status_wifi_3 -> R.drawable.rp_ic_status_wifi_3
    R.drawable.ic_system_set -> R.drawable.rp_ic_system_set
    R.drawable.ic_task_manage -> R.drawable.rp_ic_task_manage
    R.drawable.ic_temp -> R.drawable.rp_ic_temp
    R.drawable.ic_tip -> R.drawable.rp_ic_tip
    R.drawable.ic_volume_0 -> R.drawable.rp_ic_volume_0
    R.drawable.ic_volume_1 -> R.drawable.rp_ic_volume_1
    R.drawable.ic_volume_2 -> R.drawable.rp_ic_volume_2
    R.drawable.ic_volume_3 -> R.drawable.rp_ic_volume_3
    else -> null
}

/**
 * Maps a light drawable resource to its pixel-art (`TccUiLook.RETROID`) or `_black` dark-mode
 * (`TccUiLook.AYN`) equivalent, matching `N0.b` and `r0.C0469a.c(int)` from DualScreenAssistant.
 */
@Composable
@DrawableRes
fun tccDrawable(@DrawableRes lightResId: Int): Int {
    if (LocalTccUiLook.current == TccUiLook.RETROID) {
        retroidPixelDrawableOrNull(lightResId)?.let { return it }
    }
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
