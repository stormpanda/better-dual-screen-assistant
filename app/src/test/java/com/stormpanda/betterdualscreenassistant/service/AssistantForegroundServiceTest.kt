package com.stormpanda.betterdualscreenassistant.service

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.overlay.ScreenOverlayManager
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.system.MappingServiceClient
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
