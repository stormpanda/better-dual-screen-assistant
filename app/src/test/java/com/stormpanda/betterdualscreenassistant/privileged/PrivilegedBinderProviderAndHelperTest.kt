package com.stormpanda.betterdualscreenassistant.privileged

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PrivilegedBinderProviderAndHelperTest {

    @Before
    fun setUp() {
        PrivilegedBridge.resetForTesting()
        DeviceProfile.testOverride = null
        DeviceProfile.resetCacheForTesting()
    }

    @After
    fun tearDown() {
        PrivilegedBridge.resetForTesting()
        DeviceProfile.testOverride = null
        DeviceProfile.resetCacheForTesting()
        unmockkAll()
    }

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
    fun `PrivilegedBinderProvider and HelperBinder reject Process SYSTEM_UID 1000 callers`() {
        // 1. PrivilegedBinderProvider rejects Binder.getCallingUid() == Process.SYSTEM_UID (1000)
        PrivilegedBridge.resetForTesting()
        val provider = PrivilegedBinderProvider()
        val helperBinder = createAuthorizedHelperBinder()
        val extras = Bundle().apply {
            putBinder(PrivilegedHelperMain.EXTRA_BINDER, helperBinder)
        }
        mockkStatic(Binder::class)
        every { Binder.getCallingUid() } returns Process.SYSTEM_UID
        provider.call(PrivilegedHelperMain.METHOD_REGISTER_BINDER, null, extras)
        unmockkAll()
        assertFalse(PrivilegedBridge.ping())

        // 2. HelperBinder.isAuthorizedCaller rejects Process.SYSTEM_UID (1000), accepts root (0) and app UID
        val isAuthorizedMethod = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredMethod("isAuthorizedCaller", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        val uidField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("cachedAppUid").apply { isAccessible = true }
        uidField.setInt(helperBinder, 10234)

        assertFalse(isAuthorizedMethod.invoke(helperBinder, Process.SYSTEM_UID) as Boolean)
        assertTrue(isAuthorizedMethod.invoke(helperBinder, 0) as Boolean)
        assertTrue(isAuthorizedMethod.invoke(helperBinder, 10234) as Boolean)
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

        helperBinder.modernBroadcastQueueOverride = false
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
    fun `HelperBinder TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST uses 600ms timeout on modern queue and resuppresses escaped PID`() {
        val helperBinder = createAuthorizedHelperBinder()
        val sentSignals = mutableListOf<Pair<Int, Int>>()
        var stockPid = 8268
        var stockStopped = true
        var resuppressCalls = 0

        helperBinder.modernBroadcastQueueOverride = true
        helperBinder.resolveStockPidOverride = { stockPid }
        helperBinder.isPidStoppedOverride = { stockStopped }
        helperBinder.sendSignalOverride = { pid, sig ->
            sentSignals.add(pid to sig)
            stockStopped = (sig == PrivilegedHelperMain.SIGNAL_STOP)
        }
        helperBinder.stockWorkerExecutorOverride = { task -> task.run() }
        helperBinder.suppressStockOverride = {
            resuppressCalls++
            true
        }
        registerMockClientAndCaptureDeathRecipient(helperBinder, replaceStock = true)

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

        // 1. Pre-broadcast unfreeze sends SIGCONT and stays unfrozen past legacy 350ms
        sendFreezeTransaction(freeze = false)
        assertEquals(listOf(8268 to PrivilegedHelperMain.SIGNAL_CONT), sentSignals)
        assertFalse(stockStopped)
        ShadowLooper.idleMainLooper(
            PrivilegedHelperMain.STOCK_BROADCAST_UNFREEZE_TIMEOUT_MS + 50L,
            TimeUnit.MILLISECONDS
        )
        assertEquals(1, sentSignals.size)
        assertFalse(stockStopped)

        // 2. Advances to 600ms modern delay -> re-freezes with SIGSTOP
        ShadowLooper.idleMainLooper(
            PrivilegedHelperMain.STOCK_BROADCAST_MODERN_REFREEZE_DELAY_MS,
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
        assertEquals(0, resuppressCalls)

        // 3. Escaped (unfrozen) PID on pre-broadcast triggers re-suppression without SIGCONT
        sentSignals.clear()
        stockPid = 9001
        stockStopped = false
        sendFreezeTransaction(freeze = false)
        assertTrue(sentSignals.isEmpty())
        assertEquals(1, resuppressCalls)
    }

    @Test
    fun `HelperBinder TRANSACTION_IS_STOCK_ASSISTANT_ACTIVE checks TccServices and broadcast window`() {
        val helperBinder = createAuthorizedHelperBinder()
        var stockPid = 5000
        var stockStopped = true
        var tccRunning = false

        helperBinder.modernBroadcastQueueOverride = true
        helperBinder.resolveStockPidOverride = { stockPid }
        helperBinder.isPidStoppedOverride = { stockStopped }
        helperBinder.isTccServiceRunningOverride = { tccRunning }
        helperBinder.sendSignalOverride = { _, sig ->
            stockStopped = (sig == PrivilegedHelperMain.SIGNAL_STOP)
        }

        fun queryStockActive(): Boolean {
            val data = Parcel.obtain().apply {
                writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            }
            val reply = Parcel.obtain()
            assertTrue(
                helperBinder.transact(
                    PrivilegedHelperMain.TRANSACTION_IS_STOCK_ASSISTANT_ACTIVE,
                    data,
                    reply,
                    0
                )
            )
            reply.setDataPosition(0)
            reply.readException()
            val active = reply.readInt() != 0
            data.recycle()
            reply.recycle()
            return active
        }

        // Stopped and windowless -> false
        assertFalse(queryStockActive())

        // Temporarily unfrozen for broadcast while windowless -> still false
        val unfreezeData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(0)
        }
        val unfreezeReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST,
                unfreezeData,
                unfreezeReply,
                0
            )
        )
        unfreezeReply.readException()
        unfreezeData.recycle()
        unfreezeReply.recycle()
        assertFalse(stockStopped)
        assertFalse(queryStockActive())

        // Wait for modern broadcast timeout, then simulate escaped unfrozen PID -> true
        ShadowLooper.idleMainLooper(
            PrivilegedHelperMain.STOCK_BROADCAST_MODERN_REFREEZE_DELAY_MS + 50L,
            TimeUnit.MILLISECONDS
        )
        stockStopped = false
        assertTrue(queryStockActive())

        // Even if stopped, running TccServices -> true
        stockStopped = true
        tccRunning = true
        assertTrue(queryStockActive())
    }

    @Test
    fun `HelperBinder TRANSACTION_SUPPRESS_STOCK_ASSISTANT waits for startup registration and retries on PID race`() {
        DeviceProfile.testOverride = DeviceProfile.RETROID_POCKET_DUO
        val helperBinder = createAuthorizedHelperBinder()
        val rootCommands = mutableListOf<String>()
        val events = mutableListOf<String>()
        var currentPids = listOf(100)
        var tccRunning = false
        var regPollCount = 0
        var attemptScenario = 1
        var pidReadsAfterEnable = 0

        helperBinder.stockPollIntervalMsOverride = 0L
        helperBinder.stockStartupSettleMsOverride = 0L
        helperBinder.stockStartupFallbackSleepMsOverride = 0L
        helperBinder.isPidStoppedOverride = { true }
        helperBinder.isTccServiceRunningOverride = { tccRunning }
        helperBinder.execRootCommandOverride = { cmd ->
            rootCommands.add(cmd)
            events.add("cmd:$cmd")
            if (attemptScenario == 2 && cmd == "pm enable ${DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE}/.services.TccServices") {
                if (pidReadsAfterEnable == 0) {
                    // Simulate respawn race on attempt 1 where PID 200 dies and PID 300 binds TccServices
                    currentPids = listOf(300)
                    tccRunning = true
                }
                pidReadsAfterEnable++
            }
            ""
        }
        helperBinder.resolveAllStockPidsOverride = { currentPids }
        helperBinder.sendSignalOverride = { pid, sig ->
            events.add("sig:$pid:$sig")
            if (sig == PrivilegedHelperMain.SIGNAL_KILL) {
                currentPids = when (attemptScenario) {
                    1 -> listOf(200)
                    else -> if (pid == 100) listOf(200) else listOf(400)
                }
            }
        }
        helperBinder.hasStockReceiverRegisteredOverride = { pid, _ ->
            events.add("reg:$pid")
            regPollCount++
            regPollCount >= 2
        }

        fun transactSuppress(): Boolean {
            val data = Parcel.obtain().apply {
                writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            }
            val reply = Parcel.obtain()
            assertTrue(
                helperBinder.transact(
                    PrivilegedHelperMain.TRANSACTION_SUPPRESS_STOCK_ASSISTANT,
                    data,
                    reply,
                    0
                )
            )
            reply.setDataPosition(0)
            reply.readException()
            val ok = reply.readInt() != 0
            data.recycle()
            reply.recycle()
            return ok
        }

        // Scenario 1: single clean pass waits for ScreenReceiver registration before SIGSTOP and re-enables on disk
        assertTrue(transactSuppress())
        val sigStopIdx = events.indexOf("sig:200:${PrivilegedHelperMain.SIGNAL_STOP}")
        val secondRegIdx = events.lastIndexOf("reg:200")
        val enableIdx = events.indexOf("cmd:pm enable ${DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE}/.services.TccServices")
        assertTrue(secondRegIdx in 0 until sigStopIdx)
        assertTrue(enableIdx > sigStopIdx)

        // Scenario 2: PID race on attempt 1 retries and cleanly suppresses PID 400 without blindly freezing 300
        events.clear()
        rootCommands.clear()
        attemptScenario = 2
        currentPids = listOf(100)
        regPollCount = 0
        pidReadsAfterEnable = 0
        tccRunning = false
        val origHasReg = helperBinder.hasStockReceiverRegisteredOverride
        var racePoll = 0
        helperBinder.hasStockReceiverRegisteredOverride = { pid, _ ->
            if (pid == 400) {
                tccRunning = false
            }
            racePoll++
            true
        }
        assertNotNull(origHasReg)
        assertTrue(transactSuppress())
        assertFalse(events.contains("sig:300:${PrivilegedHelperMain.SIGNAL_STOP}"))
        assertTrue(events.contains("sig:400:${PrivilegedHelperMain.SIGNAL_STOP}"))
        assertEquals(
            "pm enable ${DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE}/.receivers.BootBroadcastReceiver",
            rootCommands.last()
        )
    }

    @Test
    fun `HelperBinder continuous stock monitor resuppresses escaped PID and respects broadcast window and opt-out`() {
        val helperBinder = createAuthorizedHelperBinder()
        var stockPid = 111
        var stockStopped = true
        var tccRunning = false
        var tccChecks = 0
        var resuppressCount = 0

        helperBinder.modernBroadcastQueueOverride = true
        helperBinder.resolveStockPidOverride = { stockPid }
        helperBinder.isPidStoppedOverride = { stockStopped }
        helperBinder.isTccServiceRunningOverride = {
            tccChecks++
            tccRunning
        }
        helperBinder.sendSignalOverride = { _, sig ->
            stockStopped = (sig == PrivilegedHelperMain.SIGNAL_STOP)
        }
        helperBinder.stockWorkerExecutorOverride = { task -> task.run() }
        helperBinder.suppressStockOverride = {
            resuppressCount++
            stockStopped = true
            tccRunning = false
            true
        }

        registerMockClientAndCaptureDeathRecipient(helperBinder, replaceStock = true)

        // 1. Stopped + windowless PID 111 verifies once and caches PID without repeated dumpsys checks
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS)
        assertEquals(1, tccChecks)
        assertEquals(0, resuppressCount)
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS)
        assertEquals(1, tccChecks)
        assertEquals(0, resuppressCount)

        // 2. Stopped PID 222 with TccServices running triggers re-suppression
        stockPid = 222
        tccRunning = true
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS)
        assertEquals(2, tccChecks)
        assertEquals(1, resuppressCount)

        // Advance past auto-resuppress cooldown
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_AUTO_RESUPPRESS_COOLDOWN_MS, TimeUnit.MILLISECONDS)

        // 3. Escaped unfrozen PID 333 outside broadcast window triggers re-suppression
        stockPid = 333
        stockStopped = false
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS)
        assertEquals(2, resuppressCount)

        // Advance past auto-resuppress cooldown
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_AUTO_RESUPPRESS_COOLDOWN_MS, TimeUnit.MILLISECONDS)

        // 4. Temporarily unfrozen PID 444 during broadcast window must NOT trigger monitor re-suppression
        stockPid = 444
        stockStopped = true
        val unfreezeData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(0)
        }
        val unfreezeReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST,
                unfreezeData,
                unfreezeReply,
                0
            )
        )
        unfreezeReply.readException()
        unfreezeData.recycle()
        unfreezeReply.recycle()
        // Advance to end of monitor interval (444ms < 600ms if triggered, or force monitor check mid-window)
        val monitorField = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredField("stockSuppressionMonitorRunnable").apply { isAccessible = true }
        (monitorField.get(helperBinder) as Runnable).run()
        assertEquals(2, resuppressCount)
        ShadowLooper.idleMainLooper(
            PrivilegedHelperMain.STOCK_BROADCAST_MODERN_REFREEZE_DELAY_MS + 50L,
            TimeUnit.MILLISECONDS
        )
        assertTrue(stockStopped)

        // 5. Opting out via TRANSACTION_UPDATE_WATCHDOG_STATE stops the monitor
        val optOutData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(0) // replaceStockPreferred = false
            writeFloat(0.5f)
            writeFloat(0.5f)
        }
        val optOutReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_UPDATE_WATCHDOG_STATE,
                optOutData,
                optOutReply,
                0
            )
        )
        optOutReply.readException()
        optOutData.recycle()
        optOutReply.recycle()

        stockPid = 555
        stockStopped = false
        ShadowLooper.idleMainLooper(PrivilegedHelperMain.STOCK_MONITOR_INTERVAL_MS * 2, TimeUnit.MILLISECONDS)
        assertEquals(2, resuppressCount)
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

    @Test
    fun `HelperBinder bottom blank cover layout params use TYPE_BOOT_PROGRESS without FLAG_LAYOUT_NO_LIMITS`() {
        val helperBinder = createAuthorizedHelperBinder()
        val lp = helperBinder.createBottomCoverLayoutParams(1240, 1080)

        assertEquals(1240, lp.width)
        assertEquals(1080, lp.height)
        assertEquals(2021, lp.type)
        assertEquals(android.graphics.PixelFormat.OPAQUE, lp.format)
        assertEquals(android.view.Gravity.CENTER, lp.gravity)
        assertEquals(0, lp.fitInsetsTypes)
        assertEquals(
            0,
            lp.flags and android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        val data = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(1)
            writeInt(1)
        }
        val reply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_SET_BOTTOM_SCREEN_BLANKED,
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

    @Test
    fun `HelperBinder TRANSACTION_CLEAR_ALL_RECENT_TASKS removes non-launcher tasks via removeTask and preserves CATEGORY_HOME launchers`() {
        val helperBinder = createAuthorizedHelperBinder()
        val removedTaskIds = mutableListOf<Int>()
        var removeAllCalled = false

        val fakePm = object {
            @Suppress("unused")
            fun queryIntentActivities(
                intent: android.content.Intent,
                resolvedType: String?,
                flags: Long,
                userId: Int
            ): List<android.content.pm.ResolveInfo> {
                assertEquals(0, userId)
                val pkg = when {
                    intent.hasCategory(android.content.Intent.CATEGORY_HOME) -> "com.example.duallauncher"
                    intent.hasCategory(android.content.Intent.CATEGORY_SECONDARY_HOME) -> "com.example.secondarylauncher"
                    else -> return emptyList()
                }
                val ri = android.content.pm.ResolveInfo().apply {
                    activityInfo = android.content.pm.ActivityInfo().apply {
                        packageName = pkg
                    }
                }
                return listOf(ri)
            }
        }

        val fakeAtm = object {
            @Suppress("unused")
            fun getRecentTasks(maxNum: Int, flags: Int, userId: Int): List<android.app.ActivityManager.RecentTaskInfo> {
                assertEquals(256, maxNum)
                assertEquals(2, flags)
                assertEquals(0, userId)
                val appTask1 = android.app.ActivityManager.RecentTaskInfo().apply {
                    taskId = 101
                    topActivity = android.content.ComponentName("com.example.game", "com.example.game.MainActivity")
                }
                val appTask2 = android.app.ActivityManager.RecentTaskInfo().apply {
                    taskId = 102
                    topActivity = android.content.ComponentName("com.example.browser", "com.example.browser.MainActivity")
                }
                val bdsaTask = android.app.ActivityManager.RecentTaskInfo().apply {
                    taskId = 103
                    topActivity = android.content.ComponentName(
                        PrivilegedHelperMain.APP_PACKAGE_NAME,
                        "${PrivilegedHelperMain.APP_PACKAGE_NAME}.MainActivity"
                    )
                }
                val homeLauncherBottomTask = android.app.ActivityManager.RecentTaskInfo().apply {
                    taskId = 104
                    topActivity = android.content.ComponentName(
                        "com.example.duallauncher",
                        "com.example.duallauncher.BottomScreenActivity"
                    )
                }
                val secondaryLauncherTask = android.app.ActivityManager.RecentTaskInfo().apply {
                    taskId = 105
                    baseIntent = android.content.Intent().setComponent(
                        android.content.ComponentName(
                            "com.example.secondarylauncher",
                            "com.example.secondarylauncher.SecondaryActivity"
                        )
                    )
                }
                return listOf(appTask1, appTask2, bdsaTask, homeLauncherBottomTask, secondaryLauncherTask)
            }

            @Suppress("unused")
            fun removeTask(taskId: Int): Boolean {
                removedTaskIds.add(taskId)
                return true
            }

            @Suppress("unused")
            fun removeAllVisibleRecentTasks() {
                removeAllCalled = true
            }
        }
        helperBinder.packageManagerOverride = fakePm
        helperBinder.activityTaskManagerOverride = fakeAtm

        val data = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val reply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_CLEAR_ALL_RECENT_TASKS,
                data,
                reply,
                0
            )
        )
        reply.setDataPosition(0)
        reply.readException()
        assertEquals(2, reply.readInt())
        assertEquals(listOf(101, 102), removedTaskIds)
        assertFalse(
            "Must use per-task removeTask instead of blanket removeAllVisibleRecentTasks so launcher tasks survive",
            removeAllCalled
        )
        data.recycle()
        reply.recycle()
    }

    @Test
    fun `HelperBinder screen recording transactions start query stop and auto-finalize on shutdown`() {
        val helperBinder = createAuthorizedHelperBinder()
        val mockRecorder = mockk<ScreenRecorderEngine>()
        var activeStatus = ScreenRecorderEngine.Status()

        every { mockRecorder.startRecording(any()) } answers {
            val cfg = firstArg<ScreenRecorderEngine.Config>()
            activeStatus = ScreenRecorderEngine.Status(
                isRecording = true,
                targetMode = cfg.targetMode,
                startedElapsedRealtimeMs = 12345L,
                outputPath = cfg.outputPath
            )
            true
        }
        every { mockRecorder.getStatus() } answers { activeStatus }
        every { mockRecorder.stopRecording(any()) } answers {
            val reason = firstArg<String>()
            val completed = activeStatus.outputPath
            activeStatus = ScreenRecorderEngine.Status(
                isRecording = false,
                targetMode = 0,
                startedElapsedRealtimeMs = 0L,
                outputPath = "",
                lastCompletedPath = completed,
                lastStopReason = reason
            )
            activeStatus
        }
        helperBinder.screenRecorderOverride = mockRecorder

        // 1. Start recording (Both Screens, Internal + Mic audio, 60 FPS)
        val outPath = "/sdcard/Movies/ScreenRecordings/ScreenRecord_Test_Both.mp4"
        val startData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(ScreenRecorderEngine.TARGET_BOTH)
            writeInt(1)
            writeInt(1)
            writeInt(60)
            writeString(outPath)
        }
        val startReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_START_SCREEN_RECORD,
                startData,
                startReply,
                0
            )
        )
        startReply.setDataPosition(0)
        startReply.readException()
        assertEquals(1, startReply.readInt())
        assertEquals(1, startReply.readInt()) // isRecording
        assertEquals(ScreenRecorderEngine.TARGET_BOTH, startReply.readInt())
        assertEquals(12345L, startReply.readLong())
        assertEquals(outPath, startReply.readString())
        startData.recycle()
        startReply.recycle()

        // 2. Query active recording state
        val queryData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val queryReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_GET_SCREEN_RECORD_STATE,
                queryData,
                queryReply,
                0
            )
        )
        queryReply.setDataPosition(0)
        queryReply.readException()
        assertEquals(1, queryReply.readInt())
        assertEquals(ScreenRecorderEngine.TARGET_BOTH, queryReply.readInt())
        queryData.recycle()
        queryReply.recycle()

        // 3. Helper shutdown automatically finalizes active recording before exiting
        var exited = false
        helperBinder.exitProcessOverride = { exited = true }
        val shutdownData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val shutdownReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_SHUTDOWN,
                shutdownData,
                shutdownReply,
                0
            )
        )
        shutdownReply.setDataPosition(0)
        shutdownReply.readException()
        shutdownData.recycle()
        shutdownReply.recycle()

        assertFalse(activeStatus.isRecording)
        assertEquals(outPath, activeStatus.lastCompletedPath)
        assertEquals("shutdown", activeStatus.lastStopReason)
        ShadowLooper.idleMainLooper(100L, TimeUnit.MILLISECONDS)
        assertTrue(exited)
    }

    @Test
    fun `HelperBinder TRANSACTION_SET_BACKLIGHT_POWER writes allowlisted panels and restoreDisplayAndStockAssistant resets both panels`() {
        val helperBinder = createAuthorizedHelperBinder()
        val writes = mutableListOf<Pair<Int, Boolean>>()
        helperBinder.backlightWriteOverride = { panelIndex, blanked ->
            writes.add(panelIndex to blanked)
        }

        fun sendBacklightTx(panelIndex: Int, blanked: Boolean) {
            val data = Parcel.obtain().apply {
                writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                writeInt(panelIndex)
                writeInt(if (blanked) 1 else 0)
            }
            val reply = Parcel.obtain()
            assertTrue(
                helperBinder.transact(
                    PrivilegedHelperMain.TRANSACTION_SET_BACKLIGHT_POWER,
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

        sendBacklightTx(panelIndex = 1, blanked = true)
        sendBacklightTx(panelIndex = 0, blanked = false)
        // Out-of-range panel indices are ignored safely
        sendBacklightTx(panelIndex = -1, blanked = true)
        sendBacklightTx(panelIndex = 2, blanked = true)

        assertEquals(listOf(1 to true, 0 to false), writes)

        // Uninstall recovery resets both panel0 and panel1 bl_power to false ("0")
        writes.clear()
        val restoreMethod = PrivilegedHelperMain.HelperBinder::class.java
            .getDeclaredMethod("restoreDisplayAndStockAssistant").apply { isAccessible = true }
        restoreMethod.invoke(helperBinder)

        assertTrue(writes.contains(0 to false))
        assertTrue(writes.contains(1 to false))
    }

    @Test
    fun `HelperBinder TRANSACTION_WINDOW_SESSION_TRANSACT validates IWindowSession descriptor token and code`() {
        val helperBinder = createAuthorizedHelperBinder()
        helperBinder.allowedWindowSessionCodesOverride = setOf(42)

        var transactCount = 0
        var receivedCode = -1
        var receivedFlags = -1
        var receivedWindowType = -1
        val mockSessionBinder = object : Binder() {
            init {
                attachInterface(null, PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
            }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
                    return true
                }
                transactCount++
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
                receivedCode = code
                receivedFlags = flags
                receivedWindowType = data.readInt()
                reply?.writeNoException()
                reply?.writeInt(7) // e.g. WindowManagerGlobal.ADD_OKAY | ADD_FLAG_APP_VISIBLE
                return true
            }
        }

        // 1. Valid IWindowSession binder, allowlisted addToDisplay code, and valid interface token -> forwarded
        val validForwardPayload = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
            writeInt(2038) // TYPE_APPLICATION_OVERLAY
        }
        val data = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeStrongBinder(mockSessionBinder)
            writeInt(42) // allowlisted targetCode
            writeInt(0)  // targetFlags
            appendFrom(validForwardPayload, 0, validForwardPayload.dataSize())
        }
        validForwardPayload.recycle()

        val reply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_WINDOW_SESSION_TRANSACT,
                data,
                reply,
                0
            )
        )
        reply.setDataPosition(0)
        reply.readException()
        assertEquals(1, reply.readInt()) // ok == 1
        reply.readException() // forwarded reply exception header
        assertEquals(7, reply.readInt()) // forwarded result
        assertEquals(1, transactCount)
        assertEquals(42, receivedCode)
        assertEquals(0, receivedFlags)
        assertEquals(2038, receivedWindowType)
        data.recycle()
        reply.recycle()

        // 2. Non-IWindowSession system service binder (e.g. IActivityManager) -> rejected
        val nonWindowBinder = object : Binder() {
            init {
                attachInterface(null, "android.app.IActivityManager")
            }

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString("android.app.IActivityManager")
                    return true
                }
                transactCount++
                return true
            }
        }
        val wrongServicePayload = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
            writeInt(1)
        }
        val wrongServiceData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeStrongBinder(nonWindowBinder)
            writeInt(42)
            writeInt(0)
            appendFrom(wrongServicePayload, 0, wrongServicePayload.dataSize())
        }
        wrongServicePayload.recycle()
        val wrongServiceReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_WINDOW_SESSION_TRANSACT,
                wrongServiceData,
                wrongServiceReply,
                0
            )
        )
        wrongServiceReply.setDataPosition(0)
        wrongServiceReply.readException()
        assertEquals(0, wrongServiceReply.readInt())
        assertEquals(1, transactCount)
        wrongServiceData.recycle()
        wrongServiceReply.recycle()

        // 3. Disallowed transaction code -> rejected
        val badCodePayload = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
            writeInt(2038)
        }
        val badCodeData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeStrongBinder(mockSessionBinder)
            writeInt(999) // non-addToDisplay code
            writeInt(0)
            appendFrom(badCodePayload, 0, badCodePayload.dataSize())
        }
        badCodePayload.recycle()
        val badCodeReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_WINDOW_SESSION_TRANSACT,
                badCodeData,
                badCodeReply,
                0
            )
        )
        badCodeReply.setDataPosition(0)
        badCodeReply.readException()
        assertEquals(0, badCodeReply.readInt())
        assertEquals(1, transactCount)
        badCodeData.recycle()
        badCodeReply.recycle()

        // 4. Empty allowlisted codes -> fails closed (never permits arbitrary code forwarding)
        helperBinder.allowedWindowSessionCodesOverride = emptySet()
        val emptyCodesPayload = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR_WINDOW_SESSION)
            writeInt(2038)
        }
        val emptyCodesData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeStrongBinder(mockSessionBinder)
            writeInt(42)
            writeInt(0)
            appendFrom(emptyCodesPayload, 0, emptyCodesPayload.dataSize())
        }
        emptyCodesPayload.recycle()
        val emptyCodesReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_WINDOW_SESSION_TRANSACT,
                emptyCodesData,
                emptyCodesReply,
                0
            )
        )
        emptyCodesReply.setDataPosition(0)
        emptyCodesReply.readException()
        assertEquals(0, emptyCodesReply.readInt())
        assertEquals(1, transactCount)
        emptyCodesData.recycle()
        emptyCodesReply.recycle()
    }

    @Test
    fun `HelperBinder TRANSACTION_CAPTURE_SCREENSHOT dispatches and parses physical display IDs`() {
        // 1. Verify uniqueId and address extraction
        assertEquals(
            "4630946482288158084",
            PrivilegedHelperMain.extractPhysicalDisplayIdFromUniqueId("local:4630946482288158084")
        )
        assertEquals(
            "4630946671160541331",
            PrivilegedHelperMain.extractPhysicalDisplayIdFromUniqueId("4630946671160541331")
        )
        val fakeAddress = object {
            @Suppress("unused")
            fun getPhysicalDisplayId(): Long = 4630946671160541331L
        }
        assertEquals(
            "4630946671160541331",
            PrivilegedHelperMain.extractPhysicalDisplayIdFromAddress(fakeAddress)
        )

        // 2. Verify flattened single-line dumpsys SurfaceFlinger --display-id parsing for Thor & Duo
        val thorSfFlattened =
            "Display 4630946441858561667 (HWC display 0): port=131 pnpId=QCM displayName=\"\" " +
                "Display 4630946482288158084 (HWC display 3): port=132 pnpId=QCM displayName=\"\""
        assertEquals(
            "4630946441858561667" to "4630946482288158084",
            PrivilegedHelperMain.parseSurfaceFlingerDisplayIds(thorSfFlattened)
        )

        val duoSfFlattened =
            "Display 4630947039571902866 (HWC display 0): port=146 pnpId=QCM displayName=\"\" " +
                "Display 4630946671160541331 (HWC display 3): port=147 pnpId=QCM displayName=\"\""
        assertEquals(
            "4630947039571902866" to "4630946671160541331",
            PrivilegedHelperMain.parseSurfaceFlingerDisplayIds(duoSfFlattened)
        )

        // 3. Verify scoped screenshot and screen recording output path validation
        assertTrue(
            PrivilegedHelperMain.isValidScreenshotOutputPath(
                "/sdcard/Pictures/Screenshots/Screenshot_20261110_120000_Bottom.png"
            )
        )
        assertTrue(
            PrivilegedHelperMain.isValidScreenshotOutputPath(
                "/storage/emulated/0/Pictures/Screenshots/Screenshot_20261110_120000_Top.png"
            )
        )
        assertFalse(
            PrivilegedHelperMain.isValidScreenshotOutputPath(
                "/sdcard/Screenshot_20261110_120000_Bottom.png"
            )
        )
        assertFalse(
            PrivilegedHelperMain.isValidScreenshotOutputPath(
                "/sdcard/Pictures/../Screenshot_20261110_120000_Bottom.png"
            )
        )
        assertFalse(
            PrivilegedHelperMain.isValidScreenshotOutputPath(
                "/data/local/tmp/Screenshot_20261110_120000_Bottom.png"
            )
        )
        assertTrue(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/sdcard/Movies/ScreenRecordings/ScreenRecord_20261110_120000_Both.mp4"
            )
        )
        assertTrue(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/storage/emulated/0/Movies/ScreenRecordings/ScreenRecord_20261110_120000_Top.mp4"
            )
        )
        assertFalse(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/tmp/test_both.mp4"
            )
        )
        assertFalse(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/sdcard/Movies/../ScreenRecord_20261110_120000_Both.mp4"
            )
        )

        // 4. Verify TRANSACTION_CAPTURE_SCREENSHOT binder transaction
        val helperBinder = createAuthorizedHelperBinder()
        val capturedCalls = mutableListOf<Triple<Int, String, String>>()
        helperBinder.screenshotCaptureOverride = { mode, top, bottom ->
            capturedCalls.add(Triple(mode, top, bottom))
            true
        }

        val data = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(2)
            writeString("/sdcard/Pictures/Screenshots/Screenshot_20261110_120000_Top.png")
            writeString("/sdcard/Pictures/Screenshots/Screenshot_20261110_120000_Bottom.png")
        }
        val reply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_CAPTURE_SCREENSHOT,
                data,
                reply,
                0
            )
        )
        reply.setDataPosition(0)
        reply.readException()
        assertEquals(1, reply.readInt())
        assertEquals(
            listOf(
                Triple(
                    2,
                    "/sdcard/Pictures/Screenshots/Screenshot_20261110_120000_Top.png",
                    "/sdcard/Pictures/Screenshots/Screenshot_20261110_120000_Bottom.png"
                )
            ),
            capturedCalls
        )
        data.recycle()
        reply.recycle()
    }

    @Test
    fun `HelperBinder TRANSACTION_APPLY_OS_THEME re-commits fabricated overlays even when palette matches previous call`() {
        val helperBinder = createAuthorizedHelperBinder()
        val committedPalettes = mutableListOf<Pair<List<String>, List<Int>>>()
        helperBinder.commitOsThemeOverlaysOverride = { names, colors ->
            committedPalettes.add(names.toList() to colors.toList())
        }

        fun sendApplyOsTheme(names: Array<String>, colors: IntArray) {
            val data = Parcel.obtain().apply {
                writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
                writeStringArray(names)
                writeIntArray(colors)
                writeInt(2)
                writeInt(2)
                writeString("")
                writeString("")
            }
            val reply = Parcel.obtain()
            assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_APPLY_OS_THEME, data, reply, 0))
            reply.setDataPosition(0)
            reply.readException()
            data.recycle()
            reply.recycle()
            ShadowLooper.idleMainLooper(50L, TimeUnit.MILLISECONDS)
        }

        val sampleNames = arrayOf("android:color/system_shade_active_dark", "android:color/system_accent1_100")
        val sampleColors = intArrayOf(0xFFCC0000.toInt(), 0xFFCC0000.toInt())

        sendApplyOsTheme(sampleNames, sampleColors)
        sendApplyOsTheme(sampleNames, sampleColors)

        assertEquals(2, committedPalettes.size)
        assertEquals(sampleColors.toList(), committedPalettes[0].second)
        assertEquals(sampleColors.toList(), committedPalettes[1].second)
    }

    private interface FakeMediaRouter2ManagerCallback : android.os.IInterface

    @Test
    fun `HelperBinder screen cast state and disconnect handle Miracast GMS MediaProjection and ignore screen recorders`() {
        // 1. Cast package filter & device name sanitization
        assertTrue(PrivilegedHelperMain.isWirelessCastProjectionPackage("com.google.android.gms"))
        assertTrue(PrivilegedHelperMain.isWirelessCastProjectionPackage("com.google.android.apps.chromecast.app"))
        assertFalse(PrivilegedHelperMain.isWirelessCastProjectionPackage("com.android.systemui"))
        assertFalse(PrivilegedHelperMain.isWirelessCastProjectionPackage("com.odin.gameassistant"))
        assertFalse(PrivilegedHelperMain.isWirelessCastProjectionPackage("com.rp.gameassistant"))
        assertFalse(PrivilegedHelperMain.isWirelessCastProjectionPackage(PrivilegedHelperMain.APP_PACKAGE_NAME))

        assertEquals("", PrivilegedHelperMain.sanitizeCastDeviceName("CastDisplay"))
        assertEquals("", PrivilegedHelperMain.sanitizeCastDeviceName("VirtualDisplay"))
        assertEquals("Living Room TV", PrivilegedHelperMain.sanitizeCastDeviceName("  Living Room TV  "))

        // 2. 0-arg (Android 13-14) vs 1-arg int (Android 15-16) stopActiveProjection invocation
        var zeroArgStopped = false
        val sdk33ProjMgr = object {
            @Suppress("unused")
            fun stopActiveProjection() {
                zeroArgStopped = true
            }
        }
        assertTrue(PrivilegedHelperMain.invokeStopActiveProjection(sdk33ProjMgr))
        assertTrue(zeroArgStopped)

        var capturedStopReason = -1
        val sdk36ProjMgr = object {
            @Suppress("unused")
            fun stopActiveProjection(stopReason: Int) {
                capturedStopReason = stopReason
            }
        }
        assertTrue(PrivilegedHelperMain.invokeStopActiveProjection(sdk36ProjMgr))
        assertEquals(0, capturedStopReason)

        // 3. Proxy inspection: GameAssistant screen recorder projection is ignored; GMS Cast projection + remote session resolves receiver name
        var activeProjPkg: String? = "com.odin.gameassistant"
        var wfdState = 0
        var wfdDisconnectCalled = false
        var gmsStopCalled = false

        val fakeWfdDisplay = object {
            @Suppress("unused")
            fun getFriendlyDisplayName(): String = "Office Miracast"
        }
        val fakeDm = object {
            @Suppress("unused")
            fun getWifiDisplayStatus(): Any = object {
                @Suppress("unused")
                fun getActiveDisplayState(): Int = wfdState
                @Suppress("unused")
                fun getActiveDisplay(): Any? = if (wfdState != 0) fakeWfdDisplay else null
            }

            @Suppress("unused")
            fun disconnectWifiDisplay() {
                wfdDisconnectCalled = true
                wfdState = 0
            }

            @Suppress("unused")
            fun getDisplayIds(): IntArray = intArrayOf(0, 4)

            @Suppress("unused")
            fun getDisplayInfo(id: Int): Any = object {
                @JvmField
                val type: Int = 1
                @JvmField
                val ownerPackageName: String? = null
                @JvmField
                val name: String = "Built-in Screen"
            }
        }
        val fakeProj = object {
            @Suppress("unused")
            fun getActiveProjectionInfo(): Any? = activeProjPkg?.let { pkg ->
                object {
                    @Suppress("unused")
                    fun getPackageName(): String = pkg
                }
            }

            @Suppress("unused")
            fun stopActiveProjection(stopReason: Int) {
                gmsStopCalled = true
                activeProjPkg = null
            }
        }
        var routerSessionReleased = false
        val fakeRouteSession = object {
            @Suppress("unused")
            fun getId(): String = "cast-session-1"
            @Suppress("unused")
            fun isSystemSession(): Boolean = false
            @Suppress("unused")
            fun getOwnerPackageName(): String = "com.google.android.gms"
            @Suppress("unused")
            fun getName(): CharSequence = ""
        }
        val fakeRouter = object {
            @Suppress("unused")
            fun getRemoteSessions(): List<Any> = listOf(fakeRouteSession)

            @Suppress("unused")
            fun getSelectedRoutes(session: Any): List<Any> = listOf(
                object {
                    @Suppress("unused")
                    fun getName(): CharSequence = "Family Room TV"
                }
            )

            @Suppress("unused")
            fun releaseSession(session: Any) {
                routerSessionReleased = true
            }
        }

        // Also test 1-arg IMediaRouterService.getRemoteSessions(IMediaRouter2Manager) fallback
        var serviceRegisteredMgr: FakeMediaRouter2ManagerCallback? = null
        var serviceUnregistered = false
        var serviceReleasedSessionId: String? = null
        val fakeRouterService = object {
            @Suppress("unused")
            fun registerManager(mgr: FakeMediaRouter2ManagerCallback, pkg: String) {
                if (pkg != "root") throw SecurityException("Package $pkg does not match UID 0")
                serviceRegisteredMgr = mgr
            }

            @Suppress("unused")
            fun unregisterManager(mgr: FakeMediaRouter2ManagerCallback) {
                if (mgr === serviceRegisteredMgr) {
                    serviceUnregistered = true
                }
            }

            @Suppress("unused")
            fun getRemoteSessions(mgr: FakeMediaRouter2ManagerCallback): List<Any> {
                assertNotNull(mgr.asBinder())
                return listOf(
                    object {
                        @Suppress("unused")
                        fun getId(): String = "service-cast-session"
                        @Suppress("unused")
                        fun isSystemSession(): Boolean = false
                        @Suppress("unused")
                        fun getOwnerPackageName(): String = "com.google.android.gms"
                        @Suppress("unused")
                        fun getName(): CharSequence = "Den Chromecast"
                    }
                )
            }

            @Suppress("unused")
            fun releaseSessionWithManager(
                requestId: Int,
                mgr: FakeMediaRouter2ManagerCallback,
                sessionId: String
            ) {
                serviceReleasedSessionId = sessionId
            }
        }

        // GameAssistant active -> NOT treated as wireless cast and NOT stopped on disconnect
        val nonCastStatus = PrivilegedHelperMain.resolveScreenCastStateFromProxies(fakeDm, fakeProj, fakeRouter)
        assertFalse(nonCastStatus.isCasting)
        assertFalse(PrivilegedHelperMain.disconnectScreenCastViaProxies(fakeDm, fakeProj, fakeRouter))
        assertFalse(gmsStopCalled)
        assertFalse(routerSessionReleased)

        // Non-cast GMS display names (e.g. Android Auto) are filtered out
        assertTrue(PrivilegedHelperMain.isNonCastGmsDisplayName("Android Auto"))
        assertFalse(PrivilegedHelperMain.isNonCastGmsDisplayName("Chromecast"))

        // GMS Cast active -> detected via MediaRouter2Manager getSelectedRoutes & stopped + session released
        activeProjPkg = "com.google.android.gms"
        val gmsCastStatus = PrivilegedHelperMain.resolveScreenCastStateFromProxies(fakeDm, fakeProj, fakeRouter)
        assertTrue(gmsCastStatus.isCasting)
        assertEquals("Family Room TV", gmsCastStatus.deviceName)
        assertTrue(PrivilegedHelperMain.disconnectScreenCastViaProxies(fakeDm, fakeProj, fakeRouter))
        assertTrue(gmsStopCalled)
        assertTrue(routerSessionReleased)

        // 1-arg IMediaRouterService fallback resolves receiver name & unregisters temporary manager
        activeProjPkg = "com.google.android.gms"
        val serviceFallbackStatus = PrivilegedHelperMain.resolveScreenCastStateFromProxies(
            dmProxy = fakeDm,
            projectionProxy = fakeProj,
            routerProxy = null,
            fallbackRouterProxy = fakeRouterService
        )
        assertTrue(serviceFallbackStatus.isCasting)
        assertEquals("Den Chromecast", serviceFallbackStatus.deviceName)
        assertTrue(serviceUnregistered)
        assertTrue(
            PrivilegedHelperMain.disconnectScreenCastViaProxies(
                dmProxy = fakeDm,
                projectionProxy = fakeProj,
                routerProxy = null,
                fallbackRouterProxy = fakeRouterService
            )
        )
        assertEquals("service-cast-session", serviceReleasedSessionId)

        // Miracast connecting & connected -> detected and disconnected via disconnectWifiDisplay
        wfdState = 1
        val wfdConnecting = PrivilegedHelperMain.resolveScreenCastStateFromProxies(fakeDm, fakeProj, null)
        assertFalse(wfdConnecting.isCasting)
        assertTrue(wfdConnecting.isConnecting)
        assertEquals("Office Miracast", wfdConnecting.deviceName)

        wfdState = 2
        val wfdConnected = PrivilegedHelperMain.resolveScreenCastStateFromProxies(fakeDm, fakeProj, null)
        assertTrue(wfdConnected.isCasting)
        assertEquals("Office Miracast", wfdConnected.deviceName)
        assertTrue(PrivilegedHelperMain.disconnectScreenCastViaProxies(fakeDm, fakeProj))
        assertTrue(wfdDisconnectCalled)

        // 4. Binder transaction roundtrip on HelperBinder
        val helperBinder = createAuthorizedHelperBinder()
        var overrideConnected = true
        helperBinder.screenCastStateOverride = {
            PrivilegedHelperMain.HelperCastStatus(
                isCasting = overrideConnected,
                isConnecting = false,
                deviceName = if (overrideConnected) "Studio TV" else ""
            )
        }
        helperBinder.disconnectScreenCastOverride = {
            overrideConnected = false
            true
        }

        val qData = Parcel.obtain().apply { writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR) }
        val qReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_GET_SCREEN_CAST_STATE, qData, qReply, 0))
        qReply.setDataPosition(0)
        qReply.readException()
        assertEquals(1, qReply.readInt())
        assertEquals(0, qReply.readInt())
        assertEquals("Studio TV", qReply.readString())
        qData.recycle()
        qReply.recycle()

        val dData = Parcel.obtain().apply { writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR) }
        val dReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_DISCONNECT_SCREEN_CAST, dData, dReply, 0))
        dReply.setDataPosition(0)
        dReply.readException()
        assertEquals(1, dReply.readInt())
        assertFalse(overrideConnected)
        dData.recycle()
        dReply.recycle()
    }

    @Test
    fun `HelperBinder TRANSACTION_GET_PIN_SCREEN_MODE and SET_PIN_SCREEN_MODE round-trip and handle unsupported devices`() {
        val helperBinder = createAuthorizedHelperBinder()
        val fakeWm = object {
            var pinMode: Int = 2
            @Suppress("unused")
            fun getPinScreenMode(): Int = pinMode
            @Suppress("unused")
            fun setPinScreenMode(mode: Int) {
                pinMode = mode
            }
        }
        helperBinder.windowManagerOverride = fakeWm

        val getData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val getReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_GET_PIN_SCREEN_MODE, getData, getReply, 0))
        getReply.setDataPosition(0)
        getReply.readException()
        assertEquals(2, getReply.readInt())
        getData.recycle()
        getReply.recycle()

        val setData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
            writeInt(0)
        }
        val setReply = Parcel.obtain()
        assertTrue(helperBinder.transact(PrivilegedHelperMain.TRANSACTION_SET_PIN_SCREEN_MODE, setData, setReply, 0))
        setReply.setDataPosition(0)
        setReply.readException()
        assertEquals(0, fakeWm.pinMode)
        setData.recycle()
        setReply.recycle()

        // Unsupported device (no getPinScreenMode/setPinScreenMode methods) returns -1 cleanly
        helperBinder.windowManagerOverride = Any()
        val unsupportedData = Parcel.obtain().apply {
            writeInterfaceToken(PrivilegedHelperMain.DESCRIPTOR)
        }
        val unsupportedReply = Parcel.obtain()
        assertTrue(
            helperBinder.transact(
                PrivilegedHelperMain.TRANSACTION_GET_PIN_SCREEN_MODE,
                unsupportedData,
                unsupportedReply,
                0
            )
        )
        unsupportedReply.setDataPosition(0)
        unsupportedReply.readException()
        assertEquals(-1, unsupportedReply.readInt())
        unsupportedData.recycle()
        unsupportedReply.recycle()
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
