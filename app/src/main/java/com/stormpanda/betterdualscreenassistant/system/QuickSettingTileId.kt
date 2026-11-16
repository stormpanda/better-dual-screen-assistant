package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.stormpanda.betterdualscreenassistant.R

enum class QuickSettingCategory(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val enabledByDefault: Boolean
) {
    STOCK_AYN(
        titleRes = R.string.qs_cat_stock_title,
        descriptionRes = R.string.qs_cat_stock_desc,
        enabledByDefault = true
    ),
    ANDROID_SYSTEM(
        titleRes = R.string.qs_cat_android_title,
        descriptionRes = R.string.qs_cat_android_desc,
        enabledByDefault = false
    ),
    TOOLS(
        titleRes = R.string.qs_cat_tools_title,
        descriptionRes = R.string.qs_cat_tools_desc,
        enabledByDefault = false
    );

    fun titleFor(context: Context, profile: DeviceProfile): String = when (this) {
        STOCK_AYN -> context.getString(titleRes, profile.vendorName)
        else -> context.getString(titleRes)
    }

    fun descriptionFor(context: Context, profile: DeviceProfile): String = when (this) {
        STOCK_AYN, TOOLS -> context.getString(descriptionRes, profile.vendorName)
        else -> context.getString(descriptionRes)
    }
}

/**
 * Canonical identifiers and metadata for quick setting toggles in the assistant overlay.
 * Listed in default order grouped by [QuickSettingCategory].
 */
