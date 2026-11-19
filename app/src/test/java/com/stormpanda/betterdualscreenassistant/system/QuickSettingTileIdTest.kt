package com.stormpanda.betterdualscreenassistant.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingTileIdTest {

    @Test
    fun `QuickSettingCategory default enabled flags match stock vs optional categories`() {
        assertTrue(QuickSettingCategory.STOCK_AYN.enabledByDefault)
        assertFalse(QuickSettingCategory.ANDROID_SYSTEM.enabledByDefault)
        assertFalse(QuickSettingCategory.TOOLS.enabledByDefault)
    }

    @Test
    fun `all QuickSettingTileId entries have unique keys and valid metadata`() {
        val entries = QuickSettingTileId.entries
        val keys = entries.map { it.key }
        assertEquals(entries.size, keys.toSet().size)
        entries.forEach { tile ->
            assertTrue(tile.key.isNotBlank())
            assertTrue(tile.title.isNotBlank())
            assertTrue(tile.description.isNotBlank())
            assertTrue(tile.iconRes != 0)
        }
    }

    @Test
    fun `defaultOrder and defaultDisabled match category defaults`() {
        assertEquals(QuickSettingTileId.entries.toList(), QuickSettingTileId.defaultOrder)

        val expectedDisabled = QuickSettingTileId.entries
            .filterNot { it.category.enabledByDefault }
            .toSet()
        assertEquals(expectedDisabled, QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.CHARGING_SEPARATION !in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.WIFI in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.SCREEN_CAST in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.TOUCH_LOCK in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.SCREENSHOT in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.SCREEN_RECORD in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.CLEAR_ALL_TASKS in QuickSettingTileId.defaultDisabled)
        assertTrue(QuickSettingTileId.CLUSTER_TUNE in QuickSettingTileId.defaultDisabled)
        assertEquals(QuickSettingCategory.ANDROID_SYSTEM, QuickSettingTileId.SCREEN_CAST.category)
        assertEquals("Screen Cast", QuickSettingTileId.SCREEN_CAST.title)
        assertEquals("screen_cast", QuickSettingTileId.SCREEN_CAST.key)
        assertEquals(QuickSettingCategory.TOOLS, QuickSettingTileId.SCREEN_RECORD.category)
        assertEquals("Screenshot", QuickSettingTileId.SCREENSHOT.title)
        assertEquals("Screen Record", QuickSettingTileId.SCREEN_RECORD.title)
        assertEquals(QuickSettingCategory.TOOLS, QuickSettingTileId.CLEAR_ALL_TASKS.category)
        assertEquals(QuickSettingCategory.TOOLS, QuickSettingTileId.CLUSTER_TUNE.category)
        assertEquals("ClusterTune", QuickSettingTileId.CLUSTER_TUNE.title)
        assertEquals("cluster_tune", QuickSettingTileId.CLUSTER_TUNE.key)
    }

    @Test
    fun `fromKey resolves every tile and returns null for unknown key`() {
        QuickSettingTileId.entries.forEach { tile ->
            assertEquals(tile, QuickSettingTileId.fromKey(tile.key))
        }
        assertNull(QuickSettingTileId.fromKey("non_existent_tile"))
        assertNull(QuickSettingTileId.fromKey(""))
    }

    @Test
    fun `profile-aware filtering and labels adapt cleanly for Retroid Pocket Duo`() {
        assertTrue(QuickSettingTileId.AMBIENT_LIGHT.isSupportedOn(DeviceProfile.AYN_THOR))
        assertFalse(QuickSettingTileId.AMBIENT_LIGHT.isSupportedOn(DeviceProfile.RETROID_POCKET_DUO))
        assertTrue(QuickSettingTileId.PREVENT_ACCIDENTAL_HOME.isSupportedOn(DeviceProfile.AYN_THOR))
        assertFalse(QuickSettingTileId.PREVENT_ACCIDENTAL_HOME.isSupportedOn(DeviceProfile.RETROID_POCKET_DUO))
        assertTrue(QuickSettingTileId.SCREEN_CAST.isSupportedOn(DeviceProfile.AYN_THOR))
        assertTrue(QuickSettingTileId.SCREEN_CAST.isSupportedOn(DeviceProfile.RETROID_POCKET_DUO))

        val retroidSupported = QuickSettingTileId.supportedEntries(DeviceProfile.RETROID_POCKET_DUO)
        assertFalse(QuickSettingTileId.AMBIENT_LIGHT in retroidSupported)
        assertFalse(QuickSettingTileId.PREVENT_ACCIDENTAL_HOME in retroidSupported)
        assertEquals(QuickSettingTileId.entries.size - 2, retroidSupported.size)

        assertTrue(
            QuickSettingTileId.CLUSTER_TUNE.isSupportedOn(
                DeviceProfile.AYN_THOR,
                isClusterTuneInstalled = true
            )
        )
        assertFalse(
            QuickSettingTileId.CLUSTER_TUNE.isSupportedOn(
                DeviceProfile.AYN_THOR,
                isClusterTuneInstalled = false
            )
        )
        assertEquals(
            QuickSettingTileId.entries.size - 1,
            QuickSettingTileId.supportedEntries(
                DeviceProfile.AYN_THOR,
                isClusterTuneInstalled = false
            ).size
        )

        assertEquals(
            "Stock AYN Toggles",
            QuickSettingCategory.STOCK_AYN.titleFor(DeviceProfile.AYN_THOR)
        )
        assertEquals(
            "Stock Retroid Toggles",
            QuickSettingCategory.STOCK_AYN.titleFor(DeviceProfile.RETROID_POCKET_DUO)
        )
        assertTrue(
            QuickSettingTileId.CONTROLLER_STYLE
                .descriptionFor(promotedToManager = false, profile = DeviceProfile.RETROID_POCKET_DUO)
                .contains("Retro")
        )
        assertTrue(
            QuickSettingTileId.KEY_MAPPER
                .descriptionFor(promotedToManager = false, profile = DeviceProfile.RETROID_POCKET_DUO)
                .contains("Retroid")
        )
    }
}
