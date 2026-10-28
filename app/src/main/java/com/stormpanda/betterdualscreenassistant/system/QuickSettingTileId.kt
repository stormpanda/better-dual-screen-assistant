package com.stormpanda.betterdualscreenassistant.system

import androidx.annotation.DrawableRes
import com.stormpanda.betterdualscreenassistant.R

enum class QuickSettingCategory(
    val title: String,
    val description: String,
    val enabledByDefault: Boolean
) {
    STOCK_AYN(
        title = "Stock AYN Toggles",
        description = "Built-in AYN Dual Screen Assistant quick settings (on by default)",
        enabledByDefault = true
    ),
    ANDROID_SYSTEM(
        title = "Android System Toggles",
        description = "Standard Android radio, notification, and display controls (off by default)",
        enabledByDefault = false
    ),
    TOOLS(
        title = "Tools",
        description = "Dual-screen multi-tasking utilities and AYN hardware tools (off by default)",
        enabledByDefault = false
    )
}

/**
 * Canonical identifiers and metadata for quick setting toggles in the assistant overlay.
 * Listed in default order grouped by [QuickSettingCategory].
 */
enum class QuickSettingTileId(
    val key: String,
    val title: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val category: QuickSettingCategory,
    val enabledByDefault: Boolean = category.enabledByDefault
) {
    // 1. Stock AYN Toggles (ON by default)
    FOCUS_LOCK(
        key = "focus_lock",
        title = "Key Focus Lock",
        description = "Switch gamepad focus between top screen, bottom screen, or touch-switchable mode",
        iconRes = R.drawable.ic_screen_focus_lock_mode,
        category = QuickSettingCategory.STOCK_AYN
    ),
    PERFORMANCE_MODE(
        key = "performance_mode",
        title = "Performance Mode",
        description = "Switch between Normal, Performance, and High Performance profiles",
        iconRes = R.drawable.ic_qs_performance,
        category = QuickSettingCategory.STOCK_AYN
    ),
    FAN_CONTROL(
        key = "fan_control",
        title = "Fan Control",
        description = "Configure cooling fan profile or custom fan speed",
        iconRes = R.drawable.ic_qs_fan_control,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CLEAR_MEMORY(
        key = "clear_memory",
        title = "Clear Memory",
        description = "Free background RAM and close non-essential background processes",
        iconRes = R.drawable.ic_qs_clean,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CONTROLLER_STYLE(
        key = "controller_style",
        title = "Controller Style",
        description = "Switch between Xbox, Odin, or Disabled gamepad button layout",
        iconRes = R.drawable.ic_qs_controller_style,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CHARGING_SEPARATION(
        key = "charging_separation",
        title = "Charging Separation",
        description = "Bypass battery charging and power the device directly from USB-C",
        iconRes = R.drawable.ic_qs_charging_separation,
        category = QuickSettingCategory.STOCK_AYN
    ),
    VIBRATION(
        key = "vibration",
        title = "Vibration",
        description = "Toggle system haptic feedback and vibration",
        iconRes = R.drawable.ic_qs_vibrate,
        category = QuickSettingCategory.STOCK_AYN
    ),
    AMBIENT_LIGHT(
        key = "ambient_light",
        title = "LED Lights",
        description = "Toggle joystick and side ambient RGB lighting",
        iconRes = R.drawable.ic_qs_ambient_light,
        category = QuickSettingCategory.STOCK_AYN
    ),
    FORCE_LANDSCAPE(
        key = "force_landscape",
        title = "Force Landscape",
        description = "Force bottom-screen apps into landscape orientation",
        iconRes = R.drawable.ic_landscape,
        category = QuickSettingCategory.STOCK_AYN
    ),
    THEME_MODE(
        key = "theme_mode",
        title = "Theme Mode",
        description = "Switch assistant overlay and system theme palette",
        iconRes = R.drawable.ic_qs_theme_mode,
        category = QuickSettingCategory.STOCK_AYN
    ),

    // 2. Android System Toggles (OFF by default)
    WIFI(
        key = "wifi",
        title = "Wi-Fi",
        description = "Toggle Wi-Fi wireless network connection",
        iconRes = R.drawable.ic_qs_wifi,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    BLUETOOTH(
        key = "bluetooth",
        title = "Bluetooth",
        description = "Toggle Bluetooth adapter state",
        iconRes = R.drawable.ic_bluetooth,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    DO_NOT_DISTURB(
        key = "do_not_disturb",
        title = "Do Not Disturb",
        description = "Silence notifications and alerts while gaming",
        iconRes = R.drawable.ic_qs_dnd,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    AIRPLANE_MODE(
        key = "airplane_mode",
        title = "Airplane Mode",
        description = "Disable all wireless radios to save battery",
        iconRes = R.drawable.ic_qs_airplane,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),

    // 3. Tools (OFF by default)
    TOUCH_LOCK(
        key = "touch_lock",
        title = "Touch Lock",
        description = "Disable bottom-screen touch input while keeping the display visible",
        iconRes = R.drawable.ic_qs_touch_lock,
        category = QuickSettingCategory.TOOLS
    ),
    SCREENSHOT(
        key = "screenshot",
        title = "Screenshot",
        description = "Capture a screenshot of the Top, Bottom, or Both screens",
        iconRes = R.drawable.ic_qs_screenshot,
        category = QuickSettingCategory.TOOLS
    ),
    TRIGGER_MODE(
        key = "trigger_mode",
        title = "Trigger Mode",
        description = "Switch L2/R2 trigger input mode between Analog, Digital, and Both",
        iconRes = R.drawable.ic_qs_trigger_mode,
        category = QuickSettingCategory.TOOLS
    ),
    CHARGE_LIMIT_80(
        key = "charge_limit_80",
        title = "80% Charge Limit",
        description = "Cap battery charging at 80% to preserve long-term battery health",
        iconRes = R.drawable.ic_qs_charge_limit,
        category = QuickSettingCategory.TOOLS
    ),
    GAMEPAD_MOUSE_MODE(
        key = "gamepad_mouse_mode",
        title = "Mouse Mode",
        description = "Toggle virtual gamepad-to-mouse cursor mode",
        iconRes = R.drawable.ic_qs_mouse_mode,
        category = QuickSettingCategory.TOOLS
    ),
    KEY_MAPPER(
        key = "key_mapper",
        title = "Key Mapper",
        description = "Toggle AYN screen key mapping (Key Adapter)",
        iconRes = R.drawable.ic_qs_key_mapper,
        category = QuickSettingCategory.TOOLS
    ),
    KEEP_SCREEN_ON(
        key = "keep_screen_on",
        title = "Keep Screen On",
        description = "Prevent screens from sleeping due to inactivity",
        iconRes = R.drawable.ic_qs_keep_awake,
        category = QuickSettingCategory.TOOLS
    ),
    PREVENT_ACCIDENTAL_HOME(
        key = "prevent_accidental_home",
        title = "Home Lock",
        description = "Prevent accidental Home button presses during gameplay",
        iconRes = R.drawable.ic_qs_prevent_home,
        category = QuickSettingCategory.TOOLS
    );

    companion object {
        val defaultOrder: List<QuickSettingTileId> = entries.toList()
        val defaultDisabled: Set<QuickSettingTileId> =
            entries.filter { !it.enabledByDefault }.toSet()

        fun fromKey(key: String): QuickSettingTileId? =
            entries.firstOrNull { it.key == key }
    }
}
