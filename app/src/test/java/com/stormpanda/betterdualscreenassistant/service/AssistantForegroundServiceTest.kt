package com.stormpanda.betterdualscreenassistant.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.MainActivity
import com.stormpanda.betterdualscreenassistant.overlay.ScreenOverlayManager
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.system.MappingServiceClient
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AssistantForegroundServiceTest {

    private lateinit var context: Context
    private lateinit var service: AssistantForegroundService
    private lateinit var mockOverlayManager: ScreenOverlayManager
    private lateinit var mockRepository: SystemSettingsRepository
    private lateinit var mockMappingClient: MappingServiceClient
    private val displayModeFlow = MutableStateFlow(0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PServerClient.setReplaceStockPreferred(context, true)

        service = AssistantForegroundService()
        val attachMethod = ContextWrapper::class.java.getDeclaredMethod(
            "attachBaseContext",
            Context::class.java
        ).apply { isAccessible = true }
        attachMethod.invoke(service, context)

        mockOverlayManager = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockMappingClient = mockk(relaxed = true)

        every { mockRepository.dualScreenDisplayMode } returns displayModeFlow
        every { mockMappingClient.isHomeMapped } returns false
        every { mockOverlayManager.isBottomScreenBlanked } returns false

        setServiceField("overlayManager", mockOverlayManager)
        setServiceField("repository", mockRepository)
        setServiceField("mappingServiceClient", mockMappingClient)
    }

    @After
    fun tearDown() {
        val instanceField = AssistantForegroundService::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }
        instanceField.set(null, null)
    }

    private fun setServiceField(name: String, value: Any?) {
        val field = AssistantForegroundService::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        field.set(service, value)
    }

    private fun getBooleanField(name: String): Boolean {
        val field = AssistantForegroundService::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        return field.getBoolean(service)
    }

    @Test
    fun `initial key down without long press is a strict no-op`() {
        service.handleTccButtonEvent(keyActionDown = true, isLongPress = false)

        assertFalse(getBooleanField("longPressConsumed"))
        verify(exactly = 0) { mockOverlayManager.toggleAssistantOverlay() }
        verify(exactly = 0) { mockOverlayManager.hideAssistantOverlay(any()) }
        verify(exactly = 0) { mockRepository.setDualScreenDisplayMode(any()) }
    }

    @Test
    fun `long press blanks bottom screen hides overlay and subsequent key up is consumed`() {
        // 1. Long press while key is down
        service.handleTccButtonEvent(keyActionDown = true, isLongPress = true)

        assertTrue(getBooleanField("longPressConsumed"))
        verify(exactly = 1) { mockOverlayManager.hideAssistantOverlay(immediate = true) }
        verify(exactly = 1) { mockRepository.setDualScreenDisplayMode(1) }

        // 2. Subsequent key up after long press must reset flag and neither wake screen nor toggle overlay
        displayModeFlow.value = 1
        service.handleTccButtonEvent(keyActionDown = false, isLongPress = false)

        assertFalse(getBooleanField("longPressConsumed"))
        verify(exactly = 0) { mockOverlayManager.wakeBottomScreen() }
        verify(exactly = 0) { mockOverlayManager.toggleAssistantOverlay() }
        verify(exactly = 0) { mockRepository.setDualScreenDisplayMode(0) }
    }

    @Test
    fun `short press key up wakes bottom screen when display mode is 1 or bottom screen is blanked`() {
        displayModeFlow.value = 1

        service.handleTccButtonEvent(keyActionDown = true, isLongPress = false)
        service.handleTccButtonEvent(keyActionDown = false, isLongPress = false)

        verify(exactly = 1) { mockOverlayManager.wakeBottomScreen() }
        verify(exactly = 0) { mockOverlayManager.toggleAssistantOverlay() }
    }

    @Test
    fun `short press key up toggles overlay in dual screen mode and enforces 200ms debounce`() {
        displayModeFlow.value = 0
        every { mockOverlayManager.isBottomScreenBlanked } returns false
        setServiceField("lastTccToggleElapsedMs", -1000L)

        // First short press -> toggles overlay
        service.handleTccButtonEvent(keyActionDown = true, isLongPress = false)
        service.handleTccButtonEvent(keyActionDown = false, isLongPress = false)
        verify(exactly = 1) { mockOverlayManager.toggleAssistantOverlay() }

        // Immediate second key-up within 200ms debounce window -> ignored
        service.handleTccButtonEvent(keyActionDown = false, isLongPress = false)
        verify(exactly = 1) { mockOverlayManager.toggleAssistantOverlay() }

        // Reset lastTccToggleElapsedMs past 200ms window -> toggles again
        setServiceField("lastTccToggleElapsedMs", -1000L)
        service.handleTccButtonEvent(keyActionDown = false, isLongPress = false)
        verify(exactly = 2) { mockOverlayManager.toggleAssistantOverlay() }
    }

    @Test
    fun `handleTccButtonEvent ignores key events when HOME is mapped in MappingServiceClient`() {
        every { mockMappingClient.isHomeMapped } returns true

        service.handleTccButtonEvent(keyActionDown = false, isLongPress = false)
        verify(exactly = 0) { mockOverlayManager.toggleAssistantOverlay() }
    }

    @Test
    fun `manifest marks MainActivity excludeFromRecents and AssistantForegroundService stopWithTask false`() {
        val pm = context.packageManager
        val activityInfo = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertTrue((activityInfo.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0)

        val serviceInfo = pm.getServiceInfo(ComponentName(context, AssistantForegroundService::class.java), 0)
        assertEquals(0, serviceInfo.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
    }

    @Test
    fun `onTaskRemoved keeps service alive without throwing`() {
        service.onTaskRemoved(Intent(context, MainActivity::class.java))
        assertFalse(getBooleanField("longPressConsumed"))
    }

    @Test
    fun `MainActivity finishes itself when stopped while interactive or on root back press`() {
        val pm = context.getSystemService(PowerManager::class.java)
        shadowOf(pm).setIsInteractive(true)

        // 1. Navigating away (onStop while interactive) finishes and removes task
        val controller1 = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller1.get().isFinishing)
        controller1.pause().stop()
        assertTrue(controller1.get().isFinishing)

        // 2. Screen sleep (onStop while non-interactive) keeps Settings open until user returns
        val controller2 = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(pm).setIsInteractive(false)
        controller2.pause().stop()
        assertFalse(controller2.get().isFinishing)

        // 3. Root back press finishes and removes task
        shadowOf(pm).setIsInteractive(true)
        val controller3 = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller3.get().onBackPressedDispatcher.onBackPressed()
        assertTrue(controller3.get().isFinishing)
    }
}
