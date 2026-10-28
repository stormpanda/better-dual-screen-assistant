package com.stormpanda.betterdualscreenassistant.system

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.app.UiModeManager
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.ui.graphics.toArgb
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.DEFAULT_MEGINGIARD_ACCENT_ARGB
import com.stormpanda.betterdualscreenassistant.ui.theme.TccColorPalette
import com.stormpanda.betterdualscreenassistant.ui.theme.buildOsDynamicColorOverlayEntries
import com.stormpanda.betterdualscreenassistant.ui.theme.resolveTccPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Reactive repository for all AYN Thor dual-screen, performance, fan, controller,
 * brightness, volume, and system settings.
 */
class SystemSettingsRepository(private val context: Context) {

    companion object {
        const val KEY_DUAL_SCREEN_DISPLAY_MODE = "dual_screen_display_mode"
        const val KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL = "dual_screen_brightness_level"
        const val KEY_SCREEN_FOCUS_LOCK = "screen_focus_lock"
        const val KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK = "enable_system_key_focus_lock"
        const val KEY_SECONDARY_SCREEN_VOLUME_LEVEL = "secondary_screen_volume_level"
        const val KEY_SECONDARY_SCREEN_VOLUME_HEADPHONES = "secondary_screen_volume_level_for_headphones"
        const val KEY_PERFORMANCE_MODE = "performance_mode"
        const val KEY_FAN_MODE = "fan_mode"
        const val KEY_FAN_SPEED = "fan_speed"
        const val KEY_TEMP_ABXY_LAYOUT_MODE = "temp_abxy_layout_mode"
        const val KEY_FLIP_BUTTON_LAYOUT = "flip_button_layout"
        const val KEY_NO_CREATE_GAMEPAD_LAYOUT = "no_create_gamepad_button_layout"
        const val KEY_CHARGING_SEPARATION = "is_charging_separation"
        const val KEY_VIBRATE_ON = "vibrate_on"
        const val KEY_JOYSTICK_LIGHT_ENABLED = "joystick_light_enabled"
        const val KEY_FORCE_LANDSCAPE = "force_landscape"
        const val KEY_TCC_THEME_MODE = "tcc_theme_mode"
        const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"
        const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"
        const val KEY_LED_BRIGHTNESS_PERCENT = "led_light_brightness_percent"
        const val KEY_TRIGGER_INPUT_MODE = "trigger_input_mode"
        const val KEY_CHARGE_LIMIT_80 = "percent_80_charge_limit"
        const val KEY_GAMEPAD_MOUSE_MODE = "global_gamepad_to_mouse_mode"
        const val KEY_ADAPTER_ENABLE = "key_adapter_enable"
        const val KEY_SHOW_FLOATING_ICON = "is_show_floatingIcon"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_PREVENT_ACCIDENTAL_HOME = "prevent_press_home_accidentally"

        const val PREFS_NAME = "better_dsa_prefs"
        const val PREF_NO_MORE_CLOSE_TIP = "no_more_close_tip"
        const val PREF_REMEMBER_LAST_TAB = "remember_last_tab"
        const val PREF_SHOW_LED_BRIGHTNESS_SLIDER = "show_led_brightness_slider"
        const val PREF_SHOW_SWAP_SCREENS_BUTTON = "show_swap_screens_button"
        const val PREF_SHOW_EDIT_BUTTON = "show_edit_button"
        const val PREF_ACCURATE_GAME_FPS = "accurate_game_fps"
        const val PREF_APP_THEME_MODE = "app_theme_mode"
        const val PREF_CUSTOM_ACCENT_ARGB = "custom_accent_argb"
        const val PREF_INJECT_OS_THEME = "inject_os_theme"
        const val PREF_SAVED_OS_NIGHT_MODE = "saved_os_night_mode"
        const val PREF_SAVED_OS_THEME_JSON = "saved_os_theme_json"
        const val PREF_DUAL_VOLUME_PERCENT = "dual_volume_percent"
        const val PREF_DUAL_BRIGHTNESS_ACTIVE = "dual_brightness_active"
        const val PREF_DUAL_VOLUME_ACTIVE = "dual_volume_active"
        const val PREF_QUICK_TILE_ORDER = "quick_tile_order"
        const val PREF_DISABLED_QUICK_TILES = "disabled_quick_tiles"
        const val PREF_KNOWN_QUICK_TILES = "known_quick_tiles"
        const val PREF_SAVED_DUAL_SCREEN_FOCUS_LOCK = "saved_dual_screen_focus_lock"
        const val PREF_SAVED_ENABLE_SYSTEM_KEY_FOCUS_LOCK = "saved_enable_system_key_focus_lock"

        @Volatile
        private var lastAppliedOsThemeSignature: String = ""

        @Volatile
        private var instance: SystemSettingsRepository? = null

        fun getInstance(context: Context): SystemSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SystemSettingsRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private const val OS_THEME_SYNC_DEBOUNCE_MS = 650L
        internal const val REFRESH_RATE_WRITE_SETTLE_DELAY_MS = 220L

        private val MEGINGIARD_RELEASE_THEME_URI: Uri =
            Uri.parse("content://com.stormpanda.megingiard.provider/theme")
        private val MEGINGIARD_DEBUG_THEME_URI: Uri =
            Uri.parse("content://com.stormpanda.megingiard.debug.provider/theme")
    }

