package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain
import com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemSettingsRepositoryTest {

    private lateinit var context: Context
    private val systemProps = mutableMapOf<String, String>()
    private var simulatedRecActive = false
    private var simulatedRecTarget = 0
    private var simulatedRecStartMs = 0L
    private var simulatedRecOutputPath = ""
    private var simulatedRecCompletedPath = ""
    private var simulatedRecStopReason = ""
    private var lastCapturedRecFps = 0
    private var lastCapturedRecInternal = false
    private var lastCapturedRecMic = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        systemProps.clear()
        simulatedRecActive = false
        simulatedRecTarget = 0
        simulatedRecStartMs = 0L
        simulatedRecOutputPath = ""
        simulatedRecCompletedPath = ""
        simulatedRecStopReason = ""
        lastCapturedRecFps = 0
        lastCapturedRecInternal = false
        lastCapturedRecMic = false

        val helper = mockk<IBinder>(relaxed = true)
        every { helper.isBinderAlive } returns true
        every { helper.transact(any(), any(), any(), any()) } answers {
            val code = firstArg<Int>()
            val data = secondArg<Parcel>()
            val reply = thirdArg<Parcel?>()
            data.setDataPosition(0)
            if (reply != null) {
                when (code) {
                    PrivilegedHelperMain.TRANSACTION_PING -> {
                        reply.writeNoException()
                        reply.writeInt(PrivilegedHelperMain.HELPER_PROTOCOL_VERSION)
                    }
                    PrivilegedHelperMain.TRANSACTION_SET_SYSTEM_PROP -> {
                        data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                        val k = data.readString() ?: ""
                        val v = data.readString() ?: ""
                        systemProps[k] = v
                        reply.writeNoException()
                    }
                    PrivilegedHelperMain.TRANSACTION_GET_SYSTEM_PROP -> {
                        data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                        val k = data.readString() ?: ""
                        val def = data.readString() ?: ""
                        reply.writeNoException()
                        reply.writeString(systemProps[k] ?: def)
                    }
                    PrivilegedHelperMain.TRANSACTION_GET_BRIGHTNESS -> {
                        reply.writeNoException()
                        reply.writeFloatArray(floatArrayOf(0.5f, 0.0f, 1.0f, 0.5f))
                    }
                    PrivilegedHelperMain.TRANSACTION_START_SCREEN_RECORD -> {
                        data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                        simulatedRecTarget = data.readInt()
                        lastCapturedRecInternal = data.readInt() != 0
                        lastCapturedRecMic = data.readInt() != 0
                        lastCapturedRecFps = data.readInt()
                        simulatedRecOutputPath = data.readString() ?: ""
                        simulatedRecActive = true
                        simulatedRecStartMs = 5_000L
                        reply.writeNoException()
                        reply.writeInt(1)
                        reply.writeInt(1)
                        reply.writeInt(simulatedRecTarget)
                        reply.writeLong(simulatedRecStartMs)
                        reply.writeString(simulatedRecOutputPath)
                        reply.writeString(simulatedRecCompletedPath)
                        reply.writeString(simulatedRecStopReason)
                    }
                    PrivilegedHelperMain.TRANSACTION_STOP_SCREEN_RECORD -> {
                        data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                        simulatedRecStopReason = data.readString() ?: "user"
                        simulatedRecActive = false
                        simulatedRecCompletedPath = simulatedRecOutputPath
                        simulatedRecOutputPath = ""
                        simulatedRecStartMs = 0L
                        reply.writeNoException()
                        reply.writeInt(0)
                        reply.writeInt(simulatedRecTarget)
                        reply.writeLong(0L)
                        reply.writeString("")
                        reply.writeString(simulatedRecCompletedPath)
                        reply.writeString(simulatedRecStopReason)
                    }
                    PrivilegedHelperMain.TRANSACTION_GET_SCREEN_RECORD_STATE -> {
                        data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                        reply.writeNoException()
                        reply.writeInt(if (simulatedRecActive) 1 else 0)
                        reply.writeInt(simulatedRecTarget)
                        reply.writeLong(simulatedRecStartMs)
                        reply.writeString(simulatedRecOutputPath)
                        reply.writeString(simulatedRecCompletedPath)
                        reply.writeString(simulatedRecStopReason)
                    }
                    else -> {
                        reply.writeNoException()
                    }
                }
                reply.setDataPosition(0)
            }
            true
        }
        val field = PrivilegedBridge::class.java.getDeclaredField("helperBinder").apply {
            isAccessible = true
        }
        field.set(PrivilegedBridge, helper)
    }

    @After
    fun tearDown() {
        val field = PrivilegedBridge::class.java.getDeclaredField("helperBinder").apply {
            isAccessible = true
        }
        field.set(PrivilegedBridge, null)
    }

    @Test
    fun `setDualScreenDisplayMode updates mode touch-disable property and saves-restores focus lock`() {
        val repo = SystemSettingsRepository(context)

        // Lock focus to bottom screen (2) while in Dual Screen mode (0):
        // must ONLY update screen_focus_lock and leave Home and Back Focus Lock (enable_system_key_focus_lock) off (unset / 0)
        repo.setScreenFocusLock(2)
        assertEquals(2, repo.screenFocusLock.value)
        assertEquals(2, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_SCREEN_FOCUS_LOCK, -1))
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 0))

        // Switch to Only Top Screen (mode 1) -> saves focus lock = 2 & key focus lock = 0, forces single-screen override, disables bottom touch ("0,1")
        repo.setDualScreenDisplayMode(1)
        assertEquals(1, repo.dualScreenDisplayMode.value)
        assertEquals(1, repo.screenFocusLock.value)
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 0))
        assertEquals("0,1", systemProps["sys.temp.disabled.touch"])

        // Switch to Only Bottom Screen (mode 2) -> forces focus lock to 2, disables top touch ("1,0")
        repo.setDualScreenDisplayMode(2)
        assertEquals(2, repo.dualScreenDisplayMode.value)
        assertEquals(2, repo.screenFocusLock.value)
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 0))
        assertEquals("1,0", systemProps["sys.temp.disabled.touch"])

        // Switch back to Dual Screen (mode 0) -> restores saved focus lock (2), restores Home and Back Focus Lock to 0, and enables both touch panels ("0,0")
        repo.setDualScreenDisplayMode(0)
        assertEquals(0, repo.dualScreenDisplayMode.value)
        assertEquals(2, repo.screenFocusLock.value)
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, -1))
        assertEquals("0,0", systemProps["sys.temp.disabled.touch"])

        repo.unregister()
    }

    @Test
    fun `Home and Back Focus Lock stays disabled across refreshAll observer updates and external mode resets`() {
        val repo = SystemSettingsRepository(context)
        repo.register()

        // 1. User has Home and Back Focus Lock OFF (0), sets Focus Mode to Top Screen (1)
        Settings.System.putInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 0)
        repo.setScreenFocusLock(1)
        repo.refreshAll()
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, -1))

        // 2. Long-press AYN button blanks bottom screen (mode 1) -> temporary single-screen override sets enable_system_key_focus_lock = 1
        repo.setDualScreenDisplayMode(1)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, -1))

        // 3. Even if dual_screen_display_mode is reset to 0 externally before restoreDualScreenFocusLock() runs,
        //    refreshAll() restores the saved Dual-Screen value (0) instead of clobbering it with 1.
        Settings.System.putInt(context.contentResolver, SystemSettingsRepository.KEY_DUAL_SCREEN_DISPLAY_MODE, 0)
        repo.refreshAll()
        assertEquals(0, repo.dualScreenDisplayMode.value)
        assertEquals(1, repo.screenFocusLock.value)
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, -1))

        // 4. Conversely, if the user explicitly enables Home and Back Focus Lock (1) in Thor Settings while in Dual-Screen mode,
        //    entering and exiting single-screen mode preserves their enabled (1) preference.
        Settings.System.putInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, 1)
        repo.refreshAll()
        repo.setDualScreenDisplayMode(1)
        repo.setDualScreenDisplayMode(0)
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, -1))

        repo.unregister()
    }

    @Test
    fun `setControllerStyle writes expected ABXY flip and gamepad layout settings`() {
        val repo = SystemSettingsRepository(context)

        // Style 0: Xbox
        repo.setControllerStyle(0)
        assertEquals(0, repo.controllerStyle.value)
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_TEMP_ABXY_LAYOUT_MODE, -1))
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_FLIP_BUTTON_LAYOUT, -1))
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_NO_CREATE_GAMEPAD_LAYOUT, -1))

        // Style 1: Odin / Standard
        repo.setControllerStyle(1)
        assertEquals(1, repo.controllerStyle.value)
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_TEMP_ABXY_LAYOUT_MODE, -1))
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_FLIP_BUTTON_LAYOUT, -1))
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_NO_CREATE_GAMEPAD_LAYOUT, -1))

        // Style 2: Disabled (Ban On Use)
        repo.setControllerStyle(2)
        assertEquals(2, repo.controllerStyle.value)
        assertEquals(2, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_TEMP_ABXY_LAYOUT_MODE, -1))
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_NO_CREATE_GAMEPAD_LAYOUT, -1))
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_FLIP_BUTTON_LAYOUT, -1))

        repo.unregister()
    }

    @Test
    fun `hardware and system toggles update stateFlows and Settings accurately`() {
        Settings.System.putFloat(context.contentResolver, SystemSettingsRepository.KEY_PEAK_REFRESH_RATE, 120f)
        val repo = SystemSettingsRepository(context)

        repo.setPerformanceMode(2)
        assertEquals(2, repo.performanceMode.value)

        repo.setFanMode(5)
        assertEquals(5, repo.fanMode.value)

        repo.setCustomFanSpeed(75)
        assertEquals(75, repo.fanSpeed.value)

        repo.toggleChargingSeparation()
        assertTrue(repo.chargingSeparation.value)
        repo.toggleChargingSeparation()
        assertFalse(repo.chargingSeparation.value)

        repo.toggleVibrate()
        assertFalse(repo.vibrateOn.value) // default is true (1)

        repo.toggleForceLandscape()
        assertFalse(repo.forceLandscape.value) // default is true (1)

        // Refresh rate toggles from 120 down to 60
        assertEquals(120, repo.refreshRate.value)
        repo.toggleRefreshRate()
        assertEquals(60, repo.refreshRate.value)

        assertTrue(repo.toggleTouchLock())
        assertTrue(repo.touchLockEnabled.value)

        // Trigger mode clamps to 0..2
        repo.setTriggerMode(5)
        assertEquals(2, repo.triggerMode.value)
        repo.setTriggerMode(-3)
        assertEquals(0, repo.triggerMode.value)

        repo.toggleChargeLimit80()
        assertTrue(repo.chargeLimit80.value)

        repo.toggleGamepadMouseMode()
        assertTrue(repo.gamepadMouseMode.value)

        // Simulate GameAssistant active in a game (is_show_floatingIcon = 1); toggling Key Mapper must ONLY
        // update key_adapter_enable (default 1 -> 0 -> 1) and never clobber GameAssistant's is_show_floatingIcon!
        Settings.System.putInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, 1)
        assertTrue(repo.keyMapperEnabled.value)
        repo.toggleKeyMapper()
        assertFalse(repo.keyMapperEnabled.value)
        assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ADAPTER_ENABLE, -1))
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, -1))
        repo.toggleKeyMapper()
        assertTrue(repo.keyMapperEnabled.value)
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ADAPTER_ENABLE, -1))
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, -1))

        repo.toggleKeepScreenOn()
        assertTrue(repo.keepScreenOn.value)
        repo.toggleKeepScreenOn()
        assertFalse(repo.keepScreenOn.value)

        repo.togglePreventAccidentalHome()
        assertFalse(repo.preventAccidentalHome.value) // default is true (1)

        repo.unregister()
    }

    @Test
    fun `brightness LED and volume controls clamp percentages and synchronize dual sliders`() {
        val repo = SystemSettingsRepository(context)

        repo.setDualScreenBrightnessPercent(80, temporary = false)
        assertTrue(repo.isDualBrightnessActive.value)
        assertEquals(80, repo.dualBrightness.value)
        assertEquals(80, repo.topBrightness.value)
        assertEquals(80, repo.bottomBrightness.value)

        repo.setDisplayBrightnessPercent(0, 90, temporary = false)
        repo.setDisplayBrightnessPercent(4, 30, temporary = false)
        assertFalse(repo.isDualBrightnessActive.value)
        assertEquals(90, repo.topBrightness.value)
        assertEquals(30, repo.bottomBrightness.value)

        // LED brightness
        repo.setLedBrightnessPercent(0)
        assertEquals(0, repo.ledBrightnessPercent.value)
        assertEquals(0f, Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_LED_BRIGHTNESS_PERCENT, -1f), 0.001f)

        repo.setLedBrightnessPercent(100)
        assertEquals(100, repo.ledBrightnessPercent.value)
        assertEquals(1f, Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_LED_BRIGHTNESS_PERCENT, -1f), 0.001f)

        // Secondary & Dual volume
        repo.setSecondaryVolumePercent(50)
        assertFalse(repo.isDualVolumeActive.value)
        assertEquals(50, repo.secondaryVolumePercent.value)

        repo.setDualVolumePercent(70)
        assertTrue(repo.isDualVolumeActive.value)
        assertEquals(70, repo.dualVolumePercent.value)
        assertEquals(70, repo.secondaryVolumePercent.value)

        repo.unregister()
    }

    @Test
    fun `quick tile ordering moving and minimum-1-enabled invariant work properly`() {
        val repo = SystemSettingsRepository(context)

        // 1. setQuickTileOrder normalizes partial list by appending remaining tiles
        val customPrefix = listOf(QuickSettingTileId.SCREENSHOT, QuickSettingTileId.WIFI)
        repo.setQuickTileOrder(customPrefix)
        val newOrder = repo.quickTileOrder.value
        assertEquals(QuickSettingTileId.entries.size, newOrder.size)
        assertEquals(QuickSettingTileId.SCREENSHOT, newOrder[0])
        assertEquals(QuickSettingTileId.WIFI, newOrder[1])

        // 2. Reset back to default
        repo.resetQuickTilesToDefault()
        assertEquals(QuickSettingTileId.defaultOrder, repo.quickTileOrder.value)
        assertEquals(QuickSettingTileId.defaultDisabled, repo.disabledQuickTiles.value)

        // 3. Move active quick tile swaps positions among active tiles
        val activeBefore = repo.quickTileOrder.value.filter { it !in repo.disabledQuickTiles.value }
        val firstActive = activeBefore[0]
        val secondActive = activeBefore[1]
        repo.moveActiveQuickTile(0, 1)
        val activeAfter = repo.quickTileOrder.value.filter { it !in repo.disabledQuickTiles.value }
        assertEquals(secondActive, activeAfter[0])
        assertEquals(firstActive, activeAfter[1])

        // 4. Disable all active tiles except one -> last tile cannot be disabled!
        val currentlyActive = repo.quickTileOrder.value.filter { it !in repo.disabledQuickTiles.value }
        for (i in 0 until currentlyActive.size - 1) {
            repo.setQuickTileEnabled(currentlyActive[i], false)
        }
        val lastRemainingTile = currentlyActive.last()
        assertFalse(lastRemainingTile in repo.disabledQuickTiles.value)

        // Attempt to disable the final remaining enabled tile -> must be ignored
        repo.setQuickTileEnabled(lastRemainingTile, false)
        assertFalse(lastRemainingTile in repo.disabledQuickTiles.value)

        repo.unregister()
    }

    @Test
    fun `theme mode custom accent and UI visibility preferences update stateFlows`() {
        val repo = SystemSettingsRepository(context)

        repo.setThemeMode(AppThemeOption.VALHALLA.id)
        assertEquals(AppThemeOption.VALHALLA.id, repo.themeMode.value)

        // Retroid readability boost defaults to true and persists across repository instances
        assertTrue(repo.retroidReadabilityBoost.value)
        repo.setRetroidReadabilityBoost(false)
        assertFalse(repo.retroidReadabilityBoost.value)
        assertFalse(SystemSettingsRepository(context).retroidReadabilityBoost.value)
        repo.setRetroidReadabilityBoost(true)
        assertTrue(repo.retroidReadabilityBoost.value)

        repo.setCustomAccentArgb(0xFF112233.toInt())
        assertEquals(0xFF112233.toInt(), repo.customAccentArgb.value)

        repo.setInjectOsTheme(true)
        assertTrue(repo.injectOsTheme.value)
        repo.setInjectOsTheme(false)
        assertFalse(repo.injectOsTheme.value)

        repo.setRememberLastTab(false)
        assertFalse(repo.rememberLastTab.value)

        repo.setShowLedBrightnessSlider(false)
        assertFalse(repo.showLedBrightnessSlider.value)

        repo.setShowSwapScreensButton(false)
        assertFalse(repo.showSwapScreensButton.value)

        repo.setShowEditButton(false)
        assertFalse(repo.showEditButton.value)

        repo.setNoMoreCloseBottomScreenTip(true)
        assertFalse(repo.shouldShowCloseBottomScreenTip())

        // Temperature unit defaults to Celsius (false), toggles to Fahrenheit (true), and persists
        assertFalse(repo.tempFahrenheit.value)
        repo.toggleTempFahrenheit()
        assertTrue(repo.tempFahrenheit.value)
        repo.setTempFahrenheit(false)
        assertFalse(repo.tempFahrenheit.value)
        repo.setTempFahrenheit(true)
        val reloadedRepo = SystemSettingsRepository(context)
        assertTrue(reloadedRepo.tempFahrenheit.value)
        reloadedRepo.unregister()

        repo.unregister()
    }

    @Test
    fun `toggleRefreshRate updates UI state immediately while deferring and coalescing Settings writes`() {
        Settings.System.putFloat(context.contentResolver, SystemSettingsRepository.KEY_MIN_REFRESH_RATE, 120f)
        Settings.System.putFloat(context.contentResolver, SystemSettingsRepository.KEY_PEAK_REFRESH_RATE, 120f)
        val wrappedContext = object : android.content.ContextWrapper(context) {
            override fun getSystemServiceName(serviceClass: Class<*>): String? {
                if (serviceClass == android.hardware.display.DisplayManager::class.java) {
                    return Context.DISPLAY_SERVICE
                }
                return super.getSystemServiceName(serviceClass)
            }

            override fun getSystemService(name: String): Any? {
                if (name == Context.DISPLAY_SERVICE) return null
                return super.getSystemService(name)
            }
        }

        val repo = SystemSettingsRepository(wrappedContext)
        assertEquals(120, repo.refreshRate.value)

        // Single tap: UI state flips immediately to 60, while Settings.System write is deferred
        repo.toggleRefreshRate()
        assertEquals(60, repo.refreshRate.value)
        assertEquals(
            120f,
            Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_PEAK_REFRESH_RATE, -1f),
            0.01f
        )

        // Intervening refreshAll() during the 220ms settle window must not clobber optimistic UI state
        repo.refreshAll(refreshDisplayBrightness = false, refreshMegingiardTheme = false)
        assertEquals(60, repo.refreshRate.value)

        // Once the settle window commits, both min and peak refresh rates are updated to 60f
        repo.commitPendingRefreshRate()
        assertEquals(
            60f,
            Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_MIN_REFRESH_RATE, -1f),
            0.01f
        )
        assertEquals(
            60f,
            Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_PEAK_REFRESH_RATE, -1f),
            0.01f
        )

        // Rapid double-tap (60 -> 120 -> 60) within settle window coalesces back to 60 without writing 120f
        repo.toggleRefreshRate()
        assertEquals(120, repo.refreshRate.value)
        repo.toggleRefreshRate()
        assertEquals(60, repo.refreshRate.value)
        repo.commitPendingRefreshRate()
        assertEquals(
            60f,
            Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_PEAK_REFRESH_RATE, -1f),
            0.01f
        )

        // Unregistering while a write is pending flushes it synchronously
        repo.toggleRefreshRate()
        assertEquals(120, repo.refreshRate.value)
        repo.unregister()
        assertEquals(
            120f,
            Settings.System.getFloat(context.contentResolver, SystemSettingsRepository.KEY_PEAK_REFRESH_RATE, -1f),
            0.01f
        )
    }

    @Test
    fun `resyncFloatingIconGestureGuard notifies observers only when is_show_floatingIcon is active without modifying value`() {
        val repo = SystemSettingsRepository(context)
        val uri = Settings.System.getUriFor(SystemSettingsRepository.KEY_SHOW_FLOATING_ICON)
        var observerCallCount = 0
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                observerCallCount++
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        try {
            // 1. When is_show_floatingIcon is 0 (outside a game), resync does not emit spurious notifications
            Settings.System.putInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, 0)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            observerCallCount = 0
            repo.resyncFloatingIconGestureGuard()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals(0, observerCallCount)
            assertEquals(0, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, -1))

            // 2. When is_show_floatingIcon is 1 (GameAssistant active), resync notifies SystemUI observer without modifying value
            Settings.System.putInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, 1)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            observerCallCount = 0
            repo.resyncFloatingIconGestureGuard()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals(1, observerCallCount)
            assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_SHOW_FLOATING_ICON, -1))
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
            repo.unregister()
        }
    }

    @Test
    fun `setPromotedToManager persists preference and defers launcher alias swap while MainActivity is started`() {
        val pm = context.packageManager
        val assistantComp = android.content.ComponentName(
            context.packageName,
            SystemSettingsRepository.LAUNCHER_ALIAS_ASSISTANT
        )
        val managerComp = android.content.ComponentName(
            context.packageName,
            SystemSettingsRepository.LAUNCHER_ALIAS_MANAGER
        )
        pm.setComponentEnabledSetting(
            assistantComp,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            managerComp,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            android.content.pm.PackageManager.DONT_KILL_APP
        )

        val repo = SystemSettingsRepository(context)
        assertFalse(repo.promotedToManager.value)

        // 1. While MainActivity is started, toggling promotion updates StateFlow & SharedPreferences immediately
        //    but defers PackageManager component state mutation until MainActivity stops.
        val token = 4242
        repo.setMainActivityStarted(token, started = true)
        repo.setPromotedToManager(true)
        assertTrue(repo.promotedToManager.value)
        assertTrue(
            context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SystemSettingsRepository.PREF_PROMOTED_TO_MANAGER, false)
        )
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            pm.getComponentEnabledSetting(managerComp)
        )

        // Configuration change stop (flushIfStopped = false) must NOT flush prematurely
        repo.setMainActivityStarted(token, started = false, flushIfStopped = false)
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            pm.getComponentEnabledSetting(managerComp)
        )

        // Recreated MainActivity starts and then genuinely stops (flushIfStopped = true) -> flushes alias swap!
        val recreatedToken = 4243
        repo.setMainActivityStarted(recreatedToken, started = true)
        repo.setMainActivityStarted(recreatedToken, started = false, flushIfStopped = true)
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(managerComp)
        )
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(assistantComp)
        )

        // 2. When no MainActivity is started, demoting back to Assistant swaps aliases immediately
        repo.setPromotedToManager(false)
        assertFalse(repo.promotedToManager.value)
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(assistantComp)
        )
        assertEquals(
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(managerComp)
        )

        repo.unregister()
    }

    @Test
    fun `screen recording preferences persist and start-stop-sync update screenRecordingState`() {
        val repo = SystemSettingsRepository(context)
        assertTrue(repo.screenRecordInternalAudio.value)
        assertFalse(repo.screenRecordMicAudio.value)
        assertEquals(60, repo.screenRecordFps.value)
        assertFalse(repo.screenRecordingState.value.isRecording)

        repo.setScreenRecordInternalAudio(false)
        repo.setScreenRecordMicAudio(true)
        repo.setScreenRecordFps(24) // clamps to 30
        assertFalse(repo.screenRecordInternalAudio.value)
        assertTrue(repo.screenRecordMicAudio.value)
        assertEquals(30, repo.screenRecordFps.value)

        // Verify persistence across new repository instance
        val reloaded = SystemSettingsRepository(context)
        assertFalse(reloaded.screenRecordInternalAudio.value)
        assertTrue(reloaded.screenRecordMicAudio.value)
        assertEquals(30, reloaded.screenRecordFps.value)

        val started = reloaded.startScreenRecording(targetMode = 99, outputPath = "/tmp/test_both.mp4")
        assertTrue(started)
        assertFalse(lastCapturedRecInternal)
        assertTrue(lastCapturedRecMic)
        assertEquals(30, lastCapturedRecFps)
        assertTrue(reloaded.screenRecordingState.value.isRecording)
        assertEquals(2, reloaded.screenRecordingState.value.targetMode)
        assertEquals("/tmp/test_both.mp4", reloaded.screenRecordingState.value.outputPath)

        // Verify syncScreenRecordingStateFromHelper reflects live helper state on a fresh instance
        val synced = repo.syncScreenRecordingStateFromHelper()
        assertTrue(synced.isRecording)
        assertEquals(2, synced.targetMode)
        assertEquals("/tmp/test_both.mp4", synced.outputPath)

        val stoppedState = reloaded.stopScreenRecording("user")
        assertFalse(stoppedState.isRecording)
        assertEquals("/tmp/test_both.mp4", stoppedState.lastCompletedPath)
        assertEquals("user", stoppedState.lastStopReason)

        repo.unregister()
        reloaded.unregister()
    }
}
