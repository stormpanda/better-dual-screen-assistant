package com.stormpanda.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.nio.charset.StandardCharsets

/**
 * Client for the AYN Thor vendor root execution binder service ("PServerBinder").
 *
 * Protocol reverse-engineered from J0.c in com.odin.dualscreen.assistant:
 * - Service name: "PServerBinder"
 * - Transaction code: 0
 * - Request parcel: writeStringArray(arrayOf(command, waitFlag)) where waitFlag is "1" or "0"
 * - Reply parcel: createByteArray() stdout UTF-8 bytes
 */
object PServerClient {
    private const val TAG = "PServerClient"
    private const val SERVICE_NAME = "PServerBinder"
    const val STOCK_PACKAGE_NAME = "com.odin.dualscreen.assistant"
    private const val PREFS_NAME = "better_dsa_prefs"
    private const val KEY_REPLACE_STOCK = "replace_stock_assistant"
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val stockAssistantLock = Any()

    @Volatile
    private var fallbackUnfrozenStockPid: String = ""

    fun hasFallbackUnfrozenStockPid(): Boolean = fallbackUnfrozenStockPid.isNotEmpty()

    /**
     * Safely quotes a single argument for POSIX `/system/bin/sh` by wrapping in single quotes
     * and escaping any embedded single quotes as `'\''`.
     */
    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    fun isValidPackageName(pkg: String): Boolean = PACKAGE_NAME_REGEX.matches(pkg)

