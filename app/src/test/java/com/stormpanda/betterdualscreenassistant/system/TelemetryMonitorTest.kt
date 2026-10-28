package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain
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
class TelemetryMonitorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        setHelperBinder(null)
    }

    @After
    fun tearDown() {
        setHelperBinder(null)
    }

    private fun setHelperBinder(binder: IBinder?) {
        val field = PrivilegedBridge::class.java.getDeclaredField("helperBinder").apply {
            isAccessible = true
        }
        field.set(PrivilegedBridge, binder)
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

    @Test
    fun `isAccurateGameFpsEnabled defaults to true and respects preference toggle`() {
        val monitor = TelemetryMonitor(context)
        assertTrue(monitor.isAccurateGameFpsEnabled())

        val repo = SystemSettingsRepository(context)
        assertTrue(repo.accurateGameFps.value)

        repo.setAccurateGameFps(false)
        assertFalse(repo.accurateGameFps.value)
        assertFalse(monitor.isAccurateGameFpsEnabled())

        repo.setAccurateGameFps(true)
        assertTrue(repo.accurateGameFps.value)
        assertTrue(monitor.isAccurateGameFpsEnabled())
    }

    @Test
    fun `sampleAccurateGameFps uses helper per-task FPS and clamps to peak refresh rate`() {
        Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", 60f)
        var reportedFps = 47.6f
        val binder = mockk<IBinder>(relaxed = true)
        every { binder.isBinderAlive } returns true
        every { binder.transact(PrivilegedHelperMain.TRANSACTION_GET_GAME_FPS, any(), any(), any()) } answers {
            val reply = thirdArg<Parcel?>()
            reply?.writeNoException()
            reply?.writeFloat(reportedFps)
            reply?.setDataPosition(0)
            true
        }
        setHelperBinder(binder)

        val monitor = TelemetryMonitor(context)
        assertEquals(48, monitor.sampleAccurateGameFps())

        reportedFps = 119.8f
        assertEquals(60, monitor.sampleAccurateGameFps())

        Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", 120f)
        assertEquals(120, monitor.sampleAccurateGameFps())

        reportedFps = 0f
        assertEquals(0, monitor.sampleAccurateGameFps())
    }

    @Test
    fun `normalizeGameFpsForPanel compensates for top AMOLED 118_41Hz DDIC TE clock without inflating software caps`() {
        // 120Hz mode: full VSYNC lock (118.41f = 0 missed VSYNCs) reports 120, while missed VSYNCs step down cleanly
        assertEquals(120, TelemetryMonitor.normalizeGameFpsForPanel(118.41f, 120f))
        assertEquals(119, TelemetryMonitor.normalizeGameFpsForPanel(117.48f, 120f))
        assertEquals(118, TelemetryMonitor.normalizeGameFpsForPanel(116.55f, 120f))
        assertEquals(117, TelemetryMonitor.normalizeGameFpsForPanel(115.62f, 120f))

        // Mid-high software caps (e.g. 90 FPS) and wall-clock 60 FPS caps (e.g. Genshin Impact) are never inflated
        assertEquals(90, TelemetryMonitor.normalizeGameFpsForPanel(90.0f, 120f))
        assertEquals(60, TelemetryMonitor.normalizeGameFpsForPanel(60.0f, 120f))
        assertEquals(60, TelemetryMonitor.normalizeGameFpsForPanel(60.2f, 120f))

        // Half-rate VSYNC (118.412 / 2 = 59.21f) in 120Hz mode and full VSYNC in 60Hz mode both report 60
        assertEquals(60, TelemetryMonitor.normalizeGameFpsForPanel(59.21f, 120f))
        assertEquals(60, TelemetryMonitor.normalizeGameFpsForPanel(59.21f, 60f))
        assertEquals(59, TelemetryMonitor.normalizeGameFpsForPanel(58.27f, 60f))
        assertEquals(30, TelemetryMonitor.normalizeGameFpsForPanel(29.60f, 60f))
    }
}
