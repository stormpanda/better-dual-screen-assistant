package com.stormpanda.betterdualscreenassistant.privileged

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PServerClientTest {

    private lateinit var context: Context
    private val executedCommands = mutableListOf<Pair<String, String>>()
    private var commandResponder: (String) -> String = { "" }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        executedCommands.clear()
        commandResponder = { "" }
        setCachedBinder(createFakePServerBinder())
    }

    @After
    fun tearDown() {
        setCachedBinder(null)
    }

    private fun setCachedBinder(binder: IBinder?) {
        val field = PServerClient::class.java.getDeclaredField("cachedBinder").apply {
            isAccessible = true
        }
        field.set(PServerClient, binder)
    }

    private fun createFakePServerBinder(throwOnTransact: Boolean = false): IBinder {
        val binder = mockk<IBinder>(relaxed = true)
        every { binder.isBinderAlive } returns true
        every { binder.transact(any(), any(), any(), any()) } answers {
            if (throwOnTransact) {
                throw RuntimeException("Simulated binder failure")
            }
            val code = firstArg<Int>()
            val data = secondArg<Parcel>()
            val reply = thirdArg<Parcel?>()
            assertEquals(0, code)
            data.setDataPosition(0)
            val args = data.createStringArray()
            val cmd = args?.getOrNull(0) ?: ""
            val waitFlag = args?.getOrNull(1) ?: ""
            executedCommands.add(cmd to waitFlag)
            val output = commandResponder(cmd)
            reply?.writeByteArray(output.toByteArray(Charsets.UTF_8))
            reply?.setDataPosition(0)
            true
        }
        return binder
    }

    @Test
    fun `shellQuote wraps in single quotes and escapes embedded single quotes`() {
        assertEquals("'hello'", PServerClient.shellQuote("hello"))
        assertEquals("''", PServerClient.shellQuote(""))
        assertEquals("'it'\\''s a test'", PServerClient.shellQuote("it's a test"))
        assertEquals("'; rm -rf /;'", PServerClient.shellQuote("; rm -rf /;"))
    }

    @Test
    fun `isValidPackageName accepts valid Android package names and rejects injections`() {
        assertTrue(PServerClient.isValidPackageName("com.stormpanda.betterdualscreenassistant"))
        assertTrue(PServerClient.isValidPackageName("com.odin.dualscreen.assistant"))
        assertTrue(PServerClient.isValidPackageName("org.example_app2.sub_pkg"))

        assertFalse(PServerClient.isValidPackageName(""))
        assertFalse(PServerClient.isValidPackageName("singleword"))
        assertFalse(PServerClient.isValidPackageName("123.invalid.start"))
        assertFalse(PServerClient.isValidPackageName("com.example..double"))
        assertFalse(PServerClient.isValidPackageName("com.example;id"))
        assertFalse(PServerClient.isValidPackageName("com.example/.."))
    }

    @Test
    fun `exec sends transaction code 0 with command and wait flag and decodes stdout`() {
        commandResponder = { cmd -> "output for $cmd" }
        val result = PServerClient.exec("echo hello", wait = true)
        assertTrue(result.isSuccess)
        assertEquals("output for echo hello", result.getOrNull())
        assertEquals(listOf("echo hello" to "1"), executedCommands)

        val asyncResult = PServerClient.exec("echo async", wait = false)
        assertTrue(asyncResult.isSuccess)
        assertEquals("output for echo async", asyncResult.getOrNull())
        assertEquals("echo async" to "0", executedCommands.last())
    }

    @Test
    fun `exec returns failure Result when transact throws`() {
        setCachedBinder(createFakePServerBinder(throwOnTransact = true))
        val result = PServerClient.exec("id")
        assertTrue(result.isFailure)
    }

    @Test
    fun `ensurePermissionsGranted executes grant commands for valid package and skips invalid package`() {
        PServerClient.ensurePermissionsGranted(context)
        val cmds = executedCommands.map { it.first }
        assertEquals(10, cmds.size)
        assertTrue(cmds.any { it.contains("appops set 'com.stormpanda.betterdualscreenassistant' SYSTEM_ALERT_WINDOW allow") })
        assertTrue(cmds.any { it.contains("appops set 'com.stormpanda.betterdualscreenassistant' WRITE_SETTINGS allow") })
        assertTrue(cmds.any { it.contains("appops set 'com.stormpanda.betterdualscreenassistant' GET_USAGE_STATS allow") })
        assertTrue(cmds.any { it.contains("pm grant 'com.stormpanda.betterdualscreenassistant' android.permission.WRITE_SECURE_SETTINGS") })
        assertTrue(cmds.any { it.contains("pm grant 'com.stormpanda.betterdualscreenassistant' android.permission.PACKAGE_USAGE_STATS") })
        assertTrue(cmds.any { it.contains("pm grant 'com.stormpanda.betterdualscreenassistant' android.permission.BLUETOOTH_CONNECT") })
        assertTrue(cmds.any { it.contains("dumpsys deviceidle whitelist +com.stormpanda.betterdualscreenassistant") })

        executedCommands.clear()
        val badContext = mockk<Context>(relaxed = true)
        every { badContext.packageName } returns "bad;pkg"
        PServerClient.ensurePermissionsGranted(badContext)
        assertTrue(executedCommands.isEmpty())
    }

    @Test
    fun `isReplaceStockPreferred defaults to true and persists updates`() {
        assertTrue(PServerClient.isReplaceStockPreferred(context))

        PServerClient.setReplaceStockPreferred(context, false)
        assertFalse(PServerClient.isReplaceStockPreferred(context))

        PServerClient.setReplaceStockPreferred(context, true)
        assertTrue(PServerClient.isReplaceStockPreferred(context))
    }

    @Test
    fun `isStockAssistantEnabled checks PackageManager state and proc stat for SIGSTOP`() {
        val mockCtx = mockk<Context>(relaxed = true)
        val mockPm = mockk<PackageManager>(relaxed = true)
        every { mockCtx.packageManager } returns mockPm

        // Case 1: Package disabled in PackageManager and no PID running -> false
        every { mockPm.getApplicationEnabledSetting("com.odin.dualscreen.assistant") } returns
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        commandResponder = { "" }
        assertFalse(PServerClient.isStockAssistantEnabled(mockCtx))

        // Case 2: Package enabled in PM, but process is frozen with SIGSTOP (state T) -> false
        every { mockPm.getApplicationEnabledSetting("com.odin.dualscreen.assistant") } returns
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        commandResponder = { cmd ->
            when {
                cmd.startsWith("pidof") -> "4321\n"
                cmd.startsWith("cat /proc/4321/stat") -> "4321 (reen.assistant) T 1 4321 1 0"
                else -> ""
            }
        }
        assertFalse(PServerClient.isStockAssistantEnabled(mockCtx))

        // Case 3: Process is running/sleeping (state S) -> true
        commandResponder = { cmd ->
            when {
                cmd.startsWith("pidof") -> "4321\n"
                cmd.startsWith("cat /proc/4321/stat") -> "4321 (reen.assistant) S 1 4321 1 0"
                else -> ""
            }
        }
        assertTrue(PServerClient.isStockAssistantEnabled(mockCtx))
    }

    @Test
    fun `setStockAssistantEnabled executes ephemeral suppression sequence when disabling and re-enables on disk`() {
        var pidCounter = 1000
        commandResponder = { cmd ->
            if (cmd.startsWith("pidof")) {
                "${pidCounter++}"
            } else {
                ""
            }
        }

        val ok = PServerClient.setStockAssistantEnabled(enabled = false, context = context)
        assertTrue(ok)
        assertTrue(PServerClient.isReplaceStockPreferred(context))

        val cmds = executedCommands.map { it.first }
        // Step 1: stopservice + transient disable TccServices + enable package
        assertTrue(cmds.contains("am stopservice -n com.odin.dualscreen.assistant/.services.TccServices"))
        assertTrue(cmds.contains("pm disable com.odin.dualscreen.assistant/.services.TccServices"))
        // Step 2: kill -9 old PID
        assertTrue(cmds.contains("kill -9 1000"))
        // Step 3: kill -STOP respawned PID
        assertTrue(cmds.contains("kill -STOP 1001"))
        // Step 4: re-enable TccServices on disk so it's never left disabled
        val disableIdx = cmds.indexOf("pm disable com.odin.dualscreen.assistant/.services.TccServices")
        val enableIdx = cmds.lastIndexOf("pm enable com.odin.dualscreen.assistant/.services.TccServices")
        assertTrue(enableIdx > disableIdx)
    }

    @Test
    fun `setStockAssistantEnabled unfreezes and starts TccServices when enabling`() {
        commandResponder = { cmd ->
            if (cmd.startsWith("pidof")) "2222" else ""
        }

        val ok = PServerClient.setStockAssistantEnabled(enabled = true, context = context)
        assertTrue(ok)
        assertFalse(PServerClient.isReplaceStockPreferred(context))

        val cmds = executedCommands.map { it.first }
        assertTrue(cmds.contains("pm enable com.odin.dualscreen.assistant/.services.TccServices"))
        assertTrue(cmds.contains("kill -9 2222"))
        assertTrue(cmds.contains("am startservice -n com.odin.dualscreen.assistant/.services.TccServices"))
    }

    @Test
    fun `ensureStockPackageEnabledOnDisk recovers disabled component and freezes running PID`() {
        val mockCtx = mockk<Context>(relaxed = true)
        val mockPm = mockk<PackageManager>(relaxed = true)
        every { mockCtx.packageManager } returns mockPm
        every { mockPm.getApplicationEnabledSetting("com.odin.dualscreen.assistant") } returns
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        commandResponder = { cmd ->
            if (cmd.startsWith("pidof")) "5555" else ""
        }

        PServerClient.ensureStockPackageEnabledOnDisk(mockCtx)
        val cmds = executedCommands.map { it.first }
        assertTrue(cmds.contains("pm enable com.odin.dualscreen.assistant"))
        assertTrue(cmds.contains("pm enable com.odin.dualscreen.assistant/.services.TccServices"))
        assertTrue(cmds.contains("kill -STOP 5555"))
    }
}
