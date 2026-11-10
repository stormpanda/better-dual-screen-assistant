package com.stormpanda.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-speed bridge to [PrivilegedHelperMain] with automatic fallback to in-process calls
 * and [PServerClient] shell execution.
 */
@SuppressLint("PrivateApi")
object PrivilegedBridge {
    private const val TAG = "PrivilegedBridge"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSpawning = AtomicBoolean(false)
    private val clientToken = Binder()

    @Volatile
    private var helperBinder: IBinder? = null

    @Volatile
    private var replaceStockPreferred: Boolean = true

    @Volatile
    private var savedTopBrightness: Float = 0.4f

    @Volatile
    private var savedBottomBrightness: Float = 0.4f

    @Volatile
    private var topScreenBlanked: Boolean = false

    @Volatile
    private var topScreenShowCoverWindow: Boolean = false

    @Volatile
    private var bottomScreenBlanked: Boolean = false

    @Volatile
    private var bottomScreenShowCoverWindow: Boolean = false

    @Volatile
    private var panel0BacklightBlanked: Boolean = false

    @Volatile
    private var panel1BacklightBlanked: Boolean = false

    private class PendingOsThemeRequest(
        val apply: Boolean,
        val resourceNames: Array<String>,
        val colorValues: IntArray,
        val targetNightMode: Int,
        val savedNightMode: Int,
        val injectedThemeJson: String,
        val savedThemeJson: String
    )

    @Volatile
    private var pendingOsThemeRequest: PendingOsThemeRequest? = null

    private val _isHelperConnected = MutableStateFlow(false)
    val isHelperConnected: StateFlow<Boolean> = _isHelperConnected.asStateFlow()

