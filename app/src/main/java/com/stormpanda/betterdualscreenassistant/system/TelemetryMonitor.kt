package com.stormpanda.betterdualscreenassistant.system

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.SystemClock
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
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

data class HardwareTelemetryState(
    val currentFps: Int = 60,
    val cpuTempCelsius: Float = 56f,
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
        internal const val ACCURATE_FPS_POLL_INTERVAL_MS = 500L
        internal const val LEGACY_FPS_POLL_INTERVAL_MS = 1500L

        /**
         * The AYN Thor top AMOLED panel (`1080x1920`, MIPI-DSI Command Mode, `port=131`) generates
         * hardware VSYNC from its on-panel DDIC TE oscillator at ~8.4451 ms (118.412 Hz) in 120 Hz
         * mode and ~16.890 ms (59.206 Hz) in 60 Hz mode (~1.323% slower than the nominal 120.00 Hz
         * / 60.00 Hz DisplayMode). Because SurfaceFlinger's `FrameTimeline::computeFps` divides
         * presented frame intervals by wall-clock nanoseconds, 100% VSYNC lock (0 missed VSYNCs)
         * produces ~118.41f in 120 Hz mode and ~59.21f at 60 Hz / half-rate VSYNC.
         */
        internal const val TOP_PANEL_TE_CLOCK_RATIO = 118.412f / 120f
        private const val VSYNC_CEILING_RAMP_FRACTION = 110f / 120f

        internal fun normalizeGameFpsForPanel(rawFps: Float, nominalPeakRate: Float): Int {
            if (!rawFps.isFinite() || rawFps <= 0f) return 0
            val peakInt = nominalPeakRate.roundToInt().coerceAtLeast(1)
            val nominalPeak = peakInt.toFloat()

            // Top VSYNC-ceiling ramp ([110..118.412] -> [110..120] in 120Hz mode, or
            // [55..59.206] -> [55..60] in 60Hz mode) so 0 missed VSYNCs reports full peak FPS
            // while 1, 2, 3... missed VSYNCs step down smoothly without dead numbers.
            val physicalPeak = nominalPeak * TOP_PANEL_TE_CLOCK_RATIO
            val ceilingRampStart = nominalPeak * VSYNC_CEILING_RAMP_FRACTION
            if (rawFps > ceilingRampStart && physicalPeak > ceilingRampStart) {
                val progress = ((rawFps - ceilingRampStart) / (physicalPeak - ceilingRampStart))
                    .coerceIn(0f, 1f)
                val calibrated = ceilingRampStart + progress * (nominalPeak - ceilingRampStart)
                return calibrated.roundToInt().coerceIn(0, peakInt)
            }

            // In 120Hz+ mode, a 60 FPS game paced via every-2nd-VSYNC (Choreographer / Swappy
            // swapInterval=2) hits 118.412 / 2 = 59.206f at 0 missed frames, whereas a wall-clock
            // 60.00 Hz CPU limiter (e.g. Genshin Impact) reports ~60.0f. Smoothly ramp
            // [55..59.206] -> [55..60] and clamp [59.206..60.0] to 60 so both report 60 without
            // pushing a 60.0f wall-clock game up to 61.
            if (nominalPeak >= 115f && rawFps > 55f && rawFps <= 60f) {
                val halfPhysical = 60f * TOP_PANEL_TE_CLOCK_RATIO
                val progress = ((rawFps - 55f) / (halfPhysical - 55f)).coerceIn(0f, 1f)
                val calibrated = 55f + progress * 5f
                return calibrated.roundToInt().coerceIn(0, peakInt)
            }

            return rawFps.roundToInt().coerceIn(0, peakInt)
        }

        private fun roundDiv(numerator: Long, denominator: Long, scale: Int = 2): Float {
            if (denominator == 0L) return 0f
            return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP)
                .toFloat()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val prefs = context.getSharedPreferences(
        SystemSettingsRepository.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _state = MutableStateFlow(HardwareTelemetryState())
    val state: StateFlow<HardwareTelemetryState> = _state.asStateFlow()

    private var lastFlips: Int = -1
    private var lastFlipTimestampMs: Long = 0L
    private var lastAccurateFallbackFlips: Int = -1
    private var lastAccurateFallbackElapsedMs: Long = 0L

    fun start() {
        if (pollingJob?.isActive == true) return
        lastFlips = -1
        lastFlipTimestampMs = 0L
        lastAccurateFallbackFlips = -1
        lastAccurateFallbackElapsedMs = 0L
        // Reset ring gauges to 0f on attach, matching app:hirv_progress="0" in stock layout
        _state.value = _state.value.copy(
            cpuCurFreqGhz = 0f,
            gpuCurFreqMhz = 0f,
            powerWatts = 0f,
            ramUsedGb = 0f
        )
        pollingJob = scope.launch {
            launch {
                if (!isAccurateGameFpsEnabled()) {
                    delay(500L)
                }
                while (isActive) {
                    val accurateMode = isAccurateGameFpsEnabled()
                    val fps = if (accurateMode) {
                        sampleAccurateGameFps()
                    } else {
                        sampleFps()
                    }
                    _state.value = _state.value.copy(currentFps = fps)
                    delay(
                        if (accurateMode) {
                            ACCURATE_FPS_POLL_INTERVAL_MS
                        } else {
                            LEGACY_FPS_POLL_INTERVAL_MS
                        }
                    )
                }
            }

            val maxCpuRaw = PrivilegedBridge.readSysfsLong(CPU_MAX_FREQ_PATH)
            val (cpuMaxVal, cpuUnitStr) = if (maxCpuRaw >= 1_000_000L) {
                roundDiv(maxCpuRaw, 1_000_000L) to "GHz"
            } else if (maxCpuRaw >= 1_000L) {
                roundDiv(maxCpuRaw, 1_000L) to "MHz"
            } else {
                3.2f to "GHz"
            }

            val maxGpuRaw = PrivilegedBridge.readSysfsLong(GPU_MAX_FREQ_PATH)
            val (gpuMaxVal, gpuUnitStr) = if (maxGpuRaw >= 1_000_000_000L) {
                roundDiv(maxGpuRaw, 1_000_000_000L) to "GHz"
            } else if (maxGpuRaw >= 1_000_000L) {
                roundDiv(maxGpuRaw, 1_000_000L) to "MHz"
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

            _state.value = _state.value.copy(
                cpuMaxFreqGhz = cpuMaxVal,
                cpuUnit = cpuUnitStr,
                gpuMaxFreqMhz = gpuMaxVal,
                gpuUnit = gpuUnitStr,
                ramTotalGb = totalRamGb
            )

            // Replicate stock CpuRingView / GpuRingView / PwrRingView / RamRingView onAttachedToWindow
            // postDelayed(..., 500L) before starting the first animated progress update
            delay(500L)

            while (isActive) {
                val tempRaw = PrivilegedBridge.readSysfsLong(THERMAL_CPU_PATH)
                val cpuTemp = if (tempRaw > 1000L) tempRaw / 1000f else tempRaw.toFloat()

                val fanPercent = (FanControlClient.getSpeedPercentage() * 100f).toInt().coerceIn(0, 100)

                val curCpuRaw = PrivilegedBridge.readSysfsLong(CPU_CUR_FREQ_PATH)
                val cpuCurVal = if (cpuUnitStr == "GHz") {
                    roundDiv(curCpuRaw, 1_000_000L)
                } else {
                    roundDiv(curCpuRaw, 1_000L)
                }

                val curGpuRaw = PrivilegedBridge.readSysfsLong(GPU_CUR_FREQ_PATH)
                val gpuCurVal = if (gpuUnitStr == "GHz") {
                    roundDiv(curGpuRaw, 1_000_000_000L)
                } else {
                    roundDiv(curGpuRaw, 1_000_000L)
                }

                val currentNowUa = PrivilegedBridge.readSysfsLong(BATTERY_CURRENT_NOW_PATH)
                val voltageNowUv = PrivilegedBridge.readSysfsLong(BATTERY_VOLTAGE_NOW_PATH)
                // Replicate S0.n power calculation: (current_now / 1e6f) * (voltage_now / 1e6f) -> Watts
                val powerWatts = (currentNowUa / 1_000_000f) * (voltageNowUv / 1_000_000f)

                am?.getMemoryInfo(memInfo)
                val usedRamGb = ((memInfo.totalMem - memInfo.availMem).coerceAtLeast(0L)) / 1_000_000_000f

                _state.value = _state.value.copy(
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
        _state.value = _state.value.copy(
            cpuCurFreqGhz = 0f,
            gpuCurFreqMhz = 0f,
            powerWatts = 0f,
            ramUsedGb = 0f
        )
    }

    internal fun isAccurateGameFpsEnabled(): Boolean {
        return prefs.getBoolean(SystemSettingsRepository.PREF_ACCURATE_GAME_FPS, true)
    }

    private fun readPeakRefreshRate(): Float {
        val dm = context.getSystemService(DisplayManager::class.java)
        val displayRate = dm?.getDisplay(0)?.refreshRate ?: 60f
        return try {
            Settings.System.getFloat(context.contentResolver, "peak_refresh_rate", displayRate)
        } catch (_: Throwable) {
            displayRate
        }
    }

    internal fun sampleAccurateGameFps(): Int {
        val peakRate = readPeakRefreshRate()
        val helperFps = PrivilegedBridge.getGameFps()
        if (helperFps.isFinite() && helperFps >= 0f) {
            return normalizeGameFpsForPanel(helperFps, peakRate)
        }
        val flips = PrivilegedBridge.getSurfaceFlingerFlips()
        val now = SystemClock.elapsedRealtime()
        if (flips < 0) {
            return 0
        }
        val prevFlips = lastAccurateFallbackFlips
        val prevTime = lastAccurateFallbackElapsedMs
        lastAccurateFallbackFlips = flips
        lastAccurateFallbackElapsedMs = now
        if (prevFlips < 0 || now <= prevTime || flips < prevFlips) {
            return 0
        }
        val rawFps = ((flips - prevFlips) * 1000f) / (now - prevTime).toFloat()
        return normalizeGameFpsForPanel(rawFps, peakRate)
    }

    internal fun sampleFps(): Int {
        val flips = PrivilegedBridge.getSurfaceFlingerFlips()
        val now = System.currentTimeMillis()
        val peakRate = readPeakRefreshRate()
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
