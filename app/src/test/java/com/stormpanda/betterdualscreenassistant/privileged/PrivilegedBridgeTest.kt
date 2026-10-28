package com.stormpanda.betterdualscreenassistant.privileged

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
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
class PrivilegedBridgeTest {

    private lateinit var context: Context
    private val pServerCommands = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var pServerResponder: (String) -> String = { "" }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pServerCommands.clear()
        pServerResponder = { "" }
        setHelperBinder(null)
        setPServerCachedBinder(createFakePServerBinder())
    }

    @After
    fun tearDown() {
        setHelperBinder(null)
        setPServerCachedBinder(null)
    }

    private fun setHelperBinder(binder: IBinder?) {
        val field = PrivilegedBridge::class.java.getDeclaredField("helperBinder").apply {
            isAccessible = true
        }
        field.set(PrivilegedBridge, binder)
    }

    private fun setPServerCachedBinder(binder: IBinder?) {
        val field = PServerClient::class.java.getDeclaredField("cachedBinder").apply {
            isAccessible = true
        }
        field.set(PServerClient, binder)
    }

    private fun createFakePServerBinder(): IBinder {
        val binder = mockk<IBinder>(relaxed = true)
        every { binder.isBinderAlive } returns true
        every { binder.transact(0, any(), any(), any()) } answers {
            val data = secondArg<Parcel>()
            val reply = thirdArg<Parcel?>()
            data.setDataPosition(0)
            val cmd = data.createStringArray()?.firstOrNull() ?: ""
            pServerCommands.add(cmd)
            val out = pServerResponder(cmd)
            reply?.writeByteArray(out.toByteArray(Charsets.UTF_8))
            reply?.setDataPosition(0)
            true
        }
        return binder
    }

    private fun createMockHelperBinder(
        version: Int = PrivilegedHelperMain.HELPER_PROTOCOL_VERSION,
        handler: (code: Int, data: Parcel, reply: Parcel) -> Unit = { _, _, _ -> }
    ): IBinder {
        val binder = mockk<IBinder>(relaxed = true)
        every { binder.isBinderAlive } returns true
        every { binder.transact(any(), any(), any(), any()) } answers {
            val code = firstArg<Int>()
            val data = secondArg<Parcel>()
            val reply = thirdArg<Parcel?>()
            data.setDataPosition(0)
            if (reply != null) {
                if (code == PrivilegedHelperMain.TRANSACTION_PING) {
                    reply.writeNoException()
                    reply.writeInt(version)
                } else {
                    handler(code, data, reply)
                }
                reply.setDataPosition(0)
            }
            true
        }
        return binder
    }

    @Test
    fun `onHelperBinderReceived accepts matching version and rejects outdated helper`() {
        var shutdownCalled = false
        val outdatedBinder = createMockHelperBinder(version = 1) { code, _, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_SHUTDOWN) {
                shutdownCalled = true
                reply.writeNoException()
            }
        }
        PrivilegedBridge.onHelperBinderReceived(outdatedBinder)
        assertTrue(shutdownCalled)
        assertFalse(PrivilegedBridge.ping())

        var registeredClient = false
        val validBinder = createMockHelperBinder(version = PrivilegedHelperMain.HELPER_PROTOCOL_VERSION) { code, _, reply ->
            when (code) {
                PrivilegedHelperMain.TRANSACTION_REGISTER_CLIENT -> {
                    registeredClient = true
                    reply.writeNoException()
                }
                PrivilegedHelperMain.TRANSACTION_UPDATE_WATCHDOG_STATE -> {
                    reply.writeNoException()
                }
            }
        }
        PrivilegedBridge.onHelperBinderReceived(validBinder)
        assertTrue(registeredClient)
        assertTrue(PrivilegedBridge.ping())
    }

    @Test
    fun `setDisplayBrightness clamps brightness and delegates to helper binder or PServer fallback`() {
        var receivedDisplayId = -1
        var receivedBrightness = 0f
        var receivedTemporary = false
        val helper = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_SET_BRIGHTNESS) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                receivedDisplayId = data.readInt()
                receivedBrightness = data.readFloat()
                receivedTemporary = data.readInt() != 0
                reply.writeNoException()
            }
        }
        setHelperBinder(helper)

        // Clamping > 1.0f to 1.0f
        PrivilegedBridge.setDisplayBrightness(context, 0, 1.5f, temporary = true)
        assertEquals(0, receivedDisplayId)
        assertEquals(1.0f, receivedBrightness, 0.0001f)
        assertTrue(receivedTemporary)

        // Clamping negative value to -1.0f (screen blanking)
        PrivilegedBridge.setDisplayBrightness(context, 4, -5.0f, temporary = false)
        assertEquals(4, receivedDisplayId)
        assertEquals(-1.0f, receivedBrightness, 0.0001f)
        assertFalse(receivedTemporary)

        // Fallback to PServerClient when helper binder is absent and DisplayManager reflection is unavailable
        setHelperBinder(null)
        val noDmContext = mockk<Context>(relaxed = true)
        every { noDmContext.getSystemService(DisplayManager::class.java) } throws SecurityException("No permission")
        every { noDmContext.applicationContext } returns context
        PrivilegedBridge.setDisplayBrightness(noDmContext, 0, 0.5f, temporary = false)
        assertTrue(pServerCommands.any { it.contains("cmd display set-brightness 0.5") })
    }

    @Test
    fun `getDisplayBrightnessInfo normalizes 4-float and 3-float helper responses and falls back to Settings`() {
        val helper4 = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_GET_BRIGHTNESS) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                reply.writeNoException()
                reply.writeFloatArray(floatArrayOf(0.42f, 0.05f, 1.0f, 0.40f))
            }
        }
        setHelperBinder(helper4)
        val info4 = PrivilegedBridge.getDisplayBrightnessInfo(context, 4)
        assertEquals(0.42f, info4[0], 0.001f)
        assertEquals(0.05f, info4[1], 0.001f)
        assertEquals(1.0f, info4[2], 0.001f)
        assertEquals(0.40f, info4[3], 0.001f)

        // 3-float response from helper gets normalized to 4 elements
        val helper3 = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_GET_BRIGHTNESS) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                reply.writeNoException()
                reply.writeFloatArray(floatArrayOf(0.75f, 0.0f, 1.0f))
            }
        }
        setHelperBinder(helper3)
        val info3 = PrivilegedBridge.getDisplayBrightnessInfo(context, 0)
        assertEquals(4, info3.size)
        assertEquals(0.75f, info3[0], 0.001f)
        assertEquals(0.75f, info3[3], 0.001f)

        // Fallback to Settings.System.SCREEN_BRIGHTNESS when helper is disconnected
        setHelperBinder(null)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
        val fallbackInfo = PrivilegedBridge.getDisplayBrightnessInfo(context, 0)
        assertEquals(1.0f, fallbackInfo[0], 0.01f)
    }

    @Test
    fun `forceStopPackage blocks invalid package names and allows valid ones`() {
        PrivilegedBridge.forceStopPackage("invalid;package")
        assertTrue(pServerCommands.isEmpty())

        PrivilegedBridge.forceStopPackage("com.example.validapp")
        assertTrue(pServerCommands.any { it.contains("am force-stop 'com.example.validapp'") })
    }

    @Test
    fun `getSurfaceFlingerFlips uses helper binder or parses service call SurfaceFlinger 1013 hex output`() {
        val helper = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_GET_SF_FLIPS) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                reply.writeNoException()
                reply.writeInt(123456)
            }
        }
        setHelperBinder(helper)
        assertEquals(123456, PrivilegedBridge.getSurfaceFlingerFlips())

        // Fallback to PServerClient hex output parsing: Result: Parcel(000003e8 ...) -> 1000
        setHelperBinder(null)
        pServerResponder = { cmd ->
            if (cmd.contains("SurfaceFlinger 1013")) {
                "Result: Parcel(000003e8 00000000 '........')"
            } else {
                ""
            }
        }
        assertEquals(1000, PrivilegedBridge.getSurfaceFlingerFlips())
    }

    @Test
    fun `getGameFps queries helper binder and returns -1f when disconnected`() {
        assertEquals(-1f, PrivilegedBridge.getGameFps(), 0.001f)

        val helper = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_GET_GAME_FPS) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                reply.writeNoException()
                reply.writeFloat(58.6f)
            }
        }
        setHelperBinder(helper)
        assertEquals(58.6f, PrivilegedBridge.getGameFps(), 0.001f)
    }

    @Test
    fun `readSysfsFile and readSysfsLong enforce path security and read via helper`() {
        assertEquals("", PrivilegedBridge.readSysfsFile("/sys/class/../etc/passwd"))
        assertEquals("", PrivilegedBridge.readSysfsFile("/data/local/tmp/secret"))
        assertEquals("", PrivilegedBridge.readSysfsFile("/proc/self/maps"))

        val helper = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_READ_FILE) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                val path = data.readString()
                reply.writeNoException()
                if (path == "/sys/class/thermal/thermal_zone0/temp") {
                    reply.writeString("45600")
                } else if (path == "/proc/stat") {
                    reply.writeString("cpu  100 0 50 850 0 0 0 0 0 0")
                } else {
                    reply.writeString("")
                }
            }
        }
        setHelperBinder(helper)

        assertEquals(45600L, PrivilegedBridge.readSysfsLong("/sys/class/thermal/thermal_zone0/temp"))
        assertEquals("cpu  100 0 50 850 0 0 0 0 0 0", PrivilegedBridge.readSysfsFile("/proc/stat"))
        assertEquals(0L, PrivilegedBridge.readSysfsLong("/sys/class/unknown"))
    }

    @Test
    fun `putSystemSettingInt putGlobalSettingInt and putSecureSettingInt write to Settings`() {
        PrivilegedBridge.putSystemSettingInt(context, "test_sys_int", 7)
        assertEquals(7, Settings.System.getInt(context.contentResolver, "test_sys_int", -1))

        PrivilegedBridge.putSystemSettingFloat(context, "test_sys_float", 0.75f)
        assertEquals(0.75f, Settings.System.getFloat(context.contentResolver, "test_sys_float", 0f), 0.001f)

        PrivilegedBridge.putSystemSettingString(context, "test_sys_str", "hello")
        assertEquals("hello", Settings.System.getString(context.contentResolver, "test_sys_str"))

        PrivilegedBridge.putGlobalSettingInt(context, "test_global_int", 3)
        assertEquals(3, Settings.Global.getInt(context.contentResolver, "test_global_int", -1))

        PrivilegedBridge.putSecureSettingInt(context, "test_secure_int", 9)
        assertEquals(9, Settings.Secure.getInt(context.contentResolver, "test_secure_int", -1))
    }

    @Test
    fun `setStockProcessFrozenForBroadcast delegates to helper binder or falls back to PServerClient`() {
        PrivilegedBridge.setReplaceStockPreferred(true)
        val helperFreezeCalls = mutableListOf<Boolean>()
        val helper = createMockHelperBinder { code, data, reply ->
            if (code == PrivilegedHelperMain.TRANSACTION_SET_STOCK_FROZEN_FOR_BROADCAST) {
                data.enforceInterface(PrivilegedHelperMain.DESCRIPTOR)
                helperFreezeCalls.add(data.readInt() != 0)
                reply.writeNoException()
            }
        }
        setHelperBinder(helper)

        PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = false)
        PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = true)
        assertEquals(listOf(false, true), helperFreezeCalls)

        // Fallback to PServerClient when helper binder is absent
        setHelperBinder(null)
        pServerCommands.clear()
        pServerResponder = { cmd ->
            when {
                cmd.startsWith("pidof com.odin.dualscreen.assistant") -> "7788"
                cmd.startsWith("cat /proc/7788/stat") -> "7788 (reen.assistant) T 1 7788 1 0"
                else -> ""
            }
        }
        PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = false)
        assertTrue(pServerCommands.contains("kill -CONT 7788"))

        PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = true)
        assertTrue(pServerCommands.contains("kill -STOP 7788"))
    }

    @Test
    fun `captureScreenshot parses SurfaceFlinger display IDs for port 131 and 132`() {
        pServerResponder = { cmd ->
            if (cmd.contains("dumpsys SurfaceFlinger --display-id")) {
                """
                Display 4630946746060242051 (HWC display 0, port=131)
                Display 4630946883500138628 (HWC display 1, port=132)
                """.trimIndent()
            } else {
                ""
            }
        }

        // targetMode = 0 (Top)
        assertTrue(PrivilegedBridge.captureScreenshot(context, 0))
        assertTrue(pServerCommands.any { it.contains("screencap -p -d 4630946746060242051") })

        // targetMode = 1 (Bottom)
        pServerCommands.clear()
        assertTrue(PrivilegedBridge.captureScreenshot(context, 1))
        assertTrue(pServerCommands.any { it.contains("screencap -p -d 4630946883500138628") })

        // targetMode = 2 (Both)
        pServerCommands.clear()
        assertTrue(PrivilegedBridge.captureScreenshot(context, 2))
        assertTrue(pServerCommands.any {
            it.contains("screencap -p -d 4630946746060242051") &&
                it.contains("screencap -p -d 4630946883500138628")
        })
    }
}
