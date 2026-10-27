package com.stormpanda.betterdualscreenassistant.receiver

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReceiversTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setServiceInstance(null)
        shadowOf(context as Application).clearStartedServices()
    }

    @After
    fun tearDown() {
        setServiceInstance(null)
    }

    private fun setServiceInstance(service: AssistantForegroundService?) {
        val field = AssistantForegroundService::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }
        field.set(null, service)
    }

    @Test
    fun `BootReceiver starts AssistantForegroundService on valid boot and package-replaced actions`() {
        val receiver = BootReceiver()
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        for (action in validActions) {
            shadowOf(context as Application).clearStartedServices()
            receiver.onReceive(context, Intent(action))
            val started = shadowOf(context as Application).nextStartedService
            assertNotNull("Expected service to start for $action", started)
            assertEquals(
                AssistantForegroundService::class.java.name,
                started.component?.className
            )
        }
    }

    @Test
    fun `BootReceiver ignores unrelated broadcast actions`() {
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_BATTERY_LOW))
        val started = shadowOf(context as Application).nextStartedService
        assertNull(started)
    }

    @Test
    fun `TccKeyReceiver starts service with ACTION_TCC_KEY when service instance is null`() {
        val receiver = TccKeyReceiver()
        val intent = Intent("action.tcc.button.key.event").apply {
            putExtra("key_action_down", true)
            putExtra("key_isLongPress", true)
        }

        receiver.onReceive(context, intent)
        val started = shadowOf(context as Application).nextStartedService
        assertNotNull(started)
        requireNotNull(started)
        assertEquals(AssistantForegroundService.ACTION_TCC_KEY, started.action)
        assertTrue(started.getBooleanExtra(AssistantForegroundService.EXTRA_KEY_ACTION_DOWN, false))
        assertTrue(started.getBooleanExtra(AssistantForegroundService.EXTRA_IS_LONG_PRESS, false))
    }

    @Test
    fun `TccKeyReceiver does not start duplicate service intent when AssistantForegroundService is already running`() {
        setServiceInstance(mockk(relaxed = true))
        val receiver = TccKeyReceiver()
        val intent = Intent("action.tcc.button.key.event").apply {
            putExtra("key_action_down", false)
        }

        receiver.onReceive(context, intent)
        val started = shadowOf(context as Application).nextStartedService
        assertNull(started)
    }

    @Test
    fun `TccKeyReceiver ignores unrelated intent actions`() {
        val receiver = TccKeyReceiver()
        receiver.onReceive(context, Intent("some.other.action"))
        assertNull(shadowOf(context as Application).nextStartedService)
    }
}
