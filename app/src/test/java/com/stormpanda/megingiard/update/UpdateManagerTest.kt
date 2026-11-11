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
    fun `parseGithubReleaseJson returns null on blank tag_name missing html_url non-GitHub URL or invalid JSON`() {
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "   ", "html_url": "https://github.com/stormpanda/bdsa"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "v1.0.0", "html_url": ""}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "v1.0.0", "html_url": "http://github.com/stormpanda/bdsa/releases"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "v1.0.0", "html_url": "https://evil.example.com/github.com/releases"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"tag_name": "v1.0.0", "html_url": "intent://scan/#Intent;scheme=zxing;end"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("""{"name": "No Tag"}"""))
        assertNull(UpdateManager.parseGithubReleaseJson("not valid json"))
        assertTrue(UpdateManager.isValidGithubReleaseUrl("https://github.com/stormpanda/better-dual-screen-assistant/releases/tag/v1.0.0"))
        assertFalse(UpdateManager.isValidGithubReleaseUrl("http://github.com/stormpanda/better-dual-screen-assistant/releases"))
        assertFalse(UpdateManager.isValidGithubReleaseUrl("https://attacker.com/github.com"))
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

    @Test
    fun `checkForUpdates skips network call during offline failure backoff window when not forced`() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper(5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
        UpdateManager.init(context, currentVersion = "1.0.0")
        UpdateManager.lastFailedCheckElapsedMs = android.os.SystemClock.elapsedRealtime()
        assertTrue(UpdateManager.lastFailedCheckElapsedMs > 0L)

        UpdateManager.checkForUpdates(force = false, currentVersion = "1.0.0")
        assertFalse(UpdateManager.isChecking.value)
        assertNull(UpdateManager.checkError.value)
    }
}
