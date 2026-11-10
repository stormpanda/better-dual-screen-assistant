package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceProfileTest {

    @Before
    fun setUp() {
        DeviceProfile.testOverride = null
        DeviceProfile.resetCacheForTesting()
    }

    @After
    fun tearDown() {
        DeviceProfile.testOverride = null
        DeviceProfile.resetCacheForTesting()
    }

    @Test
    fun `isRetroidPocketDuoModel matches Retroid Pocket Duo model strings case-insensitively`() {
        assertTrue(DeviceProfile.isRetroidPocketDuoModel("Retroid Pocket Duo"))
        assertTrue(DeviceProfile.isRetroidPocketDuoModel("  retroid pocket duo pro "))
        assertFalse(DeviceProfile.isRetroidPocketDuoModel("AYN Thor"))
        assertFalse(DeviceProfile.isRetroidPocketDuoModel(""))
        assertFalse(DeviceProfile.isRetroidPocketDuoModel(null))
    }

    @Test
    fun `detectFromSystem resolves model and respects testOverride`() {
        assertEquals(DeviceProfile.AYN_THOR, DeviceProfile.detectFromSystem("AYN Thor"))
        assertEquals(DeviceProfile.RETROID_POCKET_DUO, DeviceProfile.detectFromSystem("Retroid Pocket Duo"))

        DeviceProfile.testOverride = DeviceProfile.RETROID_POCKET_DUO
        assertEquals(DeviceProfile.RETROID_POCKET_DUO, DeviceProfile.detectFromSystem("AYN Thor"))
    }

    @Test
    fun `current falls back to checking installed stock Retroid package when Build MODEL is generic`() {
        val mockPm = mockk<PackageManager>()
        val mockCtx = mockk<Context>()
        every { mockCtx.packageManager } returns mockPm

        every {
            mockPm.getApplicationInfo(
                DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
        } returns ApplicationInfo().apply {
            packageName = DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE
        }

        assertEquals(DeviceProfile.RETROID_POCKET_DUO, DeviceProfile.current(mockCtx))

        DeviceProfile.resetCacheForTesting()
        // Transient runtime error must not permanently latch AYN_THOR into cachedProfile
        var shouldThrowTransientError = true
        every {
            mockPm.getApplicationInfo(
                DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
        } answers {
            if (shouldThrowTransientError) {
                throw RuntimeException("Transient binder failure")
            }
            ApplicationInfo().apply {
                packageName = DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE
            }
        }
        assertEquals(DeviceProfile.AYN_THOR, DeviceProfile.current(mockCtx))
        shouldThrowTransientError = false
        assertEquals(DeviceProfile.RETROID_POCKET_DUO, DeviceProfile.current(mockCtx))

        DeviceProfile.resetCacheForTesting()
        every {
            mockPm.getApplicationInfo(
                DeviceProfile.RETROID_STOCK_ASSISTANT_PACKAGE,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
        } throws PackageManager.NameNotFoundException()

        assertEquals(DeviceProfile.AYN_THOR, DeviceProfile.current(mockCtx))
    }

    @Test
    fun `AYN_THOR and RETROID_POCKET_DUO expose expected hardware display and capability invariants`() {
        val ayn = DeviceProfile.AYN_THOR
        assertEquals("AYN", ayn.vendorName)
        assertEquals("AYN key", ayn.wakeKeyName)
        assertEquals("physical AYN button", ayn.physicalButtonLabel)
        assertEquals(0, ayn.topDisplayId)
        assertEquals(4, ayn.bottomDisplayId)
        assertEquals("131", ayn.topSfPort)
        assertEquals("132", ayn.bottomSfPort)
        assertEquals(1240, ayn.bottomFallbackWidth)
        assertEquals(1080, ayn.bottomFallbackHeight)
        assertEquals(1, ayn.defaultUiLookPreferenceId)
        assertTrue(ayn.useLocalFocusMode)
        assertFalse(ayn.blankViaBacklightSysfs)
        assertTrue(ayn.supportsAmbientLight)
        assertTrue(ayn.supportsPreventAccidentalHome)
        assertFalse(ayn.longPressTogglesBottom)
        assertFalse(ayn.dismissOverlayOnBackMapping)
        assertEquals("Odin", ayn.standardControllerLabel)
        assertEquals("Standard Style", ayn.controllerStyleOptionLabel)
        assertTrue(ayn.closeBottomTipText.contains("AYN key"))
        assertEquals("com.odin.dualscreen.assistant", ayn.stockAssistantPackage)

        val retroid = DeviceProfile.RETROID_POCKET_DUO
        assertEquals("Retroid", retroid.vendorName)
        assertEquals("Dual Screen key", retroid.wakeKeyName)
        assertEquals("physical Dual Screen button", retroid.physicalButtonLabel)
        assertEquals(0, retroid.topDisplayId)
        assertEquals(2, retroid.bottomDisplayId)
        assertEquals("146", retroid.topSfPort)
        assertEquals("147", retroid.bottomSfPort)
        assertEquals(1280, retroid.bottomFallbackWidth)
        assertEquals(960, retroid.bottomFallbackHeight)
        assertEquals(320, retroid.bottomFallbackDensityDpi)
        assertEquals(2, retroid.defaultUiLookPreferenceId)
        assertFalse(retroid.useLocalFocusMode)
        assertTrue(retroid.blankViaBacklightSysfs)
        assertFalse(retroid.supportsAmbientLight)
        assertFalse(retroid.supportsPreventAccidentalHome)
        assertTrue(retroid.longPressTogglesBottom)
        assertTrue(retroid.dismissOverlayOnBackMapping)
        assertEquals("Retro", retroid.standardControllerLabel)
        assertEquals("Retro", retroid.controllerStyleOptionLabel)
        assertTrue(retroid.closeBottomTipText.contains("Dual Screen key"))
        assertEquals("com.retroidpocket.dualscreen.assistant", retroid.stockAssistantPackage)

        assertEquals(
            setOf("com.odin.dualscreen.assistant", "com.retroidpocket.dualscreen.assistant"),
            DeviceProfile.allStockAssistantPackages
        )
        assertEquals(setOf("131", "146"), DeviceProfile.allTopSfPorts)
        assertEquals(setOf("132", "147"), DeviceProfile.allBottomSfPorts)
        assertTrue("com.rp.mapping" in DeviceProfile.allProtectedVendorPackages)
        assertTrue("com.odin.mapping" in DeviceProfile.allProtectedVendorPackages)
    }
}