enum class QuickSettingTileId(
    val key: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
    val category: QuickSettingCategory,
    val enabledByDefault: Boolean = category.enabledByDefault
) {
    // 1. Stock AYN Toggles (ON by default)
    FOCUS_LOCK(
        key = "focus_lock",
        titleRes = R.string.qs_tile_focus_lock_title,
        descriptionRes = R.string.qs_tile_focus_lock_desc,
        iconRes = R.drawable.ic_screen_focus_lock_mode,
        category = QuickSettingCategory.STOCK_AYN
    ),
    PERFORMANCE_MODE(
        key = "performance_mode",
        titleRes = R.string.qs_tile_performance_mode_title,
        descriptionRes = R.string.qs_tile_performance_mode_desc,
        iconRes = R.drawable.ic_qs_performance,
        category = QuickSettingCategory.STOCK_AYN
    ),
    FAN_CONTROL(
        key = "fan_control",
        titleRes = R.string.qs_tile_fan_control_title,
        descriptionRes = R.string.qs_tile_fan_control_desc,
        iconRes = R.drawable.ic_qs_fan_control,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CLEAR_MEMORY(
        key = "clear_memory",
        titleRes = R.string.qs_tile_clear_memory_title,
        descriptionRes = R.string.qs_tile_clear_memory_desc,
        iconRes = R.drawable.ic_qs_clean,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CONTROLLER_STYLE(
        key = "controller_style",
        titleRes = R.string.qs_tile_controller_style_title,
        descriptionRes = R.string.qs_tile_controller_style_desc,
        iconRes = R.drawable.ic_qs_controller_style,
        category = QuickSettingCategory.STOCK_AYN
    ),
    CHARGING_SEPARATION(
        key = "charging_separation",
        titleRes = R.string.qs_tile_charging_separation_title,
        descriptionRes = R.string.qs_tile_charging_separation_desc,
        iconRes = R.drawable.ic_qs_charging_separation,
        category = QuickSettingCategory.STOCK_AYN
    ),
    VIBRATION(
        key = "vibration",
        titleRes = R.string.qs_tile_vibration_title,
        descriptionRes = R.string.qs_tile_vibration_desc,
        iconRes = R.drawable.ic_qs_vibrate,
        category = QuickSettingCategory.STOCK_AYN
    ),
    AMBIENT_LIGHT(
        key = "ambient_light",
        titleRes = R.string.qs_tile_ambient_light_title,
        descriptionRes = R.string.qs_tile_ambient_light_desc,
        iconRes = R.drawable.ic_qs_ambient_light,
        category = QuickSettingCategory.STOCK_AYN
    ),
    FORCE_LANDSCAPE(
        key = "force_landscape",
        titleRes = R.string.qs_tile_force_landscape_title,
        descriptionRes = R.string.qs_tile_force_landscape_desc,
        iconRes = R.drawable.ic_landscape,
        category = QuickSettingCategory.STOCK_AYN
    ),
    THEME_MODE(
        key = "theme_mode",
        titleRes = R.string.qs_tile_theme_mode_title,
        descriptionRes = R.string.qs_tile_theme_mode_desc,
        iconRes = R.drawable.ic_qs_theme_mode,
        category = QuickSettingCategory.STOCK_AYN
    ),

    // 2. Android System Toggles (OFF by default)
    WIFI(
        key = "wifi",
        titleRes = R.string.qs_tile_wifi_title,
        descriptionRes = R.string.qs_tile_wifi_desc,
        iconRes = R.drawable.ic_qs_wifi,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    BLUETOOTH(
        key = "bluetooth",
        titleRes = R.string.qs_tile_bluetooth_title,
        descriptionRes = R.string.qs_tile_bluetooth_desc,
        iconRes = R.drawable.ic_bluetooth,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    DO_NOT_DISTURB(
        key = "do_not_disturb",
        titleRes = R.string.qs_tile_dnd_title,
        descriptionRes = R.string.qs_tile_dnd_desc,
        iconRes = R.drawable.ic_qs_dnd,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),
    AIRPLANE_MODE(
        key = "airplane_mode",
        titleRes = R.string.qs_tile_airplane_mode_title,
        descriptionRes = R.string.qs_tile_airplane_mode_desc,
        iconRes = R.drawable.ic_qs_airplane,
        category = QuickSettingCategory.ANDROID_SYSTEM
    ),

    // 3. Tools (OFF by default)
    TOUCH_LOCK(
        key = "touch_lock",
        titleRes = R.string.qs_tile_touch_lock_title,
        descriptionRes = R.string.qs_tile_touch_lock_desc,
        iconRes = R.drawable.ic_qs_touch_lock,
        category = QuickSettingCategory.TOOLS
    ),
    SCREENSHOT(
        key = "screenshot",
        titleRes = R.string.qs_tile_screenshot_title,
        descriptionRes = R.string.qs_tile_screenshot_desc,
        iconRes = R.drawable.ic_qs_screenshot,
        category = QuickSettingCategory.TOOLS
    ),
    SCREEN_RECORD(
        key = "screen_record",
        titleRes = R.string.qs_tile_screen_record_title,
        descriptionRes = R.string.qs_tile_screen_record_desc,
        iconRes = R.drawable.ic_qs_screen_record,
        category = QuickSettingCategory.TOOLS
    ),
    TRIGGER_MODE(
        key = "trigger_mode",
        titleRes = R.string.qs_tile_trigger_mode_title,
        descriptionRes = R.string.qs_tile_trigger_mode_desc,
        iconRes = R.drawable.ic_qs_trigger_mode,
        category = QuickSettingCategory.TOOLS
    ),
    CHARGE_LIMIT_80(
        key = "charge_limit_80",
        titleRes = R.string.qs_tile_charge_limit_title,
        descriptionRes = R.string.qs_tile_charge_limit_desc,
        iconRes = R.drawable.ic_qs_charge_limit,
        category = QuickSettingCategory.TOOLS
    ),
    GAMEPAD_MOUSE_MODE(
        key = "gamepad_mouse_mode",
        titleRes = R.string.qs_tile_mouse_mode_title,
        descriptionRes = R.string.qs_tile_mouse_mode_desc,
        iconRes = R.drawable.ic_qs_mouse_mode,
        category = QuickSettingCategory.TOOLS
    ),
    KEY_MAPPER(
        key = "key_mapper",
        titleRes = R.string.qs_tile_key_mapper_title,
        descriptionRes = R.string.qs_tile_key_mapper_desc,
        iconRes = R.drawable.ic_qs_key_mapper,
        category = QuickSettingCategory.TOOLS
    ),
    KEEP_SCREEN_ON(
        key = "keep_screen_on",
        titleRes = R.string.qs_tile_keep_screen_on_title,
        descriptionRes = R.string.qs_tile_keep_screen_on_desc,
        iconRes = R.drawable.ic_qs_keep_awake,
        category = QuickSettingCategory.TOOLS
    ),
    PREVENT_ACCIDENTAL_HOME(
        key = "prevent_accidental_home",
        titleRes = R.string.qs_tile_home_lock_title,
        descriptionRes = R.string.qs_tile_home_lock_desc,
        iconRes = R.drawable.ic_qs_prevent_home,
        category = QuickSettingCategory.TOOLS
    ),
    CLEAR_ALL_TASKS(
        key = "clear_all_tasks",
        titleRes = R.string.qs_tile_clear_tasks_title,
        descriptionRes = R.string.qs_tile_clear_tasks_desc,
        iconRes = R.drawable.ic_qs_clear_tasks,
        category = QuickSettingCategory.TOOLS
    );

    fun isSupportedOn(profile: DeviceProfile): Boolean = when (this) {
        AMBIENT_LIGHT -> profile.supportsAmbientLight
        PREVENT_ACCIDENTAL_HOME -> profile.supportsPreventAccidentalHome
        else -> true
    }

    fun title(context: Context): String = context.getString(titleRes).replace("\n", "")

    fun gridTitle(context: Context): String = context.getString(titleRes)

    fun descriptionFor(
        context: Context,
        promotedToManager: Boolean,
        profile: DeviceProfile = DeviceProfile.AYN_THOR
    ): String = when {
        promotedToManager && this == THEME_MODE ->
            context.getString(R.string.qs_tile_theme_mode_desc_manager)
        this == CONTROLLER_STYLE ->
            context.getString(descriptionRes, profile.standardControllerLabel)
        this == KEY_MAPPER ->
            context.getString(descriptionRes, profile.vendorName)
        else -> context.getString(descriptionRes)
    }

    companion object {
        val defaultOrder: List<QuickSettingTileId> = entries.toList()
        val defaultDisabled: Set<QuickSettingTileId> =
            entries.filter { !it.enabledByDefault }.toSet()

        fun supportedEntries(profile: DeviceProfile): List<QuickSettingTileId> =
            entries.filter { it.isSupportedOn(profile) }

        fun fromKey(key: String): QuickSettingTileId? =
            entries.firstOrNull { it.key == key }
    }
}
