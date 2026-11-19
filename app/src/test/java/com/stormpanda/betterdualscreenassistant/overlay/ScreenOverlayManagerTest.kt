package com.stormpanda.betterdualscreenassistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.OverviewProxyClient
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.system.TelemetryMonitor
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ScreenOverlayManagerTest {

    private lateinit var context: Context
    private lateinit var mockRepository: SystemSettingsRepository
    private lateinit var mockTelemetryMonitor: TelemetryMonitor
    private lateinit var mockOverviewProxyClient: OverviewProxyClient

    @Before
    fun setUp() {
        PrivilegedBridge.resetForTesting()
        DeviceProfile.testOverride = null
        DeviceProfile.resetCacheForTesting()
        context = ApplicationProvider.getApplicationContext()
        mockRepository = mockk(relaxed = true)
        every { mockRepository.consumePendingBlankedTopLinear() } returns null
        every { mockRepository.consumePendingBlankedBottomLinear() } returns null
        mockTelemetryMonitor = mockk(relaxed = true)
        mockOverviewProxyClient = mockk(relaxed = true)
        mockkObject(PrivilegedBridge)
        every { PrivilegedBridge.getDisplayBrightnessInfo(any(), any()) } returns floatArrayOf(0.5f, 0f, 1f)
        every { PrivilegedBridge.setDisplayBrightness(any(), any(), any(), any()) } just runs
        every { PrivilegedBridge.setTopScreenBlanked(any(), any(), any()) } just runs
        every { PrivilegedBridge.setBottomScreenBlanked(any(), any(), any()) } just runs
        every { PrivilegedBridge.setBacklightPower(any(), any(), any()) } just runs
        every { PrivilegedBridge.updateSavedBrightness(any(), any()) } just runs
        every { PrivilegedBridge.setSystemProperty(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        PrivilegedBridge.resetForTesting()
        DeviceProfile.testOverride = null
        DeviceProfile.resetCacheForTesting()
    }

    @Test
    fun `createBlankOverlayLayoutParams never sets FLAG_LAYOUT_NO_LIMITS on Bottom Display 4`() {
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        val method = ScreenOverlayManager::class.java.getDeclaredMethod(
            "createBlankOverlayLayoutParams",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        // Bottom Display (ID 4): MUST NOT include FLAG_LAYOUT_NO_LIMITS (prevents 1920px primary surface clipping bug)
        val bottomLp = method.invoke(
            manager,
            DeviceProfile.AYN_THOR.bottomDisplayId,
            1240,
            1080
        ) as WindowManager.LayoutParams
        assertEquals(
            0,
            bottomLp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        assertTrue((bottomLp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, bottomLp.type)
        assertEquals(PixelFormat.TRANSLUCENT, bottomLp.format)
        assertEquals(Gravity.CENTER, bottomLp.gravity)
        assertEquals(0, bottomLp.fitInsetsTypes)

        // Top Display (ID 0): DOES include FLAG_LAYOUT_NO_LIMITS
        val topLp = method.invoke(
            manager,
            ScreenOverlayManager.TOP_DISPLAY_ID,
            1920,
            1080
        ) as WindowManager.LayoutParams
        assertTrue(
            (topLp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0
        )
    }

    @Test
    fun `createAssistantOverlayLayoutParams matches stock TccServices fullscreen flags without NO_LIMITS`() {
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        val thorLp = manager.createAssistantOverlayLayoutParams(
            width = 1240,
            height = 1080,
            useLocalFocusMode = true
        )
        assertEquals(1240, thorLp.width)
        assertEquals(1080, thorLp.height)
        assertEquals(0, thorLp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        @Suppress("DEPRECATION")
        assertTrue((thorLp.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)
        assertTrue((thorLp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0)
        assertTrue((thorLp.flags and WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0)
        assertTrue((thorLp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) != 0)
        assertTrue((thorLp.flags and WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE) != 0)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, thorLp.softInputMode)
        assertEquals(Gravity.CENTER, thorLp.gravity)
        assertEquals(0, thorLp.fitInsetsTypes)

        val duoLp = manager.createAssistantOverlayLayoutParams(
            width = 1280,
            height = 960,
            useLocalFocusMode = false
        )
        assertEquals(0, duoLp.flags and WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE)
        @Suppress("DEPRECATION")
        assertTrue((duoLp.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, duoLp.softInputMode)
    }

    @Test
    fun `performToggleTouchLockWithToast toggles touch lock in repository`() {
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        manager.performToggleTouchLockWithToast()
        ShadowLooper.idleMainLooper()
        verify(exactly = 1) { mockRepository.toggleTouchLock() }
    }

    @Test
    fun `isAssistantShowing isTopScreenBlanked and isBottomScreenBlanked default to false`() {
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        assertFalse(manager.isAssistantShowing)
        assertFalse(manager.isTopScreenBlanked)
        assertFalse(manager.isBottomScreenBlanked)
    }

    @Test
    fun `applyDisplayModeOverlays updates top and bottom screen blanked states`() {
        every { mockRepository.topBrightness } returns MutableStateFlow(50)
        every { mockRepository.bottomBrightness } returns MutableStateFlow(50)

        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        // Mode 1: Only Top -> bottom screen blanked, top screen unblanked
        manager.applyDisplayModeOverlays(1)
        ShadowLooper.idleMainLooper()
        assertFalse(manager.isTopScreenBlanked)
        assertTrue(manager.isBottomScreenBlanked)
        verify { PrivilegedBridge.setBottomScreenBlanked(context, blank = true, showCoverWindow = false) }

        // Mode 2: Only Bottom -> top screen blanked, bottom screen unblanked
        manager.applyDisplayModeOverlays(2)
        ShadowLooper.idleMainLooper()
        assertTrue(manager.isTopScreenBlanked)
        assertFalse(manager.isBottomScreenBlanked)
        verify { PrivilegedBridge.setBottomScreenBlanked(context, blank = false, showCoverWindow = false) }
        verify { PrivilegedBridge.setTopScreenBlanked(context, blank = true, showCoverWindow = false) }

        // Mode 0: Dual Screen -> both unblanked
        manager.applyDisplayModeOverlays(0)
        ShadowLooper.idleMainLooper()
        assertFalse(manager.isTopScreenBlanked)
        assertFalse(manager.isBottomScreenBlanked)
        verify { PrivilegedBridge.setTopScreenBlanked(context, blank = false, showCoverWindow = false) }
    }

    @Test
    fun `wakeBottomScreen restores dual screen mode when mode is 1 or unblanks bottom screen`() {
        every { mockRepository.dualScreenDisplayMode } returns MutableStateFlow(1)
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        manager.wakeBottomScreen()
        ShadowLooper.idleMainLooper()
        verify(exactly = 1) { mockRepository.setDualScreenDisplayMode(0) }
    }

    @Test
    fun `onDeviceScreenInteractiveChanged restores brightness on sleep and re-blanks on wake`() {
        every { mockRepository.topBrightness } returns MutableStateFlow(60)
        every { mockRepository.bottomBrightness } returns MutableStateFlow(60)

        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        // Blank bottom screen (Mode 1)
        manager.applyDisplayModeOverlays(1)
        ShadowLooper.idleMainLooper()
        assertTrue(manager.isBottomScreenBlanked)

        // Device goes non-interactive (screen sleep) -> restores saved bottom brightness (0.5f)
        manager.onDeviceScreenInteractiveChanged(false)
        ShadowLooper.idleMainLooper()
        verify { PrivilegedBridge.setDisplayBrightness(context, DeviceProfile.AYN_THOR.bottomDisplayId, 0.5f, false) }

        // Device wakes up (interactive) -> reapplies touch disable and sets blanked display brightness to -1.0f
        manager.onDeviceScreenInteractiveChanged(true)
        ShadowLooper.idleMainLooper()
        verify { mockRepository.reapplyTouchDisableForCurrentMode() }
        verify { PrivilegedBridge.setBottomScreenBlanked(context, blank = true, showCoverWindow = true) }
        verify { PrivilegedBridge.setDisplayBrightness(context, DeviceProfile.AYN_THOR.bottomDisplayId, -1.0f, false) }
    }

    @Test
    fun `performCleanMemoryWithToast invokes repository cleanMemory`() {
        every { mockRepository.cleanMemory() } returns (3 to "4.2 GB")
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        manager.performCleanMemoryWithToast()
        ShadowLooper.idleMainLooper()
        verify(exactly = 1) { mockRepository.cleanMemory() }
    }

    @Test
    fun `performClearAllTasksWithToast invokes repository clearAllRecentTasks`() {
        every { mockRepository.clearAllRecentTasks() } returns 3
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        manager.performClearAllTasksWithToast()
        ShadowLooper.idleMainLooper(300L, java.util.concurrent.TimeUnit.MILLISECONDS)
        verify(timeout = 1_500L, exactly = 1) { mockRepository.clearAllRecentTasks() }
    }

    @Test
    fun `Retroid Pocket Duo blanks via panel backlight sysfs without cover window or negative brightness`() {
        DeviceProfile.testOverride = DeviceProfile.RETROID_POCKET_DUO
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        val method = ScreenOverlayManager::class.java.getDeclaredMethod(
            "createBlankOverlayLayoutParams",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val retroidBottomLp = method.invoke(manager, 2, 1280, 960) as WindowManager.LayoutParams
        assertEquals(0, retroidBottomLp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // Mode 1: Only Top -> blanks panel 1 via sysfs, leaves panel 0 unblanked
        manager.applyDisplayModeOverlays(1)
        ShadowLooper.idleMainLooper()
        assertFalse(manager.isTopScreenBlanked)
        assertTrue(manager.isBottomScreenBlanked)
        verify { PrivilegedBridge.setBacklightPower(context, panelIndex = 1, blanked = true) }
        verify(exactly = 0) { PrivilegedBridge.setBottomScreenBlanked(any(), any(), any()) }
        verify(exactly = 0) { PrivilegedBridge.setDisplayBrightness(any(), any(), -1.0f, any()) }

        // Mode 2: Only Bottom -> blanks panel 0 via sysfs, unblanks panel 1
        manager.applyDisplayModeOverlays(2)
        ShadowLooper.idleMainLooper()
        assertTrue(manager.isTopScreenBlanked)
        assertFalse(manager.isBottomScreenBlanked)
        verify { PrivilegedBridge.setBacklightPower(context, panelIndex = 0, blanked = true) }
        verify { PrivilegedBridge.setBacklightPower(context, panelIndex = 1, blanked = false) }

        // Interactive wake re-asserts backlight power on blanked panel
        manager.onDeviceScreenInteractiveChanged(true)
        ShadowLooper.idleMainLooper()
        verify { mockRepository.reapplyTouchDisableForCurrentMode() }
        verify(atLeast = 2) { PrivilegedBridge.setBacklightPower(context, panelIndex = 0, blanked = true) }
    }

    @Test
    fun `addOverlayViewWithPrivilegeFallback uses 2021 when session installed and falls back to 2038 on error`() {
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )
        val attachMethod = ScreenOverlayManager::class.java.getDeclaredMethod(
            "addOverlayViewWithPrivilegeFallback",
            WindowManager::class.java,
            android.view.View::class.java,
            WindowManager.LayoutParams::class.java
        ).apply { isAccessible = true }

        val dummyView = android.view.View(context)
        val lp = WindowManager.LayoutParams()
        val observedTypes = mutableListOf<Int>()
        var failOnPrivilegedType = false
        val mockWm = mockk<WindowManager>()
        every { mockWm.addView(any(), any()) } answers {
            val params = secondArg<WindowManager.LayoutParams>()
            observedTypes.add(params.type)
            if (failOnPrivilegedType && params.type == ScreenOverlayManager.PRIVILEGED_OVERLAY_WINDOW_TYPE) {
                throw SecurityException("Simulated privileged type rejection")
            }
        }

        // 1. Privileged window session installed -> attaches directly as 2021 (TYPE_BOOT_PROGRESS)
        every { PrivilegedBridge.installPrivilegedWindowSession() } returns true
        attachMethod.invoke(manager, mockWm, dummyView, lp)
        assertEquals(listOf(ScreenOverlayManager.PRIVILEGED_OVERLAY_WINDOW_TYPE), observedTypes)
        assertEquals(ScreenOverlayManager.PRIVILEGED_OVERLAY_WINDOW_TYPE, lp.type)

        // 2. Privileged window type throws -> falls back cleanly to TYPE_APPLICATION_OVERLAY (2038)
        observedTypes.clear()
        failOnPrivilegedType = true
        attachMethod.invoke(manager, mockWm, dummyView, lp)
        assertEquals(
            listOf(
                ScreenOverlayManager.PRIVILEGED_OVERLAY_WINDOW_TYPE,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            ),
            observedTypes
        )
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, lp.type)
    }

    @Test
    fun `useLocalFocusMode and cold-start Retroid backlight sync follow DeviceProfile invariants`() {
        val lpMethod = ScreenOverlayManager::class.java.getDeclaredMethod(
            "createBlankOverlayLayoutParams",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        // AYN Thor sets FLAG_LOCAL_FOCUS_MODE
        DeviceProfile.testOverride = DeviceProfile.AYN_THOR
        val aynManager = ScreenOverlayManager(context, mockRepository, mockTelemetryMonitor, mockOverviewProxyClient)
        val aynLp = lpMethod.invoke(aynManager, 4, 1240, 1080) as WindowManager.LayoutParams
        assertTrue((aynLp.flags and WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE) != 0)

        // Retroid Pocket Duo omits FLAG_LOCAL_FOCUS_MODE and syncs both panel backlights on cold-start Mode 0
        DeviceProfile.testOverride = DeviceProfile.RETROID_POCKET_DUO
        val duoManager = ScreenOverlayManager(context, mockRepository, mockTelemetryMonitor, mockOverviewProxyClient)
        val duoLp = lpMethod.invoke(duoManager, 2, 1280, 960) as WindowManager.LayoutParams
        assertEquals(0, duoLp.flags and WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE)

        duoManager.applyDisplayModeOverlays(0)
        ShadowLooper.idleMainLooper()
        verify(exactly = 1) { PrivilegedBridge.setBacklightPower(context, panelIndex = 0, blanked = false) }
        verify(exactly = 1) { PrivilegedBridge.setBacklightPower(context, panelIndex = 1, blanked = false) }
    }

    @Test
    fun `resolveCastSettingsTargetDisplayId targets Top Screen unless Only Bottom Screen mode is active`() {
        assertEquals(0, ScreenOverlayManager.resolveCastSettingsTargetDisplayId(dualScreenMode = 0, bottomDisplayId = 4))
        assertEquals(0, ScreenOverlayManager.resolveCastSettingsTargetDisplayId(dualScreenMode = 1, bottomDisplayId = 4))
        assertEquals(4, ScreenOverlayManager.resolveCastSettingsTargetDisplayId(dualScreenMode = 2, bottomDisplayId = 4))
        assertEquals(2, ScreenOverlayManager.resolveCastSettingsTargetDisplayId(dualScreenMode = 2, bottomDisplayId = 2))
    }

    @Test
    fun `openClusterTuneProfilePickerOnTopScreen skips launch when uninstalled or top screen is off`() {
        every { mockRepository.dualScreenDisplayMode } returns MutableStateFlow(0)
        every { mockRepository.refreshClusterTuneInstalled() } returns false
        val manager = ScreenOverlayManager(
            context = context,
            repository = mockRepository,
            telemetryMonitor = mockTelemetryMonitor,
            overviewProxyClient = mockOverviewProxyClient
        )

        manager.openClusterTuneProfilePickerOnTopScreen()
        ShadowLooper.idleMainLooper()
        verify(exactly = 0) { mockRepository.openClusterTuneProfilePicker() }

        every { mockRepository.refreshClusterTuneInstalled() } returns true
        every { mockRepository.dualScreenDisplayMode } returns MutableStateFlow(2)
        manager.openClusterTuneProfilePickerOnTopScreen()
        ShadowLooper.idleMainLooper()
        verify(exactly = 0) { mockRepository.openClusterTuneProfilePicker() }

        // When installed and top screen is on, launches ClusterTune without dismissing BDSA overlay
        val launched = java.util.concurrent.CountDownLatch(1)
        every { mockRepository.dualScreenDisplayMode } returns MutableStateFlow(0)
        every { mockRepository.openClusterTuneProfilePicker() } answers {
            launched.countDown()
            com.stormpanda.betterdualscreenassistant.system.ClusterTuneLaunchResult.OPENED
        }
        val showingField = ScreenOverlayManager::class.java.getDeclaredField("isAssistantShowing").apply {
            isAccessible = true
        }
        showingField.setBoolean(manager, true)

        manager.openClusterTuneProfilePickerOnTopScreen()
        assertTrue(launched.await(2, java.util.concurrent.TimeUnit.SECONDS))
        ShadowLooper.idleMainLooper()
        assertTrue("BDSA overlay should remain open when opening ClusterTune", manager.isAssistantShowing)
        verify(exactly = 1) { mockRepository.openClusterTuneProfilePicker() }
    }
}
