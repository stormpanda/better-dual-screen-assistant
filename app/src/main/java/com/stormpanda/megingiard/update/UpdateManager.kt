package com.stormpanda.megingiard.update

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.stormpanda.betterdualscreenassistant.BuildConfig
import com.stormpanda.betterdualscreenassistant.system.DirectBootPrefs
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    const val FAILURE_BACKOFF_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
    private const val MAX_RELEASE_JSON_CHARS = 256 * 1024 // 256 KiB

    private const val PREF_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled"
    private const val PREF_LATEST_RELEASE_TAG = "latest_release_tag"
    private const val PREF_LATEST_RELEASE_URL = "latest_release_url"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkLock = Any()

    @Volatile
    private var activeCheckJob: Job? = null

    @Volatile
    private var checkGeneration: Long = 0L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefsLoaded = false

    @Volatile
    internal var lastCheckTimeMs: Long = 0L

    @Volatile
    internal var lastFailedCheckElapsedMs: Long = 0L

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
            DirectBootPrefs.open(ctx, SystemSettingsRepository.PREFS_NAME)
        }.getOrNull()
    }

    internal fun isValidGithubReleaseUrl(urlStr: String): Boolean {
        if (urlStr.isBlank()) return false
        return try {
            val parsed = URL(urlStr.trim())
            val host = parsed.host?.lowercase().orEmpty()
            parsed.protocol.equals("https", ignoreCase = true) &&
                (host == "github.com" || host.endsWith(".github.com"))
        } catch (_: Throwable) {
            false
        }
    }

    fun init(
        context: Context,
        currentVersion: String = BuildConfig.VERSION_NAME
    ) {
        synchronized(checkLock) {
            if (appContext == null) {
                appContext = context.applicationContext ?: context
            }
            if (prefsLoaded) return

            val prefs = getPrefs() ?: return
            prefsLoaded = true
            runCatching {
                val autoEnabled = prefs.getBoolean(PREF_AUTO_UPDATE_CHECK_ENABLED, true)
                _autoUpdateCheckEnabled.value = autoEnabled

                val savedTag = prefs.getString(PREF_LATEST_RELEASE_TAG, "") ?: ""
                val savedUrl = prefs.getString(PREF_LATEST_RELEASE_URL, "") ?: ""
                if (savedTag.isNotEmpty() && isValidGithubReleaseUrl(savedUrl)) {
                    val info = AppReleaseInfo(tagName = savedTag, htmlUrl = savedUrl)
                    applyReleaseInfo(info, currentVersion)
                }
            }.onFailure { e ->
                AppLog.e(TAG, "Failed to load update preferences", e)
            }
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
        if (!force) {
            if (!prefsLoaded || !_autoUpdateCheckEnabled.value) {
                AppLog.d(TAG, "Auto update check is disabled or uninitialized; skipping")
                return
            }
            val ctx = appContext
            if (ctx != null && !DirectBootPrefs.isUserUnlocked(ctx)) {
                AppLog.d(TAG, "User storage still in Direct Boot; deferring auto update check")
                return
            }
            val now = System.currentTimeMillis()
            if (lastCheckTimeMs > 0L && now >= lastCheckTimeMs && (now - lastCheckTimeMs) < AUTO_UPDATE_CHECK_INTERVAL_MS) {
                AppLog.d(TAG, "Auto update check already performed recently; skipping")
                return
            }
            val nowElapsed = SystemClock.elapsedRealtime()
            if (lastFailedCheckElapsedMs > 0L &&
                nowElapsed >= lastFailedCheckElapsedMs &&
                (nowElapsed - lastFailedCheckElapsedMs) < FAILURE_BACKOFF_INTERVAL_MS
            ) {
                AppLog.d(TAG, "Auto update check in offline failure backoff window; skipping")
                return
            }
        }

        val myGeneration: Long
        synchronized(checkLock) {
            if (_isChecking.value || activeCheckJob?.isActive == true) return
            _isChecking.value = true
            _checkError.value = null
            myGeneration = ++checkGeneration
            activeCheckJob = scope.launch {
                try {
                    val releaseInfo = fetchLatestRelease(releasesApiUrl)
                    synchronized(checkLock) {
                        if (myGeneration != checkGeneration) return@launch
                        if (releaseInfo != null) {
                            lastCheckTimeMs = System.currentTimeMillis()
                            lastFailedCheckElapsedMs = 0L
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
                            lastFailedCheckElapsedMs = SystemClock.elapsedRealtime()
                            _checkError.value = "Failed to fetch release info"
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    synchronized(checkLock) {
                        if (myGeneration == checkGeneration) {
                            lastFailedCheckElapsedMs = SystemClock.elapsedRealtime()
                            AppLog.e(TAG, "Error checking for updates", e)
                            _checkError.value = e.message ?: "Network error"
                        }
                    }
                } finally {
                    synchronized(checkLock) {
                        if (myGeneration == checkGeneration) {
                            _isChecking.value = false
                            activeCheckJob = null
                        }
                    }
                }
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
                    val body = connection.inputStream.bufferedReader().use { reader ->
                        val sb = StringBuilder()
                        val buf = CharArray(4096)
                        while (sb.length < MAX_RELEASE_JSON_CHARS) {
                            val toRead = minOf(buf.size, MAX_RELEASE_JSON_CHARS - sb.length)
                            val n = reader.read(buf, 0, toRead)
                            if (n == -1) break
                            sb.append(buf, 0, n)
                        }
                        sb.toString()
                    }
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
            if (tagName.isNotEmpty() && isValidGithubReleaseUrl(htmlUrl)) {
                AppReleaseInfo(tagName = tagName, htmlUrl = htmlUrl)
            } else {
                null
            }
        }.onFailure { e ->
            AppLog.e(TAG, "Failed to parse GitHub release JSON", e)
        }.getOrNull()

    internal fun resetForTesting() {
        synchronized(checkLock) {
            checkGeneration++
            activeCheckJob?.cancel()
            activeCheckJob = null
            prefsLoaded = false
            lastCheckTimeMs = 0L
            lastFailedCheckElapsedMs = 0L
            _autoUpdateCheckEnabled.value = true
            _updateAvailable.value = false
            _latestReleaseInfo.value = null
            _isChecking.value = false
            _checkError.value = null
        }
    }
}
