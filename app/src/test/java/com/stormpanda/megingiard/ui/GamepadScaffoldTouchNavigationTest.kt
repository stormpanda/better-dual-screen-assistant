package com.stormpanda.megingiard.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.core.os.HandlerCompat
import kotlin.coroutines.ContinuationInterceptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.stormpanda.betterdualscreenassistant.MainActivity
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.megingiard.settings.ThemeMode
import com.stormpanda.megingiard.update.UpdateManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-xhdpi")
class GamepadScaffoldTouchNavigationTest {

    private val activeControllers = mutableListOf<ActivityController<*>>()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        resetComposeUiDispatcher(installFactory = true)
        resetSystemSettingsRepositorySingleton()
        PrimaryOverlayInputBridge.resetJoystickState()
        DialogToastManager.clear()
        val context = ApplicationProvider.getApplicationContext<Context>()
        SystemSettingsRepository.getInstance(context).resetQuickTilesToDefault()
    }

    @After
    fun tearDown() {
        activeControllers.asReversed().forEach { controller ->
            try {
                controller.pause().stop().destroy()
            } catch (_: Throwable) {
            }
        }
        activeControllers.clear()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        PrimaryOverlayInputBridge.resetJoystickState()
        DialogToastManager.clear()
        unmockkAll()
        resetSystemSettingsRepositorySingleton()
        resetComposeUiDispatcher(installFactory = false)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `MainActivity touch navigation opens Enable Disable Toggles and Custom Color and handles Quick Toggles touch controls`() {
        mockkObject(PServerClient)
        mockkObject(PrivilegedBridge)
        mockkObject(UpdateManager)
        every { PServerClient.isAvailable() } returns true
        every { PServerClient.isStockAssistantEnabled(any()) } returns false
        every { PServerClient.ensurePermissionsGranted(any()) } just runs
        every { PrivilegedBridge.ensureInitialized(any()) } just runs
        every { UpdateManager.init(any()) } just runs
        every { UpdateManager.checkForUpdates(any(), any()) } just runs

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activeControllers.add(controller)
        val activity = controller.get()
        idleCompose(activity, 300)

        assertNotNull("Initial General tab should be visible", findNodeWithTextOrNull(activity, "Replace Stock Assistant"))

        // 1. Tap "Quick Toggles" in sidebar via touch
        tapClickableNodeWithText(activity, "Quick Toggles")
        idleCompose(activity, 300)
        assertNotNull(findNodeWithTextOrNull(activity, "Enable / Disable Toggles"))
        assertNull(findNodeWithTextOrNull(activity, "Replace Stock Assistant"))

        // 2. Tap "Enable / Disable Toggles" via touch -> must open sub-menu, NOT jump back to General
        tapClickableNodeWithText(activity, "Enable / Disable Toggles")
        idleCompose(activity, 500)
        assertNotNull(
            "Enable / Disable sub-page header should be visible",
            findNodeWithTextOrNull(activity, "Toggle Visibility (10 of 22 Active)")
        )
        assertNull(
            "Must not jump back to General tab when opening Enable / Disable Toggles via touch",
            findNodeWithTextOrNull(activity, "Replace Stock Assistant")
        )

        // 3. Tap breadcrumb trail via touch to pop back to Quick Toggles
        tapNodeBoundsViaTouch(activity, findNodeWithText(activity, "QUICK TOGGLES").boundsInWindow)
        idleCompose(activity, 450)
        assertNotNull(findNodeWithTextOrNull(activity, "Enable / Disable Toggles"))
        assertNull(findNodeWithTextOrNull(activity, "Replace Stock Assistant"))

        // 4. Re-enter "Enable / Disable Toggles" via touch, disable one toggle, and return via Android system Back
        tapClickableNodeWithText(activity, "Enable / Disable Toggles")
        idleCompose(activity, 450)
        tapClickableNodeWithText(activity, "Key Focus Lock")
        idleCompose(activity, 200)
        assertNotNull(findNodeWithTextOrNull(activity, "Toggle Visibility (9 of 22 Active)"))

        enterTouchMode(activity)
        activity.onBackPressedDispatcher.onBackPressed()
        idleCompose(activity, 450)
        assertNotNull("Should return to Quick Toggles tab", findNodeWithTextOrNull(activity, "9 / 22 Active"))
        assertNull("Must not jump to General on system Back", findNodeWithTextOrNull(activity, "Replace Stock Assistant"))

        // 5. Two-tap touch tile swap inside Quick Settings Layout must exit active grid mode after swap
        tapClickableNodeWithText(activity, "Performance Mode")
        idleCompose(activity, 150)
        assertNotNull("Grabbing first tile shows Moving pill", findNodeWithTextOrNull(activity, "Moving #1"))

        tapClickableNodeWithText(activity, "Fan Control")
        idleCompose(activity, 200)
        assertNotNull(
            "Completing 2-tap touch tile swap should restore Reorder pill instead of trapping grid active state",
            findNodeWithTextOrNull(activity, "Reorder")
        )

        // 6. "Reset Quick Toggles" two-step confirm card in touch mode: cancel via Back, then confirm via 2 taps
        tapClickableNodeWithText(activity, "Reset Quick Toggles")
        idleCompose(activity, 150)
        assertNotNull(findNodeWithTextOrNull(activity, "Confirm Reset to Default"))

        enterTouchMode(activity)
        activity.onBackPressedDispatcher.onBackPressed()
        idleCompose(activity, 150)
        assertFalse("Back while confirming should cancel confirmation instead of finishing MainActivity", activity.isFinishing)
        assertNotNull(findNodeWithTextOrNull(activity, "Reset Quick Toggles"))

        tapClickableNodeWithText(activity, "Reset Quick Toggles")
        idleCompose(activity, 150)
        tapClickableNodeWithText(activity, "Confirm Reset to Default")
        idleCompose(activity, 250)
        assertNotNull("Toggles should reset to 10 / 22 Active", findNodeWithTextOrNull(activity, "10 / 22 Active"))
        assertNotNull(
            "Reset card should exit confirmation state after confirming in touch mode",
            findNodeWithTextOrNull(activity, "Reset Quick Toggles")
        )
        assertNull(findNodeWithTextOrNull(activity, "Confirm Reset to Default"))

        // 7. Tap "Appearance" in sidebar from "Quick Toggles" (non-General to non-General tab via touch)
        tapClickableNodeWithText(activity, "Appearance")
        idleCompose(activity, 300)
        assertNotNull(findNodeWithTextOrNull(activity, "Theme Source"))
        assertNull(findNodeWithTextOrNull(activity, "Replace Stock Assistant"))

        // 8. Tap "Theme Source" choice card body via touch until "Custom Palette" is active, then open "Custom Color"
        if (findNodeWithTextOrNull(activity, "Custom Color") == null) {
            tapClickableNodeWithText(activity, "Theme Source")
            idleCompose(activity, 250)
        }
        if (findNodeWithTextOrNull(activity, "Custom Color") == null) {
            tapClickableNodeWithText(activity, "Theme Source")
            idleCompose(activity, 250)
        }
        assertNotNull("Custom Color action card should appear under Custom Palette", findNodeWithTextOrNull(activity, "Custom Color"))

        tapClickableNodeWithText(activity, "Custom Color")
        idleCompose(activity, 500)
        assertNotNull("Custom Color sub-page should open via touch", findNodeWithTextOrNull(activity, "Save Color"))
        assertNull("Must not jump back to General when opening Custom Color via touch", findNodeWithTextOrNull(activity, "Replace Stock Assistant"))

        // 9. Tapping Hue slider card header in touch mode should not trigger persistent gamepad L2 toast
        tapClickableNodeWithText(activity, "Hue")
        idleCompose(activity, 150)
        assertNull(DialogToastManager.currentToast.value)

        // 10. Tap "Save Color" via touch -> must return to Appearance without jumping to General
        tapClickableNodeWithText(activity, "Save Color")
        idleCompose(activity, 500)
        assertNotNull(findNodeWithTextOrNull(activity, "Theme Source"))
        assertNull(findNodeWithTextOrNull(activity, "Replace Stock Assistant"))
    }

    @Test
    fun `MainActivity gamepad carousel adjustment keeps focus across theme changes and D-pad Left cycles options without backing out`() {
        mockkObject(PServerClient)
        mockkObject(PrivilegedBridge)
        mockkObject(UpdateManager)
        every { PServerClient.isAvailable() } returns true
        every { PServerClient.isStockAssistantEnabled(any()) } returns false
        every { PServerClient.ensurePermissionsGranted(any()) } just runs
        every { PrivilegedBridge.ensureInitialized(any()) } just runs
        every { UpdateManager.init(any()) } just runs
        every { UpdateManager.checkForUpdates(any(), any()) } just runs

        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SystemSettingsRepository.getInstance(context)
        repo.setThemeMode(com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.STOCK_DARK.id)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activeControllers.add(controller)
        val activity = controller.get()
        idleCompose(activity, 300)

        // 1. Navigate to Quick Toggles via D-pad Down -> enter deck via D-pad Right -> D-pad Down must reach
        //    "Enable / Disable Toggles" directly instead of getting trapped inside ReorderableTccTile children.
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 150)
        assertNotNull(findNodeWithTextOrNull(activity, "Quick Settings Layout"))

        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A)
        idleCompose(activity, 450)
        assertNotNull(
            "D-pad Down from Quick Settings Layout must focus Enable / Disable Toggles directly",
            findNodeWithTextOrNull(activity, "Toggle Visibility (10 of 22 Active)")
        )

        // Back out of Enable / Disable sub-page, then switch to Appearance via R1 bumper
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_B)
        idleCompose(activity, 450)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_R1)
        idleCompose(activity, 250)
        assertNotNull("R1 should switch to Appearance tab", findNodeWithTextOrNull(activity, "Theme Source"))
        assertNotNull("Initial Theme Source should be Stock AYN", findNodeWithTextOrNull(activity, "Stock AYN"))

        // 2. Focus is on "Theme Source" (firstDeckItem). Press A to enter carousel adjustment mode.
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A)
        idleCompose(activity, 100)

        // Press D-pad Right to change Theme Source ("Stock AYN" -> "Custom Palette", mutating LocalTccColors)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(activity, 200)
        assertNotNull(
            "Theme Source should advance from Stock AYN",
            findNodeWithTextOrNull(activity, "Custom Palette") ?: findNodeWithTextOrNull(activity, "Sync with Megingiard")
        )

        // Immediately press D-pad Left -> MUST cycle Theme Source back to "Stock AYN" instead of backing out to sidebar!
        sendKey(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        idleCompose(activity, 200)
        assertNotNull(
            "D-pad Left after changing carousel value must cycle back to Stock AYN instead of backing out of menu",
            findNodeWithTextOrNull(activity, "Stock AYN")
        )

        // Verify we are STILL in adjustment mode inside the deck (NOT in the sidebar): D-pad Down would change category if we backed out to sidebar!
        // First advance to Custom Palette, exit adjustment mode with B, then D-pad Down to "Custom Palette" choice card.
        sendKey(activity, KeyEvent.KEYCODE_DPAD_LEFT) // wraps Stock AYN -> Custom Palette
        idleCompose(activity, 200)
        assertNotNull("Wrapping Left on Theme Source should select Custom Palette", findNodeWithTextOrNull(activity, "Custom Palette"))

        // Dismiss Theme Source adjustment mode with Button B (must stay inside Appearance deck, not back out to sidebar)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_B)
        idleCompose(activity, 150)

        // Move down to "Custom Palette" carousel card and cycle multiple themes Right & Left
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 100)
        assertNotNull("Should still be on Appearance tab (not Updates)", findNodeWithTextOrNull(activity, "Theme Source"))

        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT) // Dark -> Dark OLED
        idleCompose(activity, 150)
        assertNotNull(findNodeWithTextOrNull(activity, "Dark OLED"))

        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT) // Dark OLED -> Norse Forest
        idleCompose(activity, 150)
        assertNotNull(findNodeWithTextOrNull(activity, "Norse Forest"))

        sendKey(activity, KeyEvent.KEYCODE_DPAD_LEFT) // Norse Forest -> Dark OLED
        idleCompose(activity, 150)
        assertNotNull("D-pad Left must cycle Custom Palette back to Dark OLED", findNodeWithTextOrNull(activity, "Dark OLED"))

        sendKey(activity, KeyEvent.KEYCODE_DPAD_LEFT) // Dark OLED -> Dark
        idleCompose(activity, 150)
        assertEquals(
            com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.MEGINGIARD_DARK.id,
            repo.themeMode.value
        )

        // Dismiss Custom Palette adjustment mode with B, move down to Accent Color -> Custom Color, and test Hue slider + Save Color
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_B)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN) // Accent Color
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A) // Enter Accent Color adjustment
        idleCompose(activity, 100)
        val initialAccent = repo.customAccentArgb.value
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(activity, 150)
        val firstPresetAccent = repo.customAccentArgb.value
        assertTrue("D-pad Right on Accent Color should select next preset", firstPresetAccent != initialAccent)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(activity, 150)
        val secondPresetAccent = repo.customAccentArgb.value
        assertTrue("Second D-pad Right on Accent Color should advance preset", secondPresetAccent != firstPresetAccent)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        idleCompose(activity, 150)
        assertEquals("D-pad Left on Accent Color should cycle back without backing out", firstPresetAccent, repo.customAccentArgb.value)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_B) // Exit Accent Color adjustment
        idleCompose(activity, 100)

        // D-pad Down from Accent Color must go straight to "Custom Color" (skipping inner swatches)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A)
        idleCompose(activity, 450)
        assertNotNull("Custom Color sub-page should open via gamepad", findNodeWithTextOrNull(activity, "Save Color"))

        // Adjust Hue slider with gamepad, dismiss with B, and navigate down past Slider thumbs to "Save Color"
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_B)
        idleCompose(activity, 100)
        repeat(4) {
            sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            idleCompose(activity, 80)
        }
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A) // Save Color -> back to Appearance
        idleCompose(activity, 450)
        assertNotNull("Save Color via gamepad should return to Appearance", findNodeWithTextOrNull(activity, "Theme Source"))

        // 3. Hybrid Touch -> Gamepad Carousel: touch General -> touch Appearance (entering Android touch mode),
        //    then use gamepad to enter deck, press A on Theme Source (must enter adjustment mode, NOT cycle on A!),
        //    press D-pad Right to change value, then press D-pad Left to cycle back without backing out to sidebar.
        tapClickableNodeWithText(activity, "General")
        idleCompose(activity, 250)
        tapClickableNodeWithText(activity, "Appearance")
        idleCompose(activity, 250)
        assertNotNull(findNodeWithTextOrNull(activity, "Custom Palette"))

        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN) // Focus recovery -> Appearance category tile
        idleCompose(activity, 150)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT) // Enter Appearance deck -> focuses Theme Source
        idleCompose(activity, 150)
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A) // Must enter adjustment mode WITHOUT cycling Custom Palette -> Stock AYN
        idleCompose(activity, 150)
        assertNotNull(
            "Button A on carousel after touch mode must enter adjustment mode without prematurely changing value",
            findNodeWithTextOrNull(activity, "Custom Palette")
        )

        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT) // Custom Palette -> Stock AYN
        idleCompose(activity, 200)
        assertNotNull(findNodeWithTextOrNull(activity, "Stock AYN"))

        sendKey(activity, KeyEvent.KEYCODE_DPAD_LEFT) // Stock AYN -> Custom Palette (must NOT back out to sidebar!)
        idleCompose(activity, 200)
        assertNotNull(
            "D-pad Left after changing carousel value post-touch must cycle back to Custom Palette without backing out",
            findNodeWithTextOrNull(activity, "Custom Palette")
        )
    }

    @Test
    fun `MainActivity gamepad carousel with OS theme injection defers FabricatedOverlay commits while active and survives CONFIG_ASSETS_PATHS Activity recreation`() {
        mockkObject(PServerClient)
        mockkObject(PrivilegedBridge)
        mockkObject(UpdateManager)
        every { PServerClient.isAvailable() } returns true
        every { PServerClient.isStockAssistantEnabled(any()) } returns false
        every { PServerClient.ensurePermissionsGranted(any()) } just runs
        every { PrivilegedBridge.ensureInitialized(any()) } just runs
        every { UpdateManager.init(any()) } just runs
        every { UpdateManager.checkForUpdates(any(), any()) } just runs

        var osThemeApplyCount = 0
        every {
            PrivilegedBridge.applyOsThemeOverlay(any(), any(), any(), any(), any(), any(), any())
        } answers {
            osThemeApplyCount++
        }
        every { PrivilegedBridge.clearOsThemeOverlay(any(), any(), any()) } just runs

        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SystemSettingsRepository.getInstance(context)
        repo.setThemeMode(com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.MEGINGIARD_DARK.id)
        repo.setInjectOsTheme(true)
        osThemeApplyCount = 0

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activeControllers.add(controller)
        val initialActivity = controller.get()
        initialActivity.onWindowFocusChanged(true)
        idleCompose(initialActivity, 300)

        // 1. Switch to Appearance via R1 x2 (General -> Quick Toggles -> Appearance), enter deck, focus "Custom Palette"
        sendKey(initialActivity, KeyEvent.KEYCODE_BUTTON_R1)
        idleCompose(initialActivity, 150)
        sendKey(initialActivity, KeyEvent.KEYCODE_BUTTON_R1)
        idleCompose(initialActivity, 250)
        assertNotNull(findNodeWithTextOrNull(initialActivity, "Theme Source"))

        sendKey(initialActivity, KeyEvent.KEYCODE_DPAD_RIGHT) // Focus Theme Source
        idleCompose(initialActivity, 100)
        sendKey(initialActivity, KeyEvent.KEYCODE_DPAD_DOWN) // Focus Custom Palette (currently "Dark")
        idleCompose(initialActivity, 100)
        sendKey(initialActivity, KeyEvent.KEYCODE_BUTTON_A) // Enter Custom Palette adjustment mode
        idleCompose(initialActivity, 100)

        // 2. Cycle Custom Palette Right ("Dark" -> "Dark OLED") while Inject Theme Colors into OS is ON:
        //    OS FabricatedOverlay commits MUST be deferred while MainActivity holds active window focus!
        sendKey(initialActivity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(initialActivity, 200)
        assertEquals(
            com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.MEGINGIARD_DARK_OLED.id,
            repo.themeMode.value
        )
        assertEquals(
            "OS FabricatedOverlay apply must be deferred while MainActivity has window focus",
            0,
            osThemeApplyCount
        )

        // Advance one more step ("Dark OLED" -> "Norse Forest") and cycle Left ("Norse Forest" -> "Dark OLED")
        sendKey(initialActivity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(initialActivity, 150)
        assertEquals(
            com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.MEGINGIARD.id,
            repo.themeMode.value
        )
        sendKey(initialActivity, KeyEvent.KEYCODE_DPAD_LEFT)
        idleCompose(initialActivity, 150)
        assertEquals(
            "D-pad Left with OS theme injection ON must cycle back to Dark OLED without backing out",
            com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.MEGINGIARD_DARK_OLED.id,
            repo.themeMode.value
        )

        // 3. Simulate SystemUI notification shade pull-down (onWindowFocusChanged(false)):
        //    Deferred OS FabricatedOverlay commit must flush immediately!
        initialActivity.onWindowFocusChanged(false)
        idleCompose(initialActivity, 100)
        assertEquals(
            "Deferred OS theme overlay must flush as soon as MainActivity loses window focus",
            1,
            osThemeApplyCount
        )

        // 4. Simulate Android CONFIG_ASSETS_PATHS (0x80000000) Activity recreation while Custom Palette
        //    is STILL in carousel adjustment mode (isAdjusting = true)!
        controller.recreate()
        val recreatedActivity = controller.get()
        recreatedActivity.onWindowFocusChanged(true)
        idleCompose(recreatedActivity, 350)

        assertNotNull(
            "Recreated MainActivity must restore Appearance category",
            findNodeWithTextOrNull(recreatedActivity, "Theme Source")
        )
        assertNotNull(
            "Recreated MainActivity must still show Dark OLED on Custom Palette",
            findNodeWithTextOrNull(recreatedActivity, "Dark OLED")
        )

        // Immediately press D-pad Left on the recreated MainActivity WITHOUT pressing Button A again:
        // Custom Palette must have restored BOTH focus AND isAdjusting=true, cycling Dark OLED -> Dark
        // instead of backing out to the sidebar!
        sendKey(recreatedActivity, KeyEvent.KEYCODE_DPAD_LEFT)
        idleCompose(recreatedActivity, 200)
        assertEquals(
            "After CONFIG_ASSETS_PATHS Activity recreation, D-pad Left must still cycle Custom Palette backward instead of backing out to sidebar",
            com.stormpanda.betterdualscreenassistant.ui.theme.AppThemeOption.MEGINGIARD_DARK.id,
            repo.themeMode.value
        )

        // And pressing D-pad Down must NOT switch sidebar category (confirming focus never escaped to sidebar)
        sendKey(recreatedActivity, KeyEvent.KEYCODE_BUTTON_B) // exit adjustment mode
        idleCompose(recreatedActivity, 100)
        sendKey(recreatedActivity, KeyEvent.KEYCODE_DPAD_DOWN) // move to Accent Color inside Appearance deck
        idleCompose(recreatedActivity, 150)
        assertNotNull(
            "D-pad Down after exiting adjustment mode on recreated MainActivity must stay on Appearance tab",
            findNodeWithTextOrNull(recreatedActivity, "Theme Source")
        )
    }

    private enum class TestCategory(val title: String, val icon: ImageVector) {
        GENERAL("General", Icons.Rounded.Tune),
        QUICK_TOGGLES("Quick Toggles", Icons.Rounded.GridView),
        APPEARANCE("Appearance", Icons.Rounded.Palette)
    }

    private enum class TestSubPage(val parent: TestCategory) {
        ENABLE_DISABLE(TestCategory.QUICK_TOGGLES),
        CUSTOM_ACCENT(TestCategory.APPEARANCE)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `GamepadTwoPaneScaffold preserves full gamepad navigation and hybrid touch-to-gamepad focus recovery`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activeControllers.add(controller)
        val activity = controller.get()

        var selectedCategory by mutableStateOf(TestCategory.GENERAL)
        var subPageStack by mutableStateOf<List<TestSubPage>>(emptyList())
        var subPageFirstItemFocused by mutableStateOf(false)
        var enableDisableCardFocused by mutableStateOf(false)
        val boundsByTag = mutableMapOf<String, Rect>()
        val appColors = paletteFor(ThemeMode.DARK, null)

        activity.setContent {
            CompositionLocalProvider(LocalAppColors provides appColors) {
                MaterialTheme(
                    colorScheme = colorSchemeFor(appColors),
                    typography = megingiardTypography
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .focusProperties {
                                    canFocus = false
                                    enter = { FocusRequester.Cancel }
                                }
                        ) {}
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .focusGroup()
                        ) {
                            val currentSubPage = subPageStack.lastOrNull()
                            GamepadTwoPaneScaffold(
                                scrollableDeck = false,
                                isCustomBackActive = subPageStack.isNotEmpty(),
                                onCustomBack = { subPageStack = subPageStack.dropLast(1) },
                                navigationKey = subPageStack,
                                sidebarContent = {
                                    TestCategory.entries.forEach { cat ->
                                        GamepadCategoryTile(
                                            title = cat.title,
                                            icon = cat.icon,
                                            selected = (currentSubPage?.parent ?: selectedCategory) == cat,
                                            onClick = {
                                                selectedCategory = cat
                                                subPageStack = emptyList()
                                            },
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                boundsByTag["cat_${cat.name}"] = coords.boundsInWindow()
                                            }
                                        )
                                    }
                                },
                                content = {
                                    AnimatedContent(
                                        targetState = subPageStack,
                                        transitionSpec = {
                                            val dir = if (targetState.size < initialState.size) -1 else 1
                                            slideInHorizontally { it * dir } togetherWith slideOutHorizontally { -it * dir }
                                        },
                                        label = "testSubPage"
                                    ) { stack ->
                                        val isTarget = stack == subPageStack
                                        val exitingRequester = remember { FocusRequester() }
                                        val page: @Composable () -> Unit = {
                                            when (stack.lastOrNull()) {
                                                null -> {
                                                    GamepadDeck(title = selectedCategory.title) {
                                                        when (selectedCategory) {
                                                            TestCategory.GENERAL -> {
                                                                GamepadToggleCard(
                                                                    title = "Replace Stock Assistant",
                                                                    checked = true,
                                                                    onCheckedChange = {},
                                                                    modifier = Modifier.firstDeckItem()
                                                                )
                                                            }

                                                            TestCategory.QUICK_TOGGLES -> {
                                                                GamepadActionCard(
                                                                    title = "Quick Settings Layout",
                                                                    onClick = {},
                                                                    modifier = Modifier.firstDeckItem()
                                                                )
                                                                GamepadActionCard(
                                                                    title = "Enable / Disable Toggles",
                                                                    icon = Icons.Rounded.Checklist,
                                                                    onClick = {
                                                                        subPageStack = listOf(TestSubPage.ENABLE_DISABLE)
                                                                    },
                                                                    onFocusChanged = { enableDisableCardFocused = it },
                                                                    modifier = Modifier.onGloballyPositioned { coords ->
                                                                        boundsByTag["card_enable_disable"] = coords.boundsInWindow()
                                                                    }
                                                                )
                                                            }

                                                            TestCategory.APPEARANCE -> {
                                                                GamepadActionCard(
                                                                    title = "Custom Color",
                                                                    icon = Icons.Rounded.Colorize,
                                                                    onClick = {
                                                                        subPageStack = listOf(TestSubPage.CUSTOM_ACCENT)
                                                                    },
                                                                    modifier = Modifier.firstDeckItem()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                TestSubPage.ENABLE_DISABLE -> {
                                                    GamepadDeck(breadcrumbs = listOf("Quick Toggles", "Enable / Disable")) {
                                                        GamepadToggleCard(
                                                            title = "Key Focus Lock",
                                                            checked = true,
                                                            onCheckedChange = {},
                                                            onFocusChanged = { subPageFirstItemFocused = it },
                                                            modifier = Modifier.firstDeckItem()
                                                        )
                                                    }
                                                }

                                                TestSubPage.CUSTOM_ACCENT -> {
                                                    GamepadDeck(breadcrumbs = listOf("Appearance", "Custom Color")) {
                                                        GamepadActionCard(
                                                            title = "Save Color",
                                                            onClick = { subPageStack = subPageStack.dropLast(1) },
                                                            modifier = Modifier.firstDeckItem()
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (isTarget) {
                                            page()
                                        } else {
                                            CompositionLocalProvider(
                                                LocalFirstContentRequester provides exitingRequester,
                                                LocalDeckCardRegistry provides null,
                                                LocalLastFocusedDeckTracker provides null,
                                                LocalBreadcrumbConsumer provides null
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .focusProperties {
                                                            canFocus = false
                                                            enter = { FocusRequester.Cancel }
                                                        }
                                                        .focusGroup()
                                                ) {
                                                    page()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        idleCompose(activity, 250)
        assertEquals(TestCategory.GENERAL, selectedCategory)

        // 1. Gamepad D-pad Down in sidebar -> auto-selects Quick Toggles
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 150)
        assertEquals(TestCategory.QUICK_TOGGLES, selectedCategory)

        // 2. Gamepad D-pad Right enters deck -> D-pad Down focuses "Enable / Disable Toggles" -> Button A opens sub-page
        sendKey(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        idleCompose(activity, 100)
        sendKey(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 100)
        assertTrue("Enable / Disable Toggles card should hold gamepad focus", enableDisableCardFocused)

        sendKey(activity, KeyEvent.KEYCODE_BUTTON_A)
        idleCompose(activity, 450)
        assertEquals(TestCategory.QUICK_TOGGLES, selectedCategory)
        assertEquals(listOf(TestSubPage.ENABLE_DISABLE), subPageStack)
        assertTrue(
            "Entering sub-page's first item must retain focus after AnimatedContent slide transition completes",
            subPageFirstItemFocused
        )

        // 3. Gamepad B backs out of sub-page and restores focus to "Enable / Disable Toggles"
        sendKey(activity, KeyEvent.KEYCODE_BUTTON_B)
        idleCompose(activity, 450)
        assertEquals(TestCategory.QUICK_TOGGLES, selectedCategory)
        assertEquals(emptyList<TestSubPage>(), subPageStack)
        assertTrue("Focus should restore to Enable / Disable Toggles after backing out", enableDisableCardFocused)

        // 4. Hybrid Touch -> Gamepad handoff: open sub-page via Touch, then recover gamepad focus without jumping to General
        tapNodeBoundsViaTouch(activity, boundsByTag.getValue("card_enable_disable"))
        idleCompose(activity, 450)
        assertEquals(TestCategory.QUICK_TOGGLES, selectedCategory)
        assertEquals(listOf(TestSubPage.ENABLE_DISABLE), subPageStack)
        assertFalse("Touch navigation should not show gamepad focus ring", subPageFirstItemFocused)

        PrimaryOverlayInputBridge.sendFocusRecovery(KeyEvent.KEYCODE_DPAD_DOWN)
        idleCompose(activity, 200)
        assertEquals("Gamepad focus recovery from touch sub-page must not jump to General", TestCategory.QUICK_TOGGLES, selectedCategory)
        assertEquals(listOf(TestSubPage.ENABLE_DISABLE), subPageStack)
        assertTrue("Gamepad focus recovery should focus sub-page deck item", subPageFirstItemFocused)
    }

    private fun resetSystemSettingsRepositorySingleton() {
        try {
            val field = SystemSettingsRepository::class.java.getDeclaredField("instance").apply {
                isAccessible = true
            }
            field.set(null, null)
        } catch (_: Throwable) {
        }
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
    private fun resetComposeUiDispatcher(installFactory: Boolean) {
        try {
            val clazz = Class.forName("androidx.compose.ui.platform.AndroidUiDispatcher")
            val currentThreadField = clazz.getDeclaredField("currentThread").apply { isAccessible = true }
            (currentThreadField.get(null) as? ThreadLocal<*>)?.remove()

            val mainDispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor]
            if (mainDispatcher != null && clazz.isInstance(mainDispatcher)) {
                val newChoreographer = Choreographer.getInstance()
                val newHandler = HandlerCompat.createAsync(Looper.getMainLooper()) as Handler
                clazz.getDeclaredField("choreographer").apply {
                    isAccessible = true
                    set(mainDispatcher, newChoreographer)
                }
                val frameClock = AndroidUiDispatcher.Main[MonotonicFrameClock]
                if (frameClock != null) {
                    frameClock.javaClass.getDeclaredField("choreographer").apply {
                        isAccessible = true
                        set(frameClock, newChoreographer)
                    }
                }
                clazz.getDeclaredField("handler").apply {
                    isAccessible = true
                    set(mainDispatcher, newHandler)
                }
                clazz.getDeclaredField("toRunOnFrame").apply {
                    isAccessible = true
                    (get(mainDispatcher) as? MutableList<*>)?.clear()
                }
                clazz.getDeclaredField("scheduledFrameDispatch").apply {
                    isAccessible = true
                    setBoolean(mainDispatcher, false)
                }
                val scheduledTrampolineField = clazz.getDeclaredField("scheduledTrampolineDispatch").apply {
                    isAccessible = true
                }
                val dispatchCallback = clazz.getDeclaredField("dispatchCallback").apply {
                    isAccessible = true
                }.get(mainDispatcher) as Runnable
                scheduledTrampolineField.setBoolean(mainDispatcher, true)
                newHandler.post(dispatchCallback)
                shadowOf(Looper.getMainLooper()).idle()
            }

            if (installFactory) {
                WindowRecomposerPolicy.setFactory { rootView ->
                    rootView.createLifecycleAwareWindowRecomposer(AndroidUiDispatcher.CurrentThread)
                }
            } else {
                WindowRecomposerPolicy.setFactory(WindowRecomposerFactory.LifecycleAware)
            }
        } catch (_: Throwable) {
        }
    }

    private fun idleCompose(activity: ComponentActivity, advanceMs: Long) {
        val steps = (advanceMs / 16L).coerceAtLeast(2L)
        val decor = activity.window.decorView
        val canvas = Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
        val w = if (decor.width > 0) decor.width else 2560
        val h = if (decor.height > 0) decor.height else 1600
        repeat(steps.toInt()) { stepIndex ->
            if (stepIndex == 2 && advanceMs >= 50L) {
                Thread.sleep(65L)
            }
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, w, h)
            decor.draw(canvas)
        }
    }

    private fun findComposeHostView(root: View): View {
        if (root.javaClass.name.contains("AndroidComposeView")) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findComposeHostView(root.getChildAt(i))
                if (found.javaClass.name.contains("AndroidComposeView")) return found
            }
        }
        return root
    }

    private fun allSemanticsNodes(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap { allSemanticsNodes(it) }

    private fun findNodeWithTextOrNull(activity: ComponentActivity, substring: String): SemanticsNode? {
        val rootForTest = findComposeHostView(activity.window.decorView) as RootForTest
        return allSemanticsNodes(rootForTest.semanticsOwner.unmergedRootSemanticsNode).firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(substring) } == true
        }
    }

    private fun findNodeWithText(activity: ComponentActivity, substring: String): SemanticsNode =
        findNodeWithTextOrNull(activity, substring)
            ?: throw AssertionError("Expected Compose node containing text '$substring'")

    private fun tapClickableNodeWithText(activity: ComponentActivity, substring: String) {
        val textNode = findNodeWithText(activity, substring)
        var clickableNode: SemanticsNode? = textNode
        while (clickableNode != null && !clickableNode.config.contains(SemanticsActions.OnClick)) {
            clickableNode = clickableNode.parent
        }
        tapNodeBoundsViaTouch(activity, (clickableNode ?: textNode).boundsInWindow)
    }

    private fun enterTouchMode(activity: ComponentActivity) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        val decor = activity.window.decorView
        try {
            val viewRootImpl = decor.javaClass.getMethod("getViewRootImpl").invoke(decor)
            if (viewRootImpl != null) {
                val ensureTouchMode = viewRootImpl.javaClass.getDeclaredMethod(
                    "ensureTouchMode",
                    Boolean::class.javaPrimitiveType
                )
                ensureTouchMode.isAccessible = true
                ensureTouchMode.invoke(viewRootImpl, true)
            }
        } catch (_: Throwable) {
        }
    }

    private fun tapNodeBoundsViaTouch(activity: ComponentActivity, boundsInWindow: Rect) {
        enterTouchMode(activity)
        val x = boundsInWindow.center.x
        val y = boundsInWindow.center.y
        val now = SystemClock.uptimeMillis()

        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        activity.dispatchTouchEvent(down)
        idleCompose(activity, 30)
        activity.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun sendKey(activity: ComponentActivity, keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        idleCompose(activity, 16)
        activity.dispatchKeyEvent(KeyEvent(now, now + 16, KeyEvent.ACTION_UP, keyCode, 0))
    }
}
