package com.stormpanda.megingiard.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GamepadInputAndKeyHandlersTest {

    @Before
    fun setUp() {
        PrimaryOverlayInputBridge.resetJoystickState()
    }

    @After
    fun tearDown() {
        PrimaryOverlayInputBridge.resetJoystickState()
    }

    @Test
    fun `List cycle moves next and prev with wrap-around and handles empty list`() {
        val tabs = listOf("Brightness", "Performance", "Theme")
        assertEquals("Performance", tabs.cycle("Brightness", BumperDirection.NEXT))
        assertEquals("Theme", tabs.cycle("Performance", BumperDirection.NEXT))
        assertEquals("Brightness", tabs.cycle("Theme", BumperDirection.NEXT))

        assertEquals("Theme", tabs.cycle("Brightness", BumperDirection.PREV))
        assertEquals("Brightness", tabs.cycle("Performance", BumperDirection.PREV))
        assertEquals("Performance", tabs.cycle("Theme", BumperDirection.PREV))

        // Unknown item falls back to first()
        assertEquals("Brightness", tabs.cycle("NonExistent", BumperDirection.NEXT))

        // Empty list returns currentItem
        assertEquals("Any", emptyList<String>().cycle("Any", BumperDirection.NEXT))
    }

    @Test
    fun `PrimaryOverlayInputBridge sendBumper and sendFocusRecovery emit events`() = runTest {
        val unconfined = UnconfinedTestDispatcher(testScheduler)
        val deferredBumper = async(unconfined) { PrimaryOverlayInputBridge.bumperEvents.first() }
        PrimaryOverlayInputBridge.sendBumper(BumperDirection.NEXT)
        assertEquals(BumperDirection.NEXT, deferredBumper.await())

        val deferredFocus = async(unconfined) { PrimaryOverlayInputBridge.focusRecoveryEvents.first() }
        PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, deferredFocus.await())
    }

    @Test
    fun `PrimaryOverlayInputBridge ignores non-joystick motion events`() {
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_TOUCHSCREEN
        assertFalse(PrimaryOverlayInputBridge.processGenericMotionEvent(event) { _, _ -> })
    }

    @Test
    fun `PrimaryOverlayInputBridge translates L2 R2 triggers and analog stick to Dpad key events`() {
        val receivedEvents = mutableListOf<Pair<Int, Int>>()
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.source } returns InputDevice.SOURCE_JOYSTICK
        every { event.getAxisValue(any()) } returns 0f

        // 1. L2 trigger pressed (> 0.5f) -> KEYCODE_BUTTON_L2 ACTION_DOWN
        every { event.getAxisValue(MotionEvent.AXIS_LTRIGGER) } returns 0.8f
        assertTrue(PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, code ->
            receivedEvents.add(action to code)
        })
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_L2), receivedEvents)

        // 2. Release L2 and tilt stick left (AXIS_X = -0.9f) -> L2 ACTION_UP + DPAD_LEFT ACTION_DOWN
        receivedEvents.clear()
        every { event.getAxisValue(MotionEvent.AXIS_LTRIGGER) } returns 0f
        every { event.getAxisValue(MotionEvent.AXIS_X) } returns -0.9f
        assertTrue(PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, code ->
            receivedEvents.add(action to code)
        })
        assertEquals(
            listOf(
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DPAD_LEFT
            ),
            receivedEvents
        )

        // 3. Return stick to neutral -> DPAD_LEFT ACTION_UP
        receivedEvents.clear()
        every { event.getAxisValue(MotionEvent.AXIS_X) } returns 0f
        assertTrue(PrimaryOverlayInputBridge.processGenericMotionEvent(event) { action, code ->
            receivedEvents.add(action to code)
        })
        assertEquals(listOf(KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DPAD_LEFT), receivedEvents)
    }

    @Test
    fun `isBackKey recognizes KEYCODE_BACK KEYCODE_BUTTON_B and KEYCODE_ESCAPE`() {
        assertTrue(isBackKey(KeyEvent.KEYCODE_BACK))
        assertTrue(isBackKey(KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(isBackKey(KeyEvent.KEYCODE_ESCAPE))
        assertFalse(isBackKey(KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(isBackKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `launchDirectionalRepeat invokes action repeatedly after initial delay while active`() = runTest {
        var count = 0
        var active = true
        val job = launchDirectionalRepeat(
            keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
            isActiveCheck = { active },
            initialDelayMs = 250L,
            startDelayMs = 100L,
            minDelayMs = 50L,
            accelFactor = 1.0f
        ) {
            count++
        }

        advanceTimeBy(249L)
        assertEquals(0, count)

        advanceTimeBy(2L)
        assertEquals(1, count)

        advanceTimeBy(200L)
        assertEquals(3, count)

        active = false
        job.cancel()
    }
}
