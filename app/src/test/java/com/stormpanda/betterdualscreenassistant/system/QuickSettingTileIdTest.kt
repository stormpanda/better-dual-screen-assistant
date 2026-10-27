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
        assertTrue(QuickSettingTileId.TOUCH_LOCK in QuickSettingTileId.defaultDisabled)
    }

    @Test
    fun `fromKey resolves every tile and returns null for unknown key`() {
        QuickSettingTileId.entries.forEach { tile ->
            assertEquals(tile, QuickSettingTileId.fromKey(tile.key))
        }
        assertNull(QuickSettingTileId.fromKey("non_existent_tile"))
        assertNull(QuickSettingTileId.fromKey(""))
    }
}
