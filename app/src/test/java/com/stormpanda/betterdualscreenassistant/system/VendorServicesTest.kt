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
    fun `MappingServiceClient registers HOME InputEvent listener and returns consumed state`() {
        var homePressCount = 0
        var shouldConsume = true
        val client = MappingServiceClient(context) {
            homePressCount++
            shouldConsume
        }

        val listenerField = MappingServiceClient::class.java.getDeclaredField("keyListenerBinder").apply {
            isAccessible = true
        }
        val listenerBinder = listenerField.get(client) as IBinder

        // 0. Verify registerKeyListener (tx 2) and unbind (tx 3) on com.ro.mapping.sdk.IServerApi
        var registeredListener: IBinder? = null
        var registeredIds: Array<String>? = null
        var unregisteredListener: IBinder? = null
        val fakeMappingServer = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(MappingServiceClient.MAPPING_DESCRIPTOR)
                when (code) {
                    MappingServiceClient.TRANSACTION_REGISTER_INPUT_LISTENER -> {
                        registeredListener = data.readStrongBinder()
                        registeredIds = data.createStringArray()
                        reply?.writeNoException()
                        return true
                    }
                    MappingServiceClient.TRANSACTION_UNREGISTER_INPUT_LISTENER -> {
                        unregisteredListener = data.readStrongBinder()
                        reply?.writeNoException()
                        return true
                    }
                }
                return false
            }
        }
        val registerMethod = MappingServiceClient::class.java.getDeclaredMethod(
            "registerKeyListener",
            IBinder::class.java
        ).apply { isAccessible = true }
        registerMethod.invoke(client, fakeMappingServer)
        assertEquals(listenerBinder, registeredListener)
        assertArrayEquals(arrayOf("HOME"), registeredIds)

        val binderField = MappingServiceClient::class.java.getDeclaredField("mappingBinder").apply {
            isAccessible = true
        }
        binderField.set(client, fakeMappingServer)
        client.unbind()
        assertEquals(listenerBinder, unregisteredListener)

        // 1. INTERFACE_TRANSACTION
        val ifaceData = Parcel.obtain()
        val ifaceReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.INTERFACE_TRANSACTION, ifaceData, ifaceReply, 0))
        ifaceReply.setDataPosition(0)
        assertEquals(MappingServiceClient.LISTENER_DESCRIPTOR, ifaceReply.readString())
        ifaceData.recycle()
        ifaceReply.recycle()

        fun buildInputEventParcel(id: String, value1: Int): Parcel = Parcel.obtain().apply {
            writeInterfaceToken(MappingServiceClient.LISTENER_DESCRIPTOR)
            writeInt(1) // non-null InputEvent
            writeString(id)
            writeInt(value1)
            writeInt(0) // value2
            writeInt(0) // fd
            writeInt(1) // type (EV_KEY)
            writeInt(102) // code (KEY_HOME)
            writeInt(value1) // value
        }

        // 2. HOME key pressed (value1 = 1) while overlay consumes event -> returns 1
        val homeDownData = buildInputEventParcel("HOME", 1)
        val homeDownReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, homeDownData, homeDownReply, 0))
        homeDownReply.setDataPosition(0)
        homeDownReply.readException()
        assertEquals(1, homeDownReply.readInt())
        assertEquals(1, homePressCount)
        homeDownData.recycle()
        homeDownReply.recycle()

        // 3. HOME key pressed (value1 = 1) while overlay is hidden (shouldConsume = false) -> returns 0
        shouldConsume = false
        val homePassData = buildInputEventParcel("HOME", 1)
        val homePassReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, homePassData, homePassReply, 0))
        homePassReply.setDataPosition(0)
        homePassReply.readException()
        assertEquals(0, homePassReply.readInt())
        assertEquals(2, homePressCount)
        homePassData.recycle()
        homePassReply.recycle()

        // 4. HOME key released (value1 = 0) -> ignored and returns 0
        shouldConsume = true
        val homeUpData = buildInputEventParcel("HOME", 0)
        val homeUpReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, homeUpData, homeUpReply, 0))
        homeUpReply.setDataPosition(0)
        homeUpReply.readException()
        assertEquals(0, homeUpReply.readInt())
        assertEquals(2, homePressCount)
        homeUpData.recycle()
        homeUpReply.recycle()

        // 5. Non-HOME key ("BACK", value1 = 1) -> ignored and returns 0
        val otherKeyData = buildInputEventParcel("BACK", 1)
        val otherKeyReply = Parcel.obtain()
        assertTrue(listenerBinder.transact(IBinder.FIRST_CALL_TRANSACTION, otherKeyData, otherKeyReply, 0))
        otherKeyReply.setDataPosition(0)
        otherKeyReply.readException()
        assertEquals(0, otherKeyReply.readInt())
        assertEquals(2, homePressCount)
        otherKeyData.recycle()
        otherKeyReply.recycle()
    }
}
