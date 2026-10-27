package com.stormpanda.megingiard.update

import android.content.Context
import android.content.SharedPreferences
import com.stormpanda.betterdualscreenassistant.BuildConfig
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val TAG = "UpdateManager"
    const val DEFAULT_RELEASES_API_URL =
        "https://api.github.com/repos/stormpanda/better-dual-screen-assistant/releases/latest"
    const val BDSA_GITHUB_RELEASES_FALLBACK_URL =
        "https://github.com/stormpanda/better-dual-screen-assistant/releases"
    const val BDSA_OBTAINIUM_REPO_URL =
        "https://github.com/stormpanda/better-dual-screen-assistant"
    const val BDSA_OBTAINIUM_FALLBACK_URL =
        "https://github.com/ImranR98/Obtainium"
    const val AUTO_UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private const val PREF_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled"
    private const val PREF_LATEST_RELEASE_TAG = "latest_release_tag"
    private const val PREF_LATEST_RELEASE_URL = "latest_release_url"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefsLoaded = false

    @Volatile
    internal var lastCheckTimeMs: Long = 0L

    private val _autoUpdateCheckEnabled = MutableStateFlow(true)
    val autoUpdateCheckEnabled: StateFlow<Boolean> = _autoUpdateCheckEnabled.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _latestReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val latestReleaseInfo: StateFlow<AppReleaseInfo?> = _latestReleaseInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _checkError = MutableStateFlow<String?>(null)
    val checkError: StateFlow<String?> = _checkError.asStateFlow()

    private fun getPrefs(): SharedPreferences? {
        val ctx = appContext ?: return null
        return runCatching {
            ctx.getSharedPreferences(SystemSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    fun init(
        context: Context,
        currentVersion: String = BuildConfig.VERSION_NAME
    ) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        if (prefsLoaded) return

        val prefs = getPrefs() ?: return
        prefsLoaded = true
        runCatching {
            val autoEnabled = prefs.getBoolean(PREF_AUTO_UPDATE_CHECK_ENABLED, true)
            _autoUpdateCheckEnabled.value = autoEnabled

            val savedTag = prefs.getString(PREF_LATEST_RELEASE_TAG, "") ?: ""
            val savedUrl = prefs.getString(PREF_LATEST_RELEASE_URL, "") ?: ""
            if (savedTag.isNotEmpty() && savedUrl.isNotEmpty()) {
                val info = AppReleaseInfo(tagName = savedTag, htmlUrl = savedUrl)
                applyReleaseInfo(info, currentVersion)
            }
        }.onFailure { e ->
            AppLog.e(TAG, "Failed to load update preferences", e)
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        _autoUpdateCheckEnabled.value = enabled
        scope.launch {
            runCatching {
                getPrefs()?.edit()
                    ?.putBoolean(PREF_AUTO_UPDATE_CHECK_ENABLED, enabled)
                    ?.apply()
            }.onFailure { e ->
                AppLog.e(TAG, "Failed to save autoUpdateCheckEnabled", e)
            }
        }
    }

    fun checkForUpdates(
        force: Boolean = false,
        currentVersion: String = BuildConfig.VERSION_NAME,
        releasesApiUrl: String = DEFAULT_RELEASES_API_URL
    ) {
        scope.launch {
            if (!force) {
                if (!_autoUpdateCheckEnabled.value) {
                    AppLog.d(TAG, "Auto update check is disabled; skipping")
                    return@launch
                }
                val now = System.currentTimeMillis()
                if (lastCheckTimeMs > 0L && (now - lastCheckTimeMs) < AUTO_UPDATE_CHECK_INTERVAL_MS) {
                    AppLog.d(TAG, "Auto update check already performed recently; skipping")
                    return@launch
                }
            }

            if (_isChecking.value) return@launch
            _isChecking.value = true
            _checkError.value = null

            try {
                val releaseInfo = fetchLatestRelease(releasesApiUrl)
                if (releaseInfo != null) {
                    lastCheckTimeMs = System.currentTimeMillis()
                    applyReleaseInfo(releaseInfo, currentVersion)
                    runCatching {
                        getPrefs()?.edit()
                            ?.putString(PREF_LATEST_RELEASE_TAG, releaseInfo.tagName)
                            ?.putString(PREF_LATEST_RELEASE_URL, releaseInfo.htmlUrl)
                            ?.apply()
                    }
                    AppLog.i(
                        TAG,
                        "Update check complete: latest=${releaseInfo.tagName}, current=$currentVersion, available=${_updateAvailable.value}"
                    )
                } else {
                    _checkError.value = "Failed to fetch release info"
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error checking for updates", e)
                _checkError.value = e.message ?: "Network error"
            } finally {
                _isChecking.value = false
            }
        }
    }

    private fun applyReleaseInfo(
        info: AppReleaseInfo,
        currentVersion: String
    ) {
        _latestReleaseInfo.value = info
        _updateAvailable.value = SemVerComparator.isUpdateAvailable(currentVersion, info.tagName)
    }

    private suspend fun fetchLatestRelease(releasesApiUrl: String): AppReleaseInfo? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(releasesApiUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "BetterDualScreenAssistant-App")
                    connectTimeout = 8000
                    readTimeout = 10000
                }
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseGithubReleaseJson(body)
                } else {
                    AppLog.w(TAG, "GitHub API returned HTTP $responseCode")
                    null
                }
            } finally {
                connection?.disconnect()
            }
        }

    internal fun parseGithubReleaseJson(rawJson: String): AppReleaseInfo? =
        runCatching {
            val json = JSONObject(rawJson)
            val tagName = json.optString("tag_name", "").trim()
            val htmlUrl = json.optString("html_url", "").trim()
            if (tagName.isNotEmpty() && htmlUrl.isNotEmpty()) {
                AppReleaseInfo(tagName = tagName, htmlUrl = htmlUrl)
            } else {
                null
            }
        }.onFailure { e ->
            AppLog.e(TAG, "Failed to parse GitHub release JSON", e)
        }.getOrNull()

    internal fun resetForTesting() {
        prefsLoaded = false
        lastCheckTimeMs = 0L
        _autoUpdateCheckEnabled.value = true
        _updateAvailable.value = false
        _latestReleaseInfo.value = null
        _isChecking.value = false
        _checkError.value = null
    }
}
