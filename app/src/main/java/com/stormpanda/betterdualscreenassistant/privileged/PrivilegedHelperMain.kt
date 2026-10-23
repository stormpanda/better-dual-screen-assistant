package com.stormpanda.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.AttributionSource
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
    const val TRANSACTION_APPLY_OS_THEME = IBinder.FIRST_CALL_TRANSACTION + 14
    const val TRANSACTION_CLEAR_OS_THEME = IBinder.FIRST_CALL_TRANSACTION + 15
    const val TRANSACTION_SWAP_DISPLAY_TASKS = IBinder.FIRST_CALL_TRANSACTION + 16
    const val TRANSACTION_SET_TOP_SCREEN_BLANKED = IBinder.FIRST_CALL_TRANSACTION + 17

    const val HELPER_PROTOCOL_VERSION = 8
    const val OS_THEME_OWNING_PACKAGE = "com.android.shell"
    const val OS_THEME_OVERLAY_NAME = "os_theme"
    const val SYSUI_PACKAGE = "com.android.systemui"
    private const val TYPED_VALUE_INT_COLOR_ARGB8 = 0x1c
    private const val KEY_THEME_CUSTOMIZATION_PACKAGES = "theme_customization_overlay_packages"

    // StatusBarManager.DISABLE_* flags that hide top-screen status bar elements without blocking hardware keys
    private const val STATUS_BAR_DISABLE_EXPAND = 0x00010000
    private const val STATUS_BAR_DISABLE_NOTIFICATION_ICONS = 0x00020000
    private const val STATUS_BAR_DISABLE_NOTIFICATION_ALERTS = 0x00040000
    private const val STATUS_BAR_DISABLE_SYSTEM_INFO = 0x00100000
    private const val STATUS_BAR_DISABLE_CLOCK = 0x00800000
    private const val STATUS_BAR_BLANK_MASK =
        STATUS_BAR_DISABLE_EXPAND or
            STATUS_BAR_DISABLE_NOTIFICATION_ICONS or
            STATUS_BAR_DISABLE_NOTIFICATION_ALERTS or
            STATUS_BAR_DISABLE_SYSTEM_INFO or
            STATUS_BAR_DISABLE_CLOCK

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

        private val activityTaskManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "activity_task") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IActivityTaskManager", t)
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

        private val overlayManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "overlay") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.content.om.IOverlayManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IOverlayManager", t)
            null
        }

        private val uiModeManagerProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "uimode") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.app.IUiModeManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IUiModeManager", t)
            null
        }

        private val statusBarServiceProxy: Any? = try {
            val binder = getServiceMethod.invoke(null, "statusbar") as? IBinder
            if (binder != null) {
                val stubClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to get IStatusBarService", t)
            null
        }

        private val statusBarDisableToken = Binder()
        private var topDisplayContext: Context? = null
        private var topBootProgressBlankView: View? = null

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
        private var savedStockNightMode: Int = -1

        @Volatile
        private var savedStockThemeJson: String = ""

        @Volatile
        private var osThemeOverlayActive: Boolean = false

        @Volatile
        private var lastOverlayResourceNames: Array<String> = emptyArray()

        @Volatile
        private var lastOverlayColorValues: IntArray = IntArray(0)

        private val reassertOverlayPriorityRunnable = Runnable {
            if (!osThemeOverlayActive || isShuttingDown) return@Runnable
            val names = lastOverlayResourceNames
            val values = lastOverlayColorValues
            if (names.isNotEmpty() && names.size == values.size) {
                commitOsThemeFabricatedOverlays(names, values)
            }
        }

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
                        reply?.writeInt(HELPER_PROTOCOL_VERSION)
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
                            when (type) {
                                "secure" -> putSecureSettingInternal(key, value)
                                "global" -> putGlobalSettingInternal(key, value)
                                else -> putSystemSettingInternal(key, value)
                            }
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
                    TRANSACTION_APPLY_OS_THEME -> {
                        val resourceNames = data.createStringArray() ?: emptyArray()
                        val colorValues = data.createIntArray() ?: IntArray(0)
                        val targetNightMode = data.readInt()
                        val savedNightMode = data.readInt()
                        val injectedThemeJson = data.readString() ?: ""
                        val savedThemeJson = data.readString() ?: ""
                        mainHandler.post {
                            applyOsThemeOverlayInternal(
                                resourceNames = resourceNames,
                                colorValues = colorValues,
                                targetNightMode = targetNightMode,
                                savedNightMode = savedNightMode,
                                injectedThemeJson = injectedThemeJson,
                                savedThemeJson = savedThemeJson
                            )
                        }
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_CLEAR_OS_THEME -> {
                        val restoreNightMode = data.readInt()
                        val restoreThemeJson = data.readString() ?: ""
                        mainHandler.post {
                            clearOsThemeOverlayInternal(restoreNightMode, restoreThemeJson)
                        }
                        reply?.writeNoException()
                        true
                    }
                    TRANSACTION_SWAP_DISPLAY_TASKS -> {
                        val swapped = swapDisplayTasksInternal()
                        reply?.writeNoException()
                        reply?.writeInt(if (swapped) 1 else 0)
                        true
                    }
                    TRANSACTION_SET_TOP_SCREEN_BLANKED -> {
                        val blank = data.readInt() != 0
                        val showCoverWindow = data.readInt() != 0
                        mainHandler.post {
                            setTopScreenBlankedInternal(blank, showCoverWindow)
                        }
                        reply?.writeNoException()
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

        private fun putSecureSettingInternal(key: String, value: String) {
            try {
                val extras = Bundle().apply {
                    putString("value", value)
                    putInt("_user", 0)
                }
                callProviderExternal("settings", "PUT_secure", key, extras)
            } catch (_: Throwable) {
            }
        }

        private fun putGlobalSettingInternal(key: String, value: String) {
            try {
                val extras = Bundle().apply {
                    putString("value", value)
                    putInt("_user", 0)
                }
                callProviderExternal("settings", "PUT_global", key, extras)
            } catch (_: Throwable) {
            }
        }

        private fun swapDisplayTasksInternal(): Boolean {
            val atm = activityTaskManagerProxy ?: return false
            return try {
                val getTasksMethod = atm.javaClass.getMethod(
                    "getAllRootTaskInfosOnDisplay",
                    Int::class.javaPrimitiveType
                )
                val moveTaskMethod = atm.javaClass.getMethod(
                    "moveRootTaskToDisplay",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val topTasks = getTasksMethod.invoke(atm, 0) as? List<*> ?: emptyList<Any?>()
                val bottomTasks = getTasksMethod.invoke(atm, 4) as? List<*> ?: emptyList<Any?>()

                val topTaskId = findTopStandardTaskId(topTasks)
                val bottomTaskId = findTopStandardTaskId(bottomTasks)

                when {
                    topTaskId > 0 && bottomTaskId > 0 -> {
                        moveTaskMethod.invoke(atm, topTaskId, 4)
                        moveTaskMethod.invoke(atm, bottomTaskId, 0)
                        true
                    }
                    topTaskId > 0 -> {
                        moveTaskMethod.invoke(atm, topTaskId, 4)
                        true
                    }
                    bottomTaskId > 0 -> {
                        moveTaskMethod.invoke(atm, bottomTaskId, 0)
                        true
                    }
                    else -> false
                }
            } catch (t: Throwable) {
                Log.w(TAG, "swapDisplayTasksInternal failed", t)
                false
            }
        }

        private fun findTopStandardTaskId(tasks: List<*>): Int {
            var fallbackId = -1
            for (task in tasks) {
                if (task == null) continue
                try {
                    val taskId = task.javaClass.getField("taskId").getInt(task)
                    if (taskId <= 0) continue
                    val topActivity =
                        task.javaClass.getField("topActivity").get(task) as? android.content.ComponentName
                            ?: continue
                    val pkg = topActivity.packageName
                    if (pkg == APP_PACKAGE_NAME ||
                        pkg == STOCK_PACKAGE_NAME ||
                        pkg == SYSUI_PACKAGE
                    ) {
                        continue
                    }
                    val config = task.javaClass.getField("configuration").get(task)
                    val winConfig = config?.javaClass?.getField("windowConfiguration")?.get(config)
                    val actType = winConfig?.javaClass?.getMethod("getActivityType")?.invoke(winConfig) as? Int ?: 0
                    if (actType != 1) {
                        // 1 == WindowConfiguration.ACTIVITY_TYPE_STANDARD (excludes home/recents)
                        continue
                    }
                    val visible = try {
                        task.javaClass.getField("visible").getBoolean(task)
                    } catch (_: Throwable) {
                        true
                    }
                    if (visible) {
                        return taskId
                    }
                    if (fallbackId < 0) {
                        fallbackId = taskId
                    }
                } catch (_: Throwable) {
                }
            }
            return fallbackId
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
                // 0. Remove any helper top-screen blank cover window and re-enable status bar
                setTopScreenBlankedInternal(blank = false, showCoverWindow = false)

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

                // 2.5. Restore stock OS theme overlay and night mode if injected
                clearOsThemeOverlayInternal(savedStockNightMode, savedStockThemeJson)

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

        private fun applyOsThemeOverlayInternal(
            resourceNames: Array<String>,
            colorValues: IntArray,
            targetNightMode: Int,
            savedNightMode: Int,
            @Suppress("UNUSED_PARAMETER") injectedThemeJson: String,
            savedThemeJson: String
        ) {
            mainHandler.removeCallbacks(reassertOverlayPriorityRunnable)
            if (savedNightMode == 1 || savedNightMode == 2) {
                savedStockNightMode = savedNightMode
            }
            if (savedThemeJson.isNotBlank() && !savedThemeJson.contains("\"999\"")) {
                savedStockThemeJson = savedThemeJson
            }
            val nightChanged = if (targetNightMode == 1 || targetNightMode == 2) {
                setNightModeInternal(targetNightMode)
            } else {
                false
            }
            if (resourceNames.isNotEmpty() && resourceNames.size == colorValues.size) {
                val samePalette = osThemeOverlayActive &&
                    lastOverlayResourceNames.contentEquals(resourceNames) &&
                    lastOverlayColorValues.contentEquals(colorValues)
                lastOverlayResourceNames = resourceNames
                lastOverlayColorValues = colorValues
                osThemeOverlayActive = true
                if (!samePalette || nightChanged) {
                    commitOsThemeFabricatedOverlays(resourceNames, colorValues)
                }
                if (nightChanged) {
                    mainHandler.postDelayed(reassertOverlayPriorityRunnable, 200L)
                }
            } else {
                lastOverlayResourceNames = emptyArray()
                lastOverlayColorValues = IntArray(0)
                unregisterOsThemeOverlayOnly()
                restoreStockThemeJsonInternal(savedThemeJson)
            }
        }

        private fun commitOsThemeFabricatedOverlays(
            resourceNames: Array<String>,
            colorValues: IntArray
        ) {
            val om = overlayManagerProxy ?: return
            try {
                val builderClass = Class.forName("android.content.om.FabricatedOverlay\$Builder")
                val builderCtor = builderClass.getConstructor(
                    String::class.java,
                    String::class.java,
                    String::class.java
                )
                val setTargetOverlayable = builderClass.getMethod(
                    "setTargetOverlayable",
                    String::class.java
                )
                val setResourceValue = builderClass.getMethod(
                    "setResourceValue",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val buildOverlay = builderClass.getMethod("build")

                val shellBuilder = builderCtor.newInstance(
                    OS_THEME_OWNING_PACKAGE,
                    OS_THEME_OVERLAY_NAME,
                    "android"
                )
                val neutralBuilder = builderCtor.newInstance(
                    SYSUI_PACKAGE,
                    "neutral",
                    "android"
                )
                val accentBuilder = builderCtor.newInstance(
                    SYSUI_PACKAGE,
                    "accent",
                    "android"
                )
                setTargetOverlayable.invoke(shellBuilder, "")
                setTargetOverlayable.invoke(neutralBuilder, "")
                setTargetOverlayable.invoke(accentBuilder, "")

                for (i in resourceNames.indices) {
                    val resName = resourceNames[i]
                    val colorVal = colorValues[i]
                    setResourceValue.invoke(
                        shellBuilder,
                        resName,
                        TYPED_VALUE_INT_COLOR_ARGB8,
                        colorVal
                    )
                    if (resName.contains("system_neutral")) {
                        setResourceValue.invoke(
                            neutralBuilder,
                            resName,
                            TYPED_VALUE_INT_COLOR_ARGB8,
                            colorVal
                        )
                    } else if (resName.contains("system_accent")) {
                        setResourceValue.invoke(
                            accentBuilder,
                            resName,
                            TYPED_VALUE_INT_COLOR_ARGB8,
                            colorVal
                        )
                    }
                }
                val shellOverlay = buildOverlay.invoke(shellBuilder)
                val neutralOverlay = buildOverlay.invoke(neutralBuilder)
                val accentOverlay = buildOverlay.invoke(accentBuilder)

                val getIdentifier = shellOverlay.javaClass.getMethod("getIdentifier")
                val shellId = getIdentifier.invoke(shellOverlay)
                val neutralId = getIdentifier.invoke(neutralOverlay)
                val accentId = getIdentifier.invoke(accentOverlay)

                val idClass = Class.forName("android.content.om.OverlayIdentifier")
                val idCtor = idClass.getConstructor(String::class.java, String::class.java)
                val legacyOverlayId = idCtor.newInstance(APP_PACKAGE_NAME, OS_THEME_OVERLAY_NAME)

                val txBuilderClass = Class.forName(
                    "android.content.om.OverlayManagerTransaction\$Builder"
                )
                val txBuilder = txBuilderClass.getConstructor().newInstance()
                val unregMethod = txBuilderClass.getMethod(
                    "unregisterFabricatedOverlay",
                    idClass
                )
                val regMethod = txBuilderClass.getMethod(
                    "registerFabricatedOverlay",
                    shellOverlay.javaClass
                )
                val setEnabledMethod = txBuilderClass.getMethod(
                    "setEnabled",
                    idClass,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val buildTx = txBuilderClass.getMethod("build")

                unregMethod.invoke(txBuilder, legacyOverlayId)
                regMethod.invoke(txBuilder, shellOverlay)
                regMethod.invoke(txBuilder, neutralOverlay)
                regMethod.invoke(txBuilder, accentOverlay)
                setEnabledMethod.invoke(txBuilder, shellId, true, 0)
                setEnabledMethod.invoke(txBuilder, neutralId, true, 0)
                setEnabledMethod.invoke(txBuilder, accentId, true, 0)
                val tx = buildTx.invoke(txBuilder)

                val commitMethod = om.javaClass.getMethod("commit", tx.javaClass)
                commitMethod.invoke(om, tx)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to commit FabricatedOverlay for OS theme", t)
            }
        }

        private fun clearOsThemeOverlayInternal(restoreNightMode: Int, restoreThemeJson: String) {
            mainHandler.removeCallbacks(reassertOverlayPriorityRunnable)
            lastOverlayResourceNames = emptyArray()
            lastOverlayColorValues = IntArray(0)
            unregisterOsThemeOverlayOnly()
            restoreStockThemeJsonInternal(restoreThemeJson)
            savedStockThemeJson = ""
            val nightToRestore = if (restoreNightMode == 1 || restoreNightMode == 2) {
                restoreNightMode
            } else {
                savedStockNightMode
            }
            if (nightToRestore == 1 || nightToRestore == 2) {
                setNightModeInternal(nightToRestore)
            }
            savedStockNightMode = -1
        }

        private fun restoreStockThemeJsonInternal(candidateJson: String) {
            val raw = if (candidateJson.isNotBlank() && !candidateJson.contains("\"999\"")) {
                candidateJson
            } else {
                savedStockThemeJson
            }
            val now = System.currentTimeMillis()
            val fallback = """{"_applied_timestamp":$now,"android.theme.customization.color_index":"5","android.theme.customization.system_palette":"96CBFF","android.theme.customization.accent_color":"96CBFF","android.theme.customization.color_source":"preset","android.theme.customization.theme_style":"RAINBOW"}"""
            val jsonToApply = if (raw.isNotBlank() && !raw.contains("\"999\"")) {
                try {
                    val obj = org.json.JSONObject(raw)
                    obj.put("_applied_timestamp", now)
                    obj.toString()
                } catch (_: Throwable) {
                    fallback
                }
            } else {
                fallback
            }
            putSecureSettingInternal(KEY_THEME_CUSTOMIZATION_PACKAGES, jsonToApply)
        }

        private fun unregisterOsThemeOverlayOnly() {
            mainHandler.removeCallbacks(reassertOverlayPriorityRunnable)
            val om = overlayManagerProxy ?: return
            try {
                val idClass = Class.forName("android.content.om.OverlayIdentifier")
                val idCtor = idClass.getConstructor(String::class.java, String::class.java)
                val shellOverlayId = idCtor.newInstance(OS_THEME_OWNING_PACKAGE, OS_THEME_OVERLAY_NAME)
                val legacyOverlayId = idCtor.newInstance(APP_PACKAGE_NAME, OS_THEME_OVERLAY_NAME)

                val txBuilderClass = Class.forName(
                    "android.content.om.OverlayManagerTransaction\$Builder"
                )
                val txBuilder = txBuilderClass.getConstructor().newInstance()
                val unregMethod = txBuilderClass.getMethod(
                    "unregisterFabricatedOverlay",
                    idClass
                )
                val buildTx = txBuilderClass.getMethod("build")

                unregMethod.invoke(txBuilder, shellOverlayId)
                unregMethod.invoke(txBuilder, legacyOverlayId)
                val tx = buildTx.invoke(txBuilder)

                val commitMethod = om.javaClass.getMethod("commit", tx.javaClass)
                commitMethod.invoke(om, tx)
                osThemeOverlayActive = false
            } catch (t: Throwable) {
                Log.w(TAG, "unregisterOsThemeOverlayOnly failed", t)
            }
        }

        private fun setNightModeInternal(mode: Int): Boolean {
            val uim = uiModeManagerProxy
            if (uim != null) {
                try {
                    val getNightMode = uim.javaClass.getMethod("getNightMode")
                    val current = getNightMode.invoke(uim) as? Int ?: -1
                    if (current == mode) return false
                    val setNightMode = uim.javaClass.getMethod(
                        "setNightMode",
                        Int::class.javaPrimitiveType
                    )
                    setNightMode.invoke(uim, mode)
                    return true
                } catch (t: Throwable) {
                    Log.w(TAG, "IUiModeManager.setNightMode($mode) failed, using fallback", t)
                }
            }
            val cmdArg = if (mode == 2) "yes" else "no"
            execRootCommand("cmd uimode night $cmdArg")
            return true
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

        private fun getOrCreateDisplay0Context(): Context? {
            topDisplayContext?.let { return it }
            return try {
                try {
                    Class.forName("android.view.ThreadedRenderer")
                        .getMethod("disable", Boolean::class.javaPrimitiveType)
                        .invoke(null, true)
                } catch (_: Throwable) {
                }
                val atClass = Class.forName("android.app.ActivityThread")
                val currentAt = atClass.getMethod("currentActivityThread").invoke(null)
                    ?: atClass.getMethod("systemMain").invoke(null)
                val sysCtx = (systemContext ?: atClass.getMethod("getSystemContext").invoke(currentAt) as? Context)
                    ?: return null
                try {
                    sysCtx.applicationInfo?.let { appInfo ->
                        appInfo.flags = appInfo.flags and ApplicationInfo.FLAG_HARDWARE_ACCELERATED.inv()
                    }
                } catch (_: Throwable) {
                }
                val dm = sysCtx.getSystemService(DisplayManager::class.java)
                val display0 = dm?.getDisplay(0)
                val ctx = if (display0 != null) sysCtx.createDisplayContext(display0) else sysCtx
                topDisplayContext = ctx
                ctx
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to obtain Display 0 context in helper", t)
                null
            }
        }

        private fun setStatusBarBlankDisabledInternal(disabled: Boolean) {
            val sbs = statusBarServiceProxy ?: return
            try {
                val flags = if (disabled) STATUS_BAR_BLANK_MASK else 0
                val disableMethod = sbs.javaClass.methods.firstOrNull { m ->
                    m.name == "disable" && m.parameterTypes.size == 3
                }
                if (disableMethod != null) {
                    disableMethod.invoke(sbs, flags, statusBarDisableToken, "android")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "setStatusBarBlankDisabledInternal($disabled) failed", t)
            }
        }

        /**
         * Ensures the top screen (Display 0) has all status bar and gesture bar elements hidden:
         * 1. Disables status bar clock, notification icons, system icons, and heads-up alerts via `IStatusBarService`.
         * 2. Attaches a `type = 2021` (`TYPE_BOOT_PROGRESS`, Layer 27 — above `TYPE_STATUS_BAR` Layer 15 and
         *    `TYPE_NAVIGATION_BAR` Layer 24, matching stock `ScreenUtil.c()`) solid black cover window when
         *    [showCoverWindow] is true.
         */
        private fun setTopScreenBlankedInternal(blank: Boolean, showCoverWindow: Boolean) {
            setStatusBarBlankDisabledInternal(blank)
            val ctx = getOrCreateDisplay0Context() ?: return
            val wm = ctx.getSystemService(WindowManager::class.java) ?: return
            if (blank && showCoverWindow) {
                if (topBootProgressBlankView != null) return
                val view = object : View(ctx) {
                    override fun onAttachedToWindow() {
                        super.onAttachedToWindow()
                        @Suppress("DEPRECATION")
                        systemUiVisibility = 4102 or
                            SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    }

                    override fun onDraw(canvas: Canvas) {
                        canvas.drawColor(Color.BLACK)
                    }
                }.apply {
                    setBackgroundColor(Color.BLACK)
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    2021, // TYPE_BOOT_PROGRESS (Layer 27, above StatusBar & NavigationBar)
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    PixelFormat.OPAQUE
                ).apply {
                    gravity = Gravity.CENTER
                    title = "primaryScreenTopLayout"
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                try {
                    wm.addView(view, lp)
                    topBootProgressBlankView = view
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to attach type=2021 top blank cover window", t)
                }
            } else {
                val existing = topBootProgressBlankView ?: return
                topBootProgressBlankView = null
                try {
                    wm.removeViewImmediate(existing)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
