package com.stormpanda.megingiard.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
class UpdateManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        fullResetUpdateManager()
    }

    @After
    fun tearDown() {
        fullResetUpdateManager()
    }

    private fun fullResetUpdateManager() {
        UpdateManager.resetForTesting()
        val appContextField = UpdateManager::class.java.getDeclaredField("appContext").apply {
            isAccessible = true
        }
        appContextField.set(UpdateManager, null)
    }

    @Test
    fun `parseGithubReleaseJson extracts tagName and htmlUrl`() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "name": "Better Dual Screen Assistant v1.2.3",
              "body": "### Features\n- Added full unit test suite",
              "html_url": "https://github.com/stormpanda/better-dual-screen-assistant/releases/tag/v1.2.3"
            }
        """.trimIndent()

        val info = UpdateManager.parseGithubReleaseJson(json)
        assertNotNull(info)
        requireNotNull(info)
        assertEquals("v1.2.3", info.tagName)
        assertEquals(
            "https://github.com/stormpanda/better-dual-screen-assistant/releases/tag/v1.2.3",
            info.htmlUrl
        )
    }

    @Test
    fun `parseGithubReleaseJson returns null on blank tag_name missing html_url or invalid JSON`() {
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "   ", "html_url": "https://example.com"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "v1.0.0", "html_url": ""}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"name": "No Tag"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("not valid json"))
    }

    @Test
    fun `init loads saved preferences and evaluates updateAvailable against currentVersion`() {
        val prefs = context.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("auto_update_check_enabled", false)
            .putString("latest_release_tag", "v2.0.0")
            .putString("latest_release_url", "https://github.com/stormpanda/bdsa/releases/tag/v2.0.0")
            .commit()

        UpdateManager.init(context, currentVersion = "1.0.0")
        assertFalse(UpdateManager.autoUpdateCheckEnabled.value)
        assertTrue(UpdateManager.updateAvailable.value)
        assertEquals(
            AppReleaseInfo("v2.0.0", "https://github.com/stormpanda/bdsa/releases/tag/v2.0.0"),
            UpdateManager.latestReleaseInfo.value
        )

        // Reset and init with a currentVersion equal to saved tag -> updateAvailable is false
        fullResetUpdateManager()
        UpdateManager.init(context, currentVersion = "2.0.0")
        assertFalse(UpdateManager.updateAvailable.value)
    }

    @Test
    fun `setAutoUpdateCheckEnabled updates stateFlow and resetForTesting clears state`() {
        UpdateManager.init(context, currentVersion = "1.0.0")
        assertTrue(UpdateManager.autoUpdateCheckEnabled.value)

        UpdateManager.setAutoUpdateCheckEnabled(false)
        assertFalse(UpdateManager.autoUpdateCheckEnabled.value)

        fullResetUpdateManager()
        assertTrue(UpdateManager.autoUpdateCheckEnabled.value)
        assertFalse(UpdateManager.updateAvailable.value)
        assertNull(UpdateManager.latestReleaseInfo.value)
        assertFalse(UpdateManager.isChecking.value)
        assertNull(UpdateManager.checkError.value)
    }

    @Test
    fun `checkForUpdates skips network call when autoUpdateCheckEnabled is false and not forced`() {
        UpdateManager.init(context, currentVersion = "1.0.0")
        UpdateManager.setAutoUpdateCheckEnabled(false)

        UpdateManager.checkForUpdates(force = false, currentVersion = "1.0.0")
        assertFalse(UpdateManager.isChecking.value)
        assertNull(UpdateManager.checkError.value)
    }

    @Test
    fun `checkForUpdates skips network call when within 24h throttle window and not forced`() {
        UpdateManager.init(context, currentVersion = "1.0.0")
        UpdateManager.lastCheckTimeMs = System.currentTimeMillis()

        UpdateManager.checkForUpdates(force = false, currentVersion = "1.0.0")
        assertFalse(UpdateManager.isChecking.value)
        assertNull(UpdateManager.checkError.value)
    }
}
