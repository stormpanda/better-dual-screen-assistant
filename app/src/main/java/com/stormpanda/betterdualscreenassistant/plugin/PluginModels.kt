package com.stormpanda.betterdualscreenassistant.plugin

import android.content.ComponentName
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId

/**
 * Static definition of a single quick-toggle tile declared by a third-party plugin APK.
 */
data class PluginTileDefinition(
    val packageName: String,
    val tileId: String,
    val receiverComponent: ComponentName,
    val title: String,
    val gridTitle: String,
    val description: String,
    val iconResId: Int,
    val pixelIconResId: Int = 0,
    val supportsLongPress: Boolean = false,
    val dismissOnClick: Boolean = false,
    val dismissOnLongClick: Boolean = false,
    val supportedDevices: Set<String> = setOf(BdsaPluginContract.DEVICE_ALL)
) {
    val tileKey: String
        get() = BdsaPluginContract.pluginTileKey(packageName, tileId)

    val listTitle: String
        get() = QuickSettingTileId.flattenGridTitleForList(title)

    fun isSupportedOn(profile: DeviceProfile): Boolean {
        if (BdsaPluginContract.DEVICE_ALL in supportedDevices) return true
        return BdsaPluginContract.deviceIdFor(profile) in supportedDevices
    }
}

/**
 * Metadata for an installed third-party plugin APK discovered by [PluginRegistry].
 */
data class RegisteredPlugin(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val apiVersion: Int,
    val isPluginEnabled: Boolean,
    val settingsActivity: ComponentName?,
    val receiverComponent: ComponentName,
    val tiles: List<PluginTileDefinition>
) {
    val isApiCompatible: Boolean
        get() = apiVersion == BdsaPluginContract.API_VERSION

    fun supportedTilesOn(profile: DeviceProfile): List<PluginTileDefinition> =
        tiles.filter { it.isSupportedOn(profile) }
}

/**
 * Dynamic runtime state pushed by a plugin for one of its tiles and cached locally
 * so the bottom-screen overlay opens instantaneously without blocking on IPC.
 */
data class PluginTileRuntimeState(
    val label: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val iconResId: Int? = null,
    val pixelIconResId: Int? = null,
    val updatedAtMs: Long = 0L
)

/**
 * Single selectable row in a declarative picker dialog requested by a plugin.
 */
data class PluginPickerOption(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val iconResId: Int = 0
)

/**
 * Active picker dialog request to be rendered natively inside BDSA's overlay.
 */
data class PluginPickerRequest(
    val requestId: Long,
    val tileKey: String,
    val packageName: String,
    val tileId: String,
    val title: String,
    val titleIconResId: Int,
    val options: List<PluginPickerOption>,
    val selectedOptionId: String?,
    val dismissOverlayOnSelect: Boolean
)

data class PluginToastEvent(
    val eventId: Long,
    val message: String
)

data class PluginDismissEvent(
    val eventId: Long
)
