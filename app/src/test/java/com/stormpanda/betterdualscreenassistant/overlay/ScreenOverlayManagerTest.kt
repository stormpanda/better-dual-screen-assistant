package com.stormpanda.betterdualscreenassistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
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
        context = ApplicationProvider.getApplicationContext()
        mockRepository = mockk(relaxed = true)
        mockTelemetryMonitor = mockk(relaxed = true)
        mockOverviewProxyClient = mockk(relaxed = true)
        mockkObject(PrivilegedBridge)
        every { PrivilegedBridge.getDisplayBrightnessInfo(any(), any()) } returns floatArrayOf(0.5f, 0f, 1f)
        every { PrivilegedBridge.setDisplayBrightness(any(), any(), any(), any()) } just runs
        every { PrivilegedBridge.setTopScreenBlanked(any(), any(), any()) } just runs
        every { PrivilegedBridge.updateSavedBrightness(any(), any()) } just runs
        every { PrivilegedBridge.setSystemProperty(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
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
            ScreenOverlayManager.BOTTOM_DISPLAY_ID,
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

        // Mode 2: Only Bottom -> top screen blanked, bottom screen unblanked
        manager.applyDisplayModeOverlays(2)
        ShadowLooper.idleMainLooper()
        assertTrue(manager.isTopScreenBlanked)
        assertFalse(manager.isBottomScreenBlanked)
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
        verify { PrivilegedBridge.setDisplayBrightness(context, ScreenOverlayManager.BOTTOM_DISPLAY_ID, 0.5f, false) }

        // Device wakes up (interactive) -> reapplies touch disable and sets blanked display brightness to -1.0f
        manager.onDeviceScreenInteractiveChanged(true)
        ShadowLooper.idleMainLooper()
        verify { mockRepository.reapplyTouchDisableForCurrentMode() }
        verify { PrivilegedBridge.setDisplayBrightness(context, ScreenOverlayManager.BOTTOM_DISPLAY_ID, -1.0f, false) }
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
}
