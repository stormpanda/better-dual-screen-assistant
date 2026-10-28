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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        systemProps.clear()

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

        // Lock focus to bottom screen (2) while in Dual Screen mode (0)
        repo.setScreenFocusLock(2)
        assertEquals(2, repo.screenFocusLock.value)
        assertEquals(2, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_SCREEN_FOCUS_LOCK, -1))
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK, -1))

        // Switch to Only Top Screen (mode 1) -> saves focus lock = 2, forces focus lock to 1, disables bottom touch ("0,1")
        repo.setDualScreenDisplayMode(1)
        assertEquals(1, repo.dualScreenDisplayMode.value)
        assertEquals(1, repo.screenFocusLock.value)
        assertEquals("0,1", systemProps["sys.temp.disabled.touch"])

        // Switch to Only Bottom Screen (mode 2) -> forces focus lock to 2, disables top touch ("1,0")
        repo.setDualScreenDisplayMode(2)
        assertEquals(2, repo.dualScreenDisplayMode.value)
        assertEquals(2, repo.screenFocusLock.value)
        assertEquals("1,0", systemProps["sys.temp.disabled.touch"])

        // Switch back to Dual Screen (mode 0) -> restores saved focus lock (2) and enables both touch panels ("0,0")
        repo.setDualScreenDisplayMode(0)
        assertEquals(0, repo.dualScreenDisplayMode.value)
        assertEquals(2, repo.screenFocusLock.value)
        assertEquals("0,0", systemProps["sys.temp.disabled.touch"])

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

        repo.toggleKeyMapper()
        assertTrue(repo.keyMapperEnabled.value)
        assertEquals(1, Settings.System.getInt(context.contentResolver, SystemSettingsRepository.KEY_ADAPTER_ENABLE, 0))

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
}
