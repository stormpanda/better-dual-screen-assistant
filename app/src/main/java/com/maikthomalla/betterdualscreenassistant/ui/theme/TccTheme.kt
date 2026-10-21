package com.maikthomalla.betterdualscreenassistant.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.maikthomalla.betterdualscreenassistant.R

@Immutable
data class TccColorPalette(
    val isDark: Boolean,
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
    val lightning: Color
)

val LightTccPalette = TccColorPalette(
    isDark = false,
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
    lightning = Color(0xFF04AD29)
)

val PureBlackTccPalette = TccColorPalette(
    isDark = true,
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
    lightning = Color(0xFF03C82E)
)

val LocalTccColors = staticCompositionLocalOf { PureBlackTccPalette }

/**
 * Resolves the TCC theme palette based on `tcc_theme_mode`:
 * 1 = System, 2 = Pure Black, 3 = Light, 4 = Dark (resolves to Pure Black in stock app).
 */
@Composable
fun TccTheme(
    themeMode: Int,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        1 -> systemDark
        2 -> true
        3 -> false
        4 -> true
        else -> true
    }
    val palette = if (useDark) PureBlackTccPalette else LightTccPalette

    CompositionLocalProvider(LocalTccColors provides palette) {
        content()
    }
}

/**
 * Maps a light drawable resource to its `_black` dark-mode equivalent when the dark/pure-black
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