    private val contentResolver = context.contentResolver
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val themeSyncExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BDSA-ThemeSync").apply { isDaemon = true }
    }
    private val refreshRateWriteLock = Any()

    @Volatile
    private var pendingRefreshRateTarget: Float? = null

    @Volatile
    private var preferredMegingiardThemeUri: Uri? = null

    @Volatile
    private var isSelfDraggingBrightness: Boolean = false

    @Volatile
    private var lastSelfBrightnessChangeElapsedMs: Long = 0L

    private fun isInSelfBrightnessGraceWindow(): Boolean {
        if (isSelfDraggingBrightness) return true
        val elapsed = SystemClock.elapsedRealtime() - lastSelfBrightnessChangeElapsedMs
        return elapsed in 0L..250L
    }

    private val osThemeDeferLock = Any()
    private val activeSettingsUiTokens = LinkedHashSet<Int>()
    private var pendingDeferredOsThemeSync = false
    private var pendingDeferredOsThemeForce = false

    private val debouncedOsThemeSyncRunnable = Runnable {
        val force = synchronized(osThemeDeferLock) {
            val f = pendingDeferredOsThemeForce
            if (activeSettingsUiTokens.isEmpty()) {
                pendingDeferredOsThemeForce = false
            }
            f
        }
        dispatchOrDeferOsThemeSync(force = force)
    }

    private val debouncedRefreshRateWriteRunnable = Runnable {
        commitPendingRefreshRate()
    }

    // Display Mode: 0 = Dual, 1 = Top Only, 2 = Bottom Only
    private val _dualScreenDisplayMode = MutableStateFlow(getInt(KEY_DUAL_SCREEN_DISPLAY_MODE, 0))
    val dualScreenDisplayMode: StateFlow<Int> = _dualScreenDisplayMode.asStateFlow()

    // Focus Lock: 0 = Auto, 1 = Top, 2 = Bottom
    private val _screenFocusLock = MutableStateFlow(getInt(KEY_SCREEN_FOCUS_LOCK, 0))
    val screenFocusLock: StateFlow<Int> = _screenFocusLock.asStateFlow()

    // Performance Mode: 0 = Normal, 1 = Performance, 2 = High Performance
    private val _performanceMode = MutableStateFlow(getInt(KEY_PERFORMANCE_MODE, 0))
    val performanceMode: StateFlow<Int> = _performanceMode.asStateFlow()

    // Fan Mode: 0 = Off, 1 = Quiet, 4 = Smart, 5 = Sports, 6 = Custom
    private val _fanMode = MutableStateFlow(getInt(KEY_FAN_MODE, 4))
    val fanMode: StateFlow<Int> = _fanMode.asStateFlow()

    // Custom Fan Speed
    private val _fanSpeed = MutableStateFlow(getInt(KEY_FAN_SPEED, 50))
    val fanSpeed: StateFlow<Int> = _fanSpeed.asStateFlow()

    // Controller Style: 0 = Xbox, 1 = Odin/Standard, 2 = Disabled
    private val _controllerStyle = MutableStateFlow(getInt(KEY_TEMP_ABXY_LAYOUT_MODE, 1))
    val controllerStyle: StateFlow<Int> = _controllerStyle.asStateFlow()

    // Charging Separation (Direct Power Supply): false = 0, true = 1
    private val _chargingSeparation = MutableStateFlow(getInt(KEY_CHARGING_SEPARATION, 0) == 1)
    val chargingSeparation: StateFlow<Boolean> = _chargingSeparation.asStateFlow()

    // Vibration: false = 0, true = 1
    private val _vibrateOn = MutableStateFlow(getInt(KEY_VIBRATE_ON, 1) == 1)
    val vibrateOn: StateFlow<Boolean> = _vibrateOn.asStateFlow()

    // Ambient LED
    private val _ambientLightEnabled = MutableStateFlow(readAmbientLightState())
    val ambientLightEnabled: StateFlow<Boolean> = _ambientLightEnabled.asStateFlow()

    // Force Landscape
    private val _forceLandscape = MutableStateFlow(getInt(KEY_FORCE_LANDSCAPE, 1) == 1)
    val forceLandscape: StateFlow<Boolean> = _forceLandscape.asStateFlow()

    // Theme Mode: Stock (1, 3, 4), Sync with Megingiard (100), or Megingiard Theme (101..108)
    private val _themeMode = MutableStateFlow(readSavedThemeMode())
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _customAccentArgb = MutableStateFlow(
        prefs.getInt(PREF_CUSTOM_ACCENT_ARGB, DEFAULT_MEGINGIARD_ACCENT_ARGB)
    )
    val customAccentArgb: StateFlow<Int> = _customAccentArgb.asStateFlow()

    private val _syncedMegingiardOption = MutableStateFlow(AppThemeOption.MEGINGIARD)
    val syncedMegingiardOption: StateFlow<AppThemeOption> = _syncedMegingiardOption.asStateFlow()

    private val _syncedMegingiardAccentArgb = MutableStateFlow<Int?>(null)
    val syncedMegingiardAccentArgb: StateFlow<Int?> = _syncedMegingiardAccentArgb.asStateFlow()

    private val _isMegingiardProviderAvailable = MutableStateFlow(isAnyMegingiardInstalled())
    val isMegingiardProviderAvailable: StateFlow<Boolean> = _isMegingiardProviderAvailable.asStateFlow()

    private val _injectOsTheme = MutableStateFlow(
        prefs.getBoolean(PREF_INJECT_OS_THEME, false)
    )
    val injectOsTheme: StateFlow<Boolean> = _injectOsTheme.asStateFlow()

    private val _showCloseBottomScreenTip = MutableStateFlow(
        !prefs.getBoolean(PREF_NO_MORE_CLOSE_TIP, false)
    )
    val showCloseBottomScreenTip: StateFlow<Boolean> = _showCloseBottomScreenTip.asStateFlow()

    private val _rememberLastTab = MutableStateFlow(
        prefs.getBoolean(PREF_REMEMBER_LAST_TAB, true)
    )
    val rememberLastTab: StateFlow<Boolean> = _rememberLastTab.asStateFlow()

    private val _showLedBrightnessSlider = MutableStateFlow(
        prefs.getBoolean(PREF_SHOW_LED_BRIGHTNESS_SLIDER, true)
    )
    val showLedBrightnessSlider: StateFlow<Boolean> = _showLedBrightnessSlider.asStateFlow()

    private val _showSwapScreensButton = MutableStateFlow(
        prefs.getBoolean(PREF_SHOW_SWAP_SCREENS_BUTTON, true)
    )
    val showSwapScreensButton: StateFlow<Boolean> = _showSwapScreensButton.asStateFlow()

    private val _showEditButton = MutableStateFlow(
        prefs.getBoolean(PREF_SHOW_EDIT_BUTTON, true)
    )
    val showEditButton: StateFlow<Boolean> = _showEditButton.asStateFlow()

    private val _accurateGameFps = MutableStateFlow(
        prefs.getBoolean(PREF_ACCURATE_GAME_FPS, true)
    )
    val accurateGameFps: StateFlow<Boolean> = _accurateGameFps.asStateFlow()

    private val _ledBrightnessPercent = MutableStateFlow(readLedBrightnessPercent())
    val ledBrightnessPercent: StateFlow<Int> = _ledBrightnessPercent.asStateFlow()

    // Android System Toggles
    private val _wifiEnabled = MutableStateFlow(readWifiEnabled())
    val wifiEnabled: StateFlow<Boolean> = _wifiEnabled.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(readBluetoothEnabled())
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _dndEnabled = MutableStateFlow(readDndEnabled())
    val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

    private val _airplaneModeEnabled = MutableStateFlow(readAirplaneModeEnabled())
    val airplaneModeEnabled: StateFlow<Boolean> = _airplaneModeEnabled.asStateFlow()

    // Tools Toggles
    private val _touchLockEnabled = MutableStateFlow(false)
    val touchLockEnabled: StateFlow<Boolean> = _touchLockEnabled.asStateFlow()

    // Trigger Mode: 1 = Analog, 0 = Digital, 2 = Both
    private val _triggerMode = MutableStateFlow(getInt(KEY_TRIGGER_INPUT_MODE, 2))
    val triggerMode: StateFlow<Int> = _triggerMode.asStateFlow()

    private val _chargeLimit80 = MutableStateFlow(getInt(KEY_CHARGE_LIMIT_80, 0) == 1)
    val chargeLimit80: StateFlow<Boolean> = _chargeLimit80.asStateFlow()

    private val _gamepadMouseMode = MutableStateFlow(getInt(KEY_GAMEPAD_MOUSE_MODE, 0) == 1)
    val gamepadMouseMode: StateFlow<Boolean> = _gamepadMouseMode.asStateFlow()

    private val _keyMapperEnabled = MutableStateFlow(getInt(KEY_SHOW_FLOATING_ICON, 0) == 1)
    val keyMapperEnabled: StateFlow<Boolean> = _keyMapperEnabled.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(getInt(KEY_KEEP_SCREEN_ON, 0) == 1)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _preventAccidentalHome = MutableStateFlow(getInt(KEY_PREVENT_ACCIDENTAL_HOME, 1) == 1)
    val preventAccidentalHome: StateFlow<Boolean> = _preventAccidentalHome.asStateFlow()

    private val _quickTileOrder = MutableStateFlow(readSavedQuickTileOrder())
    val quickTileOrder: StateFlow<List<QuickSettingTileId>> = _quickTileOrder.asStateFlow()

    private val _disabledQuickTiles = MutableStateFlow(readSavedDisabledQuickTiles())
    val disabledQuickTiles: StateFlow<Set<QuickSettingTileId>> = _disabledQuickTiles.asStateFlow()

    // Refresh Rate: 60 or 120
    private val _refreshRate = MutableStateFlow(readRefreshRate())
    val refreshRate: StateFlow<Int> = _refreshRate.asStateFlow()

    // Brightness (0..100%)
    private val _dualBrightness = MutableStateFlow(getInt(KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, 55))
    val dualBrightness: StateFlow<Int> = _dualBrightness.asStateFlow()

    private val _topBrightness = MutableStateFlow(readDisplayBrightnessPercent(0))
    val topBrightness: StateFlow<Int> = _topBrightness.asStateFlow()

    private val _bottomBrightness = MutableStateFlow(readDisplayBrightnessPercent(4))
    val bottomBrightness: StateFlow<Int> = _bottomBrightness.asStateFlow()

    private val _isDualBrightnessActive = MutableStateFlow(
        prefs.getBoolean(
            PREF_DUAL_BRIGHTNESS_ACTIVE,
            _dualBrightness.value == _topBrightness.value && _dualBrightness.value == _bottomBrightness.value
        )
    )
    val isDualBrightnessActive: StateFlow<Boolean> = _isDualBrightnessActive.asStateFlow()

    // Volume (0..100% for slider UI)
    private val _dualVolumePercent = MutableStateFlow(readDualVolumePercent())
    val dualVolumePercent: StateFlow<Int> = _dualVolumePercent.asStateFlow()

    private val _primaryVolumePercent = MutableStateFlow(readPrimaryVolumePercent())
    val primaryVolumePercent: StateFlow<Int> = _primaryVolumePercent.asStateFlow()

    private val _secondaryVolumePercent = MutableStateFlow(readSecondaryVolumePercent())
    val secondaryVolumePercent: StateFlow<Int> = _secondaryVolumePercent.asStateFlow()

    private val _isDualVolumeActive = MutableStateFlow(
        prefs.getBoolean(
            PREF_DUAL_VOLUME_ACTIVE,
            _dualVolumePercent.value == _primaryVolumePercent.value && _dualVolumePercent.value == _secondaryVolumePercent.value
        )
    )
    val isDualVolumeActive: StateFlow<Boolean> = _isDualVolumeActive.asStateFlow()

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val lastSegment = uri?.lastPathSegment
            if (lastSegment == "screen_brightness") {
                if (!isInSelfBrightnessGraceWindow()) {
                    onExternalDisplayBrightnessChanged(0, fromDisplayListener = false)
                }
                return
            }
            val refreshBrightness = uri == null || lastSegment == KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL
            refreshAll(
                refreshDisplayBrightness = refreshBrightness && !isInSelfBrightnessGraceWindow(),
                refreshMegingiardTheme = false
            )
        }
    }

    private val displayBrightnessListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != 0 && displayId != 4) return
            if (isInSelfBrightnessGraceWindow()) return
            onExternalDisplayBrightnessChanged(displayId, fromDisplayListener = true)
        }
    }

    private val megingiardThemeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refreshMegingiardSyncedThemeAsync(
                changedUri = uri,
                debounceOsSyncMs = OS_THEME_SYNC_DEBOUNCE_MS
            )
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_APP_THEME_MODE -> {
                val saved = readSavedThemeMode()
                if (_themeMode.value != saved) {
                    _themeMode.value = saved
                    scheduleOsThemeSync(0L)
                }
            }
            PREF_CUSTOM_ACCENT_ARGB -> {
                val saved = prefs.getInt(
                    PREF_CUSTOM_ACCENT_ARGB,
                    DEFAULT_MEGINGIARD_ACCENT_ARGB
                )
                if (_customAccentArgb.value != saved) {
                    _customAccentArgb.value = saved
                    scheduleOsThemeSync(0L)
                }
            }
            PREF_INJECT_OS_THEME -> {
                val saved = prefs.getBoolean(PREF_INJECT_OS_THEME, false)
                if (_injectOsTheme.value != saved) {
                    _injectOsTheme.value = saved
                    scheduleOsThemeSync(0L)
                }
            }
            PREF_DUAL_VOLUME_PERCENT -> {
                _dualVolumePercent.value = readDualVolumePercent()
            }
            PREF_DUAL_BRIGHTNESS_ACTIVE -> {
                _isDualBrightnessActive.value = prefs.getBoolean(PREF_DUAL_BRIGHTNESS_ACTIVE, false)
            }
            PREF_DUAL_VOLUME_ACTIVE -> {
                _isDualVolumeActive.value = prefs.getBoolean(PREF_DUAL_VOLUME_ACTIVE, false)
            }
            PREF_NO_MORE_CLOSE_TIP -> {
                _showCloseBottomScreenTip.value = !prefs.getBoolean(PREF_NO_MORE_CLOSE_TIP, false)
            }
            PREF_REMEMBER_LAST_TAB -> {
                _rememberLastTab.value = prefs.getBoolean(PREF_REMEMBER_LAST_TAB, true)
            }
            PREF_SHOW_LED_BRIGHTNESS_SLIDER -> {
                _showLedBrightnessSlider.value =
                    prefs.getBoolean(PREF_SHOW_LED_BRIGHTNESS_SLIDER, true)
            }
            PREF_SHOW_SWAP_SCREENS_BUTTON -> {
                _showSwapScreensButton.value =
                    prefs.getBoolean(PREF_SHOW_SWAP_SCREENS_BUTTON, true)
            }
            PREF_SHOW_EDIT_BUTTON -> {
                _showEditButton.value =
                    prefs.getBoolean(PREF_SHOW_EDIT_BUTTON, true)
            }
            PREF_ACCURATE_GAME_FPS -> {
                _accurateGameFps.value =
                    prefs.getBoolean(PREF_ACCURATE_GAME_FPS, true)
            }
            PREF_QUICK_TILE_ORDER -> {
                _quickTileOrder.value = readSavedQuickTileOrder()
            }
            PREF_DISABLED_QUICK_TILES -> {
                _disabledQuickTiles.value = readSavedDisabledQuickTiles()
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "android.media.VOLUME_CHANGED_ACTION" -> {
                    val updated = readPrimaryVolumePercent(_primaryVolumePercent.value)
                    if (updated != _primaryVolumePercent.value) {
                        _primaryVolumePercent.value = updated
                        _isDualVolumeActive.value = false
                        prefs.edit().putBoolean(PREF_DUAL_VOLUME_ACTIVE, false).apply()
                    }
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    _wifiEnabled.value = readWifiEnabled()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    _bluetoothEnabled.value = readBluetoothEnabled()
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    _dndEnabled.value = readDndEnabled()
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    _airplaneModeEnabled.value = readAirplaneModeEnabled()
                }
            }
        }
    }

    private val megingiardPackageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart ?: return
            if (pkg == "com.stormpanda.megingiard" || pkg == "com.stormpanda.megingiard.debug") {
                registerMegingiardContentObservers()
                refreshMegingiardSyncedThemeAsync(changedUri = null, debounceOsSyncMs = 0L)
            }
        }
    }

    private var isRegistered = false

    private fun registerMegingiardContentObservers() {
        try {
            contentResolver.unregisterContentObserver(megingiardThemeObserver)
        } catch (_: Throwable) {
        }
        if (isProviderAuthorityInstalled(MEGINGIARD_RELEASE_THEME_URI)) {
            try {
                contentResolver.registerContentObserver(
                    MEGINGIARD_RELEASE_THEME_URI,
                    true,
                    megingiardThemeObserver
                )
            } catch (_: Throwable) {
            }
        }
        if (isProviderAuthorityInstalled(MEGINGIARD_DEBUG_THEME_URI)) {
            try {
                contentResolver.registerContentObserver(
                    MEGINGIARD_DEBUG_THEME_URI,
                    true,
                    megingiardThemeObserver
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun register() {
        if (isRegistered) return
        isRegistered = true
        val keys = listOf(
            KEY_DUAL_SCREEN_DISPLAY_MODE,
            KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL,
            KEY_SCREEN_FOCUS_LOCK,
            KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
            KEY_SECONDARY_SCREEN_VOLUME_LEVEL,
            KEY_SECONDARY_SCREEN_VOLUME_HEADPHONES,
            KEY_PERFORMANCE_MODE,
            KEY_FAN_MODE,
            KEY_FAN_SPEED,
            KEY_TEMP_ABXY_LAYOUT_MODE,
            KEY_CHARGING_SEPARATION,
            KEY_VIBRATE_ON,
            KEY_JOYSTICK_LIGHT_ENABLED,
            KEY_FORCE_LANDSCAPE,
            KEY_TCC_THEME_MODE,
            KEY_PEAK_REFRESH_RATE,
            KEY_LED_BRIGHTNESS_PERCENT,
            KEY_TRIGGER_INPUT_MODE,
            KEY_CHARGE_LIMIT_80,
            KEY_GAMEPAD_MOUSE_MODE,
            KEY_SHOW_FLOATING_ICON,
            KEY_KEEP_SCREEN_ON,
            KEY_PREVENT_ACCIDENTAL_HOME,
            "screen_brightness"
        )
        for (key in keys) {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(key),
                false,
                settingsObserver
            )
        }
        val globalKeys = listOf(
            Settings.Global.WIFI_ON,
            Settings.Global.BLUETOOTH_ON,
            Settings.Global.AIRPLANE_MODE_ON,
            "zen_mode"
        )
        for (gKey in globalKeys) {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(gKey),
                false,
                settingsObserver
            )
        }
        registerMegingiardContentObservers()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        context.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(megingiardPackageReceiver, pkgFilter, Context.RECEIVER_NOT_EXPORTED)
        registerDisplayBrightnessListener()
        refreshAll()
    }

    fun unregister() {
        mainHandler.removeCallbacks(debouncedRefreshRateWriteRunnable)
        if (pendingRefreshRateTarget != null) {
            commitPendingRefreshRate()
        }
        if (!isRegistered) return
        isRegistered = false
        try {
            displayManager?.unregisterDisplayListener(displayBrightnessListener)
        } catch (_: Throwable) {
        }
        try {
            contentResolver.unregisterContentObserver(settingsObserver)
        } catch (_: Throwable) {
        }
        try {
            contentResolver.unregisterContentObserver(megingiardThemeObserver)
        } catch (_: Throwable) {
        }
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Throwable) {
        }
        try {
            context.unregisterReceiver(volumeReceiver)
        } catch (_: Throwable) {
        }
        try {
            context.unregisterReceiver(megingiardPackageReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun registerDisplayBrightnessListener() {
        val dm = displayManager ?: return
        try {
            // Register with EVENT_FLAG_DISPLAY_BRIGHTNESS (1L << 3 = 8L), matching SystemUI
            // DisplayTrackerImpl and stock DualScreenAssistant Z0.a.b
            val method = DisplayManager::class.java.getMethod(
                "registerDisplayListener",
                DisplayManager.DisplayListener::class.java,
                Handler::class.java,
                Long::class.javaPrimitiveType
            )
            method.invoke(dm, displayBrightnessListener, mainHandler, 8L)
        } catch (_: Throwable) {
            try {
                dm.registerDisplayListener(displayBrightnessListener, mainHandler)
            } catch (_: Throwable) {
            }
        }
    }

    fun refreshAll(
        refreshDisplayBrightness: Boolean = true,
        refreshMegingiardTheme: Boolean = true
    ) {
        val currentDisplayMode = getInt(KEY_DUAL_SCREEN_DISPLAY_MODE, 0)
        val currentFocusLock = getInt(KEY_SCREEN_FOCUS_LOCK, 0)
        _dualScreenDisplayMode.value = currentDisplayMode
        _screenFocusLock.value = currentFocusLock
        if (currentDisplayMode == 0) {
            val currentEnableKeyFocus = getInt(
                KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
                if (currentFocusLock == 0) 0 else 1
            )
            prefs.edit()
                .putInt(PREF_SAVED_DUAL_SCREEN_FOCUS_LOCK, currentFocusLock)
                .putInt(PREF_SAVED_ENABLE_SYSTEM_KEY_FOCUS_LOCK, currentEnableKeyFocus)
                .apply()
        }
        _performanceMode.value = getInt(KEY_PERFORMANCE_MODE, 0)
        _fanMode.value = getInt(KEY_FAN_MODE, 4)
        _fanSpeed.value = getInt(KEY_FAN_SPEED, 50)
        _controllerStyle.value = getInt(KEY_TEMP_ABXY_LAYOUT_MODE, 1)
        _chargingSeparation.value = getInt(KEY_CHARGING_SEPARATION, 0) == 1
        _vibrateOn.value = getInt(KEY_VIBRATE_ON, 1) == 1
        _ambientLightEnabled.value = readAmbientLightState()
        _forceLandscape.value = getInt(KEY_FORCE_LANDSCAPE, 1) == 1
        _themeMode.value = readSavedThemeMode()
        _customAccentArgb.value = prefs.getInt(
            PREF_CUSTOM_ACCENT_ARGB,
            DEFAULT_MEGINGIARD_ACCENT_ARGB
        )
        _injectOsTheme.value = prefs.getBoolean(PREF_INJECT_OS_THEME, false)
        _showCloseBottomScreenTip.value = !prefs.getBoolean(PREF_NO_MORE_CLOSE_TIP, false)
        _rememberLastTab.value = prefs.getBoolean(PREF_REMEMBER_LAST_TAB, true)
        _showLedBrightnessSlider.value = prefs.getBoolean(PREF_SHOW_LED_BRIGHTNESS_SLIDER, true)
        _showSwapScreensButton.value = prefs.getBoolean(PREF_SHOW_SWAP_SCREENS_BUTTON, true)
        _showEditButton.value = prefs.getBoolean(PREF_SHOW_EDIT_BUTTON, true)
        _accurateGameFps.value = prefs.getBoolean(PREF_ACCURATE_GAME_FPS, true)
        _ledBrightnessPercent.value = readLedBrightnessPercent(_ledBrightnessPercent.value)
        _wifiEnabled.value = readWifiEnabled()
        _bluetoothEnabled.value = readBluetoothEnabled()
        _dndEnabled.value = readDndEnabled()
        _airplaneModeEnabled.value = readAirplaneModeEnabled()
        _triggerMode.value = getInt(KEY_TRIGGER_INPUT_MODE, 2)
        _chargeLimit80.value = getInt(KEY_CHARGE_LIMIT_80, 0) == 1
        _gamepadMouseMode.value = getInt(KEY_GAMEPAD_MOUSE_MODE, 0) == 1
        _keyMapperEnabled.value = getInt(KEY_SHOW_FLOATING_ICON, 0) == 1
        _keepScreenOn.value = getInt(KEY_KEEP_SCREEN_ON, 0) == 1
        _preventAccidentalHome.value = getInt(KEY_PREVENT_ACCIDENTAL_HOME, 1) == 1
        _quickTileOrder.value = readSavedQuickTileOrder()
        _disabledQuickTiles.value = readSavedDisabledQuickTiles()
        if (refreshMegingiardTheme) {
            refreshMegingiardSyncedThemeAsync(changedUri = null, debounceOsSyncMs = 0L)
        } else {
            syncOsThemeIfNeeded()
        }
        val pendingRefreshRate = synchronized(refreshRateWriteLock) { pendingRefreshRateTarget }
        _refreshRate.value = pendingRefreshRate?.roundToInt() ?: readRefreshRate()
        _dualBrightness.value = getInt(KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, 55)
        if (refreshDisplayBrightness) {
            _topBrightness.value = readDisplayBrightnessPercent(0, _topBrightness.value)
            _bottomBrightness.value = readDisplayBrightnessPercent(4, _bottomBrightness.value)
            if (!isInSelfBrightnessGraceWindow()) {
                syncDualBrightnessActiveState(persistToPrefs = true)
            }
        }
        _dualVolumePercent.value = readDualVolumePercent()
        _primaryVolumePercent.value = readPrimaryVolumePercent(_primaryVolumePercent.value)
        _secondaryVolumePercent.value = readSecondaryVolumePercent(_secondaryVolumePercent.value)
    }

    fun refreshDisplayBrightness() {
        _dualBrightness.value = getInt(KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, _dualBrightness.value)
        _topBrightness.value = readDisplayBrightnessPercent(0, _topBrightness.value)
        _bottomBrightness.value = readDisplayBrightnessPercent(4, _bottomBrightness.value)
        if (!isInSelfBrightnessGraceWindow()) {
            syncDualBrightnessActiveState(persistToPrefs = true)
        }
    }

    private fun isDisplayBlanked(displayId: Int): Boolean {
        val mode = _dualScreenDisplayMode.value
        return (displayId == 0 && mode == 2) || (displayId == 4 && mode == 1)
    }

    private fun isDeviceInteractive(): Boolean {
        return try {
            powerManager?.isInteractive != false
        } catch (_: Throwable) {
            true
        }
    }

    private fun onExternalDisplayBrightnessChanged(
        displayId: Int,
        fromDisplayListener: Boolean
    ) {
        if (!isDeviceInteractive() || isDisplayBlanked(displayId)) return
        val info = PrivilegedBridge.getDisplayBrightnessInfo(context, displayId)
        val committed = info.getOrElse(0) { 0.5f }
        val min = info.getOrElse(1) { 0f }
        val max = info.getOrElse(2) { 1f }
        val adjusted = info.getOrElse(3) { committed }
        if (max <= min) return
        if (committed < 0f || committed.isNaN()) return

        // While the user drags the Android SystemUI Quick Settings brightness slider on Display 0,
        // SystemUI calls setTemporaryBrightness(0, val), which updates adjustedBrightness in real time
        // while committed brightness stays at the pre-drag value until finger release.
        val isLiveTemporaryDrag = fromDisplayListener &&
            displayId == 0 &&
            !adjusted.isNaN() &&
            adjusted >= 0f &&
            kotlin.math.abs(adjusted - committed) > 0.005f

        val effectiveLinear = if (isLiveTemporaryDrag) adjusted else committed
        val percent = (((effectiveLinear - min) / (max - min)) * 100f)
            .roundToInt()
            .coerceIn(0, 100)

        if (displayId == 0) {
            if (_topBrightness.value != percent) {
                _topBrightness.value = percent
            }
            if (!isLiveTemporaryDrag && committed > 0f) {
                PrivilegedBridge.updateSavedBrightness(top = committed)
            }
        } else if (displayId == 4) {
            if (_bottomBrightness.value != percent) {
                _bottomBrightness.value = percent
            }
            if (!isLiveTemporaryDrag && committed > 0f) {
                PrivilegedBridge.updateSavedBrightness(bottom = committed)
            }
        }

        syncDualBrightnessActiveState(persistToPrefs = !isLiveTemporaryDrag)
    }

    private fun syncDualBrightnessActiveState(persistToPrefs: Boolean) {
        val allMatch = _topBrightness.value == _bottomBrightness.value &&
            _topBrightness.value == _dualBrightness.value
        if (!allMatch) {
            if (_isDualBrightnessActive.value) {
                _isDualBrightnessActive.value = false
            }
            if (persistToPrefs && prefs.getBoolean(PREF_DUAL_BRIGHTNESS_ACTIVE, false)) {
                prefs.edit().putBoolean(PREF_DUAL_BRIGHTNESS_ACTIVE, false).apply()
            }
        } else {
            val savedPref = prefs.getBoolean(PREF_DUAL_BRIGHTNESS_ACTIVE, true)
            if (_isDualBrightnessActive.value != savedPref) {
                _isDualBrightnessActive.value = savedPref
            }
        }
    }

    fun setDualScreenDisplayMode(mode: Int) {
        val previousMode = getInt(KEY_DUAL_SCREEN_DISPLAY_MODE, _dualScreenDisplayMode.value)
        if (previousMode == 0 && mode != 0) {
            val currentFocusLock = getInt(KEY_SCREEN_FOCUS_LOCK, _screenFocusLock.value)
            val currentEnableKeyFocus = getInt(
                KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
                if (currentFocusLock == 0) 0 else 1
            )
            prefs.edit()
                .putInt(PREF_SAVED_DUAL_SCREEN_FOCUS_LOCK, currentFocusLock)
                .putInt(PREF_SAVED_ENABLE_SYSTEM_KEY_FOCUS_LOCK, currentEnableKeyFocus)
                .apply()
        }
        PrivilegedBridge.putSystemSettingInt(context, KEY_DUAL_SCREEN_DISPLAY_MODE, mode)
        _dualScreenDisplayMode.value = mode
        when (mode) {
            0 -> {
                restoreDualScreenFocusLock()
                val touchProp = if (_touchLockEnabled.value) "0,1" else "0,0"
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", touchProp)
                PrivilegedBridge.setPinScreenMode(context, 0)
            }
            1 -> {
                _touchLockEnabled.value = false
                PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, 1)
                PrivilegedBridge.putSystemSettingInt(context, KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 1)
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,1")
                PrivilegedBridge.setPinScreenMode(context, 0)
                _screenFocusLock.value = 1
            }
            2 -> {
                _touchLockEnabled.value = false
                PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, 2)
                PrivilegedBridge.putSystemSettingInt(context, KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 1)
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "1,0")
                PrivilegedBridge.setPinScreenMode(context, 0)
                _screenFocusLock.value = 2
            }
        }
    }

    fun restoreDualScreenFocusLock() {
        val restoredFocusLock = prefs.getInt(PREF_SAVED_DUAL_SCREEN_FOCUS_LOCK, 0).coerceIn(0, 2)
        val restoredEnableKeyFocus = prefs.getInt(
            PREF_SAVED_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
            if (restoredFocusLock == 0) 0 else 1
        )
        PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, restoredFocusLock)
        PrivilegedBridge.putSystemSettingInt(
            context,
            KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
            restoredEnableKeyFocus
        )
        _screenFocusLock.value = restoredFocusLock
    }

    fun reapplyTouchDisableForCurrentMode() {
        when (_dualScreenDisplayMode.value) {
            0 -> {
                val touchProp = if (_touchLockEnabled.value) "0,1" else "0,0"
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", touchProp)
            }
            1 -> PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,1")
            2 -> PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "1,0")
        }
    }

    fun setScreenFocusLock(mode: Int) {
        val enableKeyFocus = if (mode == 0) 0 else 1
        PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, mode)
        PrivilegedBridge.putSystemSettingInt(
            context,
            KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
            enableKeyFocus
        )
        _screenFocusLock.value = mode
        if (_dualScreenDisplayMode.value == 0) {
            prefs.edit()
                .putInt(PREF_SAVED_DUAL_SCREEN_FOCUS_LOCK, mode)
                .putInt(PREF_SAVED_ENABLE_SYSTEM_KEY_FOCUS_LOCK, enableKeyFocus)
                .apply()
        }
    }

    fun setPerformanceMode(mode: Int) {
        PrivilegedBridge.putSystemSettingInt(context, KEY_PERFORMANCE_MODE, mode)
        _performanceMode.value = mode
    }

    fun setFanMode(mode: Int) {
        PrivilegedBridge.putSystemSettingInt(context, KEY_FAN_MODE, mode)
        _fanMode.value = mode
    }

    fun setCustomFanSpeed(speed: Int) {
        PrivilegedBridge.putSystemSettingInt(context, KEY_FAN_SPEED, speed)
        _fanSpeed.value = speed
    }

    fun setControllerStyle(mode: Int) {
        when (mode) {
            0 -> {
                // Xbox Style
                PrivilegedBridge.putSystemSettingInt(context, KEY_TEMP_ABXY_LAYOUT_MODE, 0)
                PrivilegedBridge.putSystemSettingInt(context, KEY_FLIP_BUTTON_LAYOUT, 1)
                PrivilegedBridge.putSystemSettingInt(context, KEY_NO_CREATE_GAMEPAD_LAYOUT, 0)
            }
            1 -> {
                // Standard (Odin) Style
                PrivilegedBridge.putSystemSettingInt(context, KEY_TEMP_ABXY_LAYOUT_MODE, 1)
                PrivilegedBridge.putSystemSettingInt(context, KEY_FLIP_BUTTON_LAYOUT, 0)
                PrivilegedBridge.putSystemSettingInt(context, KEY_NO_CREATE_GAMEPAD_LAYOUT, 0)
            }
            2 -> {
                // Ban On Use (Disabled)
                PrivilegedBridge.putSystemSettingInt(context, KEY_TEMP_ABXY_LAYOUT_MODE, 2)
                PrivilegedBridge.putSystemSettingInt(context, KEY_NO_CREATE_GAMEPAD_LAYOUT, 1)
                PrivilegedBridge.putSystemSettingInt(context, KEY_FLIP_BUTTON_LAYOUT, 0)
            }
        }
        _controllerStyle.value = mode
    }

    fun toggleChargingSeparation() {
        val next = !_chargingSeparation.value
        PrivilegedBridge.putSystemSettingInt(context, KEY_CHARGING_SEPARATION, if (next) 1 else 0)
        _chargingSeparation.value = next
    }

    fun toggleVibrate() {
        val next = !_vibrateOn.value
        PrivilegedBridge.putSystemSettingInt(context, KEY_VIBRATE_ON, if (next) 1 else 0)
        _vibrateOn.value = next
    }

    fun toggleAmbientLight() {
        val currentProp = PrivilegedBridge.getSystemProperty("sys.light.curr.is.show", "")
        val currentlyShowing = when (currentProp) {
            "true", "1" -> true
            "false", "0" -> false
            else -> _ambientLightEnabled.value
        }
        val next = !currentlyShowing
        val intent = Intent("com.ro.external.request.light.adj.action").apply {
            putExtra("isShow", next)
        }
        context.sendBroadcast(intent)
        _ambientLightEnabled.value = next
    }

    fun toggleForceLandscape() {
        val next = !_forceLandscape.value
        PrivilegedBridge.putSystemSettingInt(context, KEY_FORCE_LANDSCAPE, if (next) 1 else 0)
        _forceLandscape.value = next
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        prefs.edit().putInt(PREF_APP_THEME_MODE, mode).apply()
        val stockEquivalent = when (mode) {
            1 -> 1
            3 -> 3
            else -> 4
        }
        PrivilegedBridge.putSystemSettingInt(context, KEY_TCC_THEME_MODE, stockEquivalent)
        if (mode == AppThemeOption.SYNC_MEGINGIARD.id) {
            refreshMegingiardSyncedThemeAsync(changedUri = null, debounceOsSyncMs = 0L)
        } else {
            scheduleOsThemeSync(0L)
        }
    }

    fun setCustomAccentArgb(argb: Int) {
        _customAccentArgb.value = argb
        prefs.edit().putInt(PREF_CUSTOM_ACCENT_ARGB, argb).apply()
        scheduleOsThemeSync(0L)
    }

    fun setInjectOsTheme(enabled: Boolean) {
        _injectOsTheme.value = enabled
        if (enabled) {
            val editor = prefs.edit().putBoolean(PREF_INJECT_OS_THEME, true)
            if (!prefs.contains(PREF_SAVED_OS_NIGHT_MODE)) {
                editor.putInt(PREF_SAVED_OS_NIGHT_MODE, readCurrentOsNightMode())
            }
            if (!prefs.contains(PREF_SAVED_OS_THEME_JSON)) {
                val curJson = readCurrentOsThemeJson()
                if (curJson.isNotBlank() && !curJson.contains("\"999\"")) {
                    editor.putString(PREF_SAVED_OS_THEME_JSON, curJson)
                }
            }
            editor.apply()
        } else {
            prefs.edit().putBoolean(PREF_INJECT_OS_THEME, false).apply()
        }
        scheduleOsThemeSync(delayMs = 0L, force = true)
    }

    /**
     * Marks a [MainActivity] window as actively focused/resumed so OS-wide `FabricatedOverlay`
     * transactions (`CONFIG_ASSETS_PATHS` / `0x80000000`) are deferred while the user is actively
     * navigating or cycling theme carousels in Settings, then flushed immediately once the window
     * loses focus (e.g. SystemUI notification shade pull-down) or exits.
     */
    fun setSettingsUiActive(
        activityToken: Int,
        active: Boolean,
        flushIfInactive: Boolean = true
    ) {
        var shouldFlush = false
        var flushForce = false
        synchronized(osThemeDeferLock) {
            if (active) {
                activeSettingsUiTokens.add(activityToken)
            } else {
                activeSettingsUiTokens.remove(activityToken)
                if (flushIfInactive && activeSettingsUiTokens.isEmpty() && pendingDeferredOsThemeSync) {
                    pendingDeferredOsThemeSync = false
                    flushForce = pendingDeferredOsThemeForce
                    pendingDeferredOsThemeForce = false
                    shouldFlush = true
                }
            }
        }
        if (shouldFlush) {
            mainHandler.removeCallbacks(debouncedOsThemeSyncRunnable)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                syncOsThemeIfNeeded(force = flushForce)
            } else {
                val forceSnapshot = flushForce
                mainHandler.post { syncOsThemeIfNeeded(force = forceSnapshot) }
            }
        }
    }

    fun refreshMegingiardSyncedTheme() {
        refreshMegingiardSyncedThemeAsync(changedUri = null, debounceOsSyncMs = 0L)
    }

    private fun refreshMegingiardSyncedThemeAsync(
        changedUri: Uri?,
        debounceOsSyncMs: Long
    ) {
        themeSyncExecutor.execute {
            val candidates = buildMegingiardCandidateUris(changedUri)
            var anyProviderInstalled = false
            for (uri in candidates) {
                if (!isProviderAuthorityInstalled(uri)) continue
                anyProviderInstalled = true
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val modeIdx = cursor.getColumnIndex("theme_mode")
                            val accentIdx = cursor.getColumnIndex("accent_color")
                            val modeStr = if (modeIdx >= 0) cursor.getString(modeIdx) else null
                            val accentInt = if (accentIdx >= 0 && !cursor.isNull(accentIdx)) {
                                cursor.getInt(accentIdx)
                            } else {
                                null
                            }
                            preferredMegingiardThemeUri = uri
                            val resolvedOption = AppThemeOption.fromMegingiardIpcName(modeStr)
                            val changed = _syncedMegingiardOption.value != resolvedOption ||
                                _syncedMegingiardAccentArgb.value != accentInt ||
                                !_isMegingiardProviderAvailable.value
                            _syncedMegingiardOption.value = resolvedOption
                            _syncedMegingiardAccentArgb.value = accentInt
                            _isMegingiardProviderAvailable.value = true
                            if (_themeMode.value == AppThemeOption.SYNC_MEGINGIARD.id &&
                                (changed || debounceOsSyncMs == 0L)
                            ) {
                                scheduleOsThemeSync(debounceOsSyncMs)
                            }
                            return@execute
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            _isMegingiardProviderAvailable.value = anyProviderInstalled || isAnyMegingiardInstalled()
            if (debounceOsSyncMs == 0L) {
                scheduleOsThemeSync(0L)
            }
        }
    }

    private fun buildMegingiardCandidateUris(changedUri: Uri?): List<Uri> {
        val ordered = LinkedHashSet<Uri>(3)
        if (changedUri != null &&
            (changedUri.authority == MEGINGIARD_RELEASE_THEME_URI.authority ||
                changedUri.authority == MEGINGIARD_DEBUG_THEME_URI.authority)
        ) {
            ordered.add(
                if (changedUri.authority == MEGINGIARD_DEBUG_THEME_URI.authority) {
                    MEGINGIARD_DEBUG_THEME_URI
                } else {
                    MEGINGIARD_RELEASE_THEME_URI
                }
            )
        }
        preferredMegingiardThemeUri?.let { ordered.add(it) }
        ordered.add(MEGINGIARD_RELEASE_THEME_URI)
        ordered.add(MEGINGIARD_DEBUG_THEME_URI)
        return ordered.toList()
    }

    private fun isAnyMegingiardInstalled(): Boolean {
        return isProviderAuthorityInstalled(MEGINGIARD_RELEASE_THEME_URI) ||
            isProviderAuthorityInstalled(MEGINGIARD_DEBUG_THEME_URI) ||
            isMegingiardPackageInstalled("com.stormpanda.megingiard") ||
            isMegingiardPackageInstalled("com.stormpanda.megingiard.debug")
    }

    private fun isMegingiardPackageInstalled(pkg: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0) != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun isProviderAuthorityInstalled(uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        val expectedPkg = when (authority) {
            MEGINGIARD_RELEASE_THEME_URI.authority -> "com.stormpanda.megingiard"
            MEGINGIARD_DEBUG_THEME_URI.authority -> "com.stormpanda.megingiard.debug"
            else -> return false
        }
        return try {
            @Suppress("DEPRECATION")
            val providerInfo = context.packageManager.resolveContentProvider(authority, 0)
                ?: return false
            providerInfo.packageName == expectedPkg
        } catch (_: Throwable) {
            false
        }
    }

    private fun dispatchOrDeferOsThemeSync(force: Boolean = false) {
        val deferred = synchronized(osThemeDeferLock) {
            if (activeSettingsUiTokens.isNotEmpty()) {
                pendingDeferredOsThemeSync = true
                if (force) {
                    pendingDeferredOsThemeForce = true
                }
                true
            } else {
                pendingDeferredOsThemeSync = false
                pendingDeferredOsThemeForce = false
                false
            }
        }
        if (!deferred) {
            syncOsThemeIfNeeded(force = force)
        }
    }

    private fun scheduleOsThemeSync(delayMs: Long, force: Boolean = false) {
        mainHandler.removeCallbacks(debouncedOsThemeSyncRunnable)
        if (delayMs <= 0L) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dispatchOrDeferOsThemeSync(force = force)
            } else {
                if (force) {
                    synchronized(osThemeDeferLock) {
                        pendingDeferredOsThemeForce = true
                    }
                }
                mainHandler.post {
                    val effectiveForce = synchronized(osThemeDeferLock) {
                        val f = pendingDeferredOsThemeForce || force
                        if (activeSettingsUiTokens.isEmpty()) {
                            pendingDeferredOsThemeForce = false
                        }
                        f
                    }
                    dispatchOrDeferOsThemeSync(force = effectiveForce)
                }
            }
        } else {
            if (force) {
                synchronized(osThemeDeferLock) {
                    pendingDeferredOsThemeForce = true
                }
            }
            mainHandler.postDelayed(debouncedOsThemeSyncRunnable, delayMs)
        }
    }

    private fun syncOsThemeIfNeeded(force: Boolean = false) {
        val inject = _injectOsTheme.value
        if (!inject) {
            val hadSavedSnapshot =
                prefs.contains(PREF_SAVED_OS_NIGHT_MODE) || prefs.contains(PREF_SAVED_OS_THEME_JSON)
            if (!force && lastAppliedOsThemeSignature == "OFF" && !hadSavedSnapshot) {
                return
            }
            val savedNight = prefs.getInt(PREF_SAVED_OS_NIGHT_MODE, -1)
            val savedJson = prefs.getString(PREF_SAVED_OS_THEME_JSON, "") ?: ""
            lastAppliedOsThemeSignature = "OFF"
            if (hadSavedSnapshot) {
                prefs.edit()
                    .remove(PREF_SAVED_OS_NIGHT_MODE)
                    .remove(PREF_SAVED_OS_THEME_JSON)
                    .apply()
            }
            if (force || hadSavedSnapshot) {
                PrivilegedBridge.clearOsThemeOverlay(context, savedNight, savedJson)
            }
            return
        }

        if (!prefs.contains(PREF_SAVED_OS_NIGHT_MODE)) {
            prefs.edit().putInt(PREF_SAVED_OS_NIGHT_MODE, readCurrentOsNightMode()).apply()
        }
        if (!prefs.contains(PREF_SAVED_OS_THEME_JSON)) {
            val curJson = readCurrentOsThemeJson()
            if (curJson.isNotBlank() && !curJson.contains("\"999\"")) {
                prefs.edit().putString(PREF_SAVED_OS_THEME_JSON, curJson).apply()
            }
        }
        val savedNight = prefs.getInt(PREF_SAVED_OS_NIGHT_MODE, 2)
        val savedJson = prefs.getString(PREF_SAVED_OS_THEME_JSON, "") ?: ""
        val option = AppThemeOption.fromId(_themeMode.value)

        when (option) {
            AppThemeOption.STOCK_SYSTEM -> {
                val sig = "ON:STOCK_SYSTEM:$savedNight"
                if (!force && lastAppliedOsThemeSignature == sig) return
                lastAppliedOsThemeSignature = sig
                PrivilegedBridge.clearOsThemeOverlay(context, savedNight, savedJson)
            }
            AppThemeOption.STOCK_LIGHT -> {
                val sig = "ON:STOCK_LIGHT:$savedNight"
                if (!force && lastAppliedOsThemeSignature == sig) return
                lastAppliedOsThemeSignature = sig
                PrivilegedBridge.applyOsThemeOverlay(
                    context = context,
                    resourceNames = emptyArray(),
                    colorValues = IntArray(0),
                    targetNightMode = 1,
                    savedNightMode = savedNight,
                    injectedThemeJson = "",
                    savedThemeJson = savedJson
                )
            }
            AppThemeOption.STOCK_DARK -> {
                val sig = "ON:STOCK_DARK:$savedNight"
                if (!force && lastAppliedOsThemeSignature == sig) return
                lastAppliedOsThemeSignature = sig
                PrivilegedBridge.applyOsThemeOverlay(
                    context = context,
                    resourceNames = emptyArray(),
                    colorValues = IntArray(0),
                    targetNightMode = 2,
                    savedNightMode = savedNight,
                    injectedThemeJson = "",
                    savedThemeJson = savedJson
                )
            }
            else -> {
                val sig = "ON:MG:${option.id}:${_customAccentArgb.value}:" +
                    "${_syncedMegingiardOption.value.id}:${_syncedMegingiardAccentArgb.value}:$savedNight"
                if (!force && lastAppliedOsThemeSignature == sig) return
                lastAppliedOsThemeSignature = sig
                val palette = resolveTccPalette(
                    option = option,
                    systemDark = true,
                    customAccentArgb = _customAccentArgb.value,
                    syncedOption = _syncedMegingiardOption.value,
                    syncedAccentArgb = _syncedMegingiardAccentArgb.value
                )
                val (names, values) = buildOsDynamicColorOverlayEntries(palette)
                val injectedJson = buildInjectedThemeJson(palette)
                PrivilegedBridge.applyOsThemeOverlay(
                    context = context,
                    resourceNames = names,
                    colorValues = values,
                    targetNightMode = 2,
                    savedNightMode = savedNight,
                    injectedThemeJson = injectedJson,
                    savedThemeJson = savedJson
                )
            }
        }
    }

    private fun readCurrentOsThemeJson(): String {
        return try {
            Settings.Secure.getString(contentResolver, "theme_customization_overlay_packages") ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun buildInjectedThemeJson(palette: TccColorPalette): String {
        val accentHex = String.format("%06X", 0xFFFFFF and palette.accent.toArgb())
        return try {
            val obj = org.json.JSONObject()
            obj.put("_applied_timestamp", System.currentTimeMillis())
            obj.put("android.theme.customization.color_index", "999")
            obj.put("android.theme.customization.system_palette", accentHex)
            obj.put("android.theme.customization.accent_color", accentHex)
            obj.put("android.theme.customization.color_source", "preset")
            obj.put("android.theme.customization.theme_style", "CONTENT")
            obj.toString()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readCurrentOsNightMode(): Int {
        return try {
            val mode = Settings.Secure.getInt(contentResolver, "ui_night_mode", -1)
            if (mode == 1 || mode == 2) {
                mode
            } else {
                val uim = context.getSystemService(UiModeManager::class.java)
                val nm = uim?.nightMode ?: UiModeManager.MODE_NIGHT_YES
                if (nm == UiModeManager.MODE_NIGHT_NO) 1 else 2
            }
        } catch (_: Throwable) {
            2
        }
    }

    private fun readSavedThemeMode(): Int {
        if (prefs.contains(PREF_APP_THEME_MODE)) {
            return prefs.getInt(PREF_APP_THEME_MODE, AppThemeOption.STOCK_DARK.id)
        }
        val sysMode = getInt(KEY_TCC_THEME_MODE, AppThemeOption.STOCK_DARK.id)
        return if (sysMode == 2) AppThemeOption.STOCK_DARK.id else sysMode
    }

    /**
     * Updates the UI pill state immediately while deferring/coalescing the underlying
     * `min_refresh_rate` / `peak_refresh_rate` Settings.System writes by
     * [REFRESH_RATE_WRITE_SETTLE_DELAY_MS].
     *
     * On AYN Thor firmware, `DisplayModeDirector$SettingsObserver.onChange` holds
     * `DisplayModeDirector.mLock` while calling `updateRefreshRateSettingLockedForX6()` ->
     * `DisplayManagerService.getBrightnessInfo()` (`DisplayManagerService$SyncRoot`), which can
     * AB-BA deadlock with `WindowSurfacePlacer` (`DisplayManagerService$SyncRoot` ->
     * `DisplayModeDirector.mLock`) if fired concurrently with the Display 4 tap / focus-bounce
     * window traversal burst.
     */
    fun toggleRefreshRate() {
        val current = _refreshRate.value
        val maxSupported = getMaxSupportedRefreshRate()
        val next = if (current >= 90) 60f else maxSupported
        synchronized(refreshRateWriteLock) {
            pendingRefreshRateTarget = next
        }
        _refreshRate.value = next.roundToInt()
        mainHandler.removeCallbacks(debouncedRefreshRateWriteRunnable)
        mainHandler.postDelayed(
            debouncedRefreshRateWriteRunnable,
            REFRESH_RATE_WRITE_SETTLE_DELAY_MS
        )
    }

    internal fun commitPendingRefreshRate() {
        val target = synchronized(refreshRateWriteLock) { pendingRefreshRateTarget } ?: return
        val currentMin = try {
            Settings.System.getFloat(contentResolver, KEY_MIN_REFRESH_RATE, -1f)
        } catch (_: Throwable) {
            -1f
        }
        val currentPeak = try {
            Settings.System.getFloat(contentResolver, KEY_PEAK_REFRESH_RATE, -1f)
        } catch (_: Throwable) {
            -1f
        }
        val alreadyMatches = kotlin.math.abs(currentMin - target) < 0.5f &&
            kotlin.math.abs(currentPeak - target) < 0.5f
        if (!alreadyMatches) {
            PrivilegedBridge.putSystemSettingFloat(context, KEY_MIN_REFRESH_RATE, target)
            PrivilegedBridge.putSystemSettingFloat(context, KEY_PEAK_REFRESH_RATE, target)
        }
        synchronized(refreshRateWriteLock) {
            if (pendingRefreshRateTarget == target) {
                pendingRefreshRateTarget = null
            }
        }
    }

    fun setDisplayBrightnessPercent(displayId: Int, percent: Int, temporary: Boolean) {
        isSelfDraggingBrightness = temporary
        lastSelfBrightnessChangeElapsedMs = SystemClock.elapsedRealtime()
        _isDualBrightnessActive.value = false
        if (!temporary) {
            prefs.edit().putBoolean(PREF_DUAL_BRIGHTNESS_ACTIVE, false).apply()
        }
        applyDisplayBrightnessPercent(displayId, percent, temporary)
    }

    private fun applyDisplayBrightnessPercent(displayId: Int, percent: Int, temporary: Boolean) {
        val p = percent.coerceIn(0, 100)
        if (displayId == 0) {
            _topBrightness.value = p
        } else if (displayId == 4) {
            _bottomBrightness.value = p
        }
        val info = PrivilegedBridge.getDisplayBrightnessInfo(context, displayId)
        val min = info.getOrElse(1) { 0f }
        val max = info.getOrElse(2) { 1f }
        val linear = min + (p / 100f) * (max - min)
        if (!temporary && linear > 0f) {
            if (displayId == 0) {
                PrivilegedBridge.updateSavedBrightness(top = linear)
            } else if (displayId == 4) {
                PrivilegedBridge.updateSavedBrightness(bottom = linear)
            }
        }
        PrivilegedBridge.setDisplayBrightness(context, displayId, linear, temporary)
    }

    fun setDualScreenBrightnessPercent(percent: Int, temporary: Boolean) {
        isSelfDraggingBrightness = temporary
        lastSelfBrightnessChangeElapsedMs = SystemClock.elapsedRealtime()
        val p = percent.coerceIn(0, 100)
        _isDualBrightnessActive.value = true
        _dualBrightness.value = p
        _topBrightness.value = p
        _bottomBrightness.value = p
        applyDisplayBrightnessPercent(0, p, temporary)
        applyDisplayBrightnessPercent(4, p, temporary)
        if (!temporary) {
            prefs.edit().putBoolean(PREF_DUAL_BRIGHTNESS_ACTIVE, true).apply()
            PrivilegedBridge.putSystemSettingInt(context, KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, p)
        }
    }

    fun setDualVolumePercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        _isDualVolumeActive.value = true
        _dualVolumePercent.value = p
        prefs.edit()
            .putInt(PREF_DUAL_VOLUME_PERCENT, p)
            .putBoolean(PREF_DUAL_VOLUME_ACTIVE, true)
            .apply()
        applyPrimaryVolumePercent(p)
        applySecondaryVolumePercent(p)
    }

    fun setPrimaryVolumePercent(percent: Int) {
        _isDualVolumeActive.value = false
        prefs.edit().putBoolean(PREF_DUAL_VOLUME_ACTIVE, false).apply()
        applyPrimaryVolumePercent(percent)
    }

    private fun applyPrimaryVolumePercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        _primaryVolumePercent.value = p
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val minVol = audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0
        val target = ((p / 100f) * (maxVol - minVol) + minVol).roundToInt()
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    fun setSecondaryVolumePercent(percent: Int) {
        _isDualVolumeActive.value = false
        prefs.edit().putBoolean(PREF_DUAL_VOLUME_ACTIVE, false).apply()
        applySecondaryVolumePercent(percent)
    }

    private fun applySecondaryVolumePercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        _secondaryVolumePercent.value = p
        val target = ((p / 100f) * 15f).roundToInt().coerceIn(0, 15)
        PrivilegedBridge.putSystemSettingInt(context, KEY_SECONDARY_SCREEN_VOLUME_LEVEL, target)
    }

    fun shouldShowCloseBottomScreenTip(): Boolean {
        return !prefs.getBoolean(PREF_NO_MORE_CLOSE_TIP, false)
    }

    fun setNoMoreCloseBottomScreenTip(noMore: Boolean) {
        prefs.edit().putBoolean(PREF_NO_MORE_CLOSE_TIP, noMore).apply()
        _showCloseBottomScreenTip.value = !noMore
    }

    fun setRememberLastTab(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_REMEMBER_LAST_TAB, enabled).apply()
        _rememberLastTab.value = enabled
    }

    fun setShowLedBrightnessSlider(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SHOW_LED_BRIGHTNESS_SLIDER, enabled).apply()
        _showLedBrightnessSlider.value = enabled
    }

    fun setShowSwapScreensButton(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SHOW_SWAP_SCREENS_BUTTON, enabled).apply()
        _showSwapScreensButton.value = enabled
    }

    fun setShowEditButton(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SHOW_EDIT_BUTTON, enabled).apply()
        _showEditButton.value = enabled
    }

    fun setAccurateGameFps(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_ACCURATE_GAME_FPS, enabled).apply()
        _accurateGameFps.value = enabled
    }

    fun setLedBrightnessPercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        _ledBrightnessPercent.value = p
        val linear = (p / 100f).coerceIn(0f, 1f)
        PrivilegedBridge.putSystemSettingFloat(context, KEY_LED_BRIGHTNESS_PERCENT, linear)
    }

    fun toggleWifi() {
        val next = !_wifiEnabled.value
        _wifiEnabled.value = next
        themeSyncExecutor.execute {
            @Suppress("DEPRECATION")
            val handled = try {
                wifiManager?.setWifiEnabled(next) == true
            } catch (_: Throwable) {
                false
            }
            if (!handled) {
                PServerClient.exec("svc wifi ${if (next) "enable" else "disable"}", wait = false)
            }
        }
    }

    fun toggleBluetooth() {
        val next = !_bluetoothEnabled.value
        _bluetoothEnabled.value = next
        themeSyncExecutor.execute {
            @Suppress("DEPRECATION", "MissingPermission")
            val handled = try {
                val adapter = bluetoothManager?.adapter
                if (adapter != null) {
                    if (next) adapter.enable() else adapter.disable()
                } else {
                    false
                }
            } catch (_: Throwable) {
                false
            }
            if (!handled) {
                PServerClient.exec("svc bluetooth ${if (next) "enable" else "disable"}", wait = false)
            }
        }
    }

    fun toggleDnd() {
        val next = !_dndEnabled.value
        _dndEnabled.value = next
        PrivilegedBridge.putGlobalSettingInt(context, "zen_mode", if (next) 1 else 0)
        themeSyncExecutor.execute {
            PServerClient.exec("cmd notification set_dnd ${if (next) "on" else "off"}", wait = false)
        }
    }

    fun toggleAirplaneMode() {
        val next = !_airplaneModeEnabled.value
        _airplaneModeEnabled.value = next
        PrivilegedBridge.putGlobalSettingInt(
            context,
            Settings.Global.AIRPLANE_MODE_ON,
            if (next) 1 else 0
        )
        themeSyncExecutor.execute {
            PServerClient.exec(
                "cmd connectivity airplane-mode ${if (next) "enable" else "disable"}",
                wait = false
            )
        }
    }

    fun swapScreens(): Boolean {
        return PrivilegedBridge.swapDisplayTasks(context)
    }

    fun toggleTouchLock(): Boolean {
        val next = !_touchLockEnabled.value
        _touchLockEnabled.value = next
        return next
    }

    fun setTriggerMode(mode: Int) {
        val normalized = mode.coerceIn(0, 2)
        PrivilegedBridge.putSystemSettingInt(context, KEY_TRIGGER_INPUT_MODE, normalized)
        _triggerMode.value = normalized
    }

    fun toggleChargeLimit80() {
        val next = !_chargeLimit80.value
        PrivilegedBridge.putSystemSettingInt(context, KEY_CHARGE_LIMIT_80, if (next) 1 else 0)
        _chargeLimit80.value = next
    }

    fun toggleGamepadMouseMode() {
        val next = !_gamepadMouseMode.value
        PrivilegedBridge.putSystemSettingInt(context, KEY_GAMEPAD_MOUSE_MODE, if (next) 1 else 0)
        _gamepadMouseMode.value = next
    }

    fun toggleKeyMapper() {
        val next = !_keyMapperEnabled.value
        if (next) {
            PrivilegedBridge.putSystemSettingInt(context, KEY_ADAPTER_ENABLE, 1)
        }
        PrivilegedBridge.putSystemSettingInt(context, KEY_SHOW_FLOATING_ICON, if (next) 1 else 0)
        _keyMapperEnabled.value = next
    }

    fun toggleKeepScreenOn() {
        val next = !_keepScreenOn.value
        PrivilegedBridge.putSystemSettingInt(context, KEY_KEEP_SCREEN_ON, if (next) 1 else 0)
        _keepScreenOn.value = next
    }

    fun togglePreventAccidentalHome() {
        val next = !_preventAccidentalHome.value
        PrivilegedBridge.putSystemSettingInt(
            context,
            KEY_PREVENT_ACCIDENTAL_HOME,
            if (next) 1 else 0
        )
        _preventAccidentalHome.value = next
    }

    fun setQuickTileOrder(newOrder: List<QuickSettingTileId>) {
        val normalized = normalizeQuickTileOrder(newOrder)
        _quickTileOrder.value = normalized
        prefs.edit().putString(
            PREF_QUICK_TILE_ORDER,
            normalized.joinToString(",") { it.key }
        ).apply()
    }

    fun moveActiveQuickTile(fromActiveIndex: Int, toActiveIndex: Int) {
        val currentOrder = _quickTileOrder.value
        val disabled = _disabledQuickTiles.value
        val activeList = currentOrder.filter { it !in disabled }.toMutableList()
        if (fromActiveIndex !in activeList.indices || toActiveIndex !in activeList.indices) return
        if (fromActiveIndex == toActiveIndex) return

        val moved = activeList.removeAt(fromActiveIndex)
        activeList.add(toActiveIndex, moved)

        val activeIterator = activeList.iterator()
        val rebuilt = currentOrder.map { item ->
            if (item in disabled) item else activeIterator.next()
        }
        setQuickTileOrder(rebuilt)
    }

    fun setQuickTileEnabled(tile: QuickSettingTileId, enabled: Boolean) {
        val current = _disabledQuickTiles.value.toMutableSet()
        if (enabled) {
            current.remove(tile)
        } else {
            val enabledCount = QuickSettingTileId.entries.size - current.size
            if (tile !in current && enabledCount <= 1) {
                // Keep at least 1 toggle enabled
                return
            }
            current.add(tile)
        }
        val updated = current.toSet()
        _disabledQuickTiles.value = updated
        prefs.edit()
            .putString(
                PREF_DISABLED_QUICK_TILES,
                updated.joinToString(",") { it.key }
            )
            .putString(
                PREF_KNOWN_QUICK_TILES,
                QuickSettingTileId.entries.joinToString(",") { it.key }
            )
            .apply()
    }

    fun resetQuickTilesToDefault() {
        val defaultDisabled = QuickSettingTileId.defaultDisabled
        _quickTileOrder.value = QuickSettingTileId.defaultOrder
        _disabledQuickTiles.value = defaultDisabled
        prefs.edit()
            .remove(PREF_QUICK_TILE_ORDER)
            .putString(
                PREF_DISABLED_QUICK_TILES,
                defaultDisabled.joinToString(",") { it.key }
            )
            .putString(
                PREF_KNOWN_QUICK_TILES,
                QuickSettingTileId.entries.joinToString(",") { it.key }
            )
            .apply()
    }

    private fun normalizeQuickTileOrder(order: List<QuickSettingTileId>): List<QuickSettingTileId> {
        val result = mutableListOf<QuickSettingTileId>()
        for (item in order) {
            if (item !in result) {
                result.add(item)
            }
        }
        for (fallback in QuickSettingTileId.defaultOrder) {
            if (fallback !in result) {
                result.add(fallback)
            }
        }
        return result
    }

    private fun readSavedQuickTileOrder(): List<QuickSettingTileId> {
        val raw = prefs.getString(PREF_QUICK_TILE_ORDER, null)
        if (raw.isNullOrBlank()) return QuickSettingTileId.defaultOrder
        val parsed = raw.split(",").mapNotNull { QuickSettingTileId.fromKey(it.trim()) }
        return normalizeQuickTileOrder(parsed)
    }

    private fun readSavedDisabledQuickTiles(): Set<QuickSettingTileId> {
        val allKnownKeysStr = QuickSettingTileId.entries.joinToString(",") { it.key }
        if (!prefs.contains(PREF_DISABLED_QUICK_TILES)) {
            val defaultDisabled = QuickSettingTileId.defaultDisabled
            prefs.edit()
                .putString(
                    PREF_DISABLED_QUICK_TILES,
                    defaultDisabled.joinToString(",") { it.key }
                )
                .putString(PREF_KNOWN_QUICK_TILES, allKnownKeysStr)
                .apply()
            return defaultDisabled
        }

        val raw = prefs.getString(PREF_DISABLED_QUICK_TILES, "") ?: ""
        val parsed = raw.split(",")
            .mapNotNull { token -> QuickSettingTileId.fromKey(token.trim()) }
            .toMutableSet()

        val knownRaw = prefs.getString(PREF_KNOWN_QUICK_TILES, null)
        val knownTiles = if (knownRaw == null) {
            // Upgrading from version that only had STOCK_AYN tiles
            QuickSettingTileId.entries
                .filter { it.category == QuickSettingCategory.STOCK_AYN }
                .toSet()
        } else {
            knownRaw.split(",").mapNotNull { QuickSettingTileId.fromKey(it.trim()) }.toSet()
        }

        var migrated = false
        for (tile in QuickSettingTileId.entries) {
            if (tile !in knownTiles) {
                migrated = true
                if (!tile.enabledByDefault) {
                    parsed.add(tile)
                }
            }
        }
        if (migrated) {
            prefs.edit()
                .putString(
                    PREF_DISABLED_QUICK_TILES,
                    parsed.joinToString(",") { it.key }
                )
                .putString(PREF_KNOWN_QUICK_TILES, allKnownKeysStr)
                .apply()
        }

        return if (parsed.size >= QuickSettingTileId.entries.size) {
            parsed.drop(1).toSet()
        } else {
            parsed
        }
    }

    /**
     * Replicates N0.a memory cleanup from the stock app while protecting our own package,
     * system apps, and the current home launcher.
     */
    fun cleanMemory(): Pair<Int, String> {
        val am = context.getSystemService(ActivityManager::class.java)
        val homePkg = resolveHomePackage()
        val protectedPackages = setOf(
            context.packageName,
            "com.odin.dualscreen.assistant",
            "com.odin.gameassistant",
            "com.android.systemui",
            "com.android.launcher3",
            homePkg
        )
        var killedCount = 0
        val running = am?.runningAppProcesses ?: emptyList()
        for (proc in running) {
            if (proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                continue
            }
            for (pkg in proc.pkgList) {
                if (pkg in protectedPackages) continue
                val isSystem = try {
                    val ai = context.packageManager.getApplicationInfo(pkg, 0)
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                        (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
                } catch (_: Throwable) {
                    false
                }
                if (!isSystem) {
                    try {
                        am?.killBackgroundProcesses(pkg)
                        PrivilegedBridge.forceStopPackage(pkg)
                        killedCount++
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val formattedMem = String.format("%.2f GB", availGb)
        return killedCount to formattedMem
    }

    private fun resolveHomePackage(): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName ?: ""
    }

    private fun getMaxSupportedRefreshRate(): Float {
        val display = displayManager?.getDisplay(0) ?: return 120f
        var maxRate = 60f
        for (mode in display.supportedModes) {
            if (mode.refreshRate > maxRate) {
                maxRate = mode.refreshRate
            }
        }
        return maxRate
    }

    private fun readRefreshRate(): Int {
        val defaultRate = displayManager?.getDisplay(0)?.refreshRate ?: 60f
        val rate = try {
            Settings.System.getFloat(contentResolver, KEY_PEAK_REFRESH_RATE, defaultRate)
        } catch (_: Throwable) {
            defaultRate
        }
        return rate.roundToInt()
    }

    private fun readAmbientLightState(): Boolean {
        val raw = try {
            Settings.System.getString(contentResolver, KEY_JOYSTICK_LIGHT_ENABLED) ?: "1,1"
        } catch (_: Throwable) {
            "1,1"
        }
        return raw.split(",").any { it.trim() == "1" }
    }

    private fun readLedBrightnessPercent(currentPercent: Int = -1): Int {
        val rawFloat = try {
            Settings.System.getFloat(contentResolver, KEY_LED_BRIGHTNESS_PERCENT, 0.5f)
        } catch (_: Throwable) {
            0.5f
        }
        val computed = (rawFloat.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
        if (currentPercent in 0..100 && kotlin.math.abs(computed - currentPercent) <= 1) {
            return currentPercent
        }
        return computed
    }

    private fun readWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled
                ?: (Settings.Global.getInt(contentResolver, Settings.Global.WIFI_ON, 0) != 0)
        } catch (_: Throwable) {
            false
        }
    }

    private fun readBluetoothEnabled(): Boolean {
        return try {
            bluetoothManager?.adapter?.isEnabled
                ?: (Settings.Global.getInt(contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0)
        } catch (_: Throwable) {
            false
        }
    }

    private fun readDndEnabled(): Boolean {
        return try {
            val filter = notificationManager?.currentInterruptionFilter
            if (filter != null && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                filter != NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                Settings.Global.getInt(contentResolver, "zen_mode", 0) != 0
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun readAirplaneModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    private fun readDisplayBrightnessPercent(displayId: Int, currentPercent: Int = -1): Int {
        if (!isDeviceInteractive() || isDisplayBlanked(displayId)) {
            return if (currentPercent in 0..100) currentPercent else 50
        }
        val info = PrivilegedBridge.getDisplayBrightnessInfo(context, displayId)
        val b = info.getOrElse(0) { 0.5f }
        val min = info.getOrElse(1) { 0f }
        val max = info.getOrElse(2) { 1f }
        if (max <= min) return if (currentPercent in 0..100) currentPercent else 50
        if (b < 0f || b.isNaN()) {
            return if (currentPercent in 0..100) currentPercent else 50
        }
        return (((b - min) / (max - min)) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun readDualVolumePercent(): Int {
        if (prefs.contains(PREF_DUAL_VOLUME_PERCENT)) {
            return prefs.getInt(PREF_DUAL_VOLUME_PERCENT, 50).coerceIn(0, 100)
        }
        return readPrimaryVolumePercent()
    }

    private fun readPrimaryVolumePercent(currentPercent: Int = -1): Int {
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val minVol = audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5
        if (maxVol <= minVol) return 0
        if (currentPercent in 0..100) {
            val expectedStep = ((currentPercent / 100f) * (maxVol - minVol) + minVol).roundToInt()
            if (expectedStep == curVol) {
                return currentPercent
            }
        }
        return (((curVol - minVol).toFloat() / (maxVol - minVol)) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun readSecondaryVolumePercent(currentPercent: Int = -1): Int {
        val cur = getInt(KEY_SECONDARY_SCREEN_VOLUME_LEVEL, 7).coerceIn(0, 15)
        if (currentPercent in 0..100) {
            val expectedStep = ((currentPercent / 100f) * 15f).roundToInt().coerceIn(0, 15)
            if (expectedStep == cur) {
                return currentPercent
            }
        }
        return ((cur / 15f) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun getInt(key: String, defaultValue: Int): Int {
        return try {
            Settings.System.getInt(contentResolver, key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }
    }
}
