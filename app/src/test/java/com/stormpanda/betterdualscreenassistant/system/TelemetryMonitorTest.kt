package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetryMonitorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `HardwareTelemetryState defaults match stock assistant initial values`() {
        val defaultState = HardwareTelemetryState()
        assertEquals(60, defaultState.currentFps)
        assertEquals(56f, defaultState.cpuTempCelsius, 0.001f)
        assertEquals(0, defaultState.fanSpeedPercent)
        assertEquals(0f, defaultState.cpuCurFreqGhz, 0.001f)
        assertEquals(3.2f, defaultState.cpuMaxFreqGhz, 0.001f)
        assertEquals("GHz", defaultState.cpuUnit)
        assertEquals(0f, defaultState.gpuCurFreqMhz, 0.001f)
        assertEquals(680f, defaultState.gpuMaxFreqMhz, 0.001f)
        assertEquals("MHz", defaultState.gpuUnit)
        assertEquals(0f, defaultState.powerWatts, 0.001f)
        assertEquals(0f, defaultState.ramUsedGb, 0.001f)
        assertEquals(12f, defaultState.ramTotalGb, 0.001f)
    }

    @Test
    fun `roundDiv rounds using HALF_UP and handles zero denominator`() {
        val companionClass = TelemetryMonitor.Companion::class.java
        val roundDivMethod = companionClass.getDeclaredMethod(
            "roundDiv",
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        // 45600 / 1000 at scale 1 -> 45.6f
        val tempRounded = roundDivMethod.invoke(TelemetryMonitor.Companion, 45600L, 1000L, 1) as Float
        assertEquals(45.6f, tempRounded, 0.001f)

        // 3_187_200 / 1_000_000 at scale 2 -> 3.19f
        val ghzRounded = roundDivMethod.invoke(TelemetryMonitor.Companion, 3_187_200L, 1_000_000L, 2) as Float
        assertEquals(3.19f, ghzRounded, 0.001f)

        // Denominator == 0 returns 0f
        val zeroDiv = roundDivMethod.invoke(TelemetryMonitor.Companion, 100L, 0L, 2) as Float
        assertEquals(0f, zeroDiv, 0.001f)
    }

    @Test
    fun `start and stop manage polling job cleanly`() {
        val monitor = TelemetryMonitor(context)
        monitor.start()
        assertEquals(0f, monitor.state.value.cpuCurFreqGhz, 0.001f)
        monitor.stop()
    }
}
