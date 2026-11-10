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
import kotlin.math.abs
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
    val ramTotalGb: Float = 12f,
    // Expanded telemetry for Monitor Tab (Afterburner / RivaTuner style)
    val cpuTotalUsagePercent: Int = 0,
    val cpuSilverUsagePercent: Int = 0,
    val cpuSilverCurFreqGhz: Float = 0f,
    val cpuGoldUsagePercent: Int = 0,
    val cpuGoldCurFreqGhz: Float = 0f,
    val cpuPrimeUsagePercent: Int = 0,
    val cpuPrimeCurFreqGhz: Float = 0f,
    val gpuBusyPercent: Int = 0,
    val ramPercent: Int = 0,
    val swapUsedGb: Float = 0f,
    val swapTotalGb: Float = 0f,
    val swapPercent: Int = 0,
    val batteryVoltageMv: Float = 0f,
    val batteryCurrentMa: Int = 0
)

private data class CpuTime(val work: Long, val total: Long)

private data class CpuUsageResult(
    val total: Int,
    val silver: Int,
    val gold: Int,
    val prime: Int
)

private class CpuUsageCalculator {
    private var prevTotal: CpuTime? = null
    private val prevCores = mutableMapOf<Int, CpuTime>()

    fun update(statContent: String): CpuUsageResult {
        var totalUsage = 0
        val coreUsages = mutableMapOf<Int, Int>()

        val lines = statContent.lines()
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.isEmpty()) continue
            val label = parts[0]
            if (label == "cpu" && parts.size >= 8) {
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val sys = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = parts[5].toLongOrNull() ?: 0L
                val irq = parts[6].toLongOrNull() ?: 0L
                val sirq = parts[7].toLongOrNull() ?: 0L
                val steal = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L

                val work = user + nice + sys + irq + sirq + steal
                val total = work + idle + iowait
                val prev = prevTotal
                prevTotal = CpuTime(work, total)

                if (prev != null) {
                    val deltaWork = (work - prev.work).coerceAtLeast(0L)
                    val deltaTotal = (total - prev.total).coerceAtLeast(0L)
                    if (deltaTotal > 0L) {
                        totalUsage = ((deltaWork * 100L) / deltaTotal).toInt().coerceIn(0, 100)
                    }
                }
            } else if (label.startsWith("cpu") && label.length > 3 && parts.size >= 8) {
                val coreIdx = label.substring(3).toIntOrNull() ?: continue
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val sys = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = parts[5].toLongOrNull() ?: 0L
                val irq = parts[6].toLongOrNull() ?: 0L
                val sirq = parts[7].toLongOrNull() ?: 0L
                val steal = if (parts.size > 8) parts[8].toLongOrNull() ?: 0L else 0L

                val work = user + nice + sys + irq + sirq + steal
                val total = work + idle + iowait
                val prev = prevCores[coreIdx]
                prevCores[coreIdx] = CpuTime(work, total)

                if (prev != null) {
                    val deltaWork = (work - prev.work).coerceAtLeast(0L)
                    val deltaTotal = (total - prev.total).coerceAtLeast(0L)
                    if (deltaTotal > 0L) {
                        coreUsages[coreIdx] = ((deltaWork * 100L) / deltaTotal).toInt().coerceIn(0, 100)
                    }
                }
            }
        }

        val silverUsage = listOf(0, 1, 2).mapNotNull { coreUsages[it] }.let {
            if (it.isNotEmpty()) it.average().roundToInt().coerceIn(0, 100) else totalUsage
        }
        val goldUsage = listOf(3, 4, 5, 6).mapNotNull { coreUsages[it] }.let {
            if (it.isNotEmpty()) it.average().roundToInt().coerceIn(0, 100) else totalUsage
        }
        val primeUsage = coreUsages[7] ?: totalUsage

        return CpuUsageResult(totalUsage, silverUsage, goldUsage, primeUsage)
    }
}

/**
 * Polls live FPS, CPU/GPU frequencies, thermal_zone48 temperature, fan percentage,
 * battery power (W), RAM and per-cluster CPU/GPU usage when the Settings or Monitor tab is visible.
 */
class TelemetryMonitor(private val context: Context) {

