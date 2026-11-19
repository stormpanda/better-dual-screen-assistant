package com.stormpanda.betterdualscreenassistant.plugin

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.stormpanda.betterdualscreenassistant.BuildConfig
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.DirectBootPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Discovers, persists, and coordinates third-party BDSA quick-toggle plugins.
 *
 * Security & stability invariants:
 * - Plugins run in their own Android process; BDSA never exposes `PServerBinder` or
 *   `PrivilegedHelperMain` to plugins.
 * - All newly discovered plugin tiles default to OFF until explicitly enabled by the user.
 * - Every `ContentProvider` call verifies `callingPackage` owns the target plugin and tile.
 * - All strings and option counts are clamped, and per-package IPC rates are throttled.
 */
class PluginRegistry private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PluginRegistry"
        internal const val PREFS_NAME = "bdsa_plugins_prefs"
        private const val PREF_DISABLED_PLUGINS = "disabled_plugins"
        private const val PREF_ENABLED_PLUGIN_TILES = "enabled_plugin_tiles"
        private const val PREF_CACHED_TILE_STATES = "cached_tile_states"

        private const val RATE_LIMIT_WINDOW_MS = 1_000L
        private const val RATE_LIMIT_MAX_CALLS = 20
        private const val RECENT_INTERACTION_WINDOW_MS = 5_000L

        @Volatile
        private var instance: PluginRegistry? = null

        fun getInstance(context: Context): PluginRegistry {
            return instance ?: synchronized(this) {
                instance ?: PluginRegistry(context.applicationContext).also { instance = it }
            }
        }

        @VisibleForTesting
        internal fun resetForTesting() {
            synchronized(this) {
                instance = null
            }
            PluginIconLoader.clearCache()
        }

        internal fun clampString(raw: String?, maxLen: Int): String {
            if (raw.isNullOrBlank()) return ""
            val trimmed = raw.trim()
            return if (trimmed.length > maxLen) trimmed.take(maxLen) else trimmed
        }

        internal fun clampGridLabel(raw: String?, maxLen: Int = BdsaPluginContract.MAX_LABEL_LENGTH): String {
            if (raw.isNullOrBlank()) return ""
            // Preserve intentional single '\n' line breaks for 2x4 grid tiles, strip control chars.
            val sanitized = raw.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .filter { it == '\n' || !it.isISOControl() }
            return if (sanitized.length > maxLen) sanitized.take(maxLen) else sanitized
        }
    }

    private val prefs: SharedPreferences = DirectBootPrefs.open(context, PREFS_NAME)
    private val lock = Any()
    private val eventIdGen = AtomicLong(1L)

    private val _plugins = MutableStateFlow<List<RegisteredPlugin>>(emptyList())
    val plugins: StateFlow<List<RegisteredPlugin>> = _plugins.asStateFlow()

    private val _enabledPluginTileKeys = MutableStateFlow<Set<String>>(readSavedEnabledTiles())
    val enabledPluginTileKeys: StateFlow<Set<String>> = _enabledPluginTileKeys.asStateFlow()

    private val _tileRuntimeStates = MutableStateFlow<Map<String, PluginTileRuntimeState>>(readSavedRuntimeStates())
    val tileRuntimeStates: StateFlow<Map<String, PluginTileRuntimeState>> = _tileRuntimeStates.asStateFlow()

    private val _activePickerRequest = MutableStateFlow<PluginPickerRequest?>(null)
    val activePickerRequest: StateFlow<PluginPickerRequest?> = _activePickerRequest.asStateFlow()

    private val _toastEvents = MutableSharedFlow<PluginToastEvent>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<PluginToastEvent> = _toastEvents.asSharedFlow()

    private val _dismissOverlayEvents = MutableSharedFlow<PluginDismissEvent>(extraBufferCapacity = 4)
    val dismissOverlayEvents: SharedFlow<PluginDismissEvent> = _dismissOverlayEvents.asSharedFlow()

    @Volatile
    var isOverlayShowing: Boolean = false
        private set

    private val lastInteractionByPackageMs = ConcurrentHashMap<String, Long>()
    private val rateWindowByPackage = ConcurrentHashMap<String, Pair<Long, Int>>()

    /** Optional hook invoked when a plugin is actually uninstalled (not updated) so tile order can prune dead keys. */
    @Volatile
    var onPluginTilesRemovedListener: ((Set<String>) -> Unit)? = null

    /** Optional hook invoked when plugin tile enablement changes so BDSA can ensure >= 1 tile remains active. */
    @Volatile
    var onPluginTileEnablementChangedListener: (() -> Unit)? = null

    init {
        refreshPlugins()
    }

    fun setOverlayShowing(showing: Boolean) {
        isOverlayShowing = showing
        if (!showing) {
            clearActivePicker()
        }
    }

    fun clearActivePicker() {
        _activePickerRequest.value = null
    }

    /**
     * Scans installed packages for BroadcastReceivers declaring [BdsaPluginContract.ACTION_PLUGIN_TILE].
     */
    fun refreshPlugins() {
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager != null && !userManager.isUserUnlocked) {
            Log.d(TAG, "Skipping plugin scan while device is locked (Direct Boot)")
            return
        }

        val discovered = scanInstalledPlugins()
        synchronized(lock) {
            _plugins.value = discovered
            // Clean up any picker belonging to a removed/disabled plugin
            val activePicker = _activePickerRequest.value
            if (activePicker != null && findEnabledTile(activePicker.tileKey) == null) {
                _activePickerRequest.value = null
            }
        }
    }

    fun onUserUnlocked() {
        refreshPlugins()
    }

    /**
     * Handles package lifecycle broadcasts (`PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_REPLACED`,
     * `PACKAGE_CHANGED`). Preserves user-enabled tiles and order across updates (`isReplacing == true`),
     * and cleans up persisted state on full uninstall.
     */
    fun onPackageChanged(action: String?, packageName: String?, isReplacing: Boolean) {
        if (packageName.isNullOrBlank() || packageName == context.packageName) return
        PluginIconLoader.evictPackage(packageName)

        if (action == Intent.ACTION_PACKAGE_REMOVED && !isReplacing) {
            val removedKeys = prunePersistedStateForPackage(packageName)
            if (removedKeys.isNotEmpty()) {
                onPluginTilesRemovedListener?.invoke(removedKeys)
            }
        }
        refreshPlugins()
        if (action == Intent.ACTION_PACKAGE_REMOVED && !isReplacing) {
            onPluginTileEnablementChangedListener?.invoke()
        }
    }

    fun setPluginEnabled(packageName: String, enabled: Boolean) {
        synchronized(lock) {
            val disabledPlugins = readSavedDisabledPlugins().toMutableSet()
            if (enabled) {
                disabledPlugins.remove(packageName)
            } else {
                disabledPlugins.add(packageName)
            }
            saveDisabledPlugins(disabledPlugins)
            _plugins.value = _plugins.value.map { plugin ->
                if (plugin.packageName == packageName) {
                    plugin.copy(isPluginEnabled = enabled)
                } else {
                    plugin
                }
            }
            if (!enabled) {
                val picker = _activePickerRequest.value
                if (picker != null && picker.packageName == packageName) {
                    _activePickerRequest.value = null
                }
            }
        }
        onPluginTileEnablementChangedListener?.invoke()
    }

    fun setPluginTileEnabled(tileKey: String, enabled: Boolean): Boolean {
        val parsed = BdsaPluginContract.parsePluginTileKey(tileKey) ?: return false
        val tileDef = findTileDefinition(tileKey) ?: return false
        val plugin = _plugins.value.firstOrNull { it.packageName == parsed.first } ?: return false
        if (enabled && (!plugin.isApiCompatible || !plugin.isPluginEnabled)) {
            return false
        }

        synchronized(lock) {
            val updated = _enabledPluginTileKeys.value.toMutableSet()
            if (enabled) {
                updated.add(tileKey)
            } else {
                updated.remove(tileKey)
            }
            _enabledPluginTileKeys.value = updated.toSet()
            saveEnabledTiles(updated)
        }
        if (enabled) {
            dispatchRefreshTile(tileDef, isDarkTheme = false)
        }
        onPluginTileEnablementChangedListener?.invoke()
        return true
    }

    fun disableAllPluginTiles() {
        synchronized(lock) {
            if (_enabledPluginTileKeys.value.isEmpty()) return
            _enabledPluginTileKeys.value = emptySet()
            saveEnabledTiles(emptySet())
        }
    }

    /**
     * Returns all tiles from compatible, enabled plugins that are supported on [profile].
     */
    fun availableTilesFor(profile: DeviceProfile): List<Pair<RegisteredPlugin, PluginTileDefinition>> {
        val list = mutableListOf<Pair<RegisteredPlugin, PluginTileDefinition>>()
        for (plugin in _plugins.value) {
            if (!plugin.isApiCompatible) continue
            for (tile in plugin.supportedTilesOn(profile)) {
                list.add(plugin to tile)
            }
        }
        return list
    }

    /**
     * Returns only currently active plugin tiles (plugin is compatible & enabled, tile is supported
     * on [profile], and user has explicitly toggled the tile ON).
     */
    fun activePluginTilesMap(profile: DeviceProfile): Map<String, Pair<RegisteredPlugin, PluginTileDefinition>> {
        val enabledKeys = _enabledPluginTileKeys.value
        if (enabledKeys.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Pair<RegisteredPlugin, PluginTileDefinition>>()
        for (plugin in _plugins.value) {
            if (!plugin.isApiCompatible || !plugin.isPluginEnabled) continue
            for (tile in plugin.supportedTilesOn(profile)) {
                if (tile.tileKey in enabledKeys) {
                    result[tile.tileKey] = plugin to tile
                }
            }
        }
        return result
    }

    fun findPluginForPackage(packageName: String): RegisteredPlugin? =
        _plugins.value.firstOrNull { it.packageName == packageName }

    fun findTileDefinition(tileKey: String): PluginTileDefinition? {
        for (plugin in _plugins.value) {
            plugin.tiles.firstOrNull { it.tileKey == tileKey }?.let { return it }
        }
        return null
    }

    private fun findEnabledTile(tileKey: String): Pair<RegisteredPlugin, PluginTileDefinition>? {
        val enabledKeys = _enabledPluginTileKeys.value
        if (tileKey !in enabledKeys) return null
        for (plugin in _plugins.value) {
            if (!plugin.isApiCompatible || !plugin.isPluginEnabled) continue
            plugin.tiles.firstOrNull { it.tileKey == tileKey }?.let { return plugin to it }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // BDSA -> Plugin Broadcast Dispatch
    // -------------------------------------------------------------------------

    fun dispatchTileClick(tile: PluginTileDefinition, isDarkTheme: Boolean) {
        lastInteractionByPackageMs[tile.packageName] = SystemClock.elapsedRealtime()
        sendPluginBroadcast(
            action = BdsaPluginContract.ACTION_TILE_CLICK,
            tile = tile,
            isDarkTheme = isDarkTheme
        )
        if (tile.dismissOnClick) {
            _dismissOverlayEvents.tryEmit(PluginDismissEvent(eventIdGen.incrementAndGet()))
        }
    }

    fun dispatchTileLongClick(tile: PluginTileDefinition, isDarkTheme: Boolean) {
        lastInteractionByPackageMs[tile.packageName] = SystemClock.elapsedRealtime()
        sendPluginBroadcast(
            action = BdsaPluginContract.ACTION_TILE_LONG_CLICK,
            tile = tile,
            isDarkTheme = isDarkTheme
        )
        if (tile.dismissOnLongClick) {
            _dismissOverlayEvents.tryEmit(PluginDismissEvent(eventIdGen.incrementAndGet()))
        }
    }

    fun dispatchOptionSelected(
        request: PluginPickerRequest,
        optionId: String,
        isDarkTheme: Boolean
    ) {
        val tile = findTileDefinition(request.tileKey) ?: return
        lastInteractionByPackageMs[tile.packageName] = SystemClock.elapsedRealtime()
        sendPluginBroadcast(
            action = BdsaPluginContract.ACTION_TILE_OPTION_SELECTED,
            tile = tile,
            isDarkTheme = isDarkTheme,
            extraConfigurator = { putExtra(BdsaPluginContract.EXTRA_OPTION_ID, optionId) }
        )
        if (request.dismissOverlayOnSelect) {
            _dismissOverlayEvents.tryEmit(PluginDismissEvent(eventIdGen.incrementAndGet()))
        }
    }

    fun dispatchRefreshAllEnabledTiles(profile: DeviceProfile, isDarkTheme: Boolean) {
        val active = activePluginTilesMap(profile)
        for ((_, pair) in active) {
            dispatchRefreshTile(pair.second, isDarkTheme)
        }
    }

    fun dispatchRefreshTile(tile: PluginTileDefinition, isDarkTheme: Boolean) {
        sendPluginBroadcast(
            action = BdsaPluginContract.ACTION_TILE_REFRESH,
            tile = tile,
            isDarkTheme = isDarkTheme
        )
    }

    private fun sendPluginBroadcast(
        action: String,
        tile: PluginTileDefinition,
        isDarkTheme: Boolean,
        extraConfigurator: (Intent.() -> Unit)? = null
    ) {
        val profile = DeviceProfile.current(context)
        val intent = Intent(action).apply {
            component = tile.receiverComponent
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(BdsaPluginContract.EXTRA_API_VERSION, BdsaPluginContract.API_VERSION)
            putExtra(BdsaPluginContract.EXTRA_TILE_ID, tile.tileId)
            putExtra(BdsaPluginContract.EXTRA_TOP_DISPLAY_ID, profile.topDisplayId)
            putExtra(BdsaPluginContract.EXTRA_BOTTOM_DISPLAY_ID, profile.bottomDisplayId)
            putExtra(BdsaPluginContract.EXTRA_DEVICE_ID, BdsaPluginContract.deviceIdFor(profile))
            putExtra(BdsaPluginContract.EXTRA_IS_DARK_THEME, isDarkTheme)
            extraConfigurator?.invoke(this)
        }
        try {
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to send $action to ${tile.receiverComponent}", t)
        }
    }

    // -------------------------------------------------------------------------
    // Plugin -> BDSA ContentProvider Call Handlers (Caller-Verified)
    // -------------------------------------------------------------------------

    fun handleProviderCall(
        callingPackage: String?,
        method: String,
        extras: Bundle?
    ): Bundle {
        if (callingPackage.isNullOrBlank()) {
            return errorBundle("missing_caller")
        }
        if (!allowRateLimitedCall(callingPackage)) {
            return errorBundle("rate_limited")
        }

        // Allow get_host_info even if the plugin is disabled (returns is_plugin_enabled = false).
        if (method == BdsaPluginContract.METHOD_GET_HOST_INFO) {
            return handleGetHostInfo(callingPackage)
        }

        val plugin = verifyActiveCallerPlugin(callingPackage)
            ?: return errorBundle("plugin_not_registered_or_disabled")

        return when (method) {
            BdsaPluginContract.METHOD_UPDATE_TILE -> handleUpdateTile(plugin, extras)
            BdsaPluginContract.METHOD_SHOW_PICKER -> handleShowPicker(plugin, extras)
            BdsaPluginContract.METHOD_SHOW_TOAST -> handleShowToast(plugin, extras)
            BdsaPluginContract.METHOD_DISMISS_OVERLAY -> handleDismissOverlay(plugin)
            else -> errorBundle("unknown_method")
        }
    }

    private fun handleGetHostInfo(callingPackage: String): Bundle {
        val profile = DeviceProfile.current(context)
        val plugin = findPluginForPackage(callingPackage)
        val enabledKeys = _enabledPluginTileKeys.value
        val enabledTileIds = ArrayList<String>()
        if (plugin != null && plugin.isApiCompatible && plugin.isPluginEnabled) {
            for (tile in plugin.tiles) {
                if (tile.tileKey in enabledKeys) {
                    enabledTileIds.add(tile.tileId)
                }
            }
        }
        return Bundle().apply {
            putBoolean(BdsaPluginContract.EXTRA_OK, true)
            putInt(BdsaPluginContract.EXTRA_API_VERSION, BdsaPluginContract.API_VERSION)
            putString(BdsaPluginContract.EXTRA_BDSA_VERSION, BuildConfig.VERSION_NAME)
            putString(BdsaPluginContract.EXTRA_DEVICE_ID, BdsaPluginContract.deviceIdFor(profile))
            putInt(BdsaPluginContract.EXTRA_TOP_DISPLAY_ID, profile.topDisplayId)
            putInt(BdsaPluginContract.EXTRA_BOTTOM_DISPLAY_ID, profile.bottomDisplayId)
            putBoolean(
                BdsaPluginContract.EXTRA_IS_PLUGIN_ENABLED,
                plugin != null && plugin.isApiCompatible && plugin.isPluginEnabled
            )
            putStringArrayList(BdsaPluginContract.EXTRA_ENABLED_TILE_IDS, enabledTileIds)
        }
    }

    private fun handleUpdateTile(plugin: RegisteredPlugin, extras: Bundle?): Bundle {
        if (extras == null) return errorBundle("missing_extras")
        val tileId = extras.getString(BdsaPluginContract.EXTRA_TILE_ID)?.trim().orEmpty()
        val tileDef = plugin.tiles.firstOrNull { it.tileId == tileId }
            ?: return errorBundle("unknown_tile_id")
        val tileKey = tileDef.tileKey

        synchronized(lock) {
            val currentMap = _tileRuntimeStates.value.toMutableMap()
            val prev = currentMap[tileKey] ?: PluginTileRuntimeState()

            val nextLabel = if (extras.containsKey(BdsaPluginContract.EXTRA_LABEL)) {
                val raw = extras.getString(BdsaPluginContract.EXTRA_LABEL)
                clampGridLabel(raw).ifEmpty { null }
            } else {
                prev.label
            }
            val nextSelected = if (extras.containsKey(BdsaPluginContract.EXTRA_SELECTED)) {
                extras.getBoolean(BdsaPluginContract.EXTRA_SELECTED, prev.selected)
            } else {
                prev.selected
            }
            val nextEnabled = if (extras.containsKey(BdsaPluginContract.EXTRA_ENABLED)) {
                extras.getBoolean(BdsaPluginContract.EXTRA_ENABLED, prev.enabled)
            } else {
                prev.enabled
            }
            val nextIconRes = if (extras.containsKey(BdsaPluginContract.EXTRA_ICON_RES)) {
                extras.getInt(BdsaPluginContract.EXTRA_ICON_RES, 0).takeIf { it != 0 }
            } else {
                prev.iconResId
            }
            val nextPixelIconRes = if (extras.containsKey(BdsaPluginContract.EXTRA_PIXEL_ICON_RES)) {
                extras.getInt(BdsaPluginContract.EXTRA_PIXEL_ICON_RES, 0).takeIf { it != 0 }
            } else {
                prev.pixelIconResId
            }

            currentMap[tileKey] = PluginTileRuntimeState(
                label = nextLabel,
                selected = nextSelected,
                enabled = nextEnabled,
                iconResId = nextIconRes,
                pixelIconResId = nextPixelIconRes,
                updatedAtMs = System.currentTimeMillis()
            )
            _tileRuntimeStates.value = currentMap.toMap()
            saveRuntimeStates(currentMap)
        }

        val toast = clampString(
            extras.getString(BdsaPluginContract.EXTRA_TOAST),
            BdsaPluginContract.MAX_TOAST_LENGTH
        )
        if (toast.isNotEmpty() && isInteractionWindowActive(plugin.packageName)) {
            _toastEvents.tryEmit(PluginToastEvent(eventIdGen.incrementAndGet(), toast))
        }
        if (extras.getBoolean(BdsaPluginContract.EXTRA_DISMISS_OVERLAY, false) && isOverlayShowing) {
            _dismissOverlayEvents.tryEmit(PluginDismissEvent(eventIdGen.incrementAndGet()))
        }
        return okBundle()
    }

    private fun handleShowPicker(plugin: RegisteredPlugin, extras: Bundle?): Bundle {
        if (extras == null) return errorBundle("missing_extras")
        if (!isOverlayShowing) return errorBundle("overlay_not_showing")

        val tileId = extras.getString(BdsaPluginContract.EXTRA_TILE_ID)?.trim().orEmpty()
        val tileDef = plugin.tiles.firstOrNull { it.tileId == tileId }
            ?: return errorBundle("unknown_tile_id")
        if (tileDef.tileKey !in _enabledPluginTileKeys.value) {
            return errorBundle("tile_not_enabled")
        }

        val ids = extras.getStringArrayList(BdsaPluginContract.EXTRA_OPTION_IDS)
            ?: return errorBundle("missing_option_ids")
        val labels = extras.getStringArrayList(BdsaPluginContract.EXTRA_OPTION_LABELS)
            ?: return errorBundle("missing_option_labels")
        if (ids.isEmpty() || ids.size != labels.size) {
            return errorBundle("invalid_options")
        }

        val subtitles = extras.getStringArrayList(BdsaPluginContract.EXTRA_OPTION_SUBTITLES)
        val iconResArr = extras.getIntArray(BdsaPluginContract.EXTRA_OPTION_ICON_RES)
        val clampedCount = minOf(ids.size, BdsaPluginContract.MAX_PICKER_OPTIONS)

        val options = ArrayList<PluginPickerOption>(clampedCount)
        val seenIds = HashSet<String>(clampedCount)
        for (i in 0 until clampedCount) {
            val optId = clampString(ids[i], BdsaPluginContract.MAX_TILE_ID_LENGTH)
            val optLabel = clampString(labels[i], BdsaPluginContract.MAX_PICKER_OPTION_LABEL_LENGTH)
            if (optId.isEmpty() || optLabel.isEmpty() || !seenIds.add(optId)) continue
            val optSubtitle = subtitles?.getOrNull(i)?.let {
                clampString(it, BdsaPluginContract.MAX_PICKER_OPTION_SUBTITLE_LENGTH).ifEmpty { null }
            }
            val optIcon = iconResArr?.getOrNull(i) ?: 0
            options.add(
                PluginPickerOption(
                    id = optId,
                    label = optLabel,
                    subtitle = optSubtitle,
                    iconResId = optIcon
                )
            )
        }
        if (options.isEmpty()) return errorBundle("invalid_options")

        val title = clampString(
            extras.getString(BdsaPluginContract.EXTRA_DIALOG_TITLE),
            BdsaPluginContract.MAX_PICKER_TITLE_LENGTH
        ).ifEmpty { tileDef.listTitle }

        val selectedOptionId = extras.getString(BdsaPluginContract.EXTRA_SELECTED_OPTION_ID)
            ?.let { clampString(it, BdsaPluginContract.MAX_TILE_ID_LENGTH).ifEmpty { null } }
        val dismissOverlayOnSelect =
            extras.getBoolean(BdsaPluginContract.EXTRA_DISMISS_OVERLAY_ON_SELECT, false)

        _activePickerRequest.value = PluginPickerRequest(
            requestId = eventIdGen.incrementAndGet(),
            tileKey = tileDef.tileKey,
            packageName = plugin.packageName,
            tileId = tileDef.tileId,
            title = title,
            titleIconResId = tileDef.iconResId,
            options = options,
            selectedOptionId = selectedOptionId,
            dismissOverlayOnSelect = dismissOverlayOnSelect
        )
        return okBundle()
    }

    private fun handleShowToast(plugin: RegisteredPlugin, extras: Bundle?): Bundle {
        if (!isInteractionWindowActive(plugin.packageName)) {
            return errorBundle("overlay_not_active")
        }
        val msg = clampString(
            extras?.getString(BdsaPluginContract.EXTRA_MESSAGE)
                ?: extras?.getString(BdsaPluginContract.EXTRA_TOAST),
            BdsaPluginContract.MAX_TOAST_LENGTH
        )
        if (msg.isEmpty()) return errorBundle("empty_message")
        _toastEvents.tryEmit(PluginToastEvent(eventIdGen.incrementAndGet(), msg))
        return okBundle()
    }

    private fun handleDismissOverlay(plugin: RegisteredPlugin): Bundle {
        if (!isOverlayShowing && !isInteractionWindowActive(plugin.packageName)) {
            return errorBundle("overlay_not_showing")
        }
        _dismissOverlayEvents.tryEmit(PluginDismissEvent(eventIdGen.incrementAndGet()))
        return okBundle()
    }

    private fun verifyActiveCallerPlugin(callingPackage: String): RegisteredPlugin? {
        var plugin = findPluginForPackage(callingPackage)
        if (plugin == null) {
            // Package might have been installed moments ago before broadcast arrived; do one fresh scan.
            refreshPlugins()
            plugin = findPluginForPackage(callingPackage)
        }
        if (plugin == null || !plugin.isApiCompatible || !plugin.isPluginEnabled) return null
        return plugin
    }

    private fun isInteractionWindowActive(packageName: String): Boolean {
        if (isOverlayShowing) return true
        val lastMs = lastInteractionByPackageMs[packageName] ?: return false
        return (SystemClock.elapsedRealtime() - lastMs) <= RECENT_INTERACTION_WINDOW_MS
    }

    private fun allowRateLimitedCall(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val prev = rateWindowByPackage[packageName]
        return if (prev == null || (now - prev.first) >= RATE_LIMIT_WINDOW_MS) {
            rateWindowByPackage[packageName] = now to 1
            true
        } else if (prev.second < RATE_LIMIT_MAX_CALLS) {
            rateWindowByPackage[packageName] = prev.first to (prev.second + 1)
            true
        } else {
            Log.w(TAG, "Rate-limited plugin provider call from $packageName")
            false
        }
    }

    private fun okBundle(): Bundle = Bundle().apply {
        putBoolean(BdsaPluginContract.EXTRA_OK, true)
    }

    private fun errorBundle(code: String): Bundle = Bundle().apply {
        putBoolean(BdsaPluginContract.EXTRA_OK, false)
        putString(BdsaPluginContract.EXTRA_ERROR, code)
    }

    // -------------------------------------------------------------------------
    // Manifest Discovery & Resource Parsing
    // -------------------------------------------------------------------------

    @SuppressLint("QueryPermissionsNeeded")
    private fun scanInstalledPlugins(): List<RegisteredPlugin> {
        val pm = context.packageManager
        val intent = Intent(BdsaPluginContract.ACTION_PLUGIN_TILE)
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val resolveInfos: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryBroadcastReceivers(intent, flags)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to query plugin receivers", t)
            return emptyList()
        }

        val disabledPlugins = readSavedDisabledPlugins()
        val byPackage = LinkedHashMap<String, RegisteredPlugin>()

        for (ri in resolveInfos) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == context.packageName) continue
            if (!activityInfo.exported) {
                Log.w(TAG, "Skipping non-exported plugin receiver ${activityInfo.name} in $pkg")
                continue
            }

            val parsed = parseReceiverPlugin(
                pm = pm,
                packageName = pkg,
                receiverClassName = activityInfo.name,
                metaData = activityInfo.metaData,
                isPluginEnabled = pkg !in disabledPlugins
            ) ?: continue

            val existing = byPackage[pkg]
            if (existing == null) {
                byPackage[pkg] = parsed
            } else {
                // Merge additional receiver tiles for the same package if multiple receivers declared
                val mergedTiles = (existing.tiles + parsed.tiles)
                    .distinctBy { it.tileId }
                    .take(BdsaPluginContract.MAX_TILES_PER_PLUGIN)
                byPackage[pkg] = existing.copy(
                    tiles = mergedTiles,
                    description = existing.description.ifBlank { parsed.description },
                    author = existing.author.ifBlank { parsed.author },
                    settingsActivity = existing.settingsActivity ?: parsed.settingsActivity
                )
            }
        }

        return byPackage.values.sortedBy { it.appName.lowercase() }
    }

    private fun parseReceiverPlugin(
        pm: PackageManager,
        packageName: String,
        receiverClassName: String,
        metaData: Bundle?,
        isPluginEnabled: Boolean
    ): RegisteredPlugin? {
        if (metaData == null) return null

        val pkgInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to load PackageInfo for $packageName", t)
            return null
        }

        val localizedRes = getLocalizedResourcesForPackage(pm, packageName)
        val appName = try {
            pkgInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                ?.takeIf { it.isNotBlank() } ?: packageName
        } catch (_: Throwable) {
            packageName
        }
        val versionName = pkgInfo.versionName ?: ""
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }
        val receiverComponent = ComponentName(packageName, receiverClassName)

        val configXmlResId = metaData.getInt(BdsaPluginContract.META_CONFIG, 0)
        if (configXmlResId != 0 && localizedRes != null) {
            return parseXmlPluginConfig(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                receiverComponent = receiverComponent,
                pkgRes = localizedRes,
                xmlResId = configXmlResId,
                isPluginEnabled = isPluginEnabled
            )
        }

        return parseFlatMetaDataPlugin(
            packageName = packageName,
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            receiverComponent = receiverComponent,
            metaData = metaData,
            pkgRes = localizedRes,
            isPluginEnabled = isPluginEnabled
        )
    }

    private fun parseXmlPluginConfig(
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        receiverComponent: ComponentName,
        pkgRes: Resources,
        xmlResId: Int,
        isPluginEnabled: Boolean
    ): RegisteredPlugin? {
        val parser: XmlResourceParser = try {
            pkgRes.getXml(xmlResId)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to open plugin XML config res=$xmlResId in $packageName", t)
            return null
        }

        var apiVersion = BdsaPluginContract.API_VERSION
        var pluginDescription = ""
        var pluginAuthor = ""
        var settingsActivity: ComponentName? = null
        val tiles = mutableListOf<PluginTileDefinition>()
        val seenIds = HashSet<String>()

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        BdsaPluginContract.XML_TAG_PLUGIN -> {
                            apiVersion = readXmlIntAttr(
                                parser,
                                BdsaPluginContract.XML_ATTR_API_VERSION,
                                BdsaPluginContract.API_VERSION
                            )
                            pluginDescription = clampString(
                                readXmlStringAttr(parser, pkgRes, BdsaPluginContract.XML_ATTR_DESCRIPTION),
                                BdsaPluginContract.MAX_DESCRIPTION_LENGTH
                            )
                            pluginAuthor = clampString(
                                readXmlStringAttr(parser, pkgRes, BdsaPluginContract.XML_ATTR_AUTHOR),
                                BdsaPluginContract.MAX_AUTHOR_LENGTH
                            )
                            val rawSettings = readXmlStringAttr(
                                parser,
                                pkgRes,
                                BdsaPluginContract.XML_ATTR_SETTINGS_ACTIVITY
                            )
                            settingsActivity = resolveComponentName(packageName, rawSettings)
                        }

                        BdsaPluginContract.XML_TAG_TILE -> {
                            if (tiles.size < BdsaPluginContract.MAX_TILES_PER_PLUGIN) {
                                val tileId = readXmlStringAttr(
                                    parser,
                                    pkgRes,
                                    BdsaPluginContract.XML_ATTR_ID
                                )?.trim().orEmpty()
                                val rawTitle = readXmlStringAttr(
                                    parser,
                                    pkgRes,
                                    BdsaPluginContract.XML_ATTR_TITLE
                                )
                                val title = clampGridLabel(rawTitle)
                                if (BdsaPluginContract.isValidTileId(tileId) &&
                                    title.isNotEmpty() &&
                                    seenIds.add(tileId)
                                ) {
                                    val tileDesc = clampString(
                                        readXmlStringAttr(
                                            parser,
                                            pkgRes,
                                            BdsaPluginContract.XML_ATTR_DESCRIPTION
                                        ),
                                        BdsaPluginContract.MAX_DESCRIPTION_LENGTH
                                    )
                                    val iconRes = readXmlResAttr(
                                        parser,
                                        pkgRes,
                                        packageName,
                                        BdsaPluginContract.XML_ATTR_ICON
                                    )
                                    val pixelIconRes = readXmlResAttr(
                                        parser,
                                        pkgRes,
                                        packageName,
                                        BdsaPluginContract.XML_ATTR_PIXEL_ICON
                                    )
                                    val supportsLongPress = readXmlBoolAttr(
                                        parser,
                                        BdsaPluginContract.XML_ATTR_SUPPORTS_LONG_PRESS,
                                        false
                                    )
                                    val dismissOnClick = readXmlBoolAttr(
                                        parser,
                                        BdsaPluginContract.XML_ATTR_DISMISS_ON_CLICK,
                                        false
                                    )
                                    val dismissOnLongClick = readXmlBoolAttr(
                                        parser,
                                        BdsaPluginContract.XML_ATTR_DISMISS_ON_LONG_CLICK,
                                        false
                                    )
                                    val devices = BdsaPluginContract.parseSupportedDevices(
                                        readXmlStringAttr(
                                            parser,
                                            pkgRes,
                                            BdsaPluginContract.XML_ATTR_SUPPORTED_DEVICES
                                        )
                                    )
                                    tiles.add(
                                        PluginTileDefinition(
                                            packageName = packageName,
                                            tileId = tileId,
                                            receiverComponent = receiverComponent,
                                            title = title,
                                            gridTitle = title,
                                            description = tileDesc,
                                            iconResId = iconRes,
                                            pixelIconResId = pixelIconRes,
                                            supportsLongPress = supportsLongPress,
                                            dismissOnClick = dismissOnClick,
                                            dismissOnLongClick = dismissOnLongClick,
                                            supportedDevices = devices
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed parsing XML plugin config in $packageName", t)
            return null
        } finally {
            try {
                parser.close()
            } catch (_: Throwable) {
            }
        }

        if (tiles.isEmpty()) return null
        return RegisteredPlugin(
            packageName = packageName,
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            author = pluginAuthor,
            description = pluginDescription,
            apiVersion = apiVersion,
            isPluginEnabled = isPluginEnabled,
            settingsActivity = settingsActivity,
            receiverComponent = receiverComponent,
            tiles = tiles
        )
    }

    private fun parseFlatMetaDataPlugin(
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        receiverComponent: ComponentName,
        metaData: Bundle,
        pkgRes: Resources?,
        isPluginEnabled: Boolean
    ): RegisteredPlugin? {
        val tileId = metaData.getString(BdsaPluginContract.META_TILE_ID)?.trim().orEmpty()
        if (!BdsaPluginContract.isValidTileId(tileId)) return null

        val rawTitle = readMetaString(metaData, pkgRes, BdsaPluginContract.META_TILE_TITLE)
            ?: appName
        val title = clampGridLabel(rawTitle)
        if (title.isEmpty()) return null

        val apiVersion = metaData.getInt(
            BdsaPluginContract.META_API_VERSION,
            BdsaPluginContract.API_VERSION
        )
        val pluginDesc = clampString(
            readMetaString(metaData, pkgRes, BdsaPluginContract.META_PLUGIN_DESCRIPTION),
            BdsaPluginContract.MAX_DESCRIPTION_LENGTH
        )
        val tileDesc = clampString(
            readMetaString(metaData, pkgRes, BdsaPluginContract.META_TILE_DESCRIPTION),
            BdsaPluginContract.MAX_DESCRIPTION_LENGTH
        )
        val author = clampString(
            readMetaString(metaData, pkgRes, BdsaPluginContract.META_PLUGIN_AUTHOR),
            BdsaPluginContract.MAX_AUTHOR_LENGTH
        )
        val settingsActivity = resolveComponentName(
            packageName,
            metaData.getString(BdsaPluginContract.META_SETTINGS_ACTIVITY)
        )
        val iconRes = metaData.getInt(BdsaPluginContract.META_TILE_ICON, 0)
        val pixelIconRes = metaData.getInt(BdsaPluginContract.META_TILE_PIXEL_ICON, 0)
        val supportsLongPress = metaData.getBoolean(BdsaPluginContract.META_SUPPORTS_LONG_PRESS, false)
        val dismissOnClick = metaData.getBoolean(BdsaPluginContract.META_DISMISS_ON_CLICK, false)
        val dismissOnLongClick = metaData.getBoolean(BdsaPluginContract.META_DISMISS_ON_LONG_CLICK, false)
        val supportedDevices = BdsaPluginContract.parseSupportedDevices(
            metaData.getString(BdsaPluginContract.META_SUPPORTED_DEVICES)
        )

        val tile = PluginTileDefinition(
            packageName = packageName,
            tileId = tileId,
            receiverComponent = receiverComponent,
            title = title,
            gridTitle = title,
            description = tileDesc.ifEmpty { pluginDesc },
            iconResId = iconRes,
            pixelIconResId = pixelIconRes,
            supportsLongPress = supportsLongPress,
            dismissOnClick = dismissOnClick,
            dismissOnLongClick = dismissOnLongClick,
            supportedDevices = supportedDevices
        )

        return RegisteredPlugin(
            packageName = packageName,
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            author = author,
            description = pluginDesc.ifEmpty { tileDesc },
            apiVersion = apiVersion,
            isPluginEnabled = isPluginEnabled,
            settingsActivity = settingsActivity,
            receiverComponent = receiverComponent,
            tiles = listOf(tile)
        )
    }

    private fun getLocalizedResourcesForPackage(pm: PackageManager, packageName: String): Resources? {
        return try {
            val baseRes = pm.getResourcesForApplication(packageName)
            val appLocales = context.resources.configuration.locales
            if (!appLocales.isEmpty) {
                val config = Configuration(baseRes.configuration).apply {
                    setLocales(appLocales)
                }
                context.createPackageContext(packageName, 0)
                    .createConfigurationContext(config).resources
            } else {
                baseRes
            }
        } catch (_: Throwable) {
            try {
                pm.getResourcesForApplication(packageName)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun readMetaString(metaData: Bundle, pkgRes: Resources?, key: String): String? {
        if (!metaData.containsKey(key)) return null
        val resId = metaData.getInt(key, 0)
        if (resId != 0 && pkgRes != null) {
            try {
                return pkgRes.getString(resId)
            } catch (_: Throwable) {
            }
        }
        return metaData.getString(key)
    }

    private fun readXmlStringAttr(
        parser: XmlResourceParser,
        pkgRes: Resources,
        attrName: String
    ): String? {
        val resId = parser.getAttributeResourceValue(null, attrName, 0)
        if (resId != 0) {
            return try {
                pkgRes.getString(resId)
            } catch (_: Throwable) {
                null
            }
        }
        return parser.getAttributeValue(null, attrName)
    }

    private fun readXmlIntAttr(
        parser: XmlResourceParser,
        attrName: String,
        defaultValue: Int
    ): Int {
        return try {
            parser.getAttributeIntValue(null, attrName, defaultValue)
        } catch (_: Throwable) {
            parser.getAttributeValue(null, attrName)?.toIntOrNull() ?: defaultValue
        }
    }

    private fun readXmlBoolAttr(
        parser: XmlResourceParser,
        attrName: String,
        defaultValue: Boolean
    ): Boolean {
        return try {
            parser.getAttributeBooleanValue(null, attrName, defaultValue)
        } catch (_: Throwable) {
            parser.getAttributeValue(null, attrName)?.toBooleanStrictOrNull() ?: defaultValue
        }
    }

    private fun readXmlResAttr(
        parser: XmlResourceParser,
        pkgRes: Resources,
        packageName: String,
        attrName: String
    ): Int {
        val resId = parser.getAttributeResourceValue(null, attrName, 0)
        if (resId != 0) return resId
        val raw = parser.getAttributeValue(null, attrName)?.trim().orEmpty()
        if (raw.startsWith("@drawable/")) {
            val name = raw.removePrefix("@drawable/")
            return pkgRes.getIdentifier(name, "drawable", packageName)
        }
        return 0
    }

    private fun resolveComponentName(packageName: String, rawClassName: String?): ComponentName? {
        val trimmed = rawClassName?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val fullClassName = when {
            trimmed.startsWith(".") -> packageName + trimmed
            '.' !in trimmed -> "$packageName.$trimmed"
            else -> trimmed
        }
        return ComponentName(packageName, fullClassName)
    }

    // -------------------------------------------------------------------------
    // Persistence Helpers
    // -------------------------------------------------------------------------

    private fun readSavedDisabledPlugins(): Set<String> {
        val raw = prefs.getString(PREF_DISABLED_PLUGINS, null) ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun saveDisabledPlugins(disabled: Set<String>) {
        prefs.edit()
            .putString(PREF_DISABLED_PLUGINS, disabled.sorted().joinToString(","))
            .apply()
    }

    private fun readSavedEnabledTiles(): Set<String> {
        val raw = prefs.getString(PREF_ENABLED_PLUGIN_TILES, null) ?: return emptySet()
        return raw.split(',')
            .map { it.trim() }
            .filter { BdsaPluginContract.parsePluginTileKey(it) != null }
            .toSet()
    }

    private fun saveEnabledTiles(enabled: Set<String>) {
        prefs.edit()
            .putString(PREF_ENABLED_PLUGIN_TILES, enabled.sorted().joinToString(","))
            .apply()
    }

    private fun readSavedRuntimeStates(): Map<String, PluginTileRuntimeState> {
        val raw = prefs.getString(PREF_CACHED_TILE_STATES, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = HashMap<String, PluginTileRuntimeState>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (BdsaPluginContract.parsePluginTileKey(key) == null) continue
                val obj = root.optJSONObject(key) ?: continue
                val label = if (obj.has("label") && !obj.isNull("label")) {
                    clampGridLabel(obj.optString("label")).ifEmpty { null }
                } else {
                    null
                }
                result[key] = PluginTileRuntimeState(
                    label = label,
                    selected = obj.optBoolean("selected", false),
                    enabled = obj.optBoolean("enabled", true),
                    iconResId = obj.optInt("iconResId", 0).takeIf { it != 0 },
                    pixelIconResId = obj.optInt("pixelIconResId", 0).takeIf { it != 0 },
                    updatedAtMs = obj.optLong("updatedAtMs", 0L)
                )
            }
            result
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to restore cached plugin tile states", t)
            emptyMap()
        }
    }

    private fun saveRuntimeStates(states: Map<String, PluginTileRuntimeState>) {
        try {
            val root = JSONObject()
            for ((key, state) in states) {
                val obj = JSONObject().apply {
                    if (state.label != null) put("label", state.label)
                    put("selected", state.selected)
                    put("enabled", state.enabled)
                    if (state.iconResId != null) put("iconResId", state.iconResId)
                    if (state.pixelIconResId != null) put("pixelIconResId", state.pixelIconResId)
                    put("updatedAtMs", state.updatedAtMs)
                }
                root.put(key, obj)
            }
            prefs.edit().putString(PREF_CACHED_TILE_STATES, root.toString()).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to persist cached plugin tile states", t)
        }
    }

    private fun prunePersistedStateForPackage(packageName: String): Set<String> {
        val prefix = "${BdsaPluginContract.PLUGIN_TILE_KEY_PREFIX}$packageName:"
        val removedKeys = mutableSetOf<String>()
        synchronized(lock) {
            val disabled = readSavedDisabledPlugins().toMutableSet()
            if (disabled.remove(packageName)) {
                saveDisabledPlugins(disabled)
            }

            val enabled = _enabledPluginTileKeys.value.toMutableSet()
            val toRemoveEnabled = enabled.filter { it.startsWith(prefix) }
            if (toRemoveEnabled.isNotEmpty()) {
                removedKeys.addAll(toRemoveEnabled)
                enabled.removeAll(toRemoveEnabled.toSet())
                _enabledPluginTileKeys.value = enabled.toSet()
                saveEnabledTiles(enabled)
            }

            val states = _tileRuntimeStates.value.toMutableMap()
            val toRemoveStates = states.keys.filter { it.startsWith(prefix) }
            if (toRemoveStates.isNotEmpty()) {
                removedKeys.addAll(toRemoveStates)
                toRemoveStates.forEach { states.remove(it) }
                _tileRuntimeStates.value = states.toMap()
                saveRuntimeStates(states)
            }

            // Also include any known tile keys from the currently loaded plugin snapshot
            _plugins.value.firstOrNull { it.packageName == packageName }?.tiles?.forEach {
                removedKeys.add(it.tileKey)
            }
        }
        return removedKeys
    }
}
