package com.stormpanda.betterdualscreenassistant.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId
import com.stormpanda.betterdualscreenassistant.ui.components.PixelCornerCutShape
import com.stormpanda.betterdualscreenassistant.ui.components.PixelNotchedShape
import com.stormpanda.betterdualscreenassistant.ui.overlay.formatCpuTemperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TccThemeAndOverlayFormatTest {

    @Test
    fun `AppThemeOption fromId resolves all theme options including legacy ID 2`() {
        assertEquals(AppThemeOption.STOCK_SYSTEM, AppThemeOption.fromId(1))
        assertEquals(AppThemeOption.STOCK_DARK, AppThemeOption.fromId(2)) // legacy mapping
        assertEquals(AppThemeOption.STOCK_LIGHT, AppThemeOption.fromId(3))
        assertEquals(AppThemeOption.STOCK_DARK, AppThemeOption.fromId(4))
        assertEquals(AppThemeOption.SYNC_MEGINGIARD, AppThemeOption.fromId(100))
        assertEquals(AppThemeOption.MEGINGIARD_DARK, AppThemeOption.fromId(101))
        assertEquals(AppThemeOption.MEGINGIARD_DARK_OLED, AppThemeOption.fromId(102))
        assertEquals(AppThemeOption.MEGINGIARD, AppThemeOption.fromId(103))
        assertEquals(AppThemeOption.MJOLNIR, AppThemeOption.fromId(104))
        assertEquals(AppThemeOption.VALHALLA, AppThemeOption.fromId(105))
        assertEquals(AppThemeOption.AURORA, AppThemeOption.fromId(106))
        assertEquals(AppThemeOption.RETRO_PHOSPHOR, AppThemeOption.fromId(107))
        assertEquals(AppThemeOption.ROYAL_ASGARD, AppThemeOption.fromId(108))
        assertEquals(AppThemeOption.STOCK_DARK, AppThemeOption.fromId(999))
    }

    @Test
    fun `AppThemeOption fromMegingiardIpcName resolves all IPC names and falls back to MEGINGIARD`() {
        assertEquals(AppThemeOption.MEGINGIARD_DARK, AppThemeOption.fromMegingiardIpcName("DARK"))
        assertEquals(AppThemeOption.MEGINGIARD_DARK_OLED, AppThemeOption.fromMegingiardIpcName("DARK_OLED"))
        assertEquals(AppThemeOption.MEGINGIARD, AppThemeOption.fromMegingiardIpcName("MEGINGIARD"))
        assertEquals(AppThemeOption.MJOLNIR, AppThemeOption.fromMegingiardIpcName("MJOLNIR"))
        assertEquals(AppThemeOption.VALHALLA, AppThemeOption.fromMegingiardIpcName("VALHALLA"))
        assertEquals(AppThemeOption.AURORA, AppThemeOption.fromMegingiardIpcName("AURORA"))
        assertEquals(AppThemeOption.RETRO_PHOSPHOR, AppThemeOption.fromMegingiardIpcName("RETRO_PHOSPHOR"))
        assertEquals(AppThemeOption.ROYAL_ASGARD, AppThemeOption.fromMegingiardIpcName("ROYAL_ASGARD"))
        assertEquals(AppThemeOption.MEGINGIARD, AppThemeOption.fromMegingiardIpcName(null))
        assertEquals(AppThemeOption.MEGINGIARD, AppThemeOption.fromMegingiardIpcName("UNKNOWN"))
    }

    @Test
    fun `contrastingContentColor returns dark text for bright colors and white text for dark colors`() {
        assertEquals(Color(0xFF121212), Color.White.contrastingContentColor())
        assertEquals(Color(0xFF121212), Color(0xFFFFFF00).contrastingContentColor())
        assertEquals(Color.White, Color.Black.contrastingContentColor())
        assertEquals(Color.White, Color(0xFF102040).contrastingContentColor())
    }

    @Test
    fun `resolveTccPalette resolves Stock System Light Dark Sync and all Megingiard themes`() {
        // Stock System follows systemDark
        val sysLight = resolveTccPalette(AppThemeOption.STOCK_SYSTEM, systemDark = false)
        assertFalse(sysLight.isDark)
        assertEquals(LightTccPalette.panelBackground, sysLight.panelBackground)

        val sysDark = resolveTccPalette(AppThemeOption.STOCK_SYSTEM, systemDark = true)
        assertTrue(sysDark.isDark)
        assertEquals(PureBlackTccPalette.panelBackground, sysDark.panelBackground)

        // Megingiard Dark with custom accent override
        val customAccent = 0xFFFF5500.toInt()
        val megingiardDarkCustom = resolveTccPalette(
            option = AppThemeOption.MEGINGIARD_DARK,
            systemDark = true,
            customAccentArgb = customAccent
        )
        assertTrue(megingiardDarkCustom.isDark)
        assertTrue(megingiardDarkCustom.isMegingiardStyle)
        assertEquals(Color(customAccent), megingiardDarkCustom.accent)

        // Sync Megingiard delegates to syncedOption & syncedAccentArgb
        val syncedAccent = 0xFF00E5FF.toInt()
        val syncedPalette = resolveTccPalette(
            option = AppThemeOption.SYNC_MEGINGIARD,
            systemDark = false,
            customAccentArgb = DEFAULT_MEGINGIARD_ACCENT_ARGB,
            syncedOption = AppThemeOption.MEGINGIARD_DARK_OLED,
            syncedAccentArgb = syncedAccent
        )
        assertTrue(syncedPalette.isDark)
        assertEquals(Color(syncedAccent), syncedPalette.accent)

        // Verify all remaining options resolve cleanly
        for (opt in AppThemeOption.entries) {
            val resolved = resolveTccPalette(opt, systemDark = true)
            assertTrue(resolved.panelBackground.alpha > 0f)
        }
    }

    @Test
    fun `buildOsDynamicColorOverlayEntries generates all 60 Android 13 Material You color keys with opaque ARGB`() {
        val palette = resolveTccPalette(AppThemeOption.MEGINGIARD_DARK, systemDark = true)
        val (keys, colors) = buildOsDynamicColorOverlayEntries(palette)

        // 5 tonal palettes (accent1, accent2, accent3, neutral1, neutral2) * 12 steps = 60 entries
        assertEquals(60, keys.size)
        assertEquals(60, colors.size)
        assertEquals(60, keys.toSet().size)

        assertTrue(keys.contains("android:color/system_accent1_500"))
        assertTrue(keys.contains("android:color/system_accent2_500"))
        assertTrue(keys.contains("android:color/system_accent3_500"))
        assertTrue(keys.contains("android:color/system_neutral1_900"))
        assertTrue(keys.contains("android:color/system_neutral2_800"))

        for (color in colors) {
            assertEquals(0xFF, (color ushr 24) and 0xFF)
        }
    }

    @Test
    fun `formatRingValue formats telemetry numbers within 5 characters according to magnitude`() {
        val clazz = Class.forName("com.stormpanda.betterdualscreenassistant.ui.overlay.AssistantOverlayScreenKt")
        val method = clazz.getDeclaredMethod("formatRingValue", Float::class.javaPrimitiveType).apply {
            isAccessible = true
        }

        assertEquals("0", method.invoke(null, 0.0f))
        assertEquals("0", method.invoke(null, 0.005f))
        assertEquals("3.45", method.invoke(null, 3.45f))
        assertEquals("45.6", method.invoke(null, 45.6f))
        assertEquals("100", method.invoke(null, 100.0f))
        assertEquals("768", method.invoke(null, 768.4f))
    }

    @Test
    fun `formatCpuTemperature formats Celsius by default and converts accurately to Fahrenheit`() {
        assertEquals("0℃", formatCpuTemperature(0f, useFahrenheit = false))
        assertEquals("32℉", formatCpuTemperature(0f, useFahrenheit = true))

        assertEquals("56℃", formatCpuTemperature(56f, useFahrenheit = false))
        assertEquals("133℉", formatCpuTemperature(56f, useFahrenheit = true))

        assertEquals("44℃", formatCpuTemperature(44.4f, useFahrenheit = false))
        assertEquals("112℉", formatCpuTemperature(44.4f, useFahrenheit = true))

        assertEquals("100℃", formatCpuTemperature(100f, useFahrenheit = false))
        assertEquals("212℉", formatCpuTemperature(100f, useFahrenheit = true))
    }

    @Test
    fun `TccUiLookPreference resolves Auto per DeviceProfile and works orthogonally with all themes`() {
        assertEquals(TccUiLookPreference.AUTO, TccUiLookPreference.fromId(0))
        assertEquals(TccUiLookPreference.AYN, TccUiLookPreference.fromId(1))
        assertEquals(TccUiLookPreference.RETROID, TccUiLookPreference.fromId(2))
        assertEquals(TccUiLookPreference.AUTO, TccUiLookPreference.fromId(999))

        assertEquals(TccUiLook.AYN, TccUiLookPreference.AUTO.resolve(DeviceProfile.AYN_THOR))
        assertEquals(TccUiLook.RETROID, TccUiLookPreference.AUTO.resolve(DeviceProfile.RETROID_POCKET_DUO))
        assertEquals(TccUiLook.AYN, TccUiLookPreference.AYN.resolve(DeviceProfile.RETROID_POCKET_DUO))
        assertEquals(TccUiLook.RETROID, TccUiLookPreference.RETROID.resolve(DeviceProfile.AYN_THOR))

        assertEquals("Auto (AYN Smooth)", TccUiLookPreference.AUTO.displayNameFor(DeviceProfile.AYN_THOR))
        assertEquals("Auto (Retroid Pixel)", TccUiLookPreference.AUTO.displayNameFor(DeviceProfile.RETROID_POCKET_DUO))

        assertTrue(RetroidPixelTealSwatch in MegingiardAccentPresets)
        assertTrue(RetroidPixelOrangeSwatch in MegingiardAccentPresets)

        // Stock Dark & Light switch between AYN and Retroid Pixel palettes based on uiLook
        val aynStockDark = resolveTccPalette(
            option = AppThemeOption.STOCK_DARK,
            systemDark = true,
            uiLook = TccUiLook.AYN
        )
        val retroidStockDark = resolveTccPalette(
            option = AppThemeOption.STOCK_DARK,
            systemDark = true,
            uiLook = TccUiLook.RETROID
        )
        assertEquals(PureBlackTccPalette, aynStockDark)
        assertEquals(RpPixelPureBlackTccPalette, retroidStockDark)
        assertEquals(Color(0xFF141414), retroidStockDark.primaryBackground)
        assertEquals(Color.Black, retroidStockDark.panelBackground)
        assertEquals(Color(0xFFFF760A), retroidStockDark.accent)
        assertEquals(Color.Black, retroidStockDark.retroidContentOnAccent())

        val retroidStockLight = resolveTccPalette(
            option = AppThemeOption.STOCK_LIGHT,
            systemDark = false,
            uiLook = TccUiLook.RETROID
        )
        assertEquals(RpPixelLightTccPalette, retroidStockLight)
        assertEquals(Color(0xFFFF760A), retroidStockLight.accent)
        assertEquals(Color.Black, retroidStockLight.retroidContentOnAccent())

        // retroidContentOnAccent adapts contrast for dark vs bright Megingiard accents
        val darkCrimsonMegingiard = resolveTccPalette(
            option = AppThemeOption.MEGINGIARD_DARK,
            systemDark = true,
            customAccentArgb = 0xFFD50000.toInt(),
            uiLook = TccUiLook.RETROID
        )
        assertEquals(Color.White, darkCrimsonMegingiard.retroidContentOnAccent())

        val orangeMegingiard = resolveTccPalette(
            option = AppThemeOption.MEGINGIARD_DARK,
            systemDark = true,
            customAccentArgb = 0xFFFF760A.toInt(),
            uiLook = TccUiLook.RETROID
        )
        assertEquals(Color.Black, orangeMegingiard.retroidContentOnAccent())

        // Every AppThemeOption palette resolves cleanly regardless of UI Look (AYN or RETROID)
        for (look in TccUiLook.entries) {
            for (option in AppThemeOption.entries) {
                val palette = resolveTccPalette(
                    option = option,
                    systemDark = true,
                    customAccentArgb = RetroidPixelTealSwatch.hashCode(),
                    uiLook = look
                )
                assertNotNull("$look x $option must produce valid palette", palette)
                assertTrue(palette.panelBackground.alpha > 0f)
            }
        }
    }

    @Test
    fun `retroidPixelDrawableOrNull maps all quick setting navigation and status icons to rp_ic resources`() {
        for (tile in QuickSettingTileId.entries) {
            val mapped = retroidPixelDrawableOrNull(tile.iconRes)
            assertNotNull("Missing rp_ic_* mapping for ${tile.name}", mapped)
            assertNotEquals(tile.iconRes, mapped)
        }

        val additionalIcons = listOf(
            R.drawable.ic_screen_control,
            R.drawable.ic_task_manage,
            R.drawable.ic_system_set,
            R.drawable.ic_nav_edit,
            R.drawable.ic_qs_swap_screens,
            R.drawable.ic_dual_screen_display,
            R.drawable.ic_just_screen_above,
            R.drawable.ic_just_screen_below,
            R.drawable.ic_fan_quiet_mode,
            R.drawable.ic_fan_smart_mode,
            R.drawable.ic_fan_sport_mode,
            R.drawable.ic_fan_custom_mode,
            R.drawable.ic_ban_on_use,
            R.drawable.ic_brightness_0,
            R.drawable.ic_brightness_1,
            R.drawable.ic_brightness_2,
            R.drawable.ic_volume_0,
            R.drawable.ic_volume_1,
            R.drawable.ic_volume_2,
            R.drawable.ic_volume_3,
            R.drawable.ic_status_wifi_0,
            R.drawable.ic_status_wifi_1,
            R.drawable.ic_status_wifi_2,
            R.drawable.ic_status_wifi_3,
            R.drawable.ic_headset,
            R.drawable.ic_temp,
            R.drawable.ic_fan_normal,
            R.drawable.ic_tip
        )
        for (resId in additionalIcons) {
            val mapped = retroidPixelDrawableOrNull(resId)
            assertNotNull("Expected rp_ic_* mapping for resId=$resId", mapped)
            assertNotEquals(resId, mapped)
        }

        // Preserved multi-color theme preview icons stay untouched
        assertNull(retroidPixelDrawableOrNull(R.drawable.ic_qs_system_theme))
        assertNull(retroidPixelDrawableOrNull(R.drawable.ic_qs_light_theme))
        assertNull(retroidPixelDrawableOrNull(R.drawable.ic_qs_dark_theme))
    }

    @Test
    fun `PixelNotchedShape and PixelCornerCutShape build pixel outlines and fall back to rectangle when zero`() {
        val density = Density(1.33125f)
        val notchedOutline = PixelNotchedShape(9.dp).createOutline(
            size = Size(120f, 80f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        assertTrue(notchedOutline is Outline.Generic)

        val zeroOutline = PixelNotchedShape(0.dp).createOutline(
            size = Size(120f, 80f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        assertTrue(zeroOutline is Outline.Rectangle)

        val cutOutline = PixelCornerCutShape(9.dp).createOutline(
            size = Size(70f, 70f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        assertTrue(cutOutline is Outline.Generic)

        val zeroCutOutline = PixelCornerCutShape(0.dp).createOutline(
            size = Size(70f, 70f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        assertTrue(zeroCutOutline is Outline.Rectangle)
    }
}
