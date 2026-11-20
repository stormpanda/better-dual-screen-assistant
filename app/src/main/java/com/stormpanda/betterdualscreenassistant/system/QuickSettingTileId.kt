package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.stormpanda.betterdualscreenassistant.R

enum class QuickSettingCategory(
    @get:VisibleForTesting val title: String,
    @get:VisibleForTesting val description: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val enabledByDefault: Boolean
) {
    STOCK_AYN(
        title = "Stock AYN Toggles",
        description = "Built-in AYN Dual Screen Assistant quick settings (on by default)",
        titleRes = R.string.qs_cat_stock_title,
        descriptionRes = R.string.qs_cat_stock_desc,
        enabledByDefault = true
    ),
    ANDROID_SYSTEM(
        title = "Android System Toggles",
        description = "Standard Android radio, notification, and display controls (off by default)",
        titleRes = R.string.qs_cat_android_system_title,
        descriptionRes = R.string.qs_cat_android_system_desc,
        enabledByDefault = false
    ),
    TOOLS(
        title = "Tools",
        description = "Dual-screen multi-tasking utilities and AYN hardware tools (off by default)",
        titleRes = R.string.qs_cat_tools_title,
        descriptionRes = R.string.qs_cat_tools_desc,
        enabledByDefault = false
    );

    @VisibleForTesting
    fun titleFor(profile: DeviceProfile): String = when (this) {
        STOCK_AYN -> "Stock ${profile.vendorName} Toggles"
        else -> title
    }

    fun titleFor(context: Context, profile: DeviceProfile): String = when (this) {
        STOCK_AYN -> context.getString(titleRes, profile.vendorName)
        else -> context.getString(titleRes)
    }

    @VisibleForTesting
    fun descriptionFor(profile: DeviceProfile): String = when (this) {
        STOCK_AYN -> "Built-in ${profile.vendorName} Dual Screen Assistant quick settings (on by default)"
        TOOLS -> "Dual-screen multi-tasking utilities and ${profile.vendorName} hardware tools (off by default)"
        else -> description
    }

    fun descriptionFor(context: Context, profile: DeviceProfile): String = when (this) {
        STOCK_AYN -> context.getString(descriptionRes, profile.vendorName)
        TOOLS -> context.getString(descriptionRes, profile.vendorName)
        else -> context.getString(descriptionRes)
    }
}

/**
 * Canonical identifiers and metadata for quick setting toggles in the assistant overlay.
 * Listed in default order grouped by [QuickSettingCategory].
 */