    fun onHelperBinderReceived(binder: IBinder, context: Context? = null) {
        if (!pingBinder(binder)) {
            Log.w(TAG, "Received outdated PrivilegedHelperMain binder; shutting it down")
            shutdownHelperBinder(binder)
            if (context != null) {
                ensureStarted(context)
            }
            return
        }
        val oldBinder = helperBinder
        if (oldBinder != null && oldBinder !== binder && oldBinder.isBinderAlive) {
            shutdownHelperBinder(oldBinder)
        }
        try {
            binder.linkToDeath({
                Log.w(TAG, "PrivilegedHelperMain binder died")
                if (helperBinder === binder) {
                    helperBinder = null
                    _isHelperConnected.value = false
                }
            }, 0)
        } catch (_: Throwable) {
        }
        helperBinder = binder
        _isHelperConnected.value = true
        installPrivilegedWindowSession()
        registerClientWithHelper(binder)
        if (topScreenBlanked) {
            dispatchTopScreenBlankedToBinder(binder, topScreenBlanked, topScreenShowCoverWindow)
        }
        if (bottomScreenBlanked) {
            dispatchBottomScreenBlankedToBinder(binder, bottomScreenBlanked, bottomScreenShowCoverWindow)
        }
        if (panel0BacklightBlanked) {
            dispatchBacklightPowerToBinder(binder, 0, true)
        }
        if (panel1BacklightBlanked) {
            dispatchBacklightPowerToBinder(binder, 1, true)
        }
        pendingOsThemeRequest?.let { req ->
            scope.launch {
                if (dispatchOsThemeRequestToBinder(binder, req)) {
                    if (pendingOsThemeRequest === req) {
                        pendingOsThemeRequest = null
                    }
                }
            }
        }
        if (context != null) {
            val appCtx = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val repo = com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
                        .getInstance(appCtx)
                    repo.refreshDisplayBrightness()
                    repo.syncScreenRecordingStateFromHelper()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun registerClientWithHelper(binder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            data.writeStrongBinder(clientToken)
            data.writeString(PrivilegedHelperMain.APP_PACKAGE_NAME)
            data.writeInt(if (replaceStockPreferred) 1 else 0)
            data.writeFloat(savedTopBrightness)
            data.writeFloat(savedBottomBrightness)
            binder.transact(PrivilegedHelperMain.TRANSACTION_REGISTER_CLIENT, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register client token with PrivilegedHelperMain", t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun shutdownHelperBinder(binder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            binder.transact(PrivilegedHelperMain.TRANSACTION_SHUTDOWN, data, reply, 0)
            reply.readException()
        } catch (_: Throwable) {
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun setReplaceStockPreferred(replace: Boolean) {
        replaceStockPreferred = replace
        syncWatchdogStateToHelper()
    }

    fun updateSavedBrightness(top: Float? = null, bottom: Float? = null) {
        if (top != null && top > 0f) savedTopBrightness = top
        if (bottom != null && bottom > 0f) savedBottomBrightness = bottom
        syncWatchdogStateToHelper()
    }

    private fun syncWatchdogStateToHelper() {
        val binder = helperBinder ?: return
        if (!binder.isBinderAlive) return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            data.writeInt(if (replaceStockPreferred) 1 else 0)
            data.writeFloat(savedTopBrightness)
            data.writeFloat(savedBottomBrightness)
            binder.transact(PrivilegedHelperMain.TRANSACTION_UPDATE_WATCHDOG_STATE, data, reply, 0)
            reply.readException()
        } catch (_: Throwable) {
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun ensureInitialized(context: Context) = ensureStarted(context)

    fun ensureStarted(context: Context) {
        replaceStockPreferred = PServerClient.isReplaceStockPreferred(context)
        val current = helperBinder
        if (current != null && current.isBinderAlive && ping()) {
            _isHelperConnected.value = true
            syncWatchdogStateToHelper()
            return
        }
        if (!isSpawning.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                PServerClient.ensurePermissionsGranted(appContext)
                val reconnected = helperBinder
                if (reconnected != null && reconnected.isBinderAlive && ping()) {
                    _isHelperConnected.value = true
                    syncWatchdogStateToHelper()
                    return@launch
                }
                val apkPath = PServerClient.shellQuote(appContext.applicationInfo.sourceDir)
                val cmd = "CLASSPATH=$apkPath /system/bin/app_process /system/bin " +
                    "com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain >/dev/null 2>&1 &"
                PServerClient.exec(cmd, wait = false)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to spawn PrivilegedHelperMain", t)
            } finally {
                isSpawning.set(false)
            }
        }
    }

    fun ping(): Boolean {
        val binder = helperBinder ?: return false
        return pingBinder(binder)
    }

    private fun pingBinder(binder: IBinder): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            binder.transact(PrivilegedHelperMain.TRANSACTION_PING, data, reply, 0)
            reply.readException()
            reply.readInt() == PrivilegedHelperMain.HELPER_PROTOCOL_VERSION
        } catch (_: Throwable) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun setDisplayBrightness(context: Context, displayId: Int, brightness: Float, temporary: Boolean) {
        val clamped = if (brightness < 0f) -1.0f else brightness.coerceIn(0f, 1f)
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeInt(displayId)
                data.writeFloat(clamped)
                data.writeInt(if (temporary) 1 else 0)
                binder.transact(PrivilegedHelperMain.TRANSACTION_SET_BRIGHTNESS, data, reply, 0)
                reply.readException()
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Helper setDisplayBrightness failed, trying fallback", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        // Fallback 1: Direct DisplayManager reflection (works if running with permission)
        try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val methodName = if (temporary) "setTemporaryBrightness" else "setBrightness"
            val m = DisplayManager::class.java.getMethod(
                methodName,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            m.invoke(dm, displayId, clamped)
            return
        } catch (_: Throwable) {
        }
        // Fallback 2: Re-trigger helper startup and set via settings/cmd if not temporary
        ensureStarted(context)
        if (!temporary && displayId == 0) {
            PServerClient.exec("cmd display set-brightness $clamped", wait = false)
        }
    }

    /**
     * Returns floatArrayOf(brightness, min, max, adjustedBrightness) for [displayId].
     */
    fun getDisplayBrightnessInfo(context: Context, displayId: Int): FloatArray {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeInt(displayId)
                binder.transact(PrivilegedHelperMain.TRANSACTION_GET_BRIGHTNESS, data, reply, 0)
                reply.readException()
                val arr = reply.createFloatArray()
                if (arr != null && arr.size >= 3) {
                    return if (arr.size >= 4) arr else floatArrayOf(arr[0], arr[1], arr[2], arr[0])
                }
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm?.getDisplay(displayId)
            if (display != null) {
                val info = display.javaClass.getMethod("getBrightnessInfo").invoke(display)
                if (info != null) {
                    val b = info.javaClass.getField("brightness").getFloat(info)
                    val min = info.javaClass.getField("brightnessMinimum").getFloat(info)
                    val max = info.javaClass.getField("brightnessMaximum").getFloat(info)
                    val adj = try {
                        info.javaClass.getField("adjustedBrightness").getFloat(info)
                    } catch (_: Throwable) {
                        b
                    }
                    return floatArrayOf(b, min, max, adj)
                }
            }
        } catch (_: Throwable) {
        }
        if (displayId == 0) {
            try {
                val sysB = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    128
                ).coerceIn(0, 255) / 255f
                return floatArrayOf(sysB, 0f, 1f, sysB)
            } catch (_: Throwable) {
            }
        }
        return floatArrayOf(0.5f, 0f, 1f, 0.5f)
    }

    fun setPinScreenMode(context: Context, mode: Int) {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeInt(mode)
                binder.transact(PrivilegedHelperMain.TRANSACTION_SET_PIN_SCREEN_MODE, data, reply, 0)
                reply.readException()
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Helper setPinScreenMode failed", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        ensureStarted(context)
    }

    fun setTopScreenBlanked(
        context: Context,
        blank: Boolean,
        showCoverWindow: Boolean = blank
    ) {
        topScreenBlanked = blank
        topScreenShowCoverWindow = showCoverWindow
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            if (dispatchTopScreenBlankedToBinder(binder, blank, showCoverWindow)) {
                return
            }
        }
        ensureStarted(context)
    }

