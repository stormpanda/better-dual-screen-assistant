package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VendorServicesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setCachedFanProvider(null)
    }

    @After
    fun tearDown() {
        setCachedFanProvider(null)
    }

    private fun setCachedFanProvider(binder: IBinder?) {
        val field = FanControlClient::class.java.getDeclaredField("cachedFanProvider").apply {
            isAccessible = true
        }
        field.set(FanControlClient, binder)
    }

    @Test
    fun `FanControlClient returns safe defaults when vendor binder is unavailable`() {
        assertEquals(35, FanControlClient.getMinSpeed())
        assertEquals(100, FanControlClient.getMaxSpeed())
        assertArrayEquals(intArrayOf(35, 100), FanControlClient.getSpeedRange())
        assertEquals(0, FanControlClient.getFanSpeedValue())
        assertEquals(0f, FanControlClient.getSpeedPercentage(), 0.001f)
    }

    @Test
    fun `FanControlClient executes transactions 1 2 3 and 10 when cachedFanProvider is connected`() {
        val fakeFanBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("com.ro.settings.IFanControlProvider")
                when (code) {
                    1 -> { // getMinSpeed
                        reply?.writeNoException()
                        reply?.writeInt(30)
                    }
                    2 -> { // getMaxSpeed
                        reply?.writeNoException()
                        reply?.writeInt(100)
                    }
                    3 -> { // getSpeedPercentage
                        reply?.writeNoException()
                        reply?.writeFloat(0.65f)
                    }
                    10 -> { // getFanSpeedValue
                        reply?.writeNoException()
                        reply?.writeInt(4500)
                    }
                    else -> return false
                }
                return true
            }
        }

        setCachedFanProvider(fakeFanBinder)

        assertEquals(30, FanControlClient.getMinSpeed())
        assertEquals(100, FanControlClient.getMaxSpeed())
        assertArrayEquals(intArrayOf(30, 100), FanControlClient.getSpeedRange())
        assertEquals(4500, FanControlClient.getFanSpeedValue())
        assertEquals(0.65f, FanControlClient.getSpeedPercentage(), 0.001f)
    }

    @Test
    fun `OverviewProxyClient sends transaction 6 with displayId 4 and packageName when bound`() {
        val client = OverviewProxyClient(context)
        var capturedDisplayId = -1
        var capturedPkg = ""

        val fakeOverviewBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == 6) {
                    data.enforceInterface("com.android.systemui.shared.recents.IOverviewProxy")
                    capturedDisplayId = data.readInt()
                    capturedPkg = data.readString() ?: ""
                    reply?.writeNoException()
                    return true
                }
                return false
            }
        }

        val field = OverviewProxyClient::class.java.getDeclaredField("overviewBinder").apply {
            isAccessible = true
        }
        field.set(client, fakeOverviewBinder)

        client.toggleRecentAppsOnBottomScreen()
        assertEquals(4, capturedDisplayId)
        assertEquals(context.packageName, capturedPkg)
    }

    @Test
    fun `MappingServiceClient keyListenerBinder invokes onHomePressed only on HOME key down`() {
        var homePressCount = 0
        val client = MappingServiceClient(context) {
            homePressCount++
        }

        val listenerField = MappingServiceClient::class.java.getDeclaredField("keyListenerBinder").apply {
            isAccessible = true
        }
        val listenerBinder = listenerField.get(client) as IBinder

        // 1. INTERFACE_TRANSACTION
        val ifaceData = Parcel.obtain()
        val ifaceReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.INTERFACE_TRANSACTION, ifaceData, ifaceReply, 0))
        ifaceReply.setDataPosition(0)
        assertEquals("com.ro.mapping.IKeyEventListener", ifaceReply.readString())
        ifaceData.recycle()
        ifaceReply.recycle()

        // 2. HOME key (inputId = 3) + ACTION_DOWN (action = 0) -> triggers onHomePressed
        val homeDownData = Parcel.obtain().apply {
            writeInterfaceToken("com.ro.mapping.IKeyEventListener")
            writeInt(3)
            writeInt(0)
        }
        val homeDownReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, homeDownData, homeDownReply, 0))
        assertEquals(1, homePressCount)
        homeDownData.recycle()
        homeDownReply.recycle()

        // 3. HOME key (inputId = 3) + ACTION_UP (action = 1) -> ignored
        val homeUpData = Parcel.obtain().apply {
            writeInterfaceToken("com.ro.mapping.IKeyEventListener")
            writeInt(3)
            writeInt(1)
        }
        val homeUpReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, homeUpData, homeUpReply, 0))
        assertEquals(1, homePressCount)
        homeUpData.recycle()
        homeUpReply.recycle()

        // 4. Other key (inputId = 4) + ACTION_DOWN (action = 0) -> ignored
        val otherKeyData = Parcel.obtain().apply {
            writeInterfaceToken("com.ro.mapping.IKeyEventListener")
            writeInt(4)
            writeInt(0)
        }
        val otherKeyReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, otherKeyData, otherKeyReply, 0))
        assertEquals(1, homePressCount)
        otherKeyData.recycle()
        otherKeyReply.recycle()
    }
}
