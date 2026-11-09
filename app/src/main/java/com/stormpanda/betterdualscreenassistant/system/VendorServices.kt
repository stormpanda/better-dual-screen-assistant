package com.stormpanda.betterdualscreenassistant.system

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

    @Volatile
    private var cachedFanProvider: IBinder? = null

    private fun getFanProviderBinder(): IBinder? {
        val current = cachedFanProvider
        if (current != null && current.isBinderAlive) {
            return current
        }
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val controllerBinder = getService.invoke(null, "SettingsController") as? IBinder ?: return null
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                // Replicate Y0.a$a$a.m("FanProvider"): transact(1) with String arg "FanProvider"
                data.writeInterfaceToken(EXTERNAL_CONTROL_DESCRIPTOR)
                data.writeString("FanProvider")
                controllerBinder.transact(1, data, reply, 0)
                reply.readException()
                val provider = reply.readStrongBinder()
                cachedFanProvider = provider
                provider
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to obtain IFanControlProvider", t)
            cachedFanProvider = null
            null
        }
    }

    /**
     * Transaction 1 (c()) on IFanControlProvider: returns min custom fan speed.
     */
    fun getMinSpeed(): Int {
        val binder = getFanProviderBinder() ?: return 35
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FAN_PROVIDER_DESCRIPTOR)
            binder.transact(1, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read min fan speed", t)
            35
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Transaction 2 (a()) on IFanControlProvider: returns max custom fan speed.
     */
    fun getMaxSpeed(): Int {
        val binder = getFanProviderBinder() ?: return 100
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FAN_PROVIDER_DESCRIPTOR)
            binder.transact(2, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read max fan speed", t)
            100
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Returns intArrayOf(minSpeed, maxSpeed) from transactions 1 and 2 on IFanControlProvider.
     */
    fun getSpeedRange(): IntArray {
        val minSpeed = getMinSpeed()
        val maxSpeed = getMaxSpeed()
        return if (maxSpeed > minSpeed && maxSpeed > 0) {
            intArrayOf(minSpeed, maxSpeed)
        } else {
            intArrayOf(35, 100)
        }
    }

    /**
     * Transaction 10 (e()) on IFanControlProvider: returns raw fan speed value.
     */
    fun getFanSpeedValue(): Int {
        val binder = getFanProviderBinder() ?: return 0
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FAN_PROVIDER_DESCRIPTOR)
            binder.transact(10, data, reply, 0)
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
     * Transaction 3 (h()) on IFanControlProvider: returns fan speed percentage in [0.0f..1.0f].
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
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read fan speed percentage", t)
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
        internal const val DESCRIPTOR = "com.android.systemui.shared.recents.IOverviewProxy"
        internal const val DESCRIPTOR_LAUNCHER_PROXY = "com.android.systemui.shared.recents.ILauncherProxy"
        private const val TRANSACTION_ON_OVERVIEW_SHOWN = 6

        internal fun descriptorForSdk(sdkInt: Int = android.os.Build.VERSION.SDK_INT): String =
            if (sdkInt >= 36) DESCRIPTOR_LAUNCHER_PROXY else DESCRIPTOR
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
        showOverviewOnDisplay(DeviceProfile.current(context).bottomDisplayId)
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
                data.writeInterfaceToken(descriptorForSdk())
                data.writeInt(displayId)
                data.writeString(context.packageName)
                activeBinder.transact(TRANSACTION_ON_OVERVIEW_SHOWN, data, null, IBinder.FLAG_ONEWAY)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to transact OverviewProxy.onOverviewShown", t)
            } finally {
                data.recycle()
            }
        }
        // Fallback via input keyevent APP_SWITCH (187) on target display
        com.stormpanda.betterdualscreenassistant.privileged.PServerClient.exec(
            "input -d $displayId keyevent 187",
            wait = false
        )
    }
}

class MappingServiceClient(
    private val context: Context,
    private val onHomePressed: () -> Boolean = { false }
) {
    @Volatile
    var isHomeMapped: Boolean = false
        private set

    companion object {
        private const val TAG = "MappingServiceClient"
        private const val ACTION_MAPPING_SERVICE = "com.ro.mapping.action.MAPPING_SERVICE"
        private const val MAPPING_PACKAGE = "com.odin.mapping"
        internal const val MAPPING_DESCRIPTOR = "com.ro.mapping.sdk.IServerApi"
        internal const val LISTENER_DESCRIPTOR = "com.ro.mapping.sdk.listener.OnInputEventListener"
        internal const val TRANSACTION_REGISTER_INPUT_LISTENER = 2
        internal const val TRANSACTION_UNREGISTER_INPUT_LISTENER = 3
        internal const val INPUT_ID_HOME = "HOME"
        internal const val INPUT_ID_BACK = "BACK"
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
                val hasEvent = data.readInt() != 0
                var consumed = false
                if (hasEvent) {
                    val inputId = data.readString()
                    val value1 = data.readInt().toShort()
                    data.readInt() // value2
                    data.readInt() // fd
                    data.readInt() // type
                    data.readInt() // code
                    data.readInt() // value
                    val shouldDismiss = inputId == INPUT_ID_HOME ||
                        (inputId == INPUT_ID_BACK && DeviceProfile.current(context).dismissOverlayOnBackMapping)
                    if (shouldDismiss && value1.toInt() != 0) {
                        consumed = onHomePressed()
                    }
                }
                reply?.writeNoException()
                reply?.writeInt(if (consumed) 1 else 0)
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

    private fun resolveFallbackMappingPackage(): String {
        val candidates = DeviceProfile.current(context).mappingPackages
        for (pkg in candidates) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Throwable) {
            }
        }
        return candidates.firstOrNull() ?: MAPPING_PACKAGE
    }

    fun bind() {
        if (mappingBinder != null || isBound) return
        try {
            val queryIntent = Intent(ACTION_MAPPING_SERVICE)
            @Suppress("DEPRECATION")
            val resolved = context.packageManager
                .queryIntentServices(queryIntent, 0)
                ?.firstOrNull()
                ?.serviceInfo
            val bindIntent = if (resolved != null) {
                Intent(ACTION_MAPPING_SERVICE).setComponent(
                    ComponentName(resolved.packageName, resolved.name)
                )
            } else {
                Intent(ACTION_MAPPING_SERVICE).setPackage(resolveFallbackMappingPackage())
            }
            isBound = context.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
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
                service.transact(TRANSACTION_UNREGISTER_INPUT_LISTENER, data, reply, 0)
                reply.readException()
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
            val profile = DeviceProfile.current(context)
            val inputIds = if (profile.dismissOverlayOnBackMapping) {
                arrayOf(INPUT_ID_HOME, INPUT_ID_BACK)
            } else {
                arrayOf(INPUT_ID_HOME)
            }
            data.writeInterfaceToken(MAPPING_DESCRIPTOR)
            data.writeStrongBinder(keyListenerBinder)
            data.writeStringArray(inputIds)
            service.transact(TRANSACTION_REGISTER_INPUT_LISTENER, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register OnInputEventListener", t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
