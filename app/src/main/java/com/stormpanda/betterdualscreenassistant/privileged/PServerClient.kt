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
        val commands = listOf(
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "appops set $pkg WRITE_SETTINGS allow",
            "appops set $pkg GET_USAGE_STATS allow",
            "appops set --uid $pkg SYSTEM_ALERT_WINDOW allow",
            "appops set --uid $pkg WRITE_SETTINGS allow",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "pm grant $pkg android.permission.READ_LOGS",
            "pm grant $pkg android.permission.DUMP",
            "pm grant $pkg android.permission.PACKAGE_USAGE_STATS",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "pm grant $pkg android.permission.BLUETOOTH_CONNECT",
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

    fun isStockAssistantEnabled(context: Context): Boolean {
        val pkgEnabled = try {
            val state = context.packageManager.getApplicationEnabledSetting(STOCK_PACKAGE_NAME)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        } catch (_: Throwable) {
            val out = exec("pm list packages -e $STOCK_PACKAGE_NAME", wait = true).getOrDefault("")
            out.contains(STOCK_PACKAGE_NAME)
        }
        val pidOut = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("").trim()
        val firstPid = pidOut.split(Regex("\\s+")).firstOrNull().orEmpty()
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
    fun setStockAssistantEnabled(enabled: Boolean, context: Context? = null): Boolean {
        context?.let {
            setReplaceStockPreferred(it, !enabled)
            PrivilegedBridge.setReplaceStockPreferred(!enabled)
        }
        return if (enabled) {
            exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver", wait = true)
            val pidOut = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("").trim()
            if (pidOut.isNotEmpty()) {
                exec("kill -9 $pidOut", wait = true)
            }
            val r = exec("am startservice -n $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            r.isSuccess
        } else {
            exec("am stopservice -n $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm disable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
            val oldPid = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("").trim()
            if (oldPid.isNotEmpty()) {
                exec("kill -9 $oldPid", wait = true)
                try {
                    Thread.sleep(350L)
                } catch (_: InterruptedException) {
                }
            }
            val newPid = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("").trim()
            if (newPid.isNotEmpty()) {
                exec("kill -STOP $newPid", wait = true)
            }
            // Immediately re-enable in PackageManager so the stock app is never left disabled on disk
            exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
            val rEnable = exec("pm enable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
            exec("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver", wait = true)
            val verifyPid = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("").trim()
            if (verifyPid.isNotEmpty()) {
                exec("kill -STOP $verifyPid", wait = true)
            }
            rEnable.isSuccess
        }
    }

    /**
     * Ensures that if a previous version left the stock package or service disabled in
     * `PackageManager` on disk while the process is already frozen, it is re-enabled on disk.
     */
    fun ensureStockPackageEnabledOnDisk(context: Context) {
        try {
            val appState = context.packageManager.getApplicationEnabledSetting(STOCK_PACKAGE_NAME)
            if (appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            ) {
                exec("pm enable $STOCK_PACKAGE_NAME", wait = true)
                exec("pm enable $STOCK_PACKAGE_NAME/.services.TccServices", wait = true)
                exec("pm enable $STOCK_PACKAGE_NAME/.receivers.BootBroadcastReceiver", wait = true)
                val pid = exec("pidof $STOCK_PACKAGE_NAME", wait = true).getOrDefault("").trim()
                if (pid.isNotEmpty()) {
                    exec("kill -STOP $pid", wait = true)
                }
            }
        } catch (_: Throwable) {
        }
    }
}
