package com.stormpanda.betterdualscreenassistant.plugin

import android.net.Uri
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile

/**
 * Public protocol contract between Better Dual Screen Assistant (BDSA) and third-party
 * quick-toggle plugin APKs.
 *
 * Plugins are out-of-process Android apps discovered via a `<receiver>` declaring
 * [ACTION_PLUGIN_TILE]. BDSA sends explicit broadcasts to the plugin's receiver and
 * plugins push state, picker dialogs, toasts, or dismiss requests back through
 * [BdsaPluginProvider] (`content://com.stormpanda.betterdualscreenassistant.plugin`).
 */
object BdsaPluginContract {
    /** Current plugin API version supported by this build of BDSA. */
    const val API_VERSION = 1

    /** Broadcast action plugins declare in `<intent-filter>` to register with BDSA. */
    const val ACTION_PLUGIN_TILE = "com.stormpanda.betterdualscreenassistant.action.PLUGIN_TILE"

    /** Sent by BDSA to a plugin receiver when the user short-presses a tile. */
    const val ACTION_TILE_CLICK = "com.stormpanda.betterdualscreenassistant.action.TILE_CLICK"

    /** Sent by BDSA to a plugin receiver when the user long-presses a tile. */
    const val ACTION_TILE_LONG_CLICK = "com.stormpanda.betterdualscreenassistant.action.TILE_LONG_CLICK"

    /** Sent by BDSA when the user chooses an option in a plugin picker dialog. */
    const val ACTION_TILE_OPTION_SELECTED = "com.stormpanda.betterdualscreenassistant.action.TILE_OPTION_SELECTED"

    /** Sent by BDSA when the overlay opens or when a tile is first enabled. */
    const val ACTION_TILE_REFRESH = "com.stormpanda.betterdualscreenassistant.action.TILE_REFRESH"

    /** Exported ContentProvider authority for plugin -> BDSA calls. */
    const val PROVIDER_AUTHORITY = "com.stormpanda.betterdualscreenassistant.plugin"
    val CONTENT_URI: Uri = Uri.parse("content://$PROVIDER_AUTHORITY")

    // ContentProvider call() methods
    const val METHOD_UPDATE_TILE = "update_tile"
    const val METHOD_SHOW_PICKER = "show_picker"
    const val METHOD_SHOW_TOAST = "show_toast"
    const val METHOD_DISMISS_OVERLAY = "dismiss_overlay"
    const val METHOD_GET_HOST_INFO = "get_host_info"

    // Broadcast & Bundle extras
    const val EXTRA_API_VERSION = "api_version"
    const val EXTRA_BDSA_VERSION = "bdsa_version"
    const val EXTRA_TILE_ID = "tile_id"
    const val EXTRA_OPTION_ID = "option_id"
    const val EXTRA_TOP_DISPLAY_ID = "top_display_id"
    const val EXTRA_BOTTOM_DISPLAY_ID = "bottom_display_id"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_IS_DARK_THEME = "is_dark_theme"

    // update_tile Bundle keys
    const val EXTRA_LABEL = "label"
    const val EXTRA_SELECTED = "selected"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_ICON_RES = "icon_res"
    const val EXTRA_PIXEL_ICON_RES = "pixel_icon_res"
    const val EXTRA_TOAST = "toast"
    const val EXTRA_DISMISS_OVERLAY = "dismiss_overlay"
    const val EXTRA_TIMER_BASE_MS = "timer_base_ms"
    const val EXTRA_TIMER_COUNT_DOWN = "timer_count_down"
    const val EXTRA_TIMER_FORMAT = "timer_format"
    const val EXTRA_TIMER_FINISHED_LABEL = "timer_finished_label"
    const val EXTRA_REFRESH_INTERVAL_MS = "refresh_interval_ms"

    // show_picker Bundle keys
    const val EXTRA_DIALOG_TITLE = "dialog_title"
    const val EXTRA_OPTION_IDS = "option_ids"
    const val EXTRA_OPTION_LABELS = "option_labels"
    const val EXTRA_OPTION_SUBTITLES = "option_subtitles"
    const val EXTRA_OPTION_ICON_RES = "option_icon_res"
    const val EXTRA_SELECTED_OPTION_ID = "selected_option_id"
    const val EXTRA_DISMISS_OVERLAY_ON_SELECT = "dismiss_overlay_on_select"

