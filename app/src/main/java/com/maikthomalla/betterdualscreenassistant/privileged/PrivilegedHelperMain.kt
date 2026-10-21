package com.maikthomalla.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Root companion process launched via PServerBinder using `app_process`.
 * Delivers an in-memory [IBinder] to [PrivilegedBinderProvider] so our user-installed APK
 * can perform system-level Binder and sysfs operations at 120Hz with sub-millisecond latency.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object PrivilegedHelperMain {
    private const val TAG = "PrivilegedHelperMain"
    const val AUTHORITY = "com.maikthomalla.betterdualscreenassistant.privileged"
    const val METHOD_REGISTER_BINDER = "register_binder"
    const val EXTRA_BINDER = "binder"
    const val DESCRIPTOR = "com.maikthomalla.betterdualscreenassistant.IPrivilegedHelper"

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

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            if (Looper.myLooper() == null) {
                Looper.prepareMainLooper()
            }
            val binder = HelperBinder(null)
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

    class HelperBinder(private val systemContext: Context?) : Binder() {
        private val serviceManagerClass by lazy { Class.forName("android.os.ServiceManager") }
        private val getServiceMethod by lazy {
            serviceManagerClass.getMethod("getService", String::class.java)
        }

        private val displayManagerProxy: Any? by lazy {
            try {
                val binder = getServiceMethod.invoke(null, "display") as? IBinder ?: return@lazy null
                val stubClass = Class.forName("android.hardware.display.IDisplayManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to get IDisplayManager", t)
                null
            }
        }

        private val windowManagerProxy: Any? by lazy {
            try {
                val binder = getServiceMethod.invoke(null, "window") as? IBinder ?: return@lazy null
                val stubClass = Class.forName("android.view.IWindowManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to get IWindowManager", t)
                null
            }
        }

        private val activityManagerProxy: Any? by lazy {
            try {
                val binder = getServiceMethod.invoke(null, "activity") as? IBinder ?: return@lazy null
                val stubClass = Class.forName("android.app.IActivityManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to get IActivityManager", t)
                null
            }
        }

        private val powerManagerProxy: Any? by lazy {
            try {
                val binder = getServiceMethod.invoke(null, "power") as? IBinder ?: return@lazy null
                val stubClass = Class.forName("android.os.IPowerManager\$Stub")
                stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to get IPowerManager", t)
                null
            }
        }

        private val sysPropsClass by lazy { Class.forName("android.os.SystemProperties") }
        private val sysPropsSet by lazy {
            sysPropsClass.getMethod("set", String::class.java, String::class.java)
        }
        private val sysPropsGet by lazy {
            sysPropsClass.getMethod("get", String::class.java, String::class.java)
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
                            val extras = Bundle().apply {
                                putString("value", value)
                                putInt("_user", 0)
                            }
                            callProviderExternal("settings", "PUT_system", key, extras)
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

        private fun setDisplayBrightnessInternal(displayId: Int, brightness: Float, temporary: Boolean) {
            val dm = displayManagerProxy ?: return
            val methodName = if (temporary) "setTemporaryBrightness" else "setBrightness"
            val method = dm.javaClass.getMethod(
                methodName,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            method.invoke(dm, displayId, brightness)
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
