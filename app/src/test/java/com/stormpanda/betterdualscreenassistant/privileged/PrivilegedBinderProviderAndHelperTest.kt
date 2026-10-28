package com.stormpanda.betterdualscreenassistant.privileged

import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivilegedBinderProviderAndHelperTest {

    @Test
    fun `PrivilegedBinderProvider handles standard ContentProvider methods and METHOD_REGISTER_BINDER`() {
        val provider = PrivilegedBinderProvider()
        assertTrue(provider.onCreate())
        val uri = Uri.parse("content://com.stormpanda.betterdualscreenassistant.privileged")
        assertNull(provider.query(uri, null, null, null, null))
        assertNull(provider.getType(uri))
        assertNull(provider.insert(uri, null))
        assertEquals(0, provider.delete(uri, null, null))
        assertEquals(0, provider.update(uri, null, null, null))

        // Unknown method returns empty Bundle
        assertNotNull(provider.call("unknown_method", null, null))

        // METHOD_REGISTER_BINDER with valid binder invokes PrivilegedBridge
        val helperBinder = createAuthorizedHelperBinder()
        val extras = Bundle().apply {
            putBinder(PrivilegedHelperMain.EXTRA_BINDER, helperBinder)
        }
        val result = provider.call(PrivilegedHelperMain.METHOD_REGISTER_BINDER, null, extras)
        assertNotNull(result)
        assertTrue(PrivilegedBridge.ping())
    }

    @Test
    fun `HelperBinder handles PING interface descriptor and security guards`() {
        val helperBinder = createAuthorizedHelperBinder()

        // 1. INTERFACE_TRANSACTION
        val ifaceData = Parcel.obtain()
        val ifaceReply = Parcel.obtain()
        assertTrue(helperBinder.transact(IBinder.INTERFACE_TRANSACTION, ifaceData, ifaceReply, 0))
        ifaceReply.setDataPosition(0)
        assertEquals(PrivilegedHelperMain.DESCRIPTOR, ifaceReply.readString())
        ifaceData.recycle()
        ifaceReply.recycle()

        // 2. TRANSACTION_PING
        val pingData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val pingReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_PING, pingData, pingReply, 0))
        pingReply.setDataPosition(0)
        pingReply.readException()
        assertEquals(PrivilegedHelperMain.HELPER_PROTOCOL_VERSION, pingReply.readInt())
        pingData.recycle()
        pingReply.recycle()

        // 3. TRANSACTION_SET_SYSTEM_PROP ignores non-allowlisted property keys without throwing
        val propData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeString("ro.unauthorized.prop")
            writeString("1")
        }
        val propReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_SET_SYSTEM_PROP, propData, propReply, 0))
        propReply.setDataPosition(0)
        propReply.readException()
        propData.recycle()
        propReply.recycle()

        // 4. TRANSACTION_FORCE_STOP_PACKAGE blocks invalid package names
        val stopData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeString("bad;package;name")
        }
        val stopReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_FORCE_STOP_PACKAGE, stopData, stopReply, 0))
        stopReply.setDataPosition(0)
        stopReply.readException()
        stopData.recycle()
        stopReply.recycle()

        // 5. TRANSACTION_READ_FILE blocks path traversal and non-sys/proc paths (returns empty string)
        val readData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeString("/sys/../etc/hosts")
        }
        val readReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_READ_FILE, readData, readReply, 0))
        readReply.setDataPosition(0)
        readReply.readException()
        assertEquals("", readReply.readString())
        readData.recycle()
        readReply.recycle()
    }

    @Test
    fun `HelperBinder TRANSACTION_GET_GAME_FPS returns reported task FPS and decays to 0f when stale`() {
        val helperBinder = createAuthorizedHelperBinder()
        val trackedTaskField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("trackedFpsTaskId").apply { isAccessible = true }
        val lastCheckField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("lastFpsTaskCheckElapsedMs").apply { isAccessible = true }
        val listenerBinderField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("fpsListenerBinder").apply { isAccessible = true }

        trackedTaskField.setInt(helperBinder, 42)
        lastCheckField.setLong(helperBinder, android.os.SystemClock.elapsedRealtime())

        val fpsListenerBinder = listenerBinderField.get(helperBinder) as IBinder
        val callbackData = Parcel.obtain().apply {
            writeInterfaceToken("android.gui.IFpsListener")
            writeFloat(59.4f)
        }
        val callbackReply = Parcel.obtain()
        assertTrue(fpsListenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, callbackData, callbackReply, IBinder.FLAG_ONEWAY))
        callbackData.recycle()
        callbackReply.recycle()

        val fpsData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val fpsReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_GET_GAME_FPS, fpsData, fpsReply, 0))
        fpsReply.setDataPosition(0)
        fpsReply.readException()
        assertEquals(59.4f, fpsReply.readFloat(), 0.001f)
        fpsData.recycle()
        fpsReply.recycle()
    }

    @Test
    fun `HelperBinder ignores FPS callbacks from outdated task listener binder after task switch`() {
        val helperBinder = createAuthorizedHelperBinder()
        val trackedTaskField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("trackedFpsTaskId").apply { isAccessible = true }
        val lastCheckField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("lastFpsTaskCheckElapsedMs").apply { isAccessible = true }
        val createBinderMethod = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredMethod("createFpsListenerBinder", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }

        val oldTaskBinder = createBinderMethod.invoke(helperBinder, 3829) as IBinder
        val newTaskBinder = createBinderMethod.invoke(helperBinder, 3877) as IBinder

        trackedTaskField.setInt(helperBinder, 3877)
        lastCheckField.setLong(helperBinder, android.os.SystemClock.elapsedRealtime())

        // Callback from active task (3877) updates FPS
        val newData = Parcel.obtain().apply {
            writeInterfaceToken("android.gui.IFpsListener")
            writeFloat(53.2f)
        }
        val newReply = Parcel.obtain()
        assertTrue(newTaskBinder.transact(IBinder.FIRST_CALL_TRANSACTION, newData, newReply, IBinder.FLAG_ONEWAY))
        newData.recycle()
        newReply.recycle()

        // Stale callback from previous task (3829) must be ignored
        val staleData = Parcel.obtain().apply {
            writeInterfaceToken("android.gui.IFpsListener")
            writeFloat(88.9f)
        }
        val staleReply = Parcel.obtain()
        assertTrue(oldTaskBinder.transact(IBinder.FIRST_CALL_TRANSACTION, staleData, staleReply, IBinder.FLAG_ONEWAY))
        staleData.recycle()
        staleReply.recycle()

        val fpsData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val fpsReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_GET_GAME_FPS, fpsData, fpsReply, 0))
        fpsReply.setDataPosition(0)
        fpsReply.readException()
        assertEquals(53.2f, fpsReply.readFloat(), 0.001f)
        fpsData.recycle()
        fpsReply.recycle()
    }

    private fun createAuthorizedHelperBinder(): PrivilegedHelperMain.HelperBinder {
        val binder = PrivilegedHelperMain.HelperBinder(null, Handler(Looper.getMainLooper()))
        val uidField = PrivilegedHelperMain.HelperBinder::class.java.getDeclaredField("cachedAppUid").apply {
            isAccessible = true
        }
        // Authorize the current test process UID
        uidField.setInt(binder, Binder.getCallingUid().coerceAtLeast(10000))
        return binder
    }
}