    companion object {
        private const val CPU_CUR_FREQ_PATH = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq"
        private const val CPU_MAX_FREQ_PATH = "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq"
        private const val CPU_SILVER_FREQ_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq"
        private const val CPU_GOLD_FREQ_PATH = "/sys/devices/system/cpu/cpufreq/policy3/scaling_cur_freq"
        private const val CPU_PRIME_FREQ_PATH = "/sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq"
        private const val GPU_CUR_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
        private const val GPU_MAX_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
        private const val GPU_BUSY_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
        private const val THERMAL_CPU_PATH = "/sys/devices/virtual/thermal/thermal_zone48/temp"
        private const val BATTERY_CURRENT_NOW_PATH = "/sys/class/power_supply/battery/current_now"
        private const val BATTERY_VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now"
        internal const val ACCURATE_FPS_POLL_INTERVAL_MS = 500L
        internal const val LEGACY_FPS_POLL_INTERVAL_MS = 1500L

        /**
         * The AYN Thor top AMOLED panel (`1080x1920`, MIPI-DSI Command Mode, `port=131`) generates
         * hardware VSYNC from its on-panel DDIC TE oscillator at ~8.4451 ms (118.412 Hz) in 120 Hz
         * mode and ~16.890 ms (59.206 Hz) in 60 Hz mode (~1.323% slower than the nominal 120.00 Hz
         * / 60.00 Hz DisplayMode).
         */
        internal const val TOP_PANEL_TE_CLOCK_RATIO = 118.412f / 120f
        private const val VSYNC_CEILING_RAMP_FRACTION = 110f / 120f

        internal fun normalizeGameFpsForPanel(rawFps: Float, nominalPeakRate: Float): Int {
            if (!rawFps.isFinite() || rawFps <= 0f) return 0
            val peakInt = nominalPeakRate.roundToInt().coerceAtLeast(1)
            val nominalPeak = peakInt.toFloat()

            val physicalPeak = nominalPeak * TOP_PANEL_TE_CLOCK_RATIO
            val ceilingRampStart = nominalPeak * VSYNC_CEILING_RAMP_FRACTION
            if (rawFps > ceilingRampStart && physicalPeak > ceilingRampStart) {
                val progress = ((rawFps - ceilingRampStart) / (physicalPeak - ceilingRampStart))
                    .coerceIn(0f, 1f)
                val calibrated = ceilingRampStart + progress * (nominalPeak - ceilingRampStart)
                return calibrated.roundToInt().coerceIn(0, peakInt)
            }

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

    private val cpuCalculator = CpuUsageCalculator()

    fun start() {
        if (pollingJob?.isActive == true) return
        lastFlips = -1
        lastFlipTimestampMs = 0L
        lastAccurateFallbackFlips = -1
        lastAccurateFallbackElapsedMs = 0L
        _state.value = _state.value.copy(
            cpuCurFreqGhz = 0f,
            gpuCurFreqMhz = 0f,
            powerWatts = 0f,
            ramUsedGb = 0f,
            cpuTotalUsagePercent = 0,
            gpuBusyPercent = 0
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
                692f to "MHz"
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

                // Sample CPU usage per cluster via /proc/stat
                val procStat = PrivilegedBridge.readSysfsFile("/proc/stat")
                val cpuUsage = cpuCalculator.update(procStat)

                // Read per-cluster frequencies in GHz
                val silverKHz = PrivilegedBridge.readSysfsLong(CPU_SILVER_FREQ_PATH)
                val goldKHz = PrivilegedBridge.readSysfsLong(CPU_GOLD_FREQ_PATH)
                val primeKHz = PrivilegedBridge.readSysfsLong(CPU_PRIME_FREQ_PATH)
                val silverGhz = if (silverKHz > 0) silverKHz / 1_000_000f else 0f
                val goldGhz = if (goldKHz > 0) goldKHz / 1_000_000f else 0f
                val primeGhz = if (primeKHz > 0) primeKHz / 1_000_000f else cpuCurVal

                // Read GPU current frequency
                val curGpuRaw = PrivilegedBridge.readSysfsLong(GPU_CUR_FREQ_PATH)
                val gpuCurVal = if (gpuUnitStr == "GHz") {
                    roundDiv(curGpuRaw, 1_000_000_000L)
                } else {
                    roundDiv(curGpuRaw, 1_000_000L)
                }

                // Read GPU busy percentage
                val gpuBusyRaw = PrivilegedBridge.readSysfsFile(GPU_BUSY_PATH)
                val gpuBusyPct = gpuBusyRaw.replace("%", "").trim().toIntOrNull()?.coerceIn(0, 100) ?: 0

                // Read Battery stats
                val currentNowUa = PrivilegedBridge.readSysfsLong(BATTERY_CURRENT_NOW_PATH)
                val voltageNowUv = PrivilegedBridge.readSysfsLong(BATTERY_VOLTAGE_NOW_PATH)
                val powerWatts = abs((currentNowUa / 1_000_000f) * (voltageNowUv / 1_000_000f))
                val batteryVoltageMv = if (voltageNowUv > 0) voltageNowUv / 1000f else 0f
                val batteryCurrentMa = (abs(currentNowUa) / 1000L).toInt()

                // Read Memory & Swap (/proc/meminfo or ActivityManager fallback)
                var usedRamGb = 0f
                var ramPct = 0
                var swapUsedGb = 0f
                var swapTotalGb = 0f
                var swapPct = 0

                val memInfoStr = PrivilegedBridge.readSysfsFile("/proc/meminfo")
                if (memInfoStr.isNotEmpty()) {
                    var mTotalKb = 0L
                    var mAvailKb = 0L
                    var sTotalKb = 0L
                    var sFreeKb = 0L

                    for (mLine in memInfoStr.lines()) {
                        val mParts = mLine.split(Regex(":\\s+"))
                        if (mParts.size >= 2) {
                            val k = mParts[0].trim()
                            val v = mParts[1].replace("kB", "").trim().toLongOrNull() ?: 0L
                            when (k) {
                                "MemTotal" -> mTotalKb = v
                                "MemAvailable" -> mAvailKb = v
                                "SwapTotal" -> sTotalKb = v
                                "SwapFree" -> sFreeKb = v
                            }
                        }
                    }

                    if (mTotalKb > 0L) {
                        val totalG = mTotalKb / (1024f * 1024f)
                        val usedG = (mTotalKb - mAvailKb).coerceAtLeast(0L) / (1024f * 1024f)
                        usedRamGb = roundDiv((usedG * 100).toLong(), 100L)
                        ramPct = ((usedG / totalG) * 100f).roundToInt().coerceIn(0, 100)
                    }
                    if (sTotalKb > 0L) {
                        swapTotalGb = roundDiv(((sTotalKb / (1024f * 1024f)) * 100).toLong(), 100L)
                        val sUsedG = (sTotalKb - sFreeKb).coerceAtLeast(0L) / (1024f * 1024f)
                        swapUsedGb = roundDiv((sUsedG * 100).toLong(), 100L)
                        swapPct = ((sUsedG / (sTotalKb / (1024f * 1024f))) * 100f).roundToInt().coerceIn(0, 100)
                    }
                }

                if (usedRamGb <= 0f) {
                    am?.getMemoryInfo(memInfo)
                    usedRamGb = ((memInfo.totalMem - memInfo.availMem).coerceAtLeast(0L)) / 1_000_000_000f
                    ramPct = if (totalRamGb > 0f) ((usedRamGb / totalRamGb) * 100f).roundToInt().coerceIn(0, 100) else 0
                }

                _state.value = _state.value.copy(
                    cpuTempCelsius = cpuTemp,
                    fanSpeedPercent = fanPercent,
                    cpuCurFreqGhz = cpuCurVal,
                    cpuMaxFreqGhz = cpuMaxVal,
                    cpuUnit = cpuUnitStr,
                    gpuCurFreqMhz = gpuCurVal,
                    gpuMaxFreqMhz = gpuMaxVal,
                    gpuUnit = gpuUnitStr,
                    powerWatts = powerWatts.coerceIn(0f, 35f),
                    ramUsedGb = usedRamGb,
                    ramTotalGb = totalRamGb,
                    cpuTotalUsagePercent = cpuUsage.total,
                    cpuSilverUsagePercent = cpuUsage.silver,
                    cpuSilverCurFreqGhz = silverGhz,
                    cpuGoldUsagePercent = cpuUsage.gold,
                    cpuGoldCurFreqGhz = goldGhz,
                    cpuPrimeUsagePercent = cpuUsage.prime,
                    cpuPrimeCurFreqGhz = primeGhz,
                    gpuBusyPercent = gpuBusyPct,
                    ramPercent = ramPct,
                    swapUsedGb = swapUsedGb,
                    swapTotalGb = swapTotalGb,
                    swapPercent = swapPct,
                    batteryVoltageMv = batteryVoltageMv,
                    batteryCurrentMa = batteryCurrentMa
                )

                delay(1000L)
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
            ramUsedGb = 0f,
            cpuTotalUsagePercent = 0,
            cpuSilverUsagePercent = 0,
            cpuGoldUsagePercent = 0,
            cpuPrimeUsagePercent = 0,
            gpuBusyPercent = 0,
            ramPercent = 0
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
