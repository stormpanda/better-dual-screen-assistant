package com.stormpanda.betterdualscreenassistant.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.core.os.HandlerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.coroutines.ContinuationInterceptor
import com.stormpanda.betterdualscreenassistant.MainActivity
import com.stormpanda.betterdualscreenassistant.overlay.ScreenOverlayManager
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.MappingServiceClient
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.megingiard.update.UpdateManager
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-xhdpi")
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

        val assistantAlias = ComponentName(
            context.packageName,
            SystemSettingsRepository.LAUNCHER_ALIAS_ASSISTANT
        )
        val managerAlias = ComponentName(
            context.packageName,
            SystemSettingsRepository.LAUNCHER_ALIAS_MANAGER
        )
        val assistantAliasInfo = pm.getActivityInfo(assistantAlias, 0)
        assertTrue(assistantAliasInfo.enabled)
        assertEquals("Better Dual Screen Assistant", assistantAliasInfo.loadLabel(pm).toString())

        val managerAliasInfo = pm.getActivityInfo(
            managerAlias,
            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
        )
        assertFalse(managerAliasInfo.enabled)
        assertEquals("Better Dual Screen Manager", managerAliasInfo.loadLabel(pm).toString())

        val serviceInfo = pm.getServiceInfo(ComponentName(context, AssistantForegroundService::class.java), 0)
        assertEquals(0, serviceInfo.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
    }

    @Test
    fun `createForegroundNotification reflects Assistant vs promoted Manager title and channel name`() {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)

        val defaultNotification = service.createForegroundNotification(promoted = false)
        assertEquals(
            "Better Dual Screen Assistant",
            defaultNotification.extras.getString(android.app.Notification.EXTRA_TITLE)
        )
        assertEquals(
            "Dual Screen Assistant Service",
            nm.getNotificationChannel("better_dsa_service_channel")?.name?.toString()
        )

        val promotedNotification = service.createForegroundNotification(promoted = true)
        assertEquals(
            "Better Dual Screen Manager",
            promotedNotification.extras.getString(android.app.Notification.EXTRA_TITLE)
        )
        assertEquals(
            "Dual Screen Manager Service",
            nm.getNotificationChannel("better_dsa_service_channel")?.name?.toString()
        )
    }

    @Test
    fun `onTaskRemoved keeps service alive without throwing`() {
        service.onTaskRemoved(Intent(context, MainActivity::class.java))
        assertFalse(getBooleanField("longPressConsumed"))
    }

    @OptIn(
        androidx.compose.ui.InternalComposeUiApi::class,
        androidx.compose.ui.ExperimentalComposeUiApi::class
    )
    @Test
    fun `MainActivity finishes itself when stopped while interactive or on root back press`() {
        mockkObject(PServerClient)
        mockkObject(PrivilegedBridge)
        mockkObject(UpdateManager)
        every { PServerClient.isAvailable() } returns true
        every { PServerClient.isStockAssistantEnabled(any()) } returns false
        every { PServerClient.ensurePermissionsGranted(any()) } just runs
        every { PrivilegedBridge.ensureInitialized(any()) } just runs
        every { UpdateManager.init(any()) } just runs
        every { UpdateManager.checkForUpdates(any(), any()) } just runs

        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        resetComposeUiDispatcher(installFactory = true)
        val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<MainActivity>>()
        try {
            val pm = context.getSystemService(PowerManager::class.java)
            shadowOf(pm).setIsInteractive(true)

            // 1. Navigating away (onStop while interactive) finishes and removes task
            val controller1 = Robolectric.buildActivity(MainActivity::class.java).setup().also { controllers.add(it) }
            assertFalse(controller1.get().isFinishing)
            controller1.pause().stop()
            assertTrue(controller1.get().isFinishing)
            controller1.destroy()
            controllers.remove(controller1)

            // 2. Screen sleep (onStop while non-interactive) keeps Settings open until user returns
            val controller2 = Robolectric.buildActivity(MainActivity::class.java).setup().also { controllers.add(it) }
            shadowOf(pm).setIsInteractive(false)
            controller2.pause().stop()
            assertFalse(controller2.get().isFinishing)
            controller2.destroy()
            controllers.remove(controller2)

            // 3. Root back press finishes and removes task
            shadowOf(pm).setIsInteractive(true)
            val controller3 = Robolectric.buildActivity(MainActivity::class.java).setup().also { controllers.add(it) }
            controller3.get().onBackPressedDispatcher.onBackPressed()
            assertTrue(controller3.get().isFinishing)
            controller3.pause().stop().destroy()
            controllers.remove(controller3)
        } finally {
            controllers.asReversed().forEach { c ->
                try {
                    c.pause().stop().destroy()
                } catch (_: Throwable) {
                }
            }
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            unmockkAll()
            resetComposeUiDispatcher(installFactory = false)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @OptIn(
        androidx.compose.ui.InternalComposeUiApi::class,
        androidx.compose.ui.ExperimentalComposeUiApi::class
    )
    private fun resetComposeUiDispatcher(installFactory: Boolean) {
        try {
            val clazz = Class.forName("androidx.compose.ui.platform.AndroidUiDispatcher")
            val currentThreadField = clazz.getDeclaredField("currentThread").apply { isAccessible = true }
            (currentThreadField.get(null) as? ThreadLocal<*>)?.remove()

            val mainDispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor]
            if (mainDispatcher != null && clazz.isInstance(mainDispatcher)) {
                val newChoreographer = Choreographer.getInstance()
                val newHandler = HandlerCompat.createAsync(Looper.getMainLooper()) as Handler
                clazz.getDeclaredField("choreographer").apply {
                    isAccessible = true
                    set(mainDispatcher, newChoreographer)
                }
                val frameClock = AndroidUiDispatcher.Main[MonotonicFrameClock]
                if (frameClock != null) {
                    frameClock.javaClass.getDeclaredField("choreographer").apply {
                        isAccessible = true
                        set(frameClock, newChoreographer)
                    }
                }
                clazz.getDeclaredField("handler").apply {
                    isAccessible = true
                    set(mainDispatcher, newHandler)
                }
                clazz.getDeclaredField("toRunOnFrame").apply {
                    isAccessible = true
                    (get(mainDispatcher) as? MutableList<*>)?.clear()
                }
                clazz.getDeclaredField("scheduledFrameDispatch").apply {
                    isAccessible = true
                    setBoolean(mainDispatcher, false)
                }
                val scheduledTrampolineField = clazz.getDeclaredField("scheduledTrampolineDispatch").apply {
                    isAccessible = true
                }
                val dispatchCallback = clazz.getDeclaredField("dispatchCallback").apply {
                    isAccessible = true
                }.get(mainDispatcher) as Runnable
                scheduledTrampolineField.setBoolean(mainDispatcher, true)
                newHandler.post(dispatchCallback)
                shadowOf(Looper.getMainLooper()).idle()
            }

            if (installFactory) {
                WindowRecomposerPolicy.setFactory { rootView ->
                    rootView.createLifecycleAwareWindowRecomposer(AndroidUiDispatcher.CurrentThread)
                }
            } else {
                WindowRecomposerPolicy.setFactory(WindowRecomposerFactory.LifecycleAware)
            }
        } catch (_: Throwable) {
        }
    }
}