    // show_toast Bundle keys
    const val EXTRA_MESSAGE = "message"

    // get_host_info & generic response Bundle keys
    const val EXTRA_OK = "ok"
    const val EXTRA_ERROR = "error"
    const val EXTRA_IS_PLUGIN_ENABLED = "is_plugin_enabled"
    const val EXTRA_ENABLED_TILE_IDS = "enabled_tile_ids"

    // Receiver <meta-data> keys
    const val META_CONFIG = "com.stormpanda.betterdualscreenassistant.plugin.CONFIG"
    const val META_API_VERSION = "com.stormpanda.betterdualscreenassistant.plugin.API_VERSION"
    const val META_PLUGIN_DESCRIPTION = "com.stormpanda.betterdualscreenassistant.plugin.DESCRIPTION"
    const val META_PLUGIN_AUTHOR = "com.stormpanda.betterdualscreenassistant.plugin.AUTHOR"
    const val META_SETTINGS_ACTIVITY = "com.stormpanda.betterdualscreenassistant.plugin.SETTINGS_ACTIVITY"
    const val META_TILE_ID = "com.stormpanda.betterdualscreenassistant.plugin.TILE_ID"
    const val META_TILE_TITLE = "com.stormpanda.betterdualscreenassistant.plugin.TILE_TITLE"
    const val META_TILE_DESCRIPTION = "com.stormpanda.betterdualscreenassistant.plugin.TILE_DESCRIPTION"
    const val META_TILE_ICON = "com.stormpanda.betterdualscreenassistant.plugin.TILE_ICON"
    const val META_TILE_PIXEL_ICON = "com.stormpanda.betterdualscreenassistant.plugin.TILE_PIXEL_ICON"
    const val META_SUPPORTS_LONG_PRESS = "com.stormpanda.betterdualscreenassistant.plugin.SUPPORTS_LONG_PRESS"
    const val META_DISMISS_ON_CLICK = "com.stormpanda.betterdualscreenassistant.plugin.DISMISS_ON_CLICK"
    const val META_DISMISS_ON_LONG_CLICK = "com.stormpanda.betterdualscreenassistant.plugin.DISMISS_ON_LONG_CLICK"
    const val META_SUPPORTED_DEVICES = "com.stormpanda.betterdualscreenassistant.plugin.SUPPORTED_DEVICES"
    const val META_REFRESH_INTERVAL_MS = "com.stormpanda.betterdualscreenassistant.plugin.REFRESH_INTERVAL_MS"

    // XML manifest tags & attributes (for multi-tile META_CONFIG resources)
    const val XML_TAG_PLUGIN = "bdsa-plugin"
    const val XML_TAG_TILE = "tile"
    const val XML_ATTR_API_VERSION = "apiVersion"
    const val XML_ATTR_DESCRIPTION = "description"
    const val XML_ATTR_AUTHOR = "author"
    const val XML_ATTR_SETTINGS_ACTIVITY = "settingsActivity"
    const val XML_ATTR_ID = "id"
    const val XML_ATTR_TITLE = "title"
    const val XML_ATTR_ICON = "icon"
    const val XML_ATTR_PIXEL_ICON = "pixelIcon"
    const val XML_ATTR_SUPPORTS_LONG_PRESS = "supportsLongPress"
    const val XML_ATTR_DISMISS_ON_CLICK = "dismissOnClick"
    const val XML_ATTR_DISMISS_ON_LONG_CLICK = "dismissOnLongClick"
    const val XML_ATTR_SUPPORTED_DEVICES = "supportedDevices"
    const val XML_ATTR_REFRESH_INTERVAL_MS = "refreshIntervalMs"

    // Device filter tokens
    const val DEVICE_ALL = "all"
    const val DEVICE_AYN_THOR = "ayn_thor"
    const val DEVICE_RETROID_POCKET_DUO = "retroid_pocket_duo"

