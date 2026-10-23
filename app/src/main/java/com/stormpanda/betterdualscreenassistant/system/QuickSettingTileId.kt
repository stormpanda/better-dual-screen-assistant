package com.stormpanda.betterdualscreenassistant.system

import androidx.annotation.DrawableRes
import com.stormpanda.betterdualscreenassistant.R

/**
 * Canonical identifiers and metadata for the 10 quick setting toggles in the assistant overlay.
 * Listed in stock DualScreenAssistant default order.
 */
enum class QuickSettingTileId(
    val key: String,
    val title: String,
    val description: String,
    @DrawableRes val iconRes: Int
) {
    FOCUS_LOCK(
        key = "focus_lock",
        title = "Key Focus Lock",
        description = "Switch gamepad focus between top screen, bottom screen, or touch-switchable mode",
        iconRes = R.drawable.ic_screen_focus_lock_mode
    ),
    PERFORMANCE_MODE(
        key = "performance_mode",
        title = "Performance Mode",
        description = "Switch between Normal, Performance, and High Performance profiles",
        iconRes = R.drawable.ic_qs_performance
    ),
    FAN_CONTROL(
        key = "fan_control",
        title = "Fan Control",
        description = "Configure cooling fan profile or custom fan speed",
        iconRes = R.drawable.ic_qs_fan_control
    ),
    CLEAR_MEMORY(
        key = "clear_memory",
        title = "Clear Memory",
        description = "Free background RAM and close non-essential background processes",
        iconRes = R.drawable.ic_qs_clean
    ),
    CONTROLLER_STYLE(
        key = "controller_style",
        title = "Controller Style",
        description = "Switch between Xbox, Odin, or Disabled gamepad button layout",
        iconRes = R.drawable.ic_qs_controller_style
    ),
    CHARGING_SEPARATION(
        key = "charging_separation",
        title = "Charging Separation",
        description = "Bypass battery charging and power the device directly from USB-C",
        iconRes = R.drawable.ic_qs_charging_separation
    ),
    VIBRATION(
        key = "vibration",
        title = "Vibration",
        description = "Toggle system haptic feedback and vibration",
        iconRes = R.drawable.ic_qs_vibrate
    ),
    AMBIENT_LIGHT(
        key = "ambient_light",
        title = "LED Lights",
        description = "Toggle joystick and side ambient RGB lighting",
        iconRes = R.drawable.ic_qs_ambient_light
    ),
    FORCE_LANDSCAPE(
        key = "force_landscape",
        title = "Force Landscape",
        description = "Force bottom-screen apps into landscape orientation",
        iconRes = R.drawable.ic_landscape
    ),
    THEME_MODE(
        key = "theme_mode",
        title = "Theme Mode",
        description = "Switch assistant overlay and system theme palette",
        iconRes = R.drawable.ic_qs_theme_mode
    );

    companion object {
        val defaultOrder: List<QuickSettingTileId> = entries.toList()

        fun fromKey(key: String): QuickSettingTileId? =
            entries.firstOrNull { it.key == key }
    }
}
