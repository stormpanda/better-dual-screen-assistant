package com.stormpanda.betterdualscreenassistant.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.DirectBootPrefs
import com.stormpanda.betterdualscreenassistant.system.QuickSettingTileId
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
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

        // show_picker rejected when overlay is closed, accepted when open (and capped at MAX_PICKER_OPTIONS)
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
}
