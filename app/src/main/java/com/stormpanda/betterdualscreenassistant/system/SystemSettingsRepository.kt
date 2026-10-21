package com.stormpanda.betterdualscreenassistant.system

import android.app.ActivityManager
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
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import android.provider.Settings
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import com.stormpanda.betterdualscreenassistant.ui.theme.DEFAULT_MEGINGIARD_ACCENT_ARGB
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        const val PREFS_NAME = "better_dsa_prefs"
        const val PREF_NO_MORE_CLOSE_TIP = "no_more_close_tip"
        const val PREF_APP_THEME_MODE = "app_theme_mode"
        const val PREF_CUSTOM_ACCENT_ARGB = "custom_accent_argb"

        private val MEGINGIARD_RELEASE_THEME_URI: Uri =
            Uri.parse("content://com.stormpanda.megingiard.provider/theme")
        private val MEGINGIARD_DEBUG_THEME_URI: Uri =
            Uri.parse("content://com.stormpanda.megingiard.debug.provider/theme")
    }

    private val contentResolver = context.contentResolver
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    // Volume (0..100% for slider UI)
    private val _primaryVolumePercent = MutableStateFlow(readPrimaryVolumePercent())
    val primaryVolumePercent: StateFlow<Int> = _primaryVolumePercent.asStateFlow()

    private val _secondaryVolumePercent = MutableStateFlow(readSecondaryVolumePercent())
    val secondaryVolumePercent: StateFlow<Int> = _secondaryVolumePercent.asStateFlow()

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refreshAll()
        }
    }

    private val megingiardThemeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refreshMegingiardSyncedTheme()
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_APP_THEME_MODE -> {
                _themeMode.value = readSavedThemeMode()
            }
            PREF_CUSTOM_ACCENT_ARGB -> {
                _customAccentArgb.value = prefs.getInt(
                    PREF_CUSTOM_ACCENT_ARGB,
                    DEFAULT_MEGINGIARD_ACCENT_ARGB
                )
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _primaryVolumePercent.value = readPrimaryVolumePercent()
        }
    }

    private var isRegistered = false

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
            KEY_PEAK_REFRESH_RATE
        )
        for (key in keys) {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(key),
                false,
                settingsObserver
            )
        }
        try {
            contentResolver.registerContentObserver(
                MEGINGIARD_RELEASE_THEME_URI,
                true,
                megingiardThemeObserver
            )
        } catch (_: Throwable) {
        }
        try {
            contentResolver.registerContentObserver(
                MEGINGIARD_DEBUG_THEME_URI,
                true,
                megingiardThemeObserver
            )
        } catch (_: Throwable) {
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(volumeReceiver, filter)
        refreshAll()
    }

    fun unregister() {
        if (!isRegistered) return
        isRegistered = false
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
    }

    fun refreshAll() {
        _dualScreenDisplayMode.value = getInt(KEY_DUAL_SCREEN_DISPLAY_MODE, 0)
        _screenFocusLock.value = getInt(KEY_SCREEN_FOCUS_LOCK, 0)
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
        refreshMegingiardSyncedTheme()
        _refreshRate.value = readRefreshRate()
        _dualBrightness.value = getInt(KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, 55)
        _topBrightness.value = readDisplayBrightnessPercent(0)
        _bottomBrightness.value = readDisplayBrightnessPercent(4)
        _primaryVolumePercent.value = readPrimaryVolumePercent()
        _secondaryVolumePercent.value = readSecondaryVolumePercent()
    }

    fun refreshDisplayBrightness() {
        _dualBrightness.value = getInt(KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, _dualBrightness.value)
        _topBrightness.value = readDisplayBrightnessPercent(0)
        _bottomBrightness.value = readDisplayBrightnessPercent(4)
    }

    fun setDualScreenDisplayMode(mode: Int) {
        PrivilegedBridge.putSystemSettingInt(context, KEY_DUAL_SCREEN_DISPLAY_MODE, mode)
        _dualScreenDisplayMode.value = mode
        when (mode) {
            0 -> {
                PrivilegedBridge.putSystemSettingInt(context, KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 0)
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,0")
                PrivilegedBridge.setPinScreenMode(context, 0)
            }
            1 -> {
                PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, 1)
                PrivilegedBridge.putSystemSettingInt(context, KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 1)
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,1")
                PrivilegedBridge.setPinScreenMode(context, 1)
                _screenFocusLock.value = 1
            }
            2 -> {
                PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, 2)
                PrivilegedBridge.putSystemSettingInt(context, KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 1)
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "1,0")
                PrivilegedBridge.setPinScreenMode(context, 2)
                _screenFocusLock.value = 2
            }
        }
    }

    fun reapplyTouchDisableForCurrentMode() {
        when (_dualScreenDisplayMode.value) {
            0 -> PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,0")
            1 -> PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,1")
            2 -> PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "1,0")
        }
    }

    fun setScreenFocusLock(mode: Int) {
        PrivilegedBridge.putSystemSettingInt(context, KEY_SCREEN_FOCUS_LOCK, mode)
        PrivilegedBridge.putSystemSettingInt(
            context,
            KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
            if (mode == 0) 0 else 1
        )
        _screenFocusLock.value = mode
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
        prefs.edit().putInt(PREF_APP_THEME_MODE, mode).apply()
        _themeMode.value = mode
        val stockEquivalent = when (mode) {
            1 -> 1
            3 -> 3
            else -> 4
        }
        PrivilegedBridge.putSystemSettingInt(context, KEY_TCC_THEME_MODE, stockEquivalent)
        if (mode == AppThemeOption.SYNC_MEGINGIARD.id) {
            refreshMegingiardSyncedTheme()
        }
    }

    fun setCustomAccentArgb(argb: Int) {
        prefs.edit().putInt(PREF_CUSTOM_ACCENT_ARGB, argb).apply()
        _customAccentArgb.value = argb
    }

    fun refreshMegingiardSyncedTheme() {
        val uris = listOf(MEGINGIARD_RELEASE_THEME_URI, MEGINGIARD_DEBUG_THEME_URI)
        for (uri in uris) {
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
                        _syncedMegingiardOption.value = AppThemeOption.fromMegingiardIpcName(modeStr)
                        _syncedMegingiardAccentArgb.value = accentInt
                        return
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun readSavedThemeMode(): Int {
        if (prefs.contains(PREF_APP_THEME_MODE)) {
            return prefs.getInt(PREF_APP_THEME_MODE, AppThemeOption.STOCK_DARK.id)
        }
        val sysMode = getInt(KEY_TCC_THEME_MODE, AppThemeOption.STOCK_DARK.id)
        return if (sysMode == 2) AppThemeOption.STOCK_DARK.id else sysMode
    }

    fun toggleRefreshRate() {
        val current = _refreshRate.value
        val maxSupported = getMaxSupportedRefreshRate()
        val next = if (current >= 90) 60f else maxSupported
        PrivilegedBridge.putSystemSettingFloat(context, KEY_MIN_REFRESH_RATE, next)
        PrivilegedBridge.putSystemSettingFloat(context, KEY_PEAK_REFRESH_RATE, next)
        _refreshRate.value = next.roundToInt()
    }

    fun setDisplayBrightnessPercent(displayId: Int, percent: Int, temporary: Boolean) {
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
        PrivilegedBridge.setDisplayBrightness(context, displayId, linear, temporary)
    }

    fun setDualScreenBrightnessPercent(percent: Int, temporary: Boolean) {
        val p = percent.coerceIn(0, 100)
        _dualBrightness.value = p
        _topBrightness.value = p
        _bottomBrightness.value = p
        setDisplayBrightnessPercent(0, p, temporary)
        setDisplayBrightnessPercent(4, p, temporary)
        if (!temporary) {
            PrivilegedBridge.putSystemSettingInt(context, KEY_DUAL_SCREEN_BRIGHTNESS_LEVEL, p)
        }
    }

    fun setPrimaryVolumePercent(percent: Int) {
        val p = percent.coerceIn(0, 100)
        _primaryVolumePercent.value = p
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val minVol = audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0
        val target = ((p / 100f) * (maxVol - minVol) + minVol).roundToInt()
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    fun setSecondaryVolumePercent(percent: Int) {
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

    private fun readDisplayBrightnessPercent(displayId: Int): Int {
        val info = PrivilegedBridge.getDisplayBrightnessInfo(context, displayId)
        val b = info.getOrElse(0) { 0.5f }
        val min = info.getOrElse(1) { 0f }
        val max = info.getOrElse(2) { 1f }
        if (max <= min) return 50
        return (((b - min) / (max - min)) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun readPrimaryVolumePercent(): Int {
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val minVol = audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5
        if (maxVol <= minVol) return 0
        return (((curVol - minVol).toFloat() / (maxVol - minVol)) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun readSecondaryVolumePercent(): Int {
        val cur = getInt(KEY_SECONDARY_SCREEN_VOLUME_LEVEL, 7).coerceIn(0, 15)
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