    private fun dispatchTopScreenBlankedToBinder(
        binder: IBinder,
        blank: Boolean,
        showCoverWindow: Boolean
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            data.writeInt(if (blank) 1 else 0)
            data.writeInt(if (showCoverWindow) 1 else 0)
            binder.transact(PrivilegedHelperMain.TRANSACTION_SET_TOP_SCREEN_BLANKED, data, reply, 0)
            reply.readException()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Helper setTopScreenBlanked failed", t)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun setBottomScreenBlanked(
        context: Context,
        blank: Boolean,
        showCoverWindow: Boolean = blank
    ) {
        bottomScreenBlanked = blank
        bottomScreenShowCoverWindow = showCoverWindow
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            if (dispatchBottomScreenBlankedToBinder(binder, blank, showCoverWindow)) {
                return
            }
        }
        ensureStarted(context)
    }

    private fun dispatchBottomScreenBlankedToBinder(
        binder: IBinder,
        blank: Boolean,
        showCoverWindow: Boolean
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            data.writeInt(if (blank) 1 else 0)
            data.writeInt(if (showCoverWindow) 1 else 0)
            binder.transact(PrivilegedHelperMain.TRANSACTION_SET_BOTTOM_SCREEN_BLANKED, data, reply, 0)
            reply.readException()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Helper setBottomScreenBlanked failed", t)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Controls hardware kernel backlight power (`/sys/class/backlight/panel{0,1}-backlight/bl_power`)
     * on devices that blank panels via sysfs backlight power ([DeviceProfile.blankViaBacklightSysfs]).
     * `panelIndex`: `0` = Top screen (`panel0-backlight`), `1` = Bottom screen (`panel1-backlight`).
     * `blanked`: `true` writes `"1"` (backlight off), `false` writes `"0"` (backlight on).
     */
    fun setBacklightPower(context: Context, panelIndex: Int, blanked: Boolean) {
        if (panelIndex != 0 && panelIndex != 1) return
        if (panelIndex == 0) {
            panel0BacklightBlanked = blanked
        } else {
            panel1BacklightBlanked = blanked
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            if (dispatchBacklightPowerToBinder(binder, panelIndex, blanked)) {
                return
            }
        }
        val valStr = if (blanked) "1" else "0"
        PServerClient.exec(
            "echo $valStr > /sys/class/backlight/panel$panelIndex-backlight/bl_power",
            wait = true
        )
        ensureStarted(context)
    }

    private fun dispatchBacklightPowerToBinder(
        binder: IBinder,
        panelIndex: Int,
        blanked: Boolean
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            data.writeInt(panelIndex)
            data.writeInt(if (blanked) 1 else 0)
            binder.transact(PrivilegedHelperMain.TRANSACTION_SET_BACKLIGHT_POWER, data, reply, 0)
            reply.readException()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Helper setBacklightPower($panelIndex, $blanked) failed", t)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun setSystemProperty(key: String, value: String) {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString(key)
                data.writeString(value)
                binder.transact(PrivilegedHelperMain.TRANSACTION_SET_SYSTEM_PROP, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec(
            "setprop ${PServerClient.shellQuote(key)} ${PServerClient.shellQuote(value)}",
            wait = true
        )
    }

    fun getSystemProperty(key: String, defaultValue: String = ""): String {
        try {
            val sysProps = Class.forName("android.os.SystemProperties")
            val get = sysProps.getMethod("get", String::class.java, String::class.java)
            val res = get.invoke(null, key, defaultValue) as? String
            if (!res.isNullOrEmpty()) return res
        } catch (_: Throwable) {
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString(key)
                data.writeString(defaultValue)
                binder.transact(PrivilegedHelperMain.TRANSACTION_GET_SYSTEM_PROP, data, reply, 0)
                reply.readException()
                return reply.readString() ?: defaultValue
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return defaultValue
    }

    fun forceStopPackage(packageName: String) {
        if (!PServerClient.isValidPackageName(packageName)) return
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString(packageName)
                binder.transact(PrivilegedHelperMain.TRANSACTION_FORCE_STOP_PACKAGE, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec("am force-stop ${PServerClient.shellQuote(packageName)}", wait = true)
    }

    fun lockScreen() {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                binder.transact(PrivilegedHelperMain.TRANSACTION_LOCK_SCREEN, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec("input keyevent 26", wait = false)
    }

    private val sfParcelHexRegex = Regex("""Parcel\(\s*([0-9a-fA-F]{1,8})\b""")

    fun getGameFps(): Float {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                binder.transact(PrivilegedHelperMain.TRANSACTION_GET_GAME_FPS, data, reply, 0)
                reply.readException()
                val fps = reply.readFloat()
                if (fps.isFinite() && fps >= 0f) return fps
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return -1f
    }

    fun getSurfaceFlingerFlips(): Int {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                binder.transact(PrivilegedHelperMain.TRANSACTION_GET_SF_FLIPS, data, reply, 0)
                reply.readException()
                val flips = reply.readInt()
                if (flips >= 0) return flips
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        // Fallback to parsing `service call SurfaceFlinger 1013` via PServerClient (just like S0.j in stock app)
        val out = PServerClient.exec("service call SurfaceFlinger 1013", wait = true).getOrDefault("")
        val hexMatch = sfParcelHexRegex.find(out)?.groupValues?.getOrNull(1)
        if (!hexMatch.isNullOrEmpty()) {
            return hexMatch.toUIntOrNull(16)?.toInt() ?: -1
        }
        return -1
    }

    fun readSysfsFile(path: String): String {
        if (path.contains("..") || (!path.startsWith("/sys/") && path != "/proc/stat")) {
            return ""
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString(path)
                binder.transact(PrivilegedHelperMain.TRANSACTION_READ_FILE, data, reply, 0)
                reply.readException()
                val text = reply.readString() ?: ""
                if (text.isNotEmpty()) return text
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        try {
            val file = File(path)
            if (file.canRead()) {
                return file.readText().trim()
            }
        } catch (_: Throwable) {
        }
        return PServerClient.exec("cat ${PServerClient.shellQuote(path)}", wait = true).getOrDefault("").trim()
    }

    fun readSysfsLong(path: String): Long {
        val raw = readSysfsFile(path)
        return raw.toLongOrNull() ?: 0L
    }

    fun putSystemSettingInt(context: Context, key: String, value: Int) {
        try {
            if (android.provider.Settings.System.putInt(context.contentResolver, key, value)) {
                return
            }
        } catch (_: Throwable) {
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString("int")
                data.writeString(key)
                data.writeString(value.toString())
                binder.transact(PrivilegedHelperMain.TRANSACTION_PUT_SYSTEM_SETTING, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec("settings put system ${PServerClient.shellQuote(key)} $value", wait = true)
    }

    fun putSystemSettingFloat(context: Context, key: String, value: Float) {
        try {
            if (android.provider.Settings.System.putFloat(context.contentResolver, key, value)) {
                return
            }
        } catch (_: Throwable) {
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString("float")
                data.writeString(key)
                data.writeString(value.toString())
                binder.transact(PrivilegedHelperMain.TRANSACTION_PUT_SYSTEM_SETTING, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec("settings put system ${PServerClient.shellQuote(key)} $value", wait = true)
    }

    fun putSystemSettingString(context: Context, key: String, value: String) {
        try {
            if (android.provider.Settings.System.putString(context.contentResolver, key, value)) {
                return
            }
        } catch (_: Throwable) {
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString("string")
                data.writeString(key)
                data.writeString(value)
                binder.transact(PrivilegedHelperMain.TRANSACTION_PUT_SYSTEM_SETTING, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec(
            "settings put system ${PServerClient.shellQuote(key)} ${PServerClient.shellQuote(value)}",
            wait = true
        )
    }

    fun putGlobalSettingInt(context: Context, key: String, value: Int) {
        try {
            if (android.provider.Settings.Global.putInt(context.contentResolver, key, value)) {
                return
            }
        } catch (_: Throwable) {
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString("global")
                data.writeString(key)
                data.writeString(value.toString())
                binder.transact(PrivilegedHelperMain.TRANSACTION_PUT_SYSTEM_SETTING, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec("settings put global ${PServerClient.shellQuote(key)} $value", wait = true)
    }

    fun putSecureSettingInt(context: Context, key: String, value: Int) {
        try {
            if (android.provider.Settings.Secure.putInt(context.contentResolver, key, value)) {
                return
            }
        } catch (_: Throwable) {
        }
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString("secure")
                data.writeString(key)
                data.writeString(value.toString())
                binder.transact(PrivilegedHelperMain.TRANSACTION_PUT_SYSTEM_SETTING, data, reply, 0)
                reply.readException()
                return
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.exec("settings put secure ${PServerClient.shellQuote(key)} $value", wait = true)
    }

    fun swapDisplayTasks(context: Context): Boolean {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                binder.transact(PrivilegedHelperMain.TRANSACTION_SWAP_DISPLAY_TASKS, data, reply, 0)
                reply.readException()
                return reply.readInt() != 0
            } catch (t: Throwable) {
                Log.w(TAG, "Helper swapDisplayTasks failed, trying fallback", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        ensureStarted(context)
        val bottomId = DeviceProfile.current(context).bottomDisplayId
        val script = """
            top_task=$(dumpsys activity activities | awk '/Display #0/{d=0} /Display #$bottomId/{d=$bottomId} /Task\{.*type=standard/{if (match($0, /#[0-9]+/)) {id=substr($0, RSTART+1, RLENGTH-1); if (d==0 && t0=="") t0=id; if (d==$bottomId && tb=="") tb=id}} END{print t0 ":" tb}')
            t0=${'$'}{top_task%:*}
            tb=${'$'}{top_task#*:}
            if [ -n "${'$'}t0" ] && [ -n "${'$'}tb" ]; then
                am display move-stack "${'$'}t0" $bottomId
                am display move-stack "${'$'}tb" 0
                echo "OK"
            elif [ -n "${'$'}t0" ]; then
                am display move-stack "${'$'}t0" $bottomId
                echo "OK"
            elif [ -n "${'$'}tb" ]; then
                am display move-stack "${'$'}tb" 0
                echo "OK"
            fi
        """.trimIndent()
        return PServerClient.exec(script, wait = true).getOrDefault("").contains("OK")
    }

    /**
     * Closes all visible recent app tasks cleanly (matching Android Recents' "Clear all" action)
     * without killing background services or live wallpapers.
     * Returns the number of cleared tasks (`>= 0`) or `-1` on failure.
     */
    fun clearAllRecentTasks(context: Context): Int {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                binder.transact(
                    PrivilegedHelperMain.TRANSACTION_CLEAR_ALL_RECENT_TASKS,
                    data,
                    reply,
                    0
                )
                reply.readException()
                val cleared = reply.readInt()
                if (cleared >= 0) return cleared
            } catch (t: Throwable) {
                Log.w(TAG, "Helper clearAllRecentTasks failed, trying fallback", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        ensureStarted(context)
        val stockPkgsRegex = DeviceProfile.allStockAssistantPackages.joinToString("|") {
            it.replace(".", """\.""")
        }
        val script = """
            count=0
            for id in $(dumpsys activity recents | awk '/\* Recent #/{in_rec=1; id=""; skip=0} in_rec && /type=standard/ && match($0, /#[0-9]+/){id=substr($0, RSTART+1, RLENGTH-1)} in_rec && /(autoRemoveRecents=true|com\.stormpanda\.betterdualscreenassistant|${stockPkgsRegex}|com\.android\.systemui)/{skip=1} in_rec && /realActivity=/{if (id != "" && !skip) print id; in_rec=0}'); do
                am stack remove "${'$'}id" >/dev/null 2>&1 && count=${'$'}((count + 1))
            done
            echo "CLEARED:${'$'}count"
        """.trimIndent()
        val res = PServerClient.exec(script, wait = true)
        if (res.isFailure) return -1
        val out = res.getOrDefault("")
        val match = Regex("""CLEARED:(\d+)""").find(out) ?: return -1
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    internal fun resolvePhysicalDisplayId(context: Context, logicalDisplayId: Int): String? {
        return try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm?.getDisplay(logicalDisplayId) ?: return null
            val uniqueId = display.javaClass.getMethod("getUniqueId").invoke(display) as? String
            val candidate = uniqueId?.removePrefix("local:")?.trim()
            candidate?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Captures a screenshot of the Top screen (`targetMode == 0`), Bottom screen (`targetMode == 1`),
     * or Both screens (`targetMode == 2`) into `/sdcard/Pictures/Screenshots/` and notifies MediaScanner.
     */
    fun captureScreenshot(context: Context, targetMode: Int): Boolean {
        val bottomDisplayId = DeviceProfile.current(context).bottomDisplayId
        var topPhysId: String? = resolvePhysicalDisplayId(context, 0)
        var bottomPhysId: String? = resolvePhysicalDisplayId(context, bottomDisplayId)

        if (topPhysId.isNullOrEmpty() || bottomPhysId.isNullOrEmpty()) {
            val sfOut = PServerClient.exec("dumpsys SurfaceFlinger --display-id", wait = true).getOrDefault("")
            val regex = Regex("""Display\s+(\d+)\s+\([^)]*port=(\d+)""")
            for (match in regex.findAll(sfOut)) {
                val physId = match.groupValues[1]
                val port = match.groupValues[2]
                if (port in DeviceProfile.allTopSfPorts && topPhysId.isNullOrEmpty()) {
                    topPhysId = physId
                }
                if (port in DeviceProfile.allBottomSfPorts && bottomPhysId.isNullOrEmpty()) {
                    bottomPhysId = physId
                }
            }
        }

        val dir = "/sdcard/Pictures/Screenshots"
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val paths = mutableListOf<String>()
        val cmds = mutableListOf("mkdir -p $dir")

        if (targetMode == 0 || targetMode == 2) {
            val topPath = "$dir/Screenshot_${ts}_Top.png"
            paths.add(topPath)
            val dArg = if (!topPhysId.isNullOrEmpty()) "-d $topPhysId" else ""
            cmds.add("/system/bin/screencap -p $dArg $topPath")
        }
        if (targetMode == 1 || targetMode == 2) {
            val bottomPath = "$dir/Screenshot_${ts}_Bottom.png"
            paths.add(bottomPath)
            val dArg = if (!bottomPhysId.isNullOrEmpty()) "-d $bottomPhysId" else "-d $bottomDisplayId"
            cmds.add("/system/bin/screencap -p $dArg $bottomPath")
        }

        val res = PServerClient.exec(cmds.joinToString(" && "), wait = true)
        if (paths.isNotEmpty()) {
            try {
                android.media.MediaScannerConnection.scanFile(
                    context.applicationContext,
                    paths.toTypedArray(),
                    arrayOf("image/png"),
                    null
                )
            } catch (_: Throwable) {
            }
        }
        return res.isSuccess
    }

    fun applyOsThemeOverlay(
        context: Context,
        resourceNames: Array<String>,
        colorValues: IntArray,
        targetNightMode: Int,
        savedNightMode: Int,
        injectedThemeJson: String,
        savedThemeJson: String
    ) {
        val req = PendingOsThemeRequest(
            apply = true,
            resourceNames = resourceNames,
            colorValues = colorValues,
            targetNightMode = targetNightMode,
            savedNightMode = savedNightMode,
            injectedThemeJson = injectedThemeJson,
            savedThemeJson = savedThemeJson
        )
        pendingOsThemeRequest = req
        val appContext = context.applicationContext
        scope.launch {
            val binder = helperBinder
            if (binder != null && binder.isBinderAlive) {
                if (dispatchOsThemeRequestToBinder(binder, req)) {
                    if (pendingOsThemeRequest === req) {
                        pendingOsThemeRequest = null
                    }
                    return@launch
                }
            }
            ensureStarted(appContext)
        }
    }

    fun clearOsThemeOverlay(context: Context?, restoreNightMode: Int, restoreThemeJson: String = "") {
        val req = PendingOsThemeRequest(
            apply = false,
            resourceNames = emptyArray(),
            colorValues = IntArray(0),
            targetNightMode = restoreNightMode,
            savedNightMode = restoreNightMode,
            injectedThemeJson = "",
            savedThemeJson = restoreThemeJson
        )
        pendingOsThemeRequest = req
        val appContext = context?.applicationContext
        scope.launch {
            val binder = helperBinder
            if (binder != null && binder.isBinderAlive) {
                if (dispatchOsThemeRequestToBinder(binder, req)) {
                    if (pendingOsThemeRequest === req) {
                        pendingOsThemeRequest = null
                    }
                    return@launch
                }
            }
            appContext?.let { ensureStarted(it) }
        }
    }

    private fun dispatchOsThemeRequestToBinder(
        binder: IBinder,
        req: PendingOsThemeRequest
    ): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            if (req.apply) {
                data.writeStringArray(req.resourceNames)
                data.writeIntArray(req.colorValues)
                data.writeInt(req.targetNightMode)
                data.writeInt(req.savedNightMode)
                data.writeString(req.injectedThemeJson)
                data.writeString(req.savedThemeJson)
                binder.transact(PrivilegedHelperMain.TRANSACTION_APPLY_OS_THEME, data, reply, 0)
            } else {
                data.writeInt(req.targetNightMode)
                data.writeString(req.savedThemeJson)
                binder.transact(PrivilegedHelperMain.TRANSACTION_CLEAR_OS_THEME, data, reply, 0)
            }
            reply.readException()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to dispatch OS theme request to PrivilegedHelperMain", t)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Temporarily wakes (`freeze = false`) or re-freezes (`freeze = true`) the suppressed stock
     * `com.odin.dualscreen.assistant` process around foreground ordered screen broadcasts so
     * `system_server`'s `BroadcastQueue` never stalls or ANR-respawns `TccServices`.
     */
    fun setStockProcessFrozenForBroadcast(freeze: Boolean) {
        if (freeze && PServerClient.hasFallbackUnfrozenStockPid()) {
            PServerClient.setStockProcessFrozenForBroadcastFallback(true)
        }
        if (!replaceStockPreferred) return
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeInt(if (freeze) 1 else 0)
                binder.transact(
                    PrivilegedHelperMain.TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST,
                    data,
                    reply,
                    0
                )
                reply.readException()
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Helper setStockProcessFrozenForBroadcast($freeze) failed", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        PServerClient.setStockProcessFrozenForBroadcastFallback(freeze)
    }

    data class ScreenRecordHelperStatus(
        val isRecording: Boolean = false,
        val targetMode: Int = 0,
        val startedElapsedRealtimeMs: Long = 0L,
        val outputPath: String = "",
        val lastCompletedPath: String = "",
        val lastStopReason: String = ""
    )

    private fun readScreenRecordHelperStatusFromParcel(reply: Parcel): ScreenRecordHelperStatus {
        val isRecording = reply.readInt() != 0
        val targetMode = reply.readInt()
        val startedElapsedRealtimeMs = reply.readLong()
        val outputPath = reply.readString() ?: ""
        val lastCompletedPath = reply.readString() ?: ""
        val lastStopReason = reply.readString() ?: ""
        return ScreenRecordHelperStatus(
            isRecording = isRecording,
            targetMode = targetMode,
            startedElapsedRealtimeMs = startedElapsedRealtimeMs,
            outputPath = outputPath,
            lastCompletedPath = lastCompletedPath,
            lastStopReason = lastStopReason
        )
    }

    /**
     * Starts hardware screen recording inside [PrivilegedHelperMain].
     * Returns a pair of `(startedOk, currentHelperStatus)`.
     */
    fun startScreenRecording(
        context: Context,
        targetMode: Int,
        includeInternalAudio: Boolean,
        includeMicAudio: Boolean,
        fps: Int,
        outputPath: String
    ): Pair<Boolean, ScreenRecordHelperStatus> {
        var binder = helperBinder
        if (binder == null || !binder.isBinderAlive) {
            ensureStarted(context)
            var waitedMs = 0L
            while ((binder == null || !binder.isBinderAlive) && waitedMs < 1200L) {
                try {
                    Thread.sleep(60L)
                } catch (_: InterruptedException) {
                    break
                }
                waitedMs += 60L
                binder = helperBinder
            }
        }
        val activeBinder = binder
        if (activeBinder != null && activeBinder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeInt(targetMode)
                data.writeInt(if (includeInternalAudio) 1 else 0)
                data.writeInt(if (includeMicAudio) 1 else 0)
                data.writeInt(fps)
                data.writeString(outputPath)
                activeBinder.transact(
                    PrivilegedHelperMain.TRANSACTION_START_SCREEN_RECORD,
                    data,
                    reply,
                    0
                )
                reply.readException()
                val started = reply.readInt() != 0
                val status = readScreenRecordHelperStatusFromParcel(reply)
                return started to status
            } catch (t: Throwable) {
                Log.e(TAG, "Helper startScreenRecording failed", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        ensureStarted(context)
        return false to ScreenRecordHelperStatus()
    }

    /**
     * Stops and finalizes any active screen recording inside [PrivilegedHelperMain].
     */
    fun stopScreenRecording(
        context: Context? = null,
        reason: String = "user"
    ): ScreenRecordHelperStatus {
        val binder = helperBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                data.writeString(reason)
                binder.transact(
                    PrivilegedHelperMain.TRANSACTION_STOP_SCREEN_RECORD,
                    data,
                    reply,
                    0
                )
                reply.readException()
                return readScreenRecordHelperStatusFromParcel(reply)
            } catch (t: Throwable) {
                Log.e(TAG, "Helper stopScreenRecording($reason) failed", t)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        context?.let { ensureStarted(it) }
        return ScreenRecordHelperStatus()
    }

    /**
     * Queries the current screen recording status from [PrivilegedHelperMain] without spawning
     * any threads or processes if disconnected.
     */
    fun getScreenRecordingState(): ScreenRecordHelperStatus {
        val binder = helperBinder ?: return ScreenRecordHelperStatus()
        if (!binder.isBinderAlive) return ScreenRecordHelperStatus()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            binder.transact(
                PrivilegedHelperMain.TRANSACTION_GET_SCREEN_RECORD_STATE,
                data,
                reply,
                0
            )
            reply.readException()
            readScreenRecordHelperStatusFromParcel(reply)
        } catch (t: Throwable) {
            Log.w(TAG, "Helper getScreenRecordingState failed", t)
            ScreenRecordHelperStatus()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @Volatile
    private var windowSessionProxyInstalled: Boolean = false

    /**
     * Wraps `WindowManagerGlobal.sWindowSession` with a dynamic proxy that routes `addToDisplay*`
     * Binder calls through [PrivilegedHelperMain] (`UID 0`) when connected, allowing our overlay
     * windows to use privileged window type `2021` (`TYPE_BOOT_PROGRESS`, matching stock `TccServices.u()`)
     * so they are never hidden by `PRIVATE_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS` (e.g. Files app).
     *
     * Returns `true` if the privileged window session proxy is installed and [PrivilegedHelperMain]
     * is currently connected.
     */
    fun installPrivilegedWindowSession(): Boolean {
        val helperAlive = helperBinder?.isBinderAlive == true
        if (windowSessionProxyInstalled) {
            return helperAlive
        }
        return try {
            val wmGlobalClass = Class.forName("android.view.WindowManagerGlobal")
            val iWindowSessionClass = Class.forName("android.view.IWindowSession")
            val iWindowSessionStubClass = Class.forName("android.view.IWindowSession\$Stub")
            val getWindowSessionMethod = wmGlobalClass.getDeclaredMethod("getWindowSession")
            getWindowSessionMethod.isAccessible = true
            val sWindowSessionField = wmGlobalClass.getDeclaredField("sWindowSession")
            sWindowSessionField.isAccessible = true

            synchronized(wmGlobalClass) {
                val currentSession = getWindowSessionMethod.invoke(null) ?: return false
                if (java.lang.reflect.Proxy.isProxyClass(currentSession.javaClass)) {
                    windowSessionProxyInstalled = true
                    return helperAlive
                }
                val asBinderMethod = currentSession.javaClass.getMethod("asBinder")
                val realSessionBinder = asBinderMethod.invoke(currentSession) as? IBinder ?: return false

                val proxyBinder = object : Binder() {
                    override fun onTransact(
                        code: Int,
                        data: Parcel,
                        reply: Parcel?,
                        flags: Int
                    ): Boolean {
                        if (windowSessionTransact(realSessionBinder, code, data, reply, flags)) {
                            return true
                        }
                        return realSessionBinder.transact(code, data, reply, flags)
                    }
                }

                val asInterfaceMethod =
                    iWindowSessionStubClass.getMethod("asInterface", IBinder::class.java)
                val bridgedSession = asInterfaceMethod.invoke(null, proxyBinder) ?: return false

                val sessionProxy = java.lang.reflect.Proxy.newProxyInstance(
                    iWindowSessionClass.classLoader,
                    arrayOf(iWindowSessionClass)
                ) { _, method, args ->
                    val target = if (
                        method.name.startsWith("addToDisplay") &&
                        helperBinder?.isBinderAlive == true
                    ) {
                        bridgedSession
                    } else {
                        currentSession
                    }
                    try {
                        if (args == null) {
                            method.invoke(target)
                        } else {
                            method.invoke(target, *args)
                        }
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        throw (e.targetException ?: e)
                    }
                }

                sWindowSessionField.set(null, sessionProxy)
                windowSessionProxyInstalled = true
                Log.i(TAG, "Installed privileged IWindowSession proxy in WindowManagerGlobal")
            }
            helperAlive
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to install privileged IWindowSession proxy", t)
            false
        }
    }

    private fun windowSessionTransact(
        realSessionBinder: IBinder,
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int
    ): Boolean {
        val helper = helperBinder ?: return false
        if (!helper.isBinderAlive) return false
        val helperData = Parcel.obtain()
        val helperReply = Parcel.obtain()
        return try {
            helperData.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            helperData.writeStrongBinder(realSessionBinder)
            helperData.writeInt(code)
            helperData.writeInt(flags)
            val dataSize = data.dataSize()
            if (dataSize > 0) {
                helperData.appendFrom(data, 0, dataSize)
            }
            val transacted = helper.transact(
                PrivilegedHelperMain.TRANSACTION_WINDOW_SESSION_TRANSACT,
                helperData,
                helperReply,
                0
            )
            if (!transacted) return false
            helperReply.readException()
            val ok = helperReply.readInt() != 0
            if (!ok) return false
            val rem = helperReply.dataAvail()
            if (rem > 0 && reply != null) {
                reply.appendFrom(helperReply, helperReply.dataPosition(), rem)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "windowSessionTransact(code=$code) via helper failed", t)
            false
        } finally {
            helperData.recycle()
            helperReply.recycle()
        }
    }
}