    // Tile key prefix & sanitization limits
    const val PLUGIN_TILE_KEY_PREFIX = "plugin:"
    const val MAX_TILE_ID_LENGTH = 64
    const val MAX_LABEL_LENGTH = 48
    const val MAX_DESCRIPTION_LENGTH = 300
    const val MAX_AUTHOR_LENGTH = 80
    const val MAX_TOAST_LENGTH = 160
    const val MAX_PICKER_TITLE_LENGTH = 64
    const val MAX_PICKER_OPTION_LABEL_LENGTH = 48
    const val MAX_PICKER_OPTION_SUBTITLE_LENGTH = 80
    const val MAX_PICKER_OPTIONS = 12
    const val MAX_TILES_PER_PLUGIN = 16
    const val MAX_ICON_SIZE_PX = 128
    const val MIN_REFRESH_INTERVAL_MS = 1_000L
    const val MAX_REFRESH_INTERVAL_MS = 60_000L

    private val TILE_ID_REGEX = Regex("^[a-zA-Z0-9._-]{1,$MAX_TILE_ID_LENGTH}$")

    fun clampRefreshIntervalMs(rawMs: Long): Long =
        if (rawMs <= 0L) 0L else rawMs.coerceIn(MIN_REFRESH_INTERVAL_MS, MAX_REFRESH_INTERVAL_MS)

    /**
     * Formats a live chronometer duration for a plugin tile without any IPC while the overlay is open.
     * Countdowns round up to the nearest second (matching standard timers) and stop at `00:00`.
     */
    fun formatLiveTimerDuration(
        baseMs: Long,
        countDown: Boolean,
        nowMs: Long
    ): Pair<String, Boolean> {
        val (displayMs, finished) = if (countDown) {
            val remaining = (baseMs - nowMs).coerceAtLeast(0L)
            remaining to (remaining == 0L)
        } else {
            val elapsed = (nowMs - baseMs).coerceAtLeast(0L)
            elapsed to false
        }
        val totalSeconds = if (countDown) {
            (displayMs / 1000L) + if (displayMs % 1000L > 0L) 1L else 0L
        } else {
            displayMs / 1000L
        }
        val hours = totalSeconds / 3600L
        val mins = (totalSeconds % 3600L) / 60L
        val secs = totalSeconds % 60L
        val formatted = if (hours > 0L) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
        return formatted to finished
    }

    fun resolveLiveTimerLabel(
        state: PluginTileRuntimeState,
        nowMs: Long
    ): Pair<String?, Boolean> {
        if (state.timerBaseMs <= 0L) return null to false
        val (durationText, finished) = formatLiveTimerDuration(
            baseMs = state.timerBaseMs,
            countDown = state.timerCountDown,
            nowMs = nowMs
        )
        if (finished && !state.timerFinishedLabel.isNullOrBlank()) {
            return state.timerFinishedLabel to true
        }
        val format = state.timerFormat
        val label = if (!format.isNullOrBlank() && "%s" in format) {
            format.replace("%s", durationText).take(MAX_LABEL_LENGTH)
        } else {
            durationText
        }
        return label to finished
    }

    fun isValidTileId(tileId: String): Boolean = TILE_ID_REGEX.matches(tileId)

    fun pluginTileKey(packageName: String, tileId: String): String =
        "$PLUGIN_TILE_KEY_PREFIX$packageName:$tileId"

    fun isPluginTileKey(key: String): Boolean =
        key.startsWith(PLUGIN_TILE_KEY_PREFIX)

    fun parsePluginTileKey(key: String): Pair<String, String>? {
        if (!key.startsWith(PLUGIN_TILE_KEY_PREFIX)) return null
        val rest = key.removePrefix(PLUGIN_TILE_KEY_PREFIX)
        val sep = rest.indexOf(':')
        if (sep <= 0 || sep >= rest.lastIndex) return null
        val pkg = rest.substring(0, sep)
        val tileId = rest.substring(sep + 1)
        if (pkg.isBlank() || !isValidTileId(tileId)) return null
        return pkg to tileId
    }

    fun deviceIdFor(profile: DeviceProfile): String = when (profile) {
        DeviceProfile.AYN_THOR -> DEVICE_AYN_THOR
        DeviceProfile.RETROID_POCKET_DUO -> DEVICE_RETROID_POCKET_DUO
    }

    fun parseSupportedDevices(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return setOf(DEVICE_ALL)
        val tokens = raw.split(',', '|', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return if (tokens.isEmpty() || DEVICE_ALL in tokens) setOf(DEVICE_ALL) else tokens
    }
}
