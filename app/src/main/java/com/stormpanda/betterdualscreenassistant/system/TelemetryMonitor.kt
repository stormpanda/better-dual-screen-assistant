package com.stormpanda.betterdualscreenassistant.system

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

data class HardwareTelemetryState(
    val currentFps: Int = 60,
    val cpuTempCelsius: Float = 40f,
    val fanSpeedPercent: Int = 0,
    val cpuCurFreqGhz: Float = 0f,
    val cpuMaxFreqGhz: Float = 3.2f,
    val cpuUnit: String = "GHz",
    val gpuCurFreqMhz: Float = 0f,
    val gpuMaxFreqMhz: Float = 680f,
    val gpuUnit: String = "MHz",
    val powerWatts: Float = 0f,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 12f
)

/**
 * Polls live FPS, CPU/GPU frequencies, thermal_zone48 temperature, fan percentage,
 * battery power (W), and RAM usage when the Settings tab is visible.
 */
class TelemetryMonitor(private val context: Context) {

    companion object {
        private const val CPU_CUR_FREQ_PATH = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq"
        private const val CPU_MAX_FREQ_PATH = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq"
        private const val GPU_CUR_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
        private const val GPU_MAX_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
        private const val THERMAL_CPU_PATH = "/sys/devices/virtual/thermal/thermal_zone48/temp"
        private const val BATTERY_CURRENT_NOW_PATH = "/sys/class/power_supply/battery/current_now"
        private const val BATTERY_VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _state = MutableStateFlow(HardwareTelemetryState())
    val state: StateFlow<HardwareTelemetryState> = _state.asStateFlow()

    private var lastFlips: Int = -1
    private var lastFlipTimestampMs: Long = 0L

    fun start() {
        if (pollingJob?.isActive == true) return
        lastFlips = -1
        lastFlipTimestampMs = 0L
        pollingJob = scope.launch {
            val maxCpuRaw = PrivilegedBridge.readSysfsLong(CPU_MAX_FREQ_PATH)
            val (cpuMaxVal, cpuUnitStr) = if (maxCpuRaw >= 1_000_000L) {
                (maxCpuRaw / 1_000_000f) to "GHz"
            } else if (maxCpuRaw >= 1_000L) {
                (maxCpuRaw / 1_000f) to "MHz"
            } else {
                3.2f to "GHz"
            }

            val maxGpuRaw = PrivilegedBridge.readSysfsLong(GPU_MAX_FREQ_PATH)
            val (gpuMaxVal, gpuUnitStr) = if (maxGpuRaw >= 1_000_000_000L) {
                (maxGpuRaw / 1_000_000_000f) to "GHz"
            } else if (maxGpuRaw >= 1_000_000L) {
                (maxGpuRaw / 1_000_000f) to "MHz"
            } else {
                680f to "MHz"
            }

            val am = context.getSystemService(ActivityManager::class.java)
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val totalRamGb = if (memInfo.totalMem > 0) {
                memInfo.totalMem / 1_000_000_000f
            } else {
                12f
            }

            while (isActive) {
                val fps = sampleFps()
                val tempRaw = PrivilegedBridge.readSysfsLong(THERMAL_CPU_PATH)
                val cpuTemp = if (tempRaw > 1000L) tempRaw / 1000f else tempRaw.toFloat()

                val fanPercent = (FanControlClient.getSpeedPercentage() * 100f).toInt().coerceIn(0, 100)

                val curCpuRaw = PrivilegedBridge.readSysfsLong(CPU_CUR_FREQ_PATH)
                val cpuCurVal = if (cpuUnitStr == "GHz") {
                    curCpuRaw / 1_000_000f
                } else {
                    curCpuRaw / 1_000f
                }

                val curGpuRaw = PrivilegedBridge.readSysfsLong(GPU_CUR_FREQ_PATH)
                val gpuCurVal = if (gpuUnitStr == "GHz") {
                    curGpuRaw / 1_000_000_000f
                } else {
                    curGpuRaw / 1_000_000f
                }

                val currentNowUa = PrivilegedBridge.readSysfsLong(BATTERY_CURRENT_NOW_PATH)
                val voltageNowUv = PrivilegedBridge.readSysfsLong(BATTERY_VOLTAGE_NOW_PATH)
                // Replicate S0.n power calculation: -(current_mA * voltage_mV) / 1e6 -> Watts
                val currentMa = currentNowUa / 1000f
                val voltageMv = voltageNowUv / 1000f
                val powerWatts = -((currentMa * voltageMv) / 1_000_000f)

                am?.getMemoryInfo(memInfo)
                val usedRamGb = ((memInfo.totalMem - memInfo.availMem).coerceAtLeast(0L)) / 1_000_000_000f

                _state.value = HardwareTelemetryState(
                    currentFps = fps,
                    cpuTempCelsius = cpuTemp,
                    fanSpeedPercent = fanPercent,
                    cpuCurFreqGhz = cpuCurVal,
                    cpuMaxFreqGhz = cpuMaxVal,
                    cpuUnit = cpuUnitStr,
                    gpuCurFreqMhz = gpuCurVal,
                    gpuMaxFreqMhz = gpuMaxVal,
                    gpuUnit = gpuUnitStr,
                    powerWatts = powerWatts.coerceIn(-20f, 25f),
                    ramUsedGb = usedRamGb,
                    ramTotalGb = totalRamGb
                )

                delay(1500L)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun sampleFps(): Int {
        val flips = PrivilegedBridge.getSurfaceFlingerFlips()
        val now = System.currentTimeMillis()
        val dm = context.getSystemService(DisplayManager::class.java)
        val displayRate = dm?.getDisplay(0)?.refreshRate ?: 60f
        val peakRate = try {
            Settings.System.getFloat(context.contentResolver, "peak_refresh_rate", displayRate)
        } catch (_: Throwable) {
            displayRate
        }
        if (flips < 0) {
            return peakRate.roundToInt()
        }
        val prevFlips = lastFlips
        val prevTime = lastFlipTimestampMs
        lastFlips = flips
        lastFlipTimestampMs = now
        if (prevFlips < 0 || now <= prevTime) {
            return peakRate.roundToInt()
        }
        val rawFps = ((flips - prevFlips) * 1000f) / (now - prevTime).toFloat()
        return min(ceil(rawFps), peakRate).toInt().coerceAtLeast(0)
    }
}
