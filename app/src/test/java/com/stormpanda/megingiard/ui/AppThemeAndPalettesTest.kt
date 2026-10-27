package com.stormpanda.megingiard.ui

import androidx.compose.ui.graphics.Color
import com.stormpanda.megingiard.settings.SettingsCategory
import com.stormpanda.megingiard.settings.SettingsSubPage
import com.stormpanda.megingiard.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeAndPalettesTest {

    @Test
    fun `ThemeMode supportsCustomAccent is true only for DARK and DARK_OLED`() {
        assertTrue(ThemeMode.DARK.supportsCustomAccent)
        assertTrue(ThemeMode.DARK_OLED.supportsCustomAccent)
        assertFalse(ThemeMode.MEGINGIARD.supportsCustomAccent)
        assertFalse(ThemeMode.MJOLNIR.supportsCustomAccent)
        assertFalse(ThemeMode.VALHALLA.supportsCustomAccent)
        assertFalse(ThemeMode.AURORA.supportsCustomAccent)
        assertFalse(ThemeMode.RETRO_PHOSPHOR.supportsCustomAccent)
        assertFalse(ThemeMode.ROYAL_ASGARD.supportsCustomAccent)
    }

    @Test
    fun `SettingsCategory and SettingsSubPage parent mappings are accurate`() {
        assertEquals(SettingsCategory.INPUT, SettingsSubPage.DEADZONES.parentCategory)
        assertEquals(SettingsCategory.SCRAPING, SettingsSubPage.STEAMGRIDDB_TOKEN.parentCategory)
        assertEquals(SettingsCategory.APPEARANCE, SettingsSubPage.CUSTOM_ACCENT.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.CREATE_BACKUP.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.SHARE_PROFILE.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.RESTORE_BACKUP.parentCategory)
        assertEquals(SettingsCategory.CONFIGURATION, SettingsSubPage.RESTORE_REVIEW.parentCategory)
        assertEquals(SettingsCategory.UPDATES, SettingsSubPage.UPDATE_AVAILABLE.parentCategory)
    }

    @Test
    fun `paletteFor resolves all ThemeModes and applies custom userAccent only when supported`() {
        for (mode in ThemeMode.entries) {
            val base = paletteFor(mode, userAccent = null)
            assertTrue(base.appBackground.alpha > 0f)
            assertEquals(base.onSurface.copy(alpha = 0.15f), base.subduedBorder)
        }

        // Custom bright accent on DARK overrides accent and chooses dark appBackground for onAccent
        val brightAccent = Color(0xFFFFFF00)
        val darkCustom = paletteFor(ThemeMode.DARK, userAccent = brightAccent)
        assertEquals(brightAccent, darkCustom.accent)
        assertEquals(darkCustom.appBackground, darkCustom.onAccent)

        // Custom dark accent on DARK_OLED overrides accent and chooses White for onAccent
        val deepNavyAccent = Color(0xFF102060)
        val oledCustom = paletteFor(ThemeMode.DARK_OLED, userAccent = deepNavyAccent)
        assertEquals(deepNavyAccent, oledCustom.accent)
        assertEquals(Color.White, oledCustom.onAccent)

        // Fixed theme ignores userAccent
        val fixedBase = paletteFor(ThemeMode.MEGINGIARD, userAccent = null)
        val fixedWithUser = paletteFor(ThemeMode.MEGINGIARD, userAccent = brightAccent)
        assertEquals(fixedBase.accent, fixedWithUser.accent)
    }

    @Test
    fun `colorSchemeFor maps AppColors tokens to Material3 ColorScheme`() {
        val colors = paletteFor(ThemeMode.MJOLNIR)
        val scheme = colorSchemeFor(colors)
        assertEquals(colors.accent, scheme.primary)
        assertEquals(colors.onAccent, scheme.onPrimary)
        assertEquals(colors.appBackground, scheme.background)
        assertEquals(colors.surface, scheme.surface)
        assertEquals(colors.onSurface, scheme.onSurface)
        assertEquals(colors.error, scheme.error)
    }

    @Test
    fun `AppPalettes contains expected preset collections`() {
        assertEquals(10, AppPalettes.accentPresets.size)
        assertEquals(9, AppPalettes.touchPointerSlots.size)
        assertEquals(7, AppPalettes.hueSpectrum.size)
    }
}
