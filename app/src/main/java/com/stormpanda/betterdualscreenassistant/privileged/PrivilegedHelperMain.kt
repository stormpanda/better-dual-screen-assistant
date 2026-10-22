package com.stormpanda.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.AttributionSource
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Root companion process launched via PServerBinder using `app_process`.
 * Delivers an in-memory [IBinder] to [PrivilegedBinderProvider] so our user-installed APK
 * can perform system-level Binder and sysfs operations at 120Hz with sub-millisecond latency.
 *
 * Also acts as an event-driven (`IBinder.linkToDeath`) lifecycle companion that restores
 * Dual-Screen display state, unblanks screens, and wakes the stock `com.odin.dualscreen.assistant`
 * if our app is uninstalled or force-stopped.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object PrivilegedHelperMain {
    private const val TAG = "PrivilegedHelperMain"
    const val APP_PACKAGE_NAME = "com.stormpanda.betterdualscreenassistant"
    const val STOCK_PACKAGE_NAME = "com.odin.dualscreen.assistant"
    const val AUTHORITY = "com.stormpanda.betterdualscreenassistant.privileged"
    const val METHOD_REGISTER_BINDER = "register_binder"
    const val EXTRA_BINDER = "binder"
    const val DESCRIPTOR = "com.stormpanda.betterdualscreenassistant.IPrivilegedHelper"

    const val TRANSACTION_PING = IBinder.FIRST_CALL_TRANSACTION + 0
    const val TRANSACTION_SET_BRIGHTNESS = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TRANSACTION_GET_BRIGHTNESS = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TRANSACTION_SET_PIN_SCREEN_MODE = IBinder.FIRST_CALL_TRANSACTION + 3
    const val TRANSACTION_SET_SYSTEM_PROP = IBinder.FIRST_CALL_TRANSACTION + 4
    const val TRANSACTION_GET_SYSTEM_PROP = IBinder.FIRST_CALL_TRANSACTION + 5
    const val TRANSACTION_FORCE_STOP_PACKAGE = IBinder.FIRST_CALL_TRANSACTION + 6
    const val TRANSACTION_LOCK_SCREEN = IBinder.FIRST_CALL_TRANSACTION + 7
    const val TRANSACTION_GET_SF_FLIPS = IBinder.FIRST_CALL_TRANSACTION + 8
    const val TRANSACTION_READ_FILE = IBinder.FIRST_CALL_TRANSACTION + 9
    const val TRANSACTION_PUT_SYSTEM_SETTING = IBinder.FIRST_CALL_TRANSACTION + 10
    const val TRANSACTION_REGISTER_CLIENT = IBinder.FIRST_CALL_TRANSACTION + 11
    const val TRANSACTION_UPDATE_WATCHDOG_STATE = IBinder.FIRST_CALL_TRANSACTION + 12
    const val TRANSACTION_SHUTDOWN = IBinder.FIRST_CALL_TRANSACTION + 13

    private const val WATCHDOG_POLL_INTERVAL_MS = 250L
    private const val FORCE_STOP_GRACE_MS = 1500L
    private const val RESTART_GRACE_MS = 3000L

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            if (Looper.myLooper() == null) {
                Looper.prepareMainLooper()
            }
            val mainHandler = Handler(Looper.getMainLooper())
            val binder = HelperBinder( null, mainHandler)
            val bundle = Bundle().apply {
                putBinder(EXTRA_BINDER, binder)
            }
            callProviderExternal(AUTHORITY, METHOD_REGISTER_BINDER, null, bundle)
            Log.i(TAG, "Registered privileged helper binder with app process")
            Looper.loop()
        } catch (t: Throwable) {
            Log.e(TAG, "PrivilegedHelperMain fatal error", t)
        }
    }

    fun callProviderExternal(
        authority: String,
        method: String,
        arg: String?,
        extras: Bundle?
    ): Bundle? {
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        val amBinder = getService.invoke(null, "activity") as IBinder
        val amStubClass = Class.forName("android.app.IActivityManager\$Stub")
        val am = amStubClass.getMethod("asInterface", IBinder::class.java).invoke(null, amBinder)

        val token = Binder()
        val getHolder = am.javaClass.getMethod(
            "getContentProviderExternal",
            String::class.java,
            Int::class.javaPrimitiveType,
            IBinder::class.java,
            String::class.java
        )
        val removeProvider = am.javaClass.getMethod(
            "removeContentProviderExternalAsUser",
            String::class.java,
            IBinder::class.java,
            Int::class.javaPrimitiveType
        )
        val holder = getHolder.invoke(am, authority, 0, token, "*helper*") ?: return null
        try {
            val provider = holder.javaClass.getField("provider").get(holder) ?: return null
            val attrSource = AttributionSource.Builder(Process.myUid())
                .setPackageName("com.android.shell")
                .build()
            val callMethod = provider.javaClass.getMethod(
                "call",
                AttributionSource::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Bundle::class.java
            )
            return callMethod.invoke(provider, attrSource, authority, method, arg, extras) as? Bundle
        } finally {
            try {
                removeProvider.invoke(am, authority, token, 0)
            } catch (_: Throwable) {
            }
        }
    }

    class HelperBinder(
        private val systemContext: Context?,
        private val mainHandler: Handler
    ) : Binder() {
        private val serviceManagerClass = Class.forName("android.os.ServiceManager")
        private val getServiceMethod =
            serviceManagerClass.getMethod("getService", String::class.java)

        private val displayManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "display") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.hardware.display.IDisplayManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IDisplayManager", t)
            null
        }

        private val windowManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "window") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.view.IWindowManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IWindowManager", t)
            null
        }

        private val activityManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "activity") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.app.IActivityManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IActivityManager", t)
            null
        }

        private val packageManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "package") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IPackageManager", t)
            null
        }

        private val powerManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "power") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.os.IPowerManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IPowerManager", t)
            null
        }

        private val pServerBinder: IBinder? = try {
            getServiceMethod.invoke(null, "PServerBinder") as? IBinder
        } catch (_: Throwable) {
            null
        }

        private val sysPropsClass = Class.forName("android.os.SystemProperties")
        private val sysPropsSet =
            sysPropsClass.getMethod("set", String::class.java, String::class.java)
        private val sysPropsGet =
            sysPropsClass.getMethod("get", String::class.java, String::class.java)

        @Volatile
        private var clientBinder: IBinder? = null

        @Volatile
        private var clientPackageName: String = APP_PACKAGE_NAME

        @Volatile
        private var replaceStockPreferred: Boolean = true

        @Volatile
        private var savedTopBrightness: Float = 0.4f

        @Volatile
        private var savedBottomBrightness: Float = 0.4f

        @Volatile
        private var deathElapsedRealtime: Long = 0L

        @Volatile
        private var isShuttingDown: Boolean = false

        private val clientDeathRecipient = IBinder.DeathRecipient {
            if (isShuttingDown) return@DeathRecipient
            Log.w(TAG, "Client app process binder died; starting lifecycle check")
            mainHandler.post(onClientDiedRunnable)
        }

        private val onClientDiedRunnable = Runnable {
            if (isShuttingDown) return@Runnable
            clientBinder = null
            deathElapsedRealtime = SystemClock.elapsedRealtime()
            mainHandler.removeCallbacks(watchdogCheckRunnable)
            mainHandler.postDelayed(watchdogCheckRunnable, WATCHDOG_POLL_INTERVAL_MS)
        }

        private val watchdogCheckRunnable: Runnable = object : Runnable {
            override fun run() {
                if (isShuttingDown) return
                val activeClient = clientBinder
                if (activeClient != null && activeClient.isBinderAlive) {
                    return
                }

                val pkg = clientPackageName
                val installed = isPackageInstalledInternal(pkg)
                if (!installed) {
                    Log.i(TAG, "Package $pkg uninstalled; restoring Dual-Screen and stock assistant")
                    restoreDisplayAndStockAssistant()
                    System.exit(0)
                    return
                }

                if (isAppProcessRunningInternal(pkg)) {
                    // The app process restarted (e.g. after app update or START_STICKY).
                    // Try re-offering our binder; if a new helper took over, we will receive SHUTDOWN or exit.
                    try {
                        val bundle = Bundle().apply {
                            putBinder(EXTRA_BINDER, this@HelperBinder)
                        }
                        callProviderExternal(AUTHORITY, METHOD_REGISTER_BINDER, null, bundle)
                    } catch (_: Throwable) {
                    }
                    val elapsedAfterRestart = SystemClock.elapsedRealtime() - deathElapsedRealtime
                    if (elapsedAfterRestart >= RESTART_GRACE_MS && clientBinder == null) {
                        // A new helper process likely replaced us after an APK update
                        Log.i(TAG, "App process $pkg is running with a new helper; exiting old helper")
                        System.exit(0)
                        return
                    }
                    mainHandler.postDelayed(this, WATCHDOG_POLL_INTERVAL_MS)
                    return
                }

                val elapsed = SystemClock.elapsedRealtime() - deathElapsedRealtime
                val forceStopped = isPackageForceStoppedInternal(pkg)
                if ((forceStopped && elapsed >= FORCE_STOP_GRACE_MS) || elapsed >= RESTART_GRACE_MS) {
                    Log.i(
                        TAG,
                        "App $pkg stopped (forceStopped=$forceStopped, elapsed=${elapsed}ms); " +
                            "restoring Dual-Screen and stock assistant"
                    )
                    restoreDisplayAndStockAssistant()
                    System.exit(0)
                    return
                }

                mainHandler.postDelayed(this, WATCHDOG_POLL_INTERVAL_MS)
            }
        }

        private val shutdownRunnable = Runnable {
            System.exit(0)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            return try {
                data.enforceInterface(DESCRIPTOR)
                when (code) {
                    TRANSACTION_PING -> {
                        reply?.writeNoException()
                        reply?.writeInt(1)
                        true
                    }
                    TRANSACTION_SET_BRIGHTNESS -> {
                        val displayId = data.readInt()
                        val brightness = data.readFloat()
                        val temporary = data.readInt() != 0
                        setDisplayBrightnessInternal(displayId, brightness, temporary)
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_GET_BRIGHTNESS -> {
                        val displayId = data.readInt()
                        val info = getDisplayBrightnessInternal(displayId)
                        reply?.writeNoException()
                        reply?.writeFloatArray(info)
                        true
                    }
                    TRANSACTION_SET_PIN_SCREEN_MODE -> {
                        val mode = data.readInt()
                        setPinScreenModeInternal(mode)
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_SET_SYSTEM_PROP -> {
                        val key = data.readString() ?: ""
                        val value = data.readString() ?: ""
                        sysPropsSet.invoke(null, key, value)
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_GET_SYSTEM_PROP -> {
                        val key = data.readString() ?: ""
                        val def = data.readString() ?: ""
                        val result = sysPropsGet.invoke(null, key, def) as? String ?: def
                        reply?.writeNoException()
                        reply?.writeString(result)
                        true
                    }
                    TRANSACTION_FORCE_STOP_PACKAGE -> {
                        val pkg = data.readString() ?: ""
                        forceStopPackageInternal(pkg)
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_LOCK_SCREEN -> {
                        lockScreenInternal()
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_GET_SF_FLIPS -> {
                        val flips = getSurfaceFlingerFlipsInternal()
                        reply?.writeNoException()
                        reply?.writeInt(flips)
                        true
                    }
                    TRANSACTION_READ_FILE -> {
                        val path = data.readString() ?: ""
                        val content = try {
                            File(path).readText().trim()
                        } catch (_: Throwable) {
                            ""
                        }
                        reply?.writeNoException()
                        reply?.writeString(content)
                        true
                    }
                    TRANSACTION_PUT_SYSTEM_SETTING -> {
                        val type = data.readString() ?: "int"
                        val key = data.readString() ?: ""
                        val value = data.readString() ?: ""
                        if (key.isNotEmpty()) {
                            putSystemSettingInternal(key, value)
                        }
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_REGISTER_CLIENT -> {
                        val newClient = data.readStrongBinder()
                        val pkg = data.readString() ?: APP_PACKAGE_NAME
                        val replaceStock = data.readInt() != 0
                        val topBright = data.readFloat()
                        val bottomBright = data.readFloat()
                        registerClientInternal(newClient, pkg, replaceStock, topBright, bottomBright)
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_UPDATE_WATCHDOG_STATE -> {
                        replaceStockPreferred = data.readInt() != 0
                        val topBright = data.readFloat()
                        val bottomBright = data.readFloat()
                        if (topBright > 0f) savedTopBrightness = topBright
                        if (bottomBright > 0f) savedBottomBrightness = bottomBright
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_SHUTDOWN -> {
                        isShuttingDown = true
                        mainHandler.removeCallbacks(watchdogCheckRunnable)
                        mainHandler.removeCallbacks(onClientDiedRunnable)
                        unlinkClientDeath()
                        reply?.writeNoException()
                        mainHandler.postDelayed(shutdownRunnable, 50L)
                        true
                    }
                    else -> super.onTransact(code, data, reply, flags)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in HelperBinder transaction $code", t)
                reply?.writeException(Exception(t.message ?: "HelperBinder error"))
                true
            }
        }

        private fun registerClientInternal(
            newClient: IBinder?,
            pkg: String,
            replaceStock: Boolean,
            topBright: Float,
            bottomBright: Float
        ) {
            unlinkClientDeath()
            clientPackageName = pkg
            replaceStockPreferred = replaceStock
            if (topBright > 0f) savedTopBrightness = topBright
            if (bottomBright > 0f) savedBottomBrightness = bottomBright
            clientBinder = newClient
            mainHandler.removeCallbacks(watchdogCheckRunnable)
            mainHandler.removeCallbacks(onClientDiedRunnable)
            if (newClient != null) {
                try {
                    newClient.linkToDeath(clientDeathRecipient, 0)
                } catch (_: Throwable) {
                    mainHandler.post(onClientDiedRunnable)
                }
            }
        }

        private fun unlinkClientDeath() {
            val old = clientBinder
            clientBinder = null
            if (old != null) {
                try {
                    old.unlinkToDeath(clientDeathRecipient, 0)
                } catch (_: Throwable) {
                }
            }
        }

        private fun putSystemSettingInternal(key: String, value: String) {
            try {
                val extras = Bundle().apply {
                    putString("value", value)
                    putInt("_user", 0)
                }
                callProviderExternal("settings", "PUT_system", key, extras)
            } catch (_: Throwable) {
            }
        }

        private fun isPackageInstalledInternal(pkg: String): Boolean {
            if (!File("/data/data/$pkg").exists()) {
                return false
            }
            val appInfo = getApplicationInfoInternal(pkg)
            return appInfo != null
        }

        private fun isPackageForceStoppedInternal(pkg: String): Boolean {
            val appInfo = getApplicationInfoInternal(pkg) ?: return false
            return (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        }

        private fun getApplicationInfoInternal(pkg: String): ApplicationInfo? {
            val pm = packageManagerProxy ?: return null
            return try {
                val method = pm.javaClass.methods.firstOrNull { m ->
                    m.name == "getApplicationInfo" && m.parameterTypes.size == 3
                } ?: return null
                val flagsParam: Any = if (method.parameterTypes[1] == Long::class.javaPrimitiveType) {
                    0L
                } else {
                    0
                }
                method.invoke(pm, pkg, flagsParam, 0) as? ApplicationInfo
            } catch (_: Throwable) {
                null
            }
        }

        private fun isAppProcessRunningInternal(pkg: String): Boolean {
            val am = activityManagerProxy
            if (am != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val procs = am.javaClass.getMethod("getRunningAppProcesses")
                        .invoke(am) as? List<ActivityManager.RunningAppProcessInfo>
                    if (procs != null) {
                        return procs.any { it.processName == pkg }
                    }
                } catch (_: Throwable) {
                }
            }
            return execRootCommand("pidof $pkg").trim().isNotEmpty()
        }

        private fun execRootCommand(command: String): String {
            val ps = pServerBinder
            if (ps != null && ps.isBinderAlive) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    ps.transact(0, data, reply, 0)
                    val bytes = reply.createByteArray()
                    if (bytes != null) {
                        return String(bytes, StandardCharsets.UTF_8).trimEnd()
                    }
                } catch (_: Throwable) {
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
            return try {
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))
                val out = proc.inputStream.bufferedReader().readText().trimEnd()
                proc.waitFor()
                out
            } catch (_: Throwable) {
                ""
            }
        }

        /**
         * Restores Dual-Screen mode (`0`), unblanks any display whose brightness was set to `-1.0f`,
         * re-enables touch on both screens, and ensures the stock `com.odin.dualscreen.assistant`
         * is enabled and running.
         */
        private fun restoreDisplayAndStockAssistant() {
            try {
                // 1. Restore top (0) and bottom (4) display brightness if blanked (< 0.005f)
                val topCur = getDisplayBrightnessInternal(0).getOrElse(0) { 0.4f }
                if (topCur < 0.005f) {
                    val restoreTop = if (savedTopBrightness > 0.01f) savedTopBrightness else 0.4f
                    setDisplayBrightnessInternal(0, restoreTop, false)
                }
                val bottomCur = getDisplayBrightnessInternal(4).getOrElse(0) { 0.4f }
                if (bottomCur < 0.005f) {
                    val restoreBottom = if (savedBottomBrightness > 0.01f) savedBottomBrightness else 0.4f
                    setDisplayBrightnessInternal(4, restoreBottom, false)
                }

                // 2. Reset Dual-Screen mode (0), focus lock (0), touch disable ("0,0"), and pin screen mode (0)
                putSystemSettingInternal("dual_screen_display_mode", "0")
                putSystemSettingInternal("enable_system_key_focus_lock", "0")
                putSystemSettingInternal("screen_focus_lock", "0")
                try {
                    sysPropsSet.invoke(null, "sys.temp.disabled.touch", "0,0")
                } catch (_: Throwable) {
                }
                setPinScreenModeInternal(0)

                // 3. Re-enable and start stock DualScreenAssistant
                execRootCommand("pm enable $STOCK_PACKAGE_NAME")
                execRootCommand("pm enable $STOCK_PACKAGE_NAME/.services.TccServices")
                execRootCommand("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver")
                val pidOut = execRootCommand("pidof $STOCK_PACKAGE_NAME").trim()
                if (pidOut.isNotEmpty()) {
                    val firstPid = pidOut.split(Regex("\\s+")).firstOrNull().orEmpty()
                    val statOut = try {
                        if (firstPid.isNotEmpty()) File("/proc/$firstPid/stat").readText() else ""
                    } catch (_: Throwable) {
                        ""
                    }
                    if (replaceStockPreferred || statOut.contains(") T ")) {
                        execRootCommand("kill -9 $pidOut")
                    }
                }
                execRootCommand("am startservice -n $STOCK_PACKAGE_NAME/.services.TccServices")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed during restoreDisplayAndStockAssistant", t)
            }
        }

        private fun setDisplayBrightnessInternal(displayId: Int, brightness: Float, temporary: Boolean) {
            val dm = displayManagerProxy ?: return
            try {
                val methodName = if (temporary) "setTemporaryBrightness" else "setBrightness"
                val method = dm.javaClass.getMethod(
                    methodName,
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
                method.invoke(dm, displayId, brightness)
            } catch (t: Throwable) {
                Log.w(TAG, "setDisplayBrightnessInternal($displayId, $brightness) failed", t)
            }
        }

        private fun getDisplayBrightnessInternal(displayId: Int): FloatArray {
            val dm = displayManagerProxy ?: return floatArrayOf(0.5f, 0.0f, 1.0f)
            return try {
                val getInfoMethod = dm.javaClass.getMethod(
                    "getBrightnessInfo",
                    Int::class.javaPrimitiveType
                )
                val info = getInfoMethod.invoke(dm, displayId)
                if (info != null) {
                    val b = info.javaClass.getField("brightness").getFloat(info)
                    val min = info.javaClass.getField("brightnessMinimum").getFloat(info)
                    val max = info.javaClass.getField("brightnessMaximum").getFloat(info)
                    floatArrayOf(b, min, max)
                } else {
                    val getB = dm.javaClass.getMethod("getBrightness", Int::class.javaPrimitiveType)
                    val b = getB.invoke(dm, displayId) as? Float ?: 0.5f
                    floatArrayOf(b, 0.0f, 1.0f)
                }
            } catch (_: Throwable) {
                floatArrayOf(0.5f, 0.0f, 1.0f)
            }
        }

        private fun setPinScreenModeInternal(mode: Int) {
            val wm = windowManagerProxy ?: return
            try {
                val method = wm.javaClass.getMethod("setPinScreenMode", Int::class.javaPrimitiveType)
                method.invoke(wm, mode)
            } catch (t: Throwable) {
                Log.w(TAG, "setPinScreenMode($mode) failed", t)
            }
        }

        private fun forceStopPackageInternal(pkg: String) {
            val am = activityManagerProxy ?: return
            try {
                val method = am.javaClass.getMethod(
                    "forceStopPackage",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                method.invoke(am, pkg, 0)
            } catch (t: Throwable) {
                Log.w(TAG, "forceStopPackage($pkg) failed", t)
            }
        }

        private fun lockScreenInternal() {
            val pm = powerManagerProxy ?: return
            try {
                val method = pm.javaClass.getMethod(
                    "goToSleep",
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.invoke(pm, SystemClock.uptimeMillis(), 0, 0)
            } catch (t: Throwable) {
                Log.w(TAG, "goToSleep failed", t)
            }
        }

        private fun getSurfaceFlingerFlipsInternal(): Int {
            val sf = getServiceMethod.invoke(null, "SurfaceFlinger") as? IBinder ?: return -1
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken("android.ui.ISurfaceComposer")
                sf.transact(1013, data, reply, 0)
                reply.readInt()
            } catch (_: Throwable) {
                -1
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
