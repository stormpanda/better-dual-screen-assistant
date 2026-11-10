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
    fun `HelperBinder TRANSACTION_CLEAR_ALL_RECENT_TASKS invokes removeAllVisibleRecentTasks and counts clearable tasks`() {
        val helperBinder = createAuthorizedHelperBinder()
        var removeAllCalled = false

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
                return listOf(appTask1, appTask2, bdsaTask)
            }

            @Suppress("unused")
            fun removeAllVisibleRecentTasks() {
                removeAllCalled = true
            }
        }
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
        assertTrue(removeAllCalled)
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

        // 3. Verify screenshot output path validation
        assertTrue(
            PrivilegedHelperMain.isValidScreenshotOutputPath(
                "/sdcard/Pictures/Screenshots/Screenshot_20261110_120000_Bottom.png"
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