    private fun parseNumericPids(raw: String): List<String> =
        raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.all(Char::isDigit) }

    @Volatile
    private var cachedBinder: IBinder? = null

    @SuppressLint("PrivateApi")
    private fun getBinder(): IBinder? {
        val current = cachedBinder
        if (current != null && current.isBinderAlive) {
            return current
        }
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, SERVICE_NAME) as? IBinder
            cachedBinder = binder
            binder
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to obtain $SERVICE_NAME binder", t)
            null
        }
    }

    fun isAvailable(): Boolean = getBinder() != null

    fun exec(command: String, wait: Boolean = true): Result<String> {
        val binder = getBinder()
            ?: return Result.failure(IllegalStateException("$SERVICE_NAME is not available"))
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, if (wait) "1" else "0"))
            binder.transact(0, data, reply, 0)
            val bytes = reply.createByteArray()
            val output = if (bytes != null) {
                String(bytes, StandardCharsets.UTF_8).trimEnd()
            } else {
                ""
            }
            Result.success(output)
        } catch (t: Throwable) {
            Log.e(TAG, "Error executing command via $SERVICE_NAME: $command", t)
            Result.failure(t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun ensurePermissionsGranted(context: Context) {
        val pkg = context.packageName
        if (!isValidPackageName(pkg)) return
        val qPkg = shellQuote(pkg)
        val commands = listOf(
            "appops set $qPkg SYSTEM_ALERT_WINDOW allow",
            "appops set $qPkg WRITE_SETTINGS allow",
            "appops set $qPkg GET_USAGE_STATS allow",
            "appops set --uid $qPkg SYSTEM_ALERT_WINDOW allow",
            "appops set --uid $qPkg WRITE_SETTINGS allow",
            "pm grant $qPkg android.permission.WRITE_SECURE_SETTINGS",
            "pm grant $qPkg android.permission.PACKAGE_USAGE_STATS",
            "pm grant $qPkg android.permission.POST_NOTIFICATIONS",
            "pm grant $qPkg android.permission.BLUETOOTH_CONNECT",
            "dumpsys deviceidle whitelist +$pkg"
        )
        for (cmd in commands) {
            exec(cmd, wait = true)
        }
    }

    fun isReplaceStockPreferred(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_REPLACE_STOCK, true)
    }

    fun setReplaceStockPreferred(context: Context, replace: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REPLACE_STOCK, replace)
            .apply()
    }

    fun isStockAssistantEnabled(context: Context): Boolean = synchronized(stockAssistantLock) {
        val pkgEnabled = try {
            val state = context.packageManager.getApplicationEnabledSetting(STOCK_PACKAGE_NAME)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        } catch (_: Throwable) {
            val out = exec("pm list packages -e $STOCK_PACKAGE_NAME", wait = true).getOrDefault("")
            out.contains(STOCK_PACKAGE_NAME)
        }
        val pidOut = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("")
        val firstPid = parseNumericPids(pidOut).firstOrNull().orEmpty()
        if (firstPid.isNotEmpty()) {
            val statOut = exec("cat /proc/$firstPid/stat", wait = true).getOrDefault("")
            // Format: pid (comm) state ... -> if state is NOT 'T', the persistent process is still running!
            if (!statOut.contains(") T ")) {
                return true
            }
            return false
        }
        return pkgEnabled
    }

    /**
     * Enables or disables the stock `com.odin.dualscreen.assistant` ephemerally.
     *
     * Note: Because `com.odin.dualscreen.assistant` has `android:persistent="true"`,
     * `am force-stop` is ignored by ActivityManagerService, and `ThisApplication.onCreate()`
     * only attempts `bindService(TccServices, BIND_AUTO_CREATE)` once at process startup.
     *
     * To suppress the stock app **ephemerally** (without leaving it disabled on disk in
     * `PackageManager` across uninstalls or reboots):
     * 1. Transiently disable `TccServices` in `PackageManager` and ensure the package itself
     *    is enabled (recovering from any legacy `pm disable-user` state).
     * 2. Kill (`SIGKILL`) the existing stock PID so `WindowManagerService` immediately destroys
     *    any attached `com.odin.dualscreen.assistant` windows and unbinds `TccServices`.
     * 3. Wait ~350ms for `ActivityManagerService` to respawn the persistent process (whose
     *    `ThisApplication.onCreate()` fails to bind the transiently disabled `TccServices`).
     * 4. Suspend the fresh windowless PID in memory with `SIGSTOP` (`kill -STOP`).
     * 5. Immediately re-enable `TccServices` (and the package) in `PackageManager` so the
     *    stock app is never left disabled on disk.
     */
    fun setStockAssistantEnabled(enabled: Boolean, context: Context? = null): Boolean = synchronized(stockAssistantLock) {
        fallbackUnfrozenStockPid = ""
        context?.let {
            setReplaceStockPreferred(it, !enabled)
            PrivilegedBridge.setReplaceStockPreferred(!enabled)
        }
        return if (enabled) {
            val shouldClearOsTheme = if (context != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getBoolean("inject_os_theme", false) ||
                    prefs.contains("saved_os_night_mode") ||
                    prefs.contains("saved_os_theme_json")
            } else {
                true
            }
            if (shouldClearOsTheme) {
                PrivilegedBridge.clearOsThemeOverlay(context, -1)
            }
            exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver", wait = true)
            val pids = parseNumericPids(exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault(""))
            if (pids.isNotEmpty()) {
                exec("kill -9 ${pids.joinToString(" ")}", wait = true)
            }
            val r = exec("am startservice -n $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            r.isSuccess
        } else {
            exec("am stopservice -n $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm disable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
            val oldPids = parseNumericPids(exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault(""))
            if (oldPids.isNotEmpty()) {
                exec("kill -9 ${oldPids.joinToString(" ")}", wait = true)
                try {
                    Thread.sleep(350L)
                } catch (_: InterruptedException) {
                }
            }
            val newPids = parseNumericPids(exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault(""))
            if (newPids.isNotEmpty()) {
                exec("kill -STOP ${newPids.joinToString(" ")}", wait = true)
            }
            // Immediately re-enable in PackageManager so the stock app is never left disabled on disk
            exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
            val rEnable = exec("pm enable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver", wait = true)
            val verifyPids = parseNumericPids(exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault(""))
            if (verifyPids.isNotEmpty()) {
                exec("kill -STOP ${verifyPids.joinToString(" ")}", wait = true)
            }
            rEnable.isSuccess
        }
    }

    /**
     * Ensures that if a previous version left the stock package or service disabled in
     * `PackageManager` on disk while the process is already frozen, it is re-enabled on disk.
     */
    fun ensureStockPackageEnabledOnDisk(context: Context) = synchronized(stockAssistantLock) {
        try {
            val appState = context.packageManager.getApplicationEnabledSetting(STOCK_PACKAGE_NAME)
            if (appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            ) {
                exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
                exec("pm enable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
                exec("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver", wait = true)
                val pids = parseNumericPids(exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault(""))
                if (pids.isNotEmpty()) {
                    exec("kill -STOP ${pids.joinToString(" ")}", wait = true)
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Fallback via `PServerBinder` when [PrivilegedHelperMain] is not yet connected during an
     * early ordered screen broadcast (`ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`).
     */
    fun setStockProcessFrozenForBroadcastFallback(freeze: Boolean) = synchronized(stockAssistantLock) {
        if (!freeze) {
            val pidOut = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("")
            val firstPid = parseNumericPids(pidOut).firstOrNull().orEmpty()
            if (firstPid.isNotEmpty()) {
                val statOut = exec("cat /proc/$firstPid/stat", wait = true).getOrDefault("")
                if (statOut.contains(") T ")) {
                    fallbackUnfrozenStockPid = firstPid
                    exec("kill -CONT $firstPid", wait = true)
                }
            }
        } else {
            val pid = fallbackUnfrozenStockPid
            fallbackUnfrozenStockPid = ""
            if (pid.isNotEmpty()) {
                val currentPids = parseNumericPids(
                    exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("")
                )
                if (pid in currentPids) {
                    exec("kill -STOP $pid", wait = true)
                }
            }
        }
    }
}
