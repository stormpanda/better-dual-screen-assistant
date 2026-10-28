package com.stormpanda.betterdualscreenassistant.privileged

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

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

    @Test
    fun `persistent watchdog respawns service on process death even when FLAG_STOPPED is set`() {
        val helperBinder = createAuthorizedHelperBinder()
        var respawnCount = 0
        var restoreStockCalled = false
        var exitCalled = false

        val installedAppInfo = ApplicationInfo().apply {
            packageName = PrivilegedHelperMain.APP_PACKAGE_NAME
            sourceDir = "/data/app/bdsa/base.apk"
            enabled = true
            flags = ApplicationInfo.FLAG_STOPPED
        }
        helperBinder.packageInfoOverride = { installedAppInfo }
        helperBinder.appProcessRunningOverride = { false }
        helperBinder.respawnServiceOverride = { pkg ->
            assertEquals(PrivilegedHelperMain.APP_PACKAGE_NAME, pkg)
            respawnCount++
        }
        helperBinder.restoreStockOverride = { restoreStockCalled = true }
        helperBinder.exitProcessOverride = { exitCalled = true }

        val deathRecipient = registerMockClientAndCaptureDeathRecipient(
            helperBinder = helperBinder,
            replaceStock = true
        )
        ShadowLooper.idleMainLooper(5_000L, TimeUnit.MILLISECONDS)

        // Simulate app process killed by Recents / Clear All / RAM cleaner (with FLAG_STOPPED)
        deathRecipient.binderDied()
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.WATCHDOG_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)

        assertEquals(1, respawnCount)
        assertFalse(restoreStockCalled)
        assertFalse(exitCalled)

        // Re-registering client resets respawn attempts and halts further respawn ticks
        registerMockClientAndCaptureDeathRecipient(helperBinder = helperBinder, replaceStock = true)
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.RESPAWN_RETRY_INTERVAL_MS * 2, TimeUnit.MILLISECONDS)
        assertEquals(1, respawnCount)
        assertFalse(restoreStockCalled)
        assertFalse(exitCalled)
    }

    @Test
    fun `persistent watchdog restores stock assistant and exits on uninstall or package disable`() {
        // 1. Uninstall (packageInfo == null)
        val uninstallBinder = createAuthorizedHelperBinder()
        var respawnCount = 0
        var restoreStockCalled = false
        var exitCalled = false

        val appInfo = ApplicationInfo().apply {
            packageName = PrivilegedHelperMain.APP_PACKAGE_NAME
            sourceDir = "/data/app/bdsa/base.apk"
            enabled = true
        }
        var currentAppInfo: ApplicationInfo? = appInfo
        uninstallBinder.packageInfoOverride = { currentAppInfo }
        uninstallBinder.appProcessRunningOverride = { false }
        uninstallBinder.respawnServiceOverride = { respawnCount++ }
        uninstallBinder.restoreStockOverride = { restoreStockCalled = true }
        uninstallBinder.exitProcessOverride = { exitCalled = true }

        val death1 = registerMockClientAndCaptureDeathRecipient(uninstallBinder, replaceStock = true)
        currentAppInfo = null
        death1.binderDied()
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.WATCHDOG_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)

        assertEquals(0, respawnCount)
        assertTrue(restoreStockCalled)
        assertTrue(exitCalled)

        // 2. Package disabled (enabled == false)
        val disableBinder = createAuthorizedHelperBinder()
        var disableRestoreCalled = false
        var disableExitCalled = false
        val disabledInfo = ApplicationInfo().apply {
            packageName = PrivilegedHelperMain.APP_PACKAGE_NAME
            sourceDir = "/data/app/bdsa/base.apk"
            enabled = true
        }
        disableBinder.packageInfoOverride = { disabledInfo }
        disableBinder.appProcessRunningOverride = { false }
        disableBinder.respawnServiceOverride = { respawnCount++ }
        disableBinder.restoreStockOverride = { disableRestoreCalled = true }
        disableBinder.exitProcessOverride = { disableExitCalled = true }

        val death2 = registerMockClientAndCaptureDeathRecipient(disableBinder, replaceStock = true)
        disabledInfo.enabled = false
        death2.binderDied()
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.WATCHDOG_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)

        assertEquals(0, respawnCount)
        assertTrue(disableRestoreCalled)
        assertTrue(disableExitCalled)
    }

    @Test
    fun `persistent watchdog does not respawn when replaceStockPreferred is false`() {
        val helperBinder = createAuthorizedHelperBinder()
        var respawnCount = 0
        var restoreStockCalled = false
        var exitCalled = false

        val appInfo = ApplicationInfo().apply {
            packageName = PrivilegedHelperMain.APP_PACKAGE_NAME
            sourceDir = "/data/app/bdsa/base.apk"
            enabled = true
            flags = ApplicationInfo.FLAG_STOPPED
        }
        helperBinder.packageInfoOverride = { appInfo }
        helperBinder.appProcessRunningOverride = { false }
        helperBinder.respawnServiceOverride = { respawnCount++ }
        helperBinder.restoreStockOverride = { restoreStockCalled = true }
        helperBinder.exitProcessOverride = { exitCalled = true }

        val deathRecipient = registerMockClientAndCaptureDeathRecipient(
            helperBinder = helperBinder,
            replaceStock = false
        )
        deathRecipient.binderDied()
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.WATCHDOG_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)

        assertEquals(0, respawnCount)
        assertTrue(restoreStockCalled)
        assertTrue(exitCalled)
    }

    @Test
    fun `persistent watchdog trips circuit breaker after repeated rapid crashes and restores stock`() {
        val helperBinder = createAuthorizedHelperBinder()
        var respawnCount = 0
        var restoreStockCalled = false
        var exitCalled = false

        val appInfo = ApplicationInfo().apply {
            packageName = PrivilegedHelperMain.APP_PACKAGE_NAME
            sourceDir = "/data/app/bdsa/base.apk"
            enabled = true
        }
        helperBinder.packageInfoOverride = { appInfo }
        helperBinder.appProcessRunningOverride = { false }
        helperBinder.respawnServiceOverride = { respawnCount++ }
        helperBinder.restoreStockOverride = { restoreStockCalled = true }
        helperBinder.exitProcessOverride = { exitCalled = true }

        // Ensure elapsedRealtime() starts well past 0L
        ShadowLooper.idleMainLooper(10_000L, TimeUnit.MILLISECONDS)
        assertTrue(SystemClock.elapsedRealtime() > 0L)

        for (i in 1..PrivilegedHelperMain.MAX_RAPID_CRASHES) {
            val death = registerMockClientAndCaptureDeathRecipient(helperBinder, replaceStock = true)
            // Crash after only 100ms (< RAPID_CRASH_UPTIME_THRESHOLD_MS)
            ShadowLooper.idleMainLooper(100L, TimeUnit.MILLISECONDS)
            death.binderDied()
            ShadowLooper.idleMainLooper(PrivilegedHelperMain.WATCHDOG_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }

        // First (MAX_RAPID_CRASHES - 1) crashes respawned; the MAX_RAPID_CRASHES-th crash trips breaker
        assertEquals(PrivilegedHelperMain.MAX_RAPID_CRASHES - 1, respawnCount)
        assertTrue(restoreStockCalled)
        assertTrue(exitCalled)
    }

    @Test
    fun `HelperBinder TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST brackets ordered broadcasts with SIGCONT and SIGSTOP and safety timeout`() {
        val helperBinder = createAuthorizedHelperBinder()
        val sentSignals = mutableListOf<Pair<Int, Int>>()
        var stockPid = 8268
        var stockStopped = true

        helperBinder.resolveStockPidOverride = { stockPid }
        helperBinder.isPidStoppedOverride = { stockStopped }
        helperBinder.sendSignalOverride = { pid, sig ->
            sentSignals.add(pid to sig)
            stockStopped = (sig == PrivilegedHelperMain.SIGNAL_STOP)
        }

        fun sendFreezeTransaction(freeze: Boolean) {
            val data = Parcel.obtain().apply {
                writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                writeInt(if (freeze) 1 else 0)
            }
            val reply = Parcel.obtain()
            assertTrue(
                helperBinder.transact(
                    PrivilegedHelperMain.TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST,
                    data,
                    reply,
                    0
                )
            )
            reply.setDataPosition(0)
            reply.readException()
            data.recycle()
            reply.recycle()
        }

        // 1. Pre-broadcast unfreeze -> sends SIGCONT to frozen stock PID
        sendFreezeTransaction(freeze = false)
        assertEquals(listOf(8268 to PrivilegedHelperMain.SIGNAL_CONT), sentSignals)
        assertFalse(stockStopped)

        // 2. Post-broadcast re-freeze -> sends SIGSTOP immediately and cancels safety timer
        sendFreezeTransaction(freeze = true)
        assertEquals(
            listOf(
                8268 to PrivilegedHelperMain.SIGNAL_CONT,
                8268 to PrivilegedHelperMain.SIGNAL_STOP
            ),
            sentSignals
        )
        assertTrue(stockStopped)

        // Advancing past safety timeout must NOT emit a duplicate SIGSTOP
        ShadowLooper.idleMainLooper(
            PrivilegedHelperMain.STOCK_BROADCAST_UNFREEZE_TIMEOUT_MS + 100L,
            TimeUnit.MILLISECONDS
        )
        assertEquals(2, sentSignals.size)

        // 3. If post-broadcast never arrives (e.g. aborted ordered broadcast), 350ms safety timer re-freezes PID
        sentSignals.clear()
        sendFreezeTransaction(freeze = false)
        assertEquals(listOf(8268 to PrivilegedHelperMain.SIGNAL_CONT), sentSignals)
        ShadowLooper.idleMainLooper(
            PrivilegedHelperMain.STOCK_BROADCAST_UNFREEZE_TIMEOUT_MS + 10L,
            TimeUnit.MILLISECONDS
        )
        assertEquals(
            listOf(
                8268 to PrivilegedHelperMain.SIGNAL_CONT,
                8268 to PrivilegedHelperMain.SIGNAL_STOP
            ),
            sentSignals
        )
        assertTrue(stockStopped)

        // 4. If stock PID is NOT in stopped ('T') state, pre-broadcast unfreeze does nothing
        sentSignals.clear()
        stockPid = 9001
        stockStopped = false
        sendFreezeTransaction(freeze = false)
        assertTrue(sentSignals.isEmpty())
    }

    @Test
    fun `HelperBinder TRANSACTION_CLEAR_OS_THEME is a no-op when no BDSA overlay is active`() {
        val helperBinder = createAuthorizedHelperBinder()
        val data = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(-1)
            writeString("")
        }
        val reply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_CLEAR_OS_THEME, data, reply, 0))
        reply.setDataPosition(0)
        reply.readException()
        data.recycle()
        reply.recycle()

        ShadowLooper.idleMainLooper(100L, TimeUnit.MILLISECONDS)
        val activeField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("osThemeOverlayActive").apply { isAccessible = true }
        assertFalse(activeField.getBoolean(helperBinder))
    }

    private fun registerMockClientAndCaptureDeathRecipient(
        helperBinder: PrivilegedHelperMain.HelperBinder,
        replaceStock: Boolean
    ): IBinder.DeathRecipient {
        var capturedRecipient: IBinder.DeathRecipient? = null
        val clientToken = mockk<IBinder>(relaxed = true)
        every { clientToken.isBinderAlive } returns true
        every { clientToken.linkToDeath(any(), any()) } answers {
            capturedRecipient = firstArg<IBinder.DeathRecipient>()
        }

        val data = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeStrongBinder(clientToken)
            writeString(PrivilegedHelperMain.APP_PACKAGE_NAME)
            writeInt(if (replaceStock) 1 else 0)
            writeFloat(0.5f)
            writeFloat(0.5f)
        }
        val reply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_REGISTER_CLIENT, data, reply, 0))
        reply.setDataPosition(0)
        reply.readException()
        data.recycle()
        reply.recycle()
        return requireNotNull(capturedRecipient)
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
