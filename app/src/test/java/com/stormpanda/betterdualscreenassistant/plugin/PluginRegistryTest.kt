package com.stormpanda.betterdualscreenassistant.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.BadParcelableException
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.DirectBootPrefs
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class PluginRegistryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DirectBootPrefs.open(context, PluginRegistry.PREFS_NAME).edit().clear().commit()
        DirectBootPrefs.open(context, SystemSettingsRepository.PREFS_NAME).edit().clear().commit()
        PluginRegistry.resetForTesting()
    }

    @After
    fun tearDown() {
        PluginRegistry.resetForTesting()
    }

    private fun installFakeFlatPlugin(
        packageName: String,
        appLabel: String = "Demo Plugin",
        receiverClass: String = "$packageName.DemoReceiver",
        exported: Boolean = true,
        apiVersion: Int = BdsaPluginContract.API_VERSION,
        tileId: String = "boost",
        tileTitle: String = "Boost",
        pluginDescription: String = "Controls custom turbo profiles",
        tileDescription: String = "Toggle turbo boost",
        author: String = "ThirdPartyDev",
        settingsActivity: String? = ".SettingsActivity",
        supportsLongPress: Boolean = true,
        dismissOnClick: Boolean = false,
        supportedDevices: String = "all"
    ) {
        val shadowPm = shadowOf(context.packageManager)
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.nonLocalizedLabel = appLabel
        }
        val receiverInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = receiverClass
            this.exported = exported
            this.applicationInfo = appInfo
            this.metaData = Bundle().apply {
                putInt(BdsaPluginContract.META_API_VERSION, apiVersion)
                putString(BdsaPluginContract.META_PLUGIN_DESCRIPTION, pluginDescription)
                putString(BdsaPluginContract.META_PLUGIN_AUTHOR, author)
                if (settingsActivity != null) {
                    putString(BdsaPluginContract.META_SETTINGS_ACTIVITY, settingsActivity)
                }
                putString(BdsaPluginContract.META_TILE_ID, tileId)
                putString(BdsaPluginContract.META_TILE_TITLE, tileTitle)
                putString(BdsaPluginContract.META_TILE_DESCRIPTION, tileDescription)
                putBoolean(BdsaPluginContract.META_SUPPORTS_LONG_PRESS, supportsLongPress)
                putBoolean(BdsaPluginContract.META_DISMISS_ON_CLICK, dismissOnClick)
                putString(BdsaPluginContract.META_SUPPORTED_DEVICES, supportedDevices)
            }
        }
        val pkgInfo = PackageInfo().apply {
            this.packageName = packageName
            this.versionName = "1.2.3"
            this.applicationInfo = appInfo
            this.receivers = arrayOf(receiverInfo)
        }
        shadowPm.installPackage(pkgInfo)
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = receiverInfo
        }
        shadowPm.addResolveInfoForIntent(
            Intent(BdsaPluginContract.ACTION_PLUGIN_TILE),
            resolveInfo
        )
    }

    @Test
    fun `contract key parsing validation and device filtering work`() {
        assertTrue(BdsaPluginContract.isValidTileId("boost_mode-1.0"))
        assertFalse(BdsaPluginContract.isValidTileId(""))
        assertFalse(BdsaPluginContract.isValidTileId("bad:id"))
        assertFalse(BdsaPluginContract.isValidTileId("has space"))

        val key = BdsaPluginContract.pluginTileKey("com.example.foo", "my_tile")
        assertEquals("plugin:com.example.foo:my_tile", key)
        assertTrue(BdsaPluginContract.isPluginTileKey(key))
        assertEquals("com.example.foo" to "my_tile", BdsaPluginContract.parsePluginTileKey(key))
        assertNull(BdsaPluginContract.parsePluginTileKey("focus_lock"))
        assertNull(BdsaPluginContract.parsePluginTileKey("plugin:missing_colon"))

        assertEquals(setOf("all"), BdsaPluginContract.parseSupportedDevices(null))
        assertEquals(setOf("all"), BdsaPluginContract.parseSupportedDevices("ayn_thor, all"))
        assertEquals(
            setOf("ayn_thor", "retroid_pocket_duo"),
            BdsaPluginContract.parseSupportedDevices("AYN_THOR, retroid_pocket_duo")
        )
    }

    @Test
    fun `discovers exported plugins with default-OFF tiles and skips non-exported receivers`() {
        installFakeFlatPlugin(
            packageName = "com.example.good",
            appLabel = "Turbo Plugin",
            tileId = "turbo",
            tileTitle = "Turbo"
        )
        installFakeFlatPlugin(
            packageName = "com.example.private",
            appLabel = "Private Plugin",
            exported = false
        )

        val registry = PluginRegistry.getInstance(context)
        val plugins = registry.plugins.value
        assertEquals(1, plugins.size)
        val plugin = plugins.first()
        assertEquals("com.example.good", plugin.packageName)
        assertEquals("Turbo Plugin", plugin.appName)
        assertEquals("ThirdPartyDev", plugin.author)
        assertEquals("Controls custom turbo profiles", plugin.description)
        assertEquals(
            ComponentName("com.example.good", "com.example.good.SettingsActivity"),
            plugin.settingsActivity
        )
        assertTrue(plugin.isApiCompatible)
        assertTrue(plugin.isPluginEnabled)
        assertEquals(1, plugin.tiles.size)
        assertTrue(registry.enabledPluginTileKeys.value.isEmpty())
        assertTrue(registry.activePluginTilesMap(DeviceProfile.AYN_THOR).isEmpty())
    }

    @Test
    fun `incompatible apiVersion plugins are listed but cannot enable tiles or call provider`() {
        installFakeFlatPlugin(
            packageName = "com.example.future",
            apiVersion = 99,
            tileId = "future_tile"
        )

        val registry = PluginRegistry.getInstance(context)
        val plugin = registry.findPluginForPackage("com.example.future")
        assertNotNull(plugin)
        assertFalse(plugin!!.isApiCompatible)

        val tileKey = BdsaPluginContract.pluginTileKey("com.example.future", "future_tile")
        assertFalse(registry.setPluginTileEnabled(tileKey, true))

        val reply = registry.handleProviderCall(
            callingPackage = "com.example.future",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "future_tile")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
            }
        )
        assertFalse(reply.getBoolean(BdsaPluginContract.EXTRA_OK))
    }

    @Test
    fun `caller verification blocks spoofing and allows legitimate state updates and picker dialogs`() {
        installFakeFlatPlugin(packageName = "com.example.alpha", tileId = "alpha_tile", tileTitle = "Alpha")
        installFakeFlatPlugin(packageName = "com.example.beta", tileId = "beta_tile", tileTitle = "Beta")

        val registry = PluginRegistry.getInstance(context)
        val alphaKey = BdsaPluginContract.pluginTileKey("com.example.alpha", "alpha_tile")
        assertTrue(registry.setPluginTileEnabled(alphaKey, true))

        // Unregistered caller rejected
        val rogueReply = registry.handleProviderCall(
            callingPackage = "com.evil.spoof",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "alpha_tile")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
            }
        )
        assertFalse(rogueReply.getBoolean(BdsaPluginContract.EXTRA_OK))

        // Registered plugin beta trying to mutate alpha's tile ID is rejected
        val crossReply = registry.handleProviderCall(
            callingPackage = "com.example.beta",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "alpha_tile")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
            }
        )
        assertFalse(crossReply.getBoolean(BdsaPluginContract.EXTRA_OK))

        // Legitimate update succeeds and clamps long labels
        val overlongLabel = "A".repeat(120)
        val okReply = registry.handleProviderCall(
            callingPackage = "com.example.alpha",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "alpha_tile")
                putString(BdsaPluginContract.EXTRA_LABEL, overlongLabel)
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
                putBoolean(BdsaPluginContract.EXTRA_ENABLED, false)
            }
        )
        assertTrue(okReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        val state = registry.tileRuntimeStates.value[alphaKey]
        assertNotNull(state)
        assertEquals(BdsaPluginContract.MAX_LABEL_LENGTH, state!!.label!!.length)
        assertTrue(state.selected)
        assertFalse(state.enabled)

        // show_picker rejected when overlay is closed, rejected without recent interaction,
        // and accepted after user interaction when open (and capped at MAX_PICKER_OPTIONS)
        val pickerExtras = Bundle().apply {
            putString(BdsaPluginContract.EXTRA_TILE_ID, "alpha_tile")
            putString(BdsaPluginContract.EXTRA_DIALOG_TITLE, "Choose Mode")
            putStringArrayList(
                BdsaPluginContract.EXTRA_OPTION_IDS,
                ArrayList((1..20).map { "opt_$it" })
            )
            putStringArrayList(
                BdsaPluginContract.EXTRA_OPTION_LABELS,
                ArrayList((1..20).map { "Option $it" })
            )
            putString(BdsaPluginContract.EXTRA_SELECTED_OPTION_ID, "opt_2")
        }
        val closedPickerReply = registry.handleProviderCall(
            callingPackage = "com.example.alpha",
            method = BdsaPluginContract.METHOD_SHOW_PICKER,
            extras = pickerExtras
        )
        assertFalse(closedPickerReply.getBoolean(BdsaPluginContract.EXTRA_OK))

        registry.setOverlayShowing(true)
        val noInteractionPickerReply = registry.handleProviderCall(
            callingPackage = "com.example.alpha",
            method = BdsaPluginContract.METHOD_SHOW_PICKER,
            extras = pickerExtras
        )
        assertFalse(noInteractionPickerReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("no_recent_interaction", noInteractionPickerReply.getString(BdsaPluginContract.EXTRA_ERROR))

        val alphaTile = registry.findPluginForPackage("com.example.alpha")!!.tiles.first()
        registry.dispatchTileClick(alphaTile, isDarkTheme = false)
        val openPickerReply = registry.handleProviderCall(
            callingPackage = "com.example.alpha",
            method = BdsaPluginContract.METHOD_SHOW_PICKER,
            extras = pickerExtras
        )
        assertTrue(openPickerReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        val picker = registry.activePickerRequest.value
        assertNotNull(picker)
        assertEquals(BdsaPluginContract.MAX_PICKER_OPTIONS, picker!!.options.size)
        assertEquals("opt_2", picker.selectedOptionId)

        // get_host_info reports caller's enabled tile IDs
        val hostInfo = registry.handleProviderCall(
            callingPackage = "com.example.alpha",
            method = BdsaPluginContract.METHOD_GET_HOST_INFO,
            extras = null
        )
        assertTrue(hostInfo.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals(BdsaPluginContract.API_VERSION, hostInfo.getInt(BdsaPluginContract.EXTRA_API_VERSION))
        assertTrue(hostInfo.getBoolean(BdsaPluginContract.EXTRA_IS_PLUGIN_ENABLED))
        assertEquals(arrayListOf("alpha_tile"), hostInfo.getStringArrayList(BdsaPluginContract.EXTRA_ENABLED_TILE_IDS))
    }

    @Test
    fun `click and long-click broadcasts include stopped-package flag and dual-screen extras`() {
        installFakeFlatPlugin(packageName = "com.example.disp", tileId = "disp_tile")
        val registry = PluginRegistry.getInstance(context)
        val tile = registry.findPluginForPackage("com.example.disp")!!.tiles.first()

        registry.dispatchTileClick(tile, isDarkTheme = true)
        registry.dispatchTileLongClick(tile, isDarkTheme = false)

        val broadcasts = shadowOf(context as android.app.Application).broadcastIntents
        val clickIntent = broadcasts.firstOrNull { it.action == BdsaPluginContract.ACTION_TILE_CLICK }
        val longClickIntent = broadcasts.firstOrNull { it.action == BdsaPluginContract.ACTION_TILE_LONG_CLICK }

        assertNotNull(clickIntent)
        assertNotNull(longClickIntent)
        assertTrue((clickIntent!!.flags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES) != 0)
        assertEquals("disp_tile", clickIntent.getStringExtra(BdsaPluginContract.EXTRA_TILE_ID))
        assertEquals(BdsaPluginContract.API_VERSION, clickIntent.getIntExtra(BdsaPluginContract.EXTRA_API_VERSION, 0))
        assertTrue(clickIntent.getBooleanExtra(BdsaPluginContract.EXTRA_IS_DARK_THEME, false))
        assertEquals(0, clickIntent.getIntExtra(BdsaPluginContract.EXTRA_TOP_DISPLAY_ID, -1))
        assertEquals("com.example.disp", clickIntent.component?.packageName)
        assertFalse(longClickIntent!!.getBooleanExtra(BdsaPluginContract.EXTRA_IS_DARK_THEME, true))
    }

    @Test
    fun `update preserves enabled tile and state while uninstall prunes persisted data`() {
        installFakeFlatPlugin(packageName = "com.example.life", tileId = "life_tile")
        val registry = PluginRegistry.getInstance(context)
        val tileKey = BdsaPluginContract.pluginTileKey("com.example.life", "life_tile")
        assertTrue(registry.setPluginTileEnabled(tileKey, true))
        registry.handleProviderCall(
            callingPackage = "com.example.life",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "life_tile")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
            }
        )

        var removedCallbackKeys: Set<String> = emptySet()
        registry.onPluginTilesRemovedListener = { removedCallbackKeys = it }

        // Simulating update (isReplacing = true) must preserve state
        registry.onPackageChanged(Intent.ACTION_PACKAGE_REMOVED, "com.example.life", isReplacing = true)
        assertTrue(tileKey in registry.enabledPluginTileKeys.value)
        assertTrue(registry.tileRuntimeStates.value[tileKey]?.selected == true)
        assertTrue(removedCallbackKeys.isEmpty())

        // Simulating full uninstall (isReplacing = false) must prune state
        registry.onPackageChanged(Intent.ACTION_PACKAGE_REMOVED, "com.example.life", isReplacing = false)
        assertFalse(tileKey in registry.enabledPluginTileKeys.value)
        assertNull(registry.tileRuntimeStates.value[tileKey])
        assertTrue(tileKey in removedCallbackKeys)
    }

    @Test
    fun `unified quick tile order interleaves plugin tiles, reorders cleanly, and resets on default`() {
        installFakeFlatPlugin(packageName = "com.example.order", tileId = "my_tile")
        val registry = PluginRegistry.getInstance(context)
        val repo = SystemSettingsRepository.getInstance(context)
        val pluginKey = BdsaPluginContract.pluginTileKey("com.example.order", "my_tile")

        assertTrue(registry.setPluginTileEnabled(pluginKey, true))
        repo.ensureQuickTileKeyInOrder(pluginKey)
        assertTrue(pluginKey in repo.unifiedQuickTileOrder.value)

        // Move plugin tile to slot 0 among active tiles
        val activeBuiltIns = repo.quickTileOrder.value
            .filter { it !in repo.disabledQuickTiles.value && it.isSupportedOn(DeviceProfile.AYN_THOR, false) }
            .map { it.key }
        val activeKeys = activeBuiltIns + pluginKey
        repo.moveActiveUnifiedQuickTile(
            fromActiveIndex = activeKeys.lastIndex,
            toActiveIndex = 0,
            activeOrderedKeys = activeKeys
        )
        assertEquals(pluginKey, repo.unifiedQuickTileOrder.value.first())

        // Reset to defaults disables plugin tiles and restores built-in order
        repo.resetQuickTilesToDefault()
        assertFalse(pluginKey in repo.unifiedQuickTileOrder.value)
        assertTrue(registry.enabledPluginTileKeys.value.isEmpty())
        assertEquals(QuickSettingTileId.defaultOrder, repo.quickTileOrder.value)
    }

    @Test
    fun `native live timer fields and overlay refresh interval persist and clamp cleanly`() {
        installFakeFlatPlugin(packageName = "com.example.timer", tileId = "session_timer")
        val registry = PluginRegistry.getInstance(context)
        val tileKey = BdsaPluginContract.pluginTileKey("com.example.timer", "session_timer")
        assertTrue(registry.setPluginTileEnabled(tileKey, true))

        val nowMs = 1_000_000L
        val targetFinishMs = nowMs + 95_000L // 1m 35s
        val reply = registry.handleProviderCall(
            callingPackage = "com.example.timer",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "session_timer")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
                putLong(BdsaPluginContract.EXTRA_TIMER_BASE_MS, targetFinishMs)
                putBoolean(BdsaPluginContract.EXTRA_TIMER_COUNT_DOWN, true)
                putString(BdsaPluginContract.EXTRA_TIMER_FORMAT, "Timer\n%s")
                putString(BdsaPluginContract.EXTRA_TIMER_FINISHED_LABEL, "Time's Up!")
                putLong(BdsaPluginContract.EXTRA_REFRESH_INTERVAL_MS, 250L) // should clamp up to 1000ms
            }
        )
        assertTrue(reply.getBoolean(BdsaPluginContract.EXTRA_OK))

        val state = registry.tileRuntimeStates.value[tileKey]!!
        assertEquals(targetFinishMs, state.timerBaseMs)
        assertTrue(state.timerCountDown)
        assertEquals("Timer\n%s", state.timerFormat)
        assertEquals("Time's Up!", state.timerFinishedLabel)
        assertEquals(BdsaPluginContract.MIN_REFRESH_INTERVAL_MS, state.refreshIntervalMs)

        // Mid-countdown formatting
        val (midLabel, midFinished) = BdsaPluginContract.resolveLiveTimerLabel(state, nowMs)
        assertEquals("Timer\n01:35", midLabel)
        assertFalse(midFinished)

        // Finished countdown formatting
        val (doneLabel, doneFinished) = BdsaPluginContract.resolveLiveTimerLabel(state, targetFinishMs + 500L)
        assertEquals("Time's Up!", doneLabel)
        assertTrue(doneFinished)

        // Clearing timer_base_ms = 0 clears chronometer cleanly
        registry.handleProviderCall(
            callingPackage = "com.example.timer",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "session_timer")
                putLong(BdsaPluginContract.EXTRA_TIMER_BASE_MS, 0L)
                putLong(BdsaPluginContract.EXTRA_REFRESH_INTERVAL_MS, 0L)
            }
        )
        val cleared = registry.tileRuntimeStates.value[tileKey]!!
        assertEquals(0L, cleared.timerBaseMs)
        assertEquals(0L, cleared.refreshIntervalMs)
        assertEquals(null to false, BdsaPluginContract.resolveLiveTimerLabel(cleared, nowMs))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `un-enabled plugin cannot dismiss overlay or spam toasts`() = runTest {
        installFakeFlatPlugin(packageName = "com.example.idle", tileId = "idle_tile")
        val registry = PluginRegistry.getInstance(context)
        registry.setOverlayShowing(true)

        val toasts = mutableListOf<String>()
        val dismisses = mutableListOf<Long>()
        val toastJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.toastEvents.collect { toasts.add(it.message) }
        }
        val dismissJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.dismissOverlayEvents.collect { dismisses.add(it.eventId) }
        }

        val toastReply = registry.handleProviderCall(
            callingPackage = "com.example.idle",
            method = BdsaPluginContract.METHOD_SHOW_TOAST,
            extras = Bundle().apply { putString(BdsaPluginContract.EXTRA_MESSAGE, "Spam") }
        )
        assertFalse(toastReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("tile_not_enabled", toastReply.getString(BdsaPluginContract.EXTRA_ERROR))

        val dismissReply = registry.handleProviderCall(
            callingPackage = "com.example.idle",
            method = BdsaPluginContract.METHOD_DISMISS_OVERLAY,
            extras = null
        )
        assertFalse(dismissReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("tile_not_enabled", dismissReply.getString(BdsaPluginContract.EXTRA_ERROR))

        val updateReply = registry.handleProviderCall(
            callingPackage = "com.example.idle",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "idle_tile")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
                putString(BdsaPluginContract.EXTRA_TOAST, "Sneaky Toast")
                putBoolean(BdsaPluginContract.EXTRA_DISMISS_OVERLAY, true)
            }
        )
        assertTrue(updateReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertTrue(toasts.isEmpty())
        assertTrue(dismisses.isEmpty())

        toastJob.cancel()
        dismissJob.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `enabled plugin requires interaction to dismiss or show picker and option selection emits extras`() = runTest {
        installFakeFlatPlugin(packageName = "com.example.active", tileId = "act_tile")
        val registry = PluginRegistry.getInstance(context)
        val tileKey = BdsaPluginContract.pluginTileKey("com.example.active", "act_tile")
        assertTrue(registry.setPluginTileEnabled(tileKey, true))
        registry.setOverlayShowing(true)

        val toasts = mutableListOf<String>()
        val dismisses = mutableListOf<Long>()
        val toastJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.toastEvents.collect { toasts.add(it.message) }
        }
        val dismissJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            registry.dismissOverlayEvents.collect { dismisses.add(it.eventId) }
        }

        // Without recent click: dismiss_overlay and update_tile(dismiss_overlay=true) must not dismiss
        val noClickDismiss = registry.handleProviderCall(
            callingPackage = "com.example.active",
            method = BdsaPluginContract.METHOD_DISMISS_OVERLAY,
            extras = null
        )
        assertFalse(noClickDismiss.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("no_recent_interaction", noClickDismiss.getString(BdsaPluginContract.EXTRA_ERROR))

        registry.handleProviderCall(
            callingPackage = "com.example.active",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "act_tile")
                putBoolean(BdsaPluginContract.EXTRA_DISMISS_OVERLAY, true)
            }
        )
        assertTrue(dismisses.isEmpty())

        // After click: show_toast, show_picker, dispatchOptionSelected (with dismiss), and dismiss_overlay work
        val tile = registry.findTileDefinition(tileKey)!!
        registry.dispatchTileClick(tile, isDarkTheme = true)

        val toastReply = registry.handleProviderCall(
            callingPackage = "com.example.active",
            method = BdsaPluginContract.METHOD_SHOW_TOAST,
            extras = Bundle().apply { putString(BdsaPluginContract.EXTRA_MESSAGE, "Hello!") }
        )
        assertTrue(toastReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals(listOf("Hello!"), toasts)

        val pickerReply = registry.handleProviderCall(
            callingPackage = "com.example.active",
            method = BdsaPluginContract.METHOD_SHOW_PICKER,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "act_tile")
                putStringArrayList(BdsaPluginContract.EXTRA_OPTION_IDS, arrayListOf("a", "b"))
                putStringArrayList(BdsaPluginContract.EXTRA_OPTION_LABELS, arrayListOf("Opt A", "Opt B"))
                putBoolean(BdsaPluginContract.EXTRA_DISMISS_OVERLAY_ON_SELECT, true)
            }
        )
        assertTrue(pickerReply.getBoolean(BdsaPluginContract.EXTRA_OK))
        val pickerReq = registry.activePickerRequest.value!!

        registry.dispatchOptionSelected(pickerReq, optionId = "b", isDarkTheme = true)
        val broadcasts = shadowOf(context as android.app.Application).broadcastIntents
        val optionIntent = broadcasts.lastOrNull { it.action == BdsaPluginContract.ACTION_TILE_OPTION_SELECTED }
        assertNotNull(optionIntent)
        assertEquals("b", optionIntent!!.getStringExtra(BdsaPluginContract.EXTRA_OPTION_ID))
        assertEquals(1, dismisses.size)

        val okDismiss = registry.handleProviderCall(
            callingPackage = "com.example.active",
            method = BdsaPluginContract.METHOD_DISMISS_OVERLAY,
            extras = null
        )
        assertTrue(okDismiss.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals(2, dismisses.size)

        toastJob.cancel()
        dismissJob.cancel()
    }

    @Test
    fun `clearActivePicker with expectedRequestId preserves newer chained picker`() {
        installFakeFlatPlugin(packageName = "com.example.chain", tileId = "chain_tile")
        val registry = PluginRegistry.getInstance(context)
        val tileKey = BdsaPluginContract.pluginTileKey("com.example.chain", "chain_tile")
        assertTrue(registry.setPluginTileEnabled(tileKey, true))
        registry.setOverlayShowing(true)
        registry.dispatchTileClick(registry.findTileDefinition(tileKey)!!, isDarkTheme = false)

        val extras = Bundle().apply {
            putString(BdsaPluginContract.EXTRA_TILE_ID, "chain_tile")
            putStringArrayList(BdsaPluginContract.EXTRA_OPTION_IDS, arrayListOf("x"))
            putStringArrayList(BdsaPluginContract.EXTRA_OPTION_LABELS, arrayListOf("X"))
        }
        assertTrue(registry.handleProviderCall("com.example.chain", BdsaPluginContract.METHOD_SHOW_PICKER, extras).getBoolean(BdsaPluginContract.EXTRA_OK))
        val firstId = registry.activePickerRequest.value!!.requestId

        assertTrue(registry.handleProviderCall("com.example.chain", BdsaPluginContract.METHOD_SHOW_PICKER, extras).getBoolean(BdsaPluginContract.EXTRA_OK))
        val secondId = registry.activePickerRequest.value!!.requestId
        assertTrue(secondId != firstId)

        // Clearing with stale firstId must NOT dismiss secondId
        registry.clearActivePicker(expectedRequestId = firstId)
        assertEquals(secondId, registry.activePickerRequest.value?.requestId)

        // Clearing with matching secondId dismisses it
        registry.clearActivePicker(expectedRequestId = secondId)
        assertNull(registry.activePickerRequest.value)
    }

    @Test
    fun `rate limiting and unknown caller cooldown throttle repeated IPC calls`() {
        installFakeFlatPlugin(packageName = "com.example.spam", tileId = "spam_tile")
        val registry = PluginRegistry.getInstance(context)

        // 20 calls succeed within 1s window; 21st is rate-limited
        for (i in 1..20) {
            val res = registry.handleProviderCall("com.example.spam", BdsaPluginContract.METHOD_GET_HOST_INFO, null)
            assertTrue("Call $i should succeed", res.getBoolean(BdsaPluginContract.EXTRA_OK))
        }
        val limited = registry.handleProviderCall("com.example.spam", BdsaPluginContract.METHOD_GET_HOST_INFO, null)
        assertFalse(limited.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("rate_limited", limited.getString(BdsaPluginContract.EXTRA_ERROR))

        // Unknown caller triggers 1 scan then hits 10s cooldown before scanning again
        val firstMiss = registry.handleProviderCall("com.example.late", BdsaPluginContract.METHOD_UPDATE_TILE, null)
        assertFalse(firstMiss.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("plugin_not_registered_or_disabled", firstMiss.getString(BdsaPluginContract.EXTRA_ERROR))

        installFakeFlatPlugin(packageName = "com.example.late", tileId = "late_tile")
        val withinCooldown = registry.handleProviderCall(
            "com.example.late",
            BdsaPluginContract.METHOD_UPDATE_TILE,
            Bundle().apply { putString(BdsaPluginContract.EXTRA_TILE_ID, "late_tile") }
        )
        assertFalse(withinCooldown.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertNull(registry.findPluginForPackage("com.example.late"))

        ShadowSystemClock.advanceBy(Duration.ofSeconds(11))
        val afterCooldown = registry.handleProviderCall(
            "com.example.late",
            BdsaPluginContract.METHOD_UPDATE_TILE,
            Bundle().apply { putString(BdsaPluginContract.EXTRA_TILE_ID, "late_tile") }
        )
        assertTrue(afterCooldown.getBoolean(BdsaPluginContract.EXTRA_OK))
    }

    @Test
    fun `malformed extras bundle returns invalid_extras instead of crashing`() {
        installFakeFlatPlugin(packageName = "com.example.badbundle", tileId = "t1")
        val registry = PluginRegistry.getInstance(context)
        val badExtras = mockk<Bundle> {
            every { getString(any()) } throws BadParcelableException("untrusted parcelable")
        }
        val reply = registry.handleProviderCall(
            callingPackage = "com.example.badbundle",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = badExtras
        )
        assertFalse(reply.getBoolean(BdsaPluginContract.EXTRA_OK))
        assertEquals("invalid_extras", reply.getString(BdsaPluginContract.EXTRA_ERROR))
    }

    @Test
    fun `parseXmlPluginConfig parses multi-tile config, enforces caps, and skips invalid or duplicate tiles`() {
        val registry = PluginRegistry.getInstance(context)
        val mockRes = mockk<Resources>()
        every { mockRes.getString(1001) } returns "XML Plugin Desc"
        every { mockRes.getString(1002) } returns "Tile One\nWrapped"
        every { mockRes.getIdentifier("ic_px", "drawable", "com.example.xml") } returns 2002

        val extraTiles = (3..20).joinToString("\n") { idx ->
            """<tile id="tile_$idx" title="Tile $idx" />"""
        }
        val xml = """
            <bdsa-plugin
                apiVersion="1"
                author="XmlAuthor"
                description="@string/plugin_desc"
                settingsActivity=".XmlSettingsActivity">
                <tile
                    id="tile_1"
                    title="@string/tile_1_title"
                    description="First tile"
                    icon="@drawable/ic_1"
                    pixelIcon="@drawable/ic_px"
                    supportsLongPress="true"
                    dismissOnClick="true"
                    dismissOnLongClick="false"
                    supportedDevices="ayn_thor"
                    refreshIntervalMs="250" />
                <tile id="tile_1" title="Duplicate Ignored" />
                <tile id="bad:id" title="Invalid Id Ignored" />
                <tile id="tile_2" title="Tile 2" supportedDevices="retroid_pocket_duo" refreshIntervalMs="5000" />
                $extraTiles
            </bdsa-plugin>
        """.trimIndent()

        val stringIds = mapOf("@string/plugin_desc" to 1001, "@string/tile_1_title" to 1002)
        val drawableIds = mapOf("@drawable/ic_1" to 2001)
        val parser = createXmlResourceParser(xml, stringIds + drawableIds)
        every { mockRes.getXml(999) } returns parser

        val plugin = registry.parseXmlPluginConfig(
            packageName = "com.example.xml",
            appName = "XmlPlugin",
            versionName = "2.0",
            versionCode = 20L,
            receiverComponent = ComponentName("com.example.xml", "com.example.xml.Receiver"),
            pkgRes = mockRes,
            xmlResId = 999,
            isPluginEnabled = true
        )
        assertNotNull(plugin)
        assertEquals("XmlAuthor", plugin!!.author)
        assertEquals("XML Plugin Desc", plugin.description)
        assertEquals(ComponentName("com.example.xml", "com.example.xml.XmlSettingsActivity"), plugin.settingsActivity)
        assertTrue(plugin.isApiCompatible)
        assertEquals(BdsaPluginContract.MAX_TILES_PER_PLUGIN, plugin.tiles.size)

        val first = plugin.tiles[0]
        assertEquals("tile_1", first.tileId)
        assertEquals("Tile One\nWrapped", first.gridTitle)
        assertEquals("Tile One Wrapped", first.listTitle)
        assertEquals(2001, first.iconResId)
        assertEquals(2002, first.pixelIconResId)
        assertTrue(first.supportsLongPress)
        assertTrue(first.dismissOnClick)
        assertFalse(first.dismissOnLongClick)
        assertEquals(setOf("ayn_thor"), first.supportedDevices)
        assertEquals(BdsaPluginContract.MIN_REFRESH_INTERVAL_MS, first.refreshIntervalMs)

        val second = plugin.tiles[1]
        assertEquals("tile_2", second.tileId)
        assertEquals(5000L, second.refreshIntervalMs)
        assertEquals(setOf("retroid_pocket_duo"), second.supportedDevices)

        // Incompatible apiVersion is still parsed with isApiCompatible = false
        val futureXml = """<bdsa-plugin apiVersion="2"><tile id="t" title="Future" /></bdsa-plugin>"""
        every { mockRes.getXml(998) } returns createXmlResourceParser(futureXml, emptyMap())
        val futurePlugin = registry.parseXmlPluginConfig(
            packageName = "com.example.xmlfuture",
            appName = "FutureXml",
            versionName = "1.0",
            versionCode = 1L,
            receiverComponent = ComponentName("com.example.xmlfuture", "com.example.xmlfuture.Receiver"),
            pkgRes = mockRes,
            xmlResId = 998,
            isPluginEnabled = true
        )
        assertNotNull(futurePlugin)
        assertFalse(futurePlugin!!.isApiCompatible)
    }

    @Test
    fun `readMetaString resolves string resources, plain strings, and numeric literals cleanly`() {
        val registry = PluginRegistry.getInstance(context)
        val mockRes = mockk<Resources>()
        every { mockRes.getString(555) } returns "Localized String"
        every { mockRes.getString(12345) } throws Resources.NotFoundException("not a res id")

        val meta = Bundle().apply {
            putInt("k_res", 555)
            putInt("k_num", 12345)
            putInt("k_zero", 0)
            putString("k_str", "Literal Title")
            putBoolean("k_bool", true)
        }
        assertEquals("Localized String", registry.readMetaString(meta, mockRes, "k_res"))
        assertEquals("12345", registry.readMetaString(meta, mockRes, "k_num"))
        assertEquals("0", registry.readMetaString(meta, mockRes, "k_zero"))
        assertEquals("Literal Title", registry.readMetaString(meta, mockRes, "k_str"))
        assertEquals("true", registry.readMetaString(meta, mockRes, "k_bool"))
        assertNull(registry.readMetaString(meta, mockRes, "k_missing"))
    }

    @Test
    fun `persisted plugin state survives PluginRegistry reset and reload`() {
        installFakeFlatPlugin(packageName = "com.example.persist", tileId = "p_tile")
        installFakeFlatPlugin(packageName = "com.example.disabled", tileId = "d_tile")
        val firstRegistry = PluginRegistry.getInstance(context)
        val tileKey = BdsaPluginContract.pluginTileKey("com.example.persist", "p_tile")
        assertTrue(firstRegistry.setPluginTileEnabled(tileKey, true))
        firstRegistry.setPluginEnabled("com.example.disabled", false)

        firstRegistry.handleProviderCall(
            callingPackage = "com.example.persist",
            method = BdsaPluginContract.METHOD_UPDATE_TILE,
            extras = Bundle().apply {
                putString(BdsaPluginContract.EXTRA_TILE_ID, "p_tile")
                putString(BdsaPluginContract.EXTRA_LABEL, "Live\nTile")
                putBoolean(BdsaPluginContract.EXTRA_SELECTED, true)
                putBoolean(BdsaPluginContract.EXTRA_ENABLED, false)
                putLong(BdsaPluginContract.EXTRA_TIMER_BASE_MS, 2_000_000L)
                putBoolean(BdsaPluginContract.EXTRA_TIMER_COUNT_DOWN, true)
                putString(BdsaPluginContract.EXTRA_TIMER_FORMAT, "Left\n%s")
                putString(BdsaPluginContract.EXTRA_TIMER_FINISHED_LABEL, "Done")
                putLong(BdsaPluginContract.EXTRA_REFRESH_INTERVAL_MS, 0L)
            }
        )

        PluginRegistry.resetForTesting()
        val reloaded = PluginRegistry.getInstance(context)
        assertTrue(tileKey in reloaded.enabledPluginTileKeys.value)
        assertFalse(reloaded.findPluginForPackage("com.example.disabled")!!.isPluginEnabled)

        val state = reloaded.tileRuntimeStates.value[tileKey]!!
        assertEquals("Live\nTile", state.label)
        assertTrue(state.selected)
        assertFalse(state.enabled)
        assertEquals(2_000_000L, state.timerBaseMs)
        assertTrue(state.timerCountDown)
        assertEquals("Left\n%s", state.timerFormat)
        assertEquals("Done", state.timerFinishedLabel)
        assertEquals(0L, state.refreshIntervalMs)
    }

    @Test
    fun `package changes and tile disables trigger enablement listener and clear matching active picker`() {
        installFakeFlatPlugin(packageName = "com.example.pkg", tileId = "t_one")
        val registry = PluginRegistry.getInstance(context)
        val tileKey = BdsaPluginContract.pluginTileKey("com.example.pkg", "t_one")
        assertTrue(registry.setPluginTileEnabled(tileKey, true))
        registry.setOverlayShowing(true)
        registry.dispatchTileClick(registry.findTileDefinition(tileKey)!!, isDarkTheme = false)

        val pickerExtras = Bundle().apply {
            putString(BdsaPluginContract.EXTRA_TILE_ID, "t_one")
            putStringArrayList(BdsaPluginContract.EXTRA_OPTION_IDS, arrayListOf("1"))
            putStringArrayList(BdsaPluginContract.EXTRA_OPTION_LABELS, arrayListOf("One"))
        }
        assertTrue(registry.handleProviderCall("com.example.pkg", BdsaPluginContract.METHOD_SHOW_PICKER, pickerExtras).getBoolean(BdsaPluginContract.EXTRA_OK))
        assertNotNull(registry.activePickerRequest.value)

        // Disabling the tile clears its active picker
        assertTrue(registry.setPluginTileEnabled(tileKey, false))
        assertNull(registry.activePickerRequest.value)

        // Re-enable, open picker again, and disableAllPluginTiles clears it
        assertTrue(registry.setPluginTileEnabled(tileKey, true))
        registry.dispatchTileClick(registry.findTileDefinition(tileKey)!!, isDarkTheme = false)
        assertTrue(registry.handleProviderCall("com.example.pkg", BdsaPluginContract.METHOD_SHOW_PICKER, pickerExtras).getBoolean(BdsaPluginContract.EXTRA_OK))
        registry.disableAllPluginTiles()
        assertNull(registry.activePickerRequest.value)

        // onPackageChanged invokes enablement listener on ACTION_PACKAGE_CHANGED and ACTION_PACKAGE_REPLACED
        var listenerCalls = 0
        registry.onPluginTileEnablementChangedListener = { listenerCalls++ }
        registry.onPackageChanged(Intent.ACTION_PACKAGE_CHANGED, "com.example.pkg", isReplacing = false)
        registry.onPackageChanged(Intent.ACTION_PACKAGE_REPLACED, "com.example.pkg", isReplacing = true)
        assertEquals(2, listenerCalls)
    }

    @Test
    fun `formatLiveTimerDuration handles Long MAX_VALUE countdown without overflow`() {
        val (formatted, finished) = BdsaPluginContract.formatLiveTimerDuration(
            baseMs = Long.MAX_VALUE,
            countDown = true,
            nowMs = 0L
        )
        assertFalse(finished)
        val expectedSecs = (Long.MAX_VALUE / 1000L) + 1L
        val expectedHours = expectedSecs / 3600L
        assertTrue(formatted.startsWith("$expectedHours:"))
    }

    private fun createXmlResourceParser(
        xml: String,
        resIdsByRef: Map<String, Int>
    ): XmlResourceParser {
        val pullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }
        return object : XmlResourceParser, XmlPullParser by pullParser {
            override fun close() {}
            override fun getIdAttribute(): String? = null
            override fun getClassAttribute(): String? = null
            override fun getIdAttributeResourceValue(defaultValue: Int): Int = defaultValue
            override fun getStyleAttribute(): Int = 0
            override fun getAttributeNameResource(index: Int): Int = 0
            override fun getAttributeListValue(namespace: String?, attribute: String?, options: Array<out String>?, defaultValue: Int): Int = defaultValue
            override fun getAttributeListValue(index: Int, options: Array<out String>?, defaultValue: Int): Int = defaultValue
            override fun getAttributeBooleanValue(namespace: String?, attribute: String?, defaultValue: Boolean): Boolean {
                return pullParser.getAttributeValue(namespace, attribute)?.toBooleanStrictOrNull() ?: defaultValue
            }
            override fun getAttributeBooleanValue(index: Int, defaultValue: Boolean): Boolean = defaultValue
            override fun getAttributeResourceValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
                val raw = pullParser.getAttributeValue(namespace, attribute) ?: return defaultValue
                return resIdsByRef[raw] ?: defaultValue
            }
            override fun getAttributeResourceValue(index: Int, defaultValue: Int): Int = defaultValue
            override fun getAttributeIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int {
                return pullParser.getAttributeValue(namespace, attribute)?.toIntOrNull() ?: defaultValue
            }
            override fun getAttributeIntValue(index: Int, defaultValue: Int): Int = defaultValue
            override fun getAttributeUnsignedIntValue(namespace: String?, attribute: String?, defaultValue: Int): Int = defaultValue
            override fun getAttributeUnsignedIntValue(index: Int, defaultValue: Int): Int = defaultValue
            override fun getAttributeFloatValue(namespace: String?, attribute: String?, defaultValue: Float): Float = defaultValue
            override fun getAttributeFloatValue(index: Int, defaultValue: Float): Float = defaultValue
        }
    }
}
