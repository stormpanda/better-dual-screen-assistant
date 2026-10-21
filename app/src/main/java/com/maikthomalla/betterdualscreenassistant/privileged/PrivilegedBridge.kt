package com.maikthomalla.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
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

    @Volatile
    private var helperBinder: IBinder? = null

    private val _isHelperConnected = MutableStateFlow(false)
    val isHelperConnected: StateFlow<Boolean> = _isHelperConnected.asStateFlow()

    fun onHelperBinderReceived(binder: IBinder) {
        try {
            binder.linkToDeath({
                Log.w(TAG, "PrivilegedHelperMain binder died")
                helperBinder = null
                _isHelperConnected.value = false
            }, 0)
        } catch (_: Throwable) {
        }
        helperBinder = binder
        _isHelperConnected.value = true
    }

    fun ensureInitialized(context: Context) = ensureStarted(context)

    fun ensureStarted(context: Context) {
        val current = helperBinder
        if (current != null && current.isBinderAlive && ping()) {
            _isHelperConnected.value = true
            return
        }
        if (!isSpawning.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                PServerClient.ensurePermissionsGranted(appContext)
                val apkPath = appContext.applicationInfo.sourceDir
                val cmd = "CLASSPATH=$apkPath /system/bin/app_process /system/bin " +
                    "com.maikthomalla.betterdualscreenassistant.privileged.PrivilegedHelperMain >/dev/null 2>&1 &"
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
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            binder.transact(PrivilegedHelperMain.TRANSACTION_PING, data, reply, 0)
            reply.readException()
            reply.readInt() == 1
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
     * Returns floatArrayOf(brightness, min, max) for [displayId].
     */
    fun getDisplayBrightnessInfo(context: Context, displayId: Int): FloatArray {
        // First try standard Display.getBrightnessInfo() via reflection (allowed for normal apps if readable, or via helper)
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
                if (arr != null && arr.size == 3) {
                    return arr
                }
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm?.getDisplay(displayId)
            if (display != null) {
                val info = display.javaClass.getMethod("getBrightnessInfo").invoke(display)
                if (info != null) {
                    val b = info.javaClass.getField("brightness").getFloat(info)
                    val min = info.javaClass.getField("brightnessMinimum").getFloat(info)
                    val max = info.javaClass.getField("brightnessMaximum").getFloat(info)
                    return floatArrayOf(b, min, max)
                }
            }
            floatArrayOf(0.5f, 0f, 1f)
        } catch (_: Throwable) {
            floatArrayOf(0.5f, 0f, 1f)
        }
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
        PServerClient.exec("setprop $key $value", wait = true)
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
        PServerClient.exec("am force-stop $packageName", wait = true)
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
        val start = out.indexOf("(")
        val end = out.indexOf(" ")
        if (start != -1 && end > start + 1) {
            return out.substring(start + 1, end).toIntOrNull(16) ?: -1
        }
        return -1
    }

    fun readSysfsFile(path: String): String {
        try {
            val file = File(path)
            if (file.canRead()) {
                return file.readText().trim()
            }
        } catch (_: Throwable) {
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
        return PServerClient.exec("cat $path", wait = true).getOrDefault("").trim()
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
        PServerClient.exec("settings put system $key $value", wait = true)
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
        PServerClient.exec("settings put system $key $value", wait = true)
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
        PServerClient.exec("settings put system $key \"$value\"", wait = true)
    }
}