enum class QuickSettingTileId(
    val key: String,
    @get:VisibleForTesting val title: String,
    @get:VisibleForTesting val description: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
    val category: QuickSettingCategory,
    val enabledByDefault: Boolean = category.enabledByDefault
) {
    // 1. Stock AYN Toggles (ON by default)
    FOCUS_LOCK(
        key = "focus_lock",
        title = "Key Focus Lock",
        description = "Switch gamepad focus between top screen, bottom screen, or touch-switchable mode",
        titleRes = R.string.qs_tile_focus_lock_title,
        descriptionRes = R.string.qs_tile_focus_lock_desc,
        iconRes = R.drawable.ic_screen_focus_lock_mode,
        category = QuickSettingCategory.STOCK_AYN
    ),
    PERFORMANCE_MODE(
        key = "performance_mode",
        title = "Performance Mode",
        description = "Switch between Normal, Performance, and High Performance profiles",
        titleRes = R.string.qs_tile_performance_mode_title,
        descriptionRes = R.string.qs_tile_performance_mode_desc,
        iconRes = R.drawable.ic_qs_performance,
        category = QuickSettingCategory.STOCK_AYN
    ),
    FAN_CONTROL(
        key = "fan_control",
        title = "Fan Control",
        description = "Configure cooling fan profile or custom fan speed",
        titleRes = R.string.qs_tile_fan_control_title,
        descriptionRes = R.string.qs_tile_fan_control_desc,
        iconRes = R.drawable.ic_qs_fan_control,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CLEAR_MEMORY(
        key = "clear_memory",
        title = "Clear Memory",
        description = "Free background RAM and close non-essential background processes",
        titleRes = R.string.qs_tile_clear_memory_title,
        descriptionRes = R.string.qs_tile_clear_memory_desc,
        iconRes = R.drawable.ic_qs_clean,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CONTROLLER_STYLE(
        key = "controller_style",
        title = "Controller Style",
        description = "Switch between Xbox, Odin, or Disabled gamepad button layout",
        titleRes = R.string.qs_tile_controller_style_title,
        descriptionRes = R.string.qs_tile_controller_style_desc,
        iconRes = R.drawable.ic_qs_controller_style,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CHARGING_SEPARATION(
        key = "charging_separation",
        title = "Charging Separation",
        description = "Bypass battery charging and power the device directly from USB-C",
        titleRes = R.string.qs_tile_charging_separation_title,
        descriptionRes = R.string.qs_tile_charging_separation_desc,
        iconRes = R.drawable.ic_qs_charging_separation,
        category = QuickSettingCategory.STOCK_AYN
    ),
    VIBRATION(
        key = "vibration",
        title = "Vibration",
        description = "Toggle system haptic feedback and vibration",
        titleRes = R.string.qs_tile_vibration_title,
        descriptionRes = R.string.qs_tile_vibration_desc,
        iconRes = R.drawable.ic_qs_vibrate,
        category = QuickSettingCategory.STOCK_AYN
    ),
    AMBIENT_LIGHT(
        key = "ambient_light",
        title = "LED Lights",
        description = "Toggle joystick and side ambient RGB lighting",
        titleRes = R.string.qs_tile_ambient_light_title,
        descriptionRes = R.string.qs_tile_ambient_light_desc,
        iconRes = R.drawable.ic_qs_ambient_light,
        category = QuickSettingCategory.STOCK_AYN
    ),
    FORCE_LANDSCAPE(
        key = "force_landscape",
        title = "Force Landscape",
        description = "Force bottom-screen apps into landscape orientation",
        titleRes = R.string.qs_tile_force_landscape_title,
        descriptionRes = R.string.qs_tile_force_landscape_desc,
        iconRes = R.drawable.ic_landscape,
        category = QuickSettingCategory.STOCK_AYN
    ),
    THEME_MODE(
        key = "theme_mode",
        title = "Theme Mode",
        description = "Switch assistant overlay and system theme palette",
        titleRes = R.string.qs_tile_theme_mode_title,
        descriptionRes = R.string.qs_tile_theme_mode_desc,
        iconRes = R.drawable.ic_qs_theme_mode,
        category = QuickSettingCategory.STOCK_AYN
    ),

    // 2. Android System Toggles (OFF by default)
    WIFI(
        key = "wifi",
        title = "Wi-Fi",
        description = "Toggle Wi-Fi wireless network connection",
        titleRes = R.string.qs_tile_wifi_title,
        descriptionRes = R.string.qs_tile_wifi_desc,
        iconRes = R.drawable.ic_qs_wifi,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    BLUETOOTH(
        key = "bluetooth",
        title = "Bluetooth",
        description = "Toggle Bluetooth adapter state",
        titleRes = R.string.qs_tile_bluetooth_title,
        descriptionRes = R.string.qs_tile_bluetooth_desc,
        iconRes = R.drawable.ic_bluetooth,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    DO_NOT_DISTURB(
        key = "do_not_disturb",
        title = "Do Not Disturb",
        description = "Silence notifications and alerts while gaming",
        titleRes = R.string.qs_tile_dnd_title,
        descriptionRes = R.string.qs_tile_dnd_desc,
        iconRes = R.drawable.ic_qs_dnd,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    AIRPLANE_MODE(
        key = "airplane_mode",
        title = "Airplane Mode",
        description = "Disable all wireless radios to save battery",
        titleRes = R.string.qs_tile_airplane_mode_title,
        descriptionRes = R.string.qs_tile_airplane_mode_desc,
        iconRes = R.drawable.ic_qs_airplane,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    SCREEN_CAST(
        key = "screen_cast",
        title = "Screen Cast",
        description = "Cast screen wirelessly via Chromecast or Miracast, or disconnect active cast",
        titleRes = R.string.qs_tile_screen_cast_title,
        descriptionRes = R.string.qs_tile_screen_cast_desc,
        iconRes = R.drawable.ic_qs_cast,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),

    // 3. Tools (OFF by default)
    TOUCH_LOCK(
        key = "touch_lock",
        title = "Touch Lock",
        description = "Disable bottom-screen touch input while keeping the display visible",
        titleRes = R.string.qs_tile_touch_lock_title,
        descriptionRes = R.string.qs_tile_touch_lock_desc,
        iconRes = R.drawable.ic_qs_touch_lock,
        category = QuickSettingCategory.TOOLS
    ),
    SCREENSHOT(
        key = "screenshot",
        title = "Screenshot",
        description = "Capture a screenshot of the Top, Bottom, or Both screens",
        titleRes = R.string.qs_tile_screenshot_title,
        descriptionRes = R.string.qs_tile_screenshot_desc,
        iconRes = R.drawable.ic_qs_screenshot,
        category = QuickSettingCategory.TOOLS
    ),
    SCREEN_RECORD(
        key = "screen_record",
        title = "Screen Record",
        description = "Record video and audio of the Top, Bottom, or Both screens",
        titleRes = R.string.qs_tile_screen_record_title,
        descriptionRes = R.string.qs_tile_screen_record_desc,
        iconRes = R.drawable.ic_qs_screen_record,
        category = QuickSettingCategory.TOOLS
    ),
    TRIGGER_MODE(
        key = "trigger_mode",
        title = "Trigger Mode",
        description = "Switch L2/R2 trigger input mode between Analog, Digital, and Both",
        titleRes = R.string.qs_tile_trigger_mode_title,
        descriptionRes = R.string.qs_tile_trigger_mode_desc,
        iconRes = R.drawable.ic_qs_trigger_mode,
        category = QuickSettingCategory.TOOLS
    ),
    CHARGE_LIMIT_80(
        key = "charge_limit_80",
        title = "80% Charge Limit",
        description = "Cap battery charging at 80% to preserve long-term battery health",
        titleRes = R.string.qs_tile_charge_limit_80_title,
        descriptionRes = R.string.qs_tile_charge_limit_80_desc,
        iconRes = R.drawable.ic_qs_charge_limit,
        category = QuickSettingCategory.TOOLS
    ),
    GAMEPAD_MOUSE_MODE(
        key = "gamepad_mouse_mode",
        title = "Mouse Mode",
        description = "Toggle virtual gamepad-to-mouse cursor mode",
        titleRes = R.string.qs_tile_gamepad_mouse_mode_title,
        descriptionRes = R.string.qs_tile_gamepad_mouse_mode_desc,
        iconRes = R.drawable.ic_qs_mouse_mode,
        category = QuickSettingCategory.TOOLS
    ),
    KEY_MAPPER(
        key = "key_mapper",
        title = "Key Mapper",
        description = "Toggle AYN screen key mapping (Key Adapter)",
        titleRes = R.string.qs_tile_key_mapper_title,
        descriptionRes = R.string.qs_tile_key_mapper_desc,
        iconRes = R.drawable.ic_qs_key_mapper,
        category = QuickSettingCategory.TOOLS
    ),
    KEEP_SCREEN_ON(
        key = "keep_screen_on",
        title = "Keep Screen On",
        description = "Prevent screens from sleeping due to inactivity",
        titleRes = R.string.qs_tile_keep_screen_on_title,
        descriptionRes = R.string.qs_tile_keep_screen_on_desc,
        iconRes = R.drawable.ic_qs_keep_awake,
        category = QuickSettingCategory.TOOLS
    ),
    PREVENT_ACCIDENTAL_HOME(
        key = "prevent_accidental_home",
        title = "Home Lock",
        description = "Prevent accidental Home button presses during gameplay",
        titleRes = R.string.qs_tile_prevent_accidental_home_title,
        descriptionRes = R.string.qs_tile_prevent_accidental_home_desc,
        iconRes = R.drawable.ic_qs_prevent_home,
        category = QuickSettingCategory.TOOLS
    ),
    CLEAR_ALL_TASKS(
        key = "clear_all_tasks",
        title = "Clear All Tasks",
        description = "Close all recent app tasks cleanly without killing background services",
        titleRes = R.string.qs_tile_clear_all_tasks_title,
        descriptionRes = R.string.qs_tile_clear_all_tasks_desc,
        iconRes = R.drawable.ic_qs_clear_tasks,
        category = QuickSettingCategory.TOOLS
    ),
    CLUSTER_TUNE(
        key = "cluster_tune",
        title = "ClusterTune",
        description = "Open ClusterTune profile picker popup on the top screen",
        titleRes = R.string.qs_tile_cluster_tune_title,
        descriptionRes = R.string.qs_tile_cluster_tune_desc,
        iconRes = R.drawable.ic_qs_clustertune,
        category = QuickSettingCategory.TOOLS
    ),
    DPAD_SWAP(
        key = "dpad_swap",
        title = "D-Pad Swap",
        description = "Swap physical D-Pad and Left Analog Stick system-wide",
        titleRes = R.string.qs_tile_dpad_swap_title,
        descriptionRes = R.string.qs_tile_dpad_swap_desc,
        iconRes = R.drawable.ic_qs_dpad_swap,
        category = QuickSettingCategory.TOOLS
    );

    fun isSupportedOn(
        profile: DeviceProfile,
        isClusterTuneInstalled: Boolean = true
    ): Boolean = when (this) {
        AMBIENT_LIGHT -> profile.supportsAmbientLight
        PREVENT_ACCIDENTAL_HOME -> profile.supportsPreventAccidentalHome
        CLUSTER_TUNE -> isClusterTuneInstalled
        else -> true
    }

    /**
     * Single-line localized title for settings lists, subtitles, and accessibility labels.
     * Flattens explicit `\n` line breaks that translators may include for compact grid cells:
     * joins spaceless scripts (e.g., CJK/Thai) or hyphenated/whitespace boundaries without extra
     * spaces, while preserving word separation for space-delimited scripts (e.g., Latin).
     */
    fun title(context: Context): String =
        flattenGridTitleForList(context.getString(titleRes))

    /**
     * Localized title preserving optional explicit `\n` line breaks for narrow 2x4 tile cells
     * (useful for CJK translations that wrap cleanly at semantic word boundaries).
     */
    fun gridTitle(context: Context): String =
        context.getString(titleRes)

    @VisibleForTesting
    fun descriptionFor(
        promotedToManager: Boolean,
        profile: DeviceProfile = DeviceProfile.AYN_THOR
    ): String = when {
        promotedToManager && this == THEME_MODE ->
            "Switch manager overlay and system theme palette"
        this == CONTROLLER_STYLE ->
            "Switch between Xbox, ${profile.standardControllerLabel}, or Disabled gamepad button layout"
        this == KEY_MAPPER ->
            "Toggle ${profile.vendorName} screen key mapping (Key Adapter)"
        else -> description
    }

    fun descriptionFor(
        context: Context,
        promotedToManager: Boolean,
        profile: DeviceProfile = DeviceProfile.AYN_THOR
    ): String = when {
        promotedToManager && this == THEME_MODE ->
            context.getString(R.string.qs_tile_theme_mode_desc_manager)
        this == CONTROLLER_STYLE ->
            context.getString(descriptionRes, profile.standardControllerLabel(context))
        this == KEY_MAPPER ->
            context.getString(descriptionRes, profile.vendorName)
        else -> context.getString(descriptionRes)
    }

    companion object {
        private val SPACELESS_SCRIPTS: Set<Character.UnicodeScript> = setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.BOPOMOFO,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO,
            Character.UnicodeScript.MYANMAR,
            Character.UnicodeScript.KHMER
        )

        private fun isNoSpaceBoundaryChar(cp: Int): Boolean {
            if (Character.isWhitespace(cp)) return true
            if (cp == '-'.code || cp == '‐'.code || cp == '‑'.code || cp == '–'.code || cp == '/'.code) {
                return true
            }
            return Character.UnicodeScript.of(cp) in SPACELESS_SCRIPTS
        }

        internal fun flattenGridTitleForList(raw: String): String {
            if ('\n' !in raw && '\r' !in raw) return raw
            val out = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch == '\r' || ch == '\n') {
                    var j = i + 1
                    while (j < raw.length && (raw[j] == '\r' || raw[j] == '\n')) {
                        j++
                    }
                    if (out.isNotEmpty() && j < raw.length) {
                        val prevCp = Character.codePointBefore(out, out.length)
                        val nextCp = Character.codePointAt(raw, j)
                        if (!isNoSpaceBoundaryChar(prevCp) && !isNoSpaceBoundaryChar(nextCp)) {
                            out.append(' ')
                        }
                    }
                    i = j
                } else {
                    out.append(ch)
                    i++
                }
            }
            return out.toString()
        }

        val defaultOrder: List<QuickSettingTileId> = entries.toList()
        val defaultDisabled: Set<QuickSettingTileId> =
            entries.filter { !it.enabledByDefault }.toSet()

        fun supportedEntries(
            profile: DeviceProfile,
            isClusterTuneInstalled: Boolean = true
        ): List<QuickSettingTileId> =
            entries.filter { it.isSupportedOn(profile, isClusterTuneInstalled) }

        fun fromKey(key: String): QuickSettingTileId? =
            entries.firstOrNull { it.key == key }
    }
}
