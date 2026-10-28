package com.stormpanda.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
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
        registerClientWithHelper(binder)
        if (topScreenBlanked) {
            dispatchTopScreenBlankedToBinder(binder, topScreenBlanked, topScreenShowCoverWindow)
        }
        pendingOsThemeRequest?.let { req ->
            scope.launch {
                dispatchOsThemeRequestToBinder(binder, req)
            }
        }
        if (context != null) {
            val appCtx = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
                        .getInstance(appCtx)
                        .refreshDisplayBrightness()
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
        val script = """
            top_task=$(dumpsys activity activities | awk '/Display #0/{d=0} /Display #4/{d=4} /Task\{.*type=standard/{if (match($0, /#[0-9]+/)) {id=substr($0, RSTART+1, RLENGTH-1); if (d==0 && t0=="") t0=id; if (d==4 && t4=="") t4=id}} END{print t0 ":" t4}')
            t0=${'$'}{top_task%:*}
            t4=${'$'}{top_task#*:}
            if [ -n "${'$'}t0" ] && [ -n "${'$'}t4" ]; then
                am display move-stack "${'$'}t0" 4
                am display move-stack "${'$'}t4" 0
                echo "OK"
            elif [ -n "${'$'}t0" ]; then
                am display move-stack "${'$'}t0" 4
                echo "OK"
            elif [ -n "${'$'}t4" ]; then
                am display move-stack "${'$'}t4" 0
                echo "OK"
            fi
        """.trimIndent()
        return PServerClient.exec(script, wait = true).getOrDefault("").contains("OK")
    }

    /**
     * Captures a screenshot of the Top screen (`targetMode == 0`), Bottom screen (`targetMode == 1`),
     * or Both screens (`targetMode == 2`) into `/sdcard/Pictures/Screenshots/` and notifies MediaScanner.
     */
    fun captureScreenshot(context: Context, targetMode: Int): Boolean {
        val sfOut = PServerClient.exec("dumpsys SurfaceFlinger --display-id", wait = true).getOrDefault("")
        var topPhysId: String? = null
        var bottomPhysId: String? = null
        val regex = Regex("""Display\s+(\d+)\s+\([^)]*port=(\d+)""")
        for (match in regex.findAll(sfOut)) {
            val physId = match.groupValues[1]
            val port = match.groupValues[2]
            if (port == "131") topPhysId = physId
            if (port == "132") bottomPhysId = physId
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
            val dArg = if (!bottomPhysId.isNullOrEmpty()) "-d $bottomPhysId" else "-d 4"
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
}
