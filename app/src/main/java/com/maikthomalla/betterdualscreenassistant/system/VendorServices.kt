package com.maikthomalla.betterdualscreenassistant.system

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Wrappers for AYN / Odin vendor binder services:
 * 1. [FanControlClient]: "SettingsController" -> com.ro.settings.IExternalControlManager -> IFanControlProvider
 * 2. [OverviewProxyClient]: "android.intent.action.QUICKSTEP_SERVICE" -> IOverviewProxy (opens Recents on Display 4)
 * 3. [MappingServiceClient]: "com.ro.mapping.action.MAPPING_SERVICE" -> IMappingService (detects HOME key press)
 */
@SuppressLint("PrivateApi")
object FanControlClient {
    private const val TAG = "FanControlClient"
    private const val EXTERNAL_CONTROL_DESCRIPTOR = "com.ro.settings.IExternalControlManager"
    private const val FAN_PROVIDER_DESCRIPTOR = "com.ro.settings.IFanControlProvider"

    private fun getFanProviderBinder(): IBinder? {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val controllerBinder = getService.invoke(null, "SettingsController") as? IBinder ?: return null
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(EXTERNAL_CONTROL_DESCRIPTOR)
                controllerBinder.transact(2, data, reply, 0)
                reply.readException()
                reply.readStrongBinder()
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to obtain IFanControlProvider", t)
            null
        }
    }

    /**
     * Transaction 1 on IFanControlProvider: returns intArrayOf(minSpeed, maxSpeed), default [35, 100].
     */
    fun getSpeedRange(): IntArray {
        val binder = getFanProviderBinder() ?: return intArrayOf(35, 100)
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FAN_PROVIDER_DESCRIPTOR)
            binder.transact(1, data, reply, 0)
            reply.readException()
            val arr = reply.createIntArray()
            if (arr != null && arr.size >= 2) arr else intArrayOf(35, 100)
        } catch (_: Throwable) {
            intArrayOf(35, 100)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Transaction 2 on IFanControlProvider: returns raw fan speed value.
     */
    fun getFanSpeedValue(): Int {
        val binder = getFanProviderBinder() ?: return 0
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FAN_PROVIDER_DESCRIPTOR)
            binder.transact(2, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (_: Throwable) {
            0
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Transaction 3 on IFanControlProvider: returns fan speed percentage in [0.0f..1.0f].
     */
    fun getSpeedPercentage(): Float {
        val binder = getFanProviderBinder() ?: return 0f
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FAN_PROVIDER_DESCRIPTOR)
            binder.transact(3, data, reply, 0)
            reply.readException()
            reply.readFloat()
        } catch (_: Throwable) {
            0f
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

class OverviewProxyClient(private val context: Context) {
    companion object {
        private const val TAG = "OverviewProxyClient"
        private const val DESCRIPTOR = "com.android.systemui.shared.recents.IOverviewProxy"
        private const val TRANSACTION_ON_OVERVIEW_SHOWN = 6
    }

    @Volatile
    private var overviewBinder: IBinder? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            overviewBinder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            overviewBinder = null
            isBound = false
        }
    }

    private fun resolveQuickstepPackage(): String {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val homePkg = resolveInfo?.activityInfo?.packageName
        if (!homePkg.isNullOrEmpty()) {
            val qsIntent = Intent("android.intent.action.QUICKSTEP_SERVICE").setPackage(homePkg)
            if (context.packageManager.resolveService(qsIntent, 0) != null) {
                return homePkg
            }
        }
        return "com.android.launcher3"
    }

    fun bind() {
        try {
            val pkg = resolveQuickstepPackage()
            val intent = Intent("android.intent.action.QUICKSTEP_SERVICE").setPackage(pkg)
            isBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to bind QUICKSTEP_SERVICE", t)
        }
    }

    fun unbind() {
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (_: Throwable) {
            }
            isBound = false
            overviewBinder = null
        }
    }

    fun toggleRecentAppsOnBottomScreen() {
        showOverviewOnDisplay(4)
    }

    fun showOverviewOnDisplay(displayId: Int) {
        val binder = overviewBinder
        if (binder == null || !binder.isBinderAlive) {
            bind()
        }
        val activeBinder = overviewBinder
        if (activeBinder != null && activeBinder.isBinderAlive) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(displayId)
                data.writeString(context.packageName)
                activeBinder.transact(TRANSACTION_ON_OVERVIEW_SHOWN, data, null, IBinder.FLAG_ONEWAY)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to transact IOverviewProxy.onOverviewShown", t)
            } finally {
                data.recycle()
            }
        }
        // Fallback via input keyevent APP_SWITCH (187) on target display
        com.maikthomalla.betterdualscreenassistant.privileged.PServerClient.exec(
            "input -d $displayId keyevent 187",
            wait = false
        )
    }
}

class MappingServiceClient(
    private val context: Context,
    private val onHomePressed: () -> Unit = {}
) {
    @Volatile
    var isHomeMapped: Boolean = false
        private set
    companion object {
        private const val TAG = "MappingServiceClient"
        private const val MAPPING_DESCRIPTOR = "com.ro.mapping.IMappingService"
        private const val LISTENER_DESCRIPTOR = "com.ro.mapping.IKeyEventListener"
        private const val INPUT_ID_HOME = 3
    }

    private var mappingBinder: IBinder? = null
    private var isBound = false

    private val keyListenerBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(LISTENER_DESCRIPTOR)
                return true
            }
            if (code == FIRST_CALL_TRANSACTION) {
                data.enforceInterface(LISTENER_DESCRIPTOR)
                val inputId = data.readInt()
                val action = data.readInt()
                if (inputId == INPUT_ID_HOME && action == 0) {
                    onHomePressed()
                }
                reply?.writeNoException()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mappingBinder = service
            if (service != null) {
                registerKeyListener(service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mappingBinder = null
            isBound = false
        }
    }

    fun bind() {
        try {
            val intent = Intent("com.ro.mapping.action.MAPPING_SERVICE")
                .setPackage("com.odin.gameassistant")
            isBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to bind MAPPING_SERVICE", t)
        }
    }

    fun unbind() {
        val service = mappingBinder
        if (service != null && service.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(MAPPING_DESCRIPTOR)
                data.writeStrongBinder(keyListenerBinder)
                service.transact(2, data, reply, 0)
            } catch (_: Throwable) {
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (_: Throwable) {
            }
            isBound = false
            mappingBinder = null
        }
    }

    private fun registerKeyListener(service: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MAPPING_DESCRIPTOR)
            data.writeStrongBinder(keyListenerBinder)
            service.transact(1, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register IKeyEventListener", t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
