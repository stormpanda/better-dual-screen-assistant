package com.stormpanda.betterdualscreenassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.stormpanda.betterdualscreenassistant.MainActivity
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.overlay.ScreenOverlayManager
import com.stormpanda.betterdualscreenassistant.plugin.PluginRegistry
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import com.stormpanda.betterdualscreenassistant.system.DirectBootPrefs
import com.stormpanda.betterdualscreenassistant.system.MappingServiceClient
import com.stormpanda.betterdualscreenassistant.system.OverviewProxyClient
import com.stormpanda.betterdualscreenassistant.system.ScreenRecordingState
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.system.TelemetryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantForegroundService : Service() {

    companion object {
        private const val TAG = "AssistantFgService"
        private const val CHANNEL_ID = "better_dsa_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.stormpanda.betterdualscreenassistant.action.START"
        const val ACTION_TOGGLE_OVERLAY = "com.stormpanda.betterdualscreenassistant.action.TOGGLE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "com.stormpanda.betterdualscreenassistant.action.SHOW_OVERLAY"
        const val ACTION_SHOW_MODE = "com.stormpanda.betterdualscreenassistant.action.SHOW_MODE"
        const val ACTION_SHOW_SETTINGS = "com.stormpanda.betterdualscreenassistant.action.SHOW_SETTINGS"
        const val ACTION_HIDE_OVERLAY = "com.stormpanda.betterdualscreenassistant.action.HIDE_OVERLAY"
        const val ACTION_STOP_RECORDING = "com.stormpanda.betterdualscreenassistant.action.STOP_RECORDING"
        const val ACTION_TCC_KEY = "com.stormpanda.betterdualscreenassistant.action.TCC_KEY"
        const val EXTRA_KEY_ACTION_DOWN = "key_action_down"
        const val EXTRA_IS_LONG_PRESS = "isLongPress"
        private const val TCC_TOGGLE_DEBOUNCE_MS = 200L
        private const val MODERN_BROADCAST_REFREEZE_FALLBACK_MS = 650L
        private const val POST_SCREEN_STOCK_CHECK_DELAY_LEGACY_MS = 500L
        private const val POST_SCREEN_STOCK_CHECK_DELAY_MODERN_MS = 1_000L

        @Volatile
        var instance: AssistantForegroundService? = null
            private set

        fun startService(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, AssistantForegroundService::class.java).apply {
                this.action = action
            }
            try {
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start foreground service", t)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: SystemSettingsRepository
        private set
    lateinit var telemetryMonitor: TelemetryMonitor
        private set
    lateinit var overviewProxyClient: OverviewProxyClient
        private set
    lateinit var mappingServiceClient: MappingServiceClient
        private set
    lateinit var overlayManager: ScreenOverlayManager
        private set
    lateinit var pluginRegistry: PluginRegistry
        private set

    private var lastTccToggleElapsedMs: Long = 0L
    private var longPressConsumed: Boolean = false
    private var postBroadcastStockCheckJob: Job? = null
    private var fallbackBroadcastRefreezeJob: Job? = null
    private var lastLocaleTags: String = ""

    internal var modernParallelBroadcastQueueOverride: Boolean? = null

    internal fun usesModernParallelBroadcastQueue(): Boolean =
        modernParallelBroadcastQueueOverride ?: (android.os.Build.VERSION.SDK_INT >= 34)

    /**
     * Runs at high priority (`SYSTEM_HIGH_PRIORITY - 1` = `999`) ahead of stock
     * `com.odin.dualscreen.assistant`'s `ScreenReceiver` (`priority = 0`) and
     * `com.odin.gameassistant` (`priority = 0`) on Android 13 legacy serial ordered broadcasts.
     * Briefly sends `SIGCONT` to the windowless stock process so `BroadcastQueue` never stalls
     * 10s or triggers a 12s ANR respawn.
     *
     * On Android 14+ (`BroadcastQueueModernImpl`), screen broadcasts dispatch in parallel
     * per-process queues, so re-freezing relies on `PrivilegedHelperMain`'s safety timeout (600ms)
     * or [scheduleFallbackBroadcastRefreeze] rather than [postOrderedScreenReceiver].
     */
    private val preOrderedScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val appCtx = context ?: this@AssistantForegroundService
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    if (::repository.isInitialized) {
                        repository.handlePreScreenOnImePinMode()
                    }
                    PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = false, context = appCtx)
                    if (usesModernParallelBroadcastQueue() && PServerClient.hasFallbackUnfrozenStockPid()) {
                        scheduleFallbackBroadcastRefreeze(appCtx)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (::repository.isInitialized) {
                        repository.captureImePinModeBeforePowerTransition(synchronous = false)
                    }
                    PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = false, context = appCtx)
                    if (usesModernParallelBroadcastQueue() && PServerClient.hasFallbackUnfrozenStockPid()) {
                        scheduleFallbackBroadcastRefreeze(appCtx)
                    }
                    stopActiveRecordingOnPowerTransition("screen_off")
                }
                Intent.ACTION_SHUTDOWN,
                "android.intent.action.REBOOT" -> {
                    if (::repository.isInitialized) {
                        repository.captureImePinModeBeforePowerTransition(synchronous = true)
                    }
                    fallbackBroadcastRefreezeJob?.cancel()
                    PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = false, context = appCtx)
                    stopActiveRecordingOnPowerTransition("shutdown")
                }
            }
        }
    }

    /**
     * On Android 13 legacy serial ordered broadcasts (`!usesModernParallelBroadcastQueue()`), runs
     * at low priority (`SYSTEM_LOW_PRIORITY + 1` = `-999`) immediately after all default-priority
     * receivers finish, re-freezing the windowless stock process (`SIGSTOP`).
     *
     * On Android 14+ (`BroadcastQueueModernImpl`), receivers in the same app run back-to-back in
     * their own per-process queue before the stock app finishes its `ScreenReceiver`, so re-freezing
     * here would freeze the stock process mid-broadcast and trigger ANRs; re-freezing is therefore
     * skipped here when [usesModernParallelBroadcastQueue] is true.
     */
    private val postOrderedScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val appCtx = context ?: this@AssistantForegroundService
            val modernQueue = usesModernParallelBroadcastQueue()
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    if (!modernQueue) {
                        PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = true, context = appCtx)
                        if (::repository.isInitialized) {
                            repository.handlePostScreenOnImePinMode()
                        }
                    }
                    if (::overlayManager.isInitialized) {
                        overlayManager.reassertBlankedHardwareStateIfNeeded()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (!modernQueue) {
                        PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = true, context = appCtx)
                        if (::repository.isInitialized) {
                            repository.reassertImePinModeAfterScreenOffBroadcast()
                        }
                    }
                }
            }
        }
    }

    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "action.tcc.button.key.event" -> {
                    val keyActionDown = if (intent.hasExtra("key_action_down")) {
                        intent.getBooleanExtra("key_action_down", true)
                    } else {
                        false
                    }
                    val isLongPress = intent.getBooleanExtra("key_isLongPress", false) ||
                        intent.getBooleanExtra("isLongPress", false)
                    handleTccButtonEvent(keyActionDown = keyActionDown, isLongPress = isLongPress)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopActiveRecordingOnPowerTransition("screen_off")
                    overlayManager.onDeviceScreenInteractiveChanged(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    overlayManager.onDeviceScreenInteractiveChanged(true)
                    repository.resyncFloatingIconGestureGuard()
                    schedulePostScreenBroadcastStockCheck()
                }
                Intent.ACTION_USER_PRESENT -> {
                    repository.handleUserPresentImePinMode()
                    overlayManager.onDeviceScreenInteractiveChanged(true)
                    repository.resyncFloatingIconGestureGuard()
                    schedulePostScreenBroadcastStockCheck()
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey") {
                        overlayManager.hideAssistantOverlay()
                    }
                }
            }
        }
    }

    private val pluginPackageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::pluginRegistry.isInitialized) return
            val pkg = intent?.data?.schemeSpecificPart
            val replacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true
            pluginRegistry.onPackageChanged(intent?.action, pkg, replacing)
        }
    }

    private val userUnlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_UNLOCKED && ::pluginRegistry.isInitialized) {
                pluginRegistry.onUserUnlocked()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val initialPromoted = DirectBootPrefs.open(
            this,
            SystemSettingsRepository.PREFS_NAME
        ).getBoolean(SystemSettingsRepository.PREF_PROMOTED_TO_MANAGER, false)
        lastLocaleTags = resources.configuration.locales.toLanguageTags()
        startForeground(NOTIFICATION_ID, createForegroundNotification(initialPromoted))

        PrivilegedBridge.ensureInitialized(applicationContext)
        enforceStockTakeoverIfPreferred()

        repository = SystemSettingsRepository.getInstance(applicationContext).also { it.register() }
        pluginRegistry = PluginRegistry.getInstance(applicationContext).also { registry ->
            registry.onPluginTilesRemovedListener = { removedKeys ->
                repository.prunePluginTilesFromOrder(removedKeys)
            }
            registry.onPluginTileEnablementChangedListener = {
                val activePlugins = registry.activePluginTilesMap(DeviceProfile.current(applicationContext)).size
                repository.ensureAtLeastOneTileEnabled(activePlugins)
            }
        }
        telemetryMonitor = TelemetryMonitor(applicationContext)
        overviewProxyClient = OverviewProxyClient(applicationContext).also { it.bind() }
        overlayManager = ScreenOverlayManager(
            context = applicationContext,
            repository = repository,
            telemetryMonitor = telemetryMonitor,
            overviewProxyClient = overviewProxyClient
        )
        mappingServiceClient = MappingServiceClient(applicationContext) {
            if (!overlayManager.isBottomScreenBlanked && overlayManager.isAssistantShowing) {
                overlayManager.hideAssistantOverlay()
                true
            } else {
                false
            }
        }.also { it.bind() }

        serviceScope.launch {
            PrivilegedBridge.isHelperConnected.first { it }
            withContext(Dispatchers.IO) {
                repository.reconcileImePinModeOnStartup()
            }
        }
        serviceScope.launch {
            pluginRegistry.toastEvents.collect { event ->
                overlayManager.showBottomToast(event.message)
            }
        }
        serviceScope.launch {
            pluginRegistry.dismissOverlayEvents.collect {
                overlayManager.hideAssistantOverlay()
            }
        }
        serviceScope.launch {
            repository.dualScreenDisplayMode.collectLatest { mode ->
                overlayManager.applyDisplayModeOverlays(mode)
            }
        }
        serviceScope.launch {
            combine(
                repository.promotedToManager,
                repository.screenRecordingState
            ) { promoted, recState ->
                promoted to recState
            }.collectLatest { (promoted, recState) ->
                updateForegroundNotification(promoted, recState)
                if (recState.isRecording) {
                    while (true) {
                        delay(2_000L)
                        val synced = withContext(Dispatchers.IO) {
                            repository.syncScreenRecordingStateFromHelper()
                        }
                        if (!synced.isRecording) {
                            if (synced.lastStopReason == "low_storage") {
                                overlayManager.showBottomToast(getString(R.string.toast_recording_saved_low_storage))
                            }
                            break
                        }
                    }
                }
            }
        }

        val preScreenFilter = IntentFilter().apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("android.intent.action.REBOOT")
        }
        registerReceiver(
            preOrderedScreenReceiver,
            preScreenFilter,
            Context.RECEIVER_EXPORTED
        )

        val postScreenFilter = IntentFilter().apply {
            priority = IntentFilter.SYSTEM_LOW_PRIORITY + 1
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(
            postOrderedScreenReceiver,
            postScreenFilter,
            Context.RECEIVER_EXPORTED
        )

        val filter = IntentFilter().apply {
            addAction("action.tcc.button.key.event")
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        }
        registerReceiver(
            systemEventsReceiver,
            filter,
            android.Manifest.permission.DUMP,
            null,
            Context.RECEIVER_EXPORTED
        )

        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(
            pluginPackageReceiver,
            pkgFilter,
            Context.RECEIVER_EXPORTED
        )

        val unlockFilter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        registerReceiver(
            userUnlockedReceiver,
            unlockFilter,
            Context.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PrivilegedBridge.ensureInitialized(applicationContext)
        enforceStockTakeoverIfPreferred()
        when (intent?.action) {
            ACTION_TOGGLE_OVERLAY -> overlayManager.toggleAssistantOverlay()
            ACTION_SHOW_OVERLAY -> overlayManager.showAssistantOverlay()
            ACTION_SHOW_MODE -> overlayManager.showAssistantOverlay(initialTab = 0)
            ACTION_SHOW_SETTINGS -> overlayManager.showAssistantOverlay(initialTab = 1)
            ACTION_HIDE_OVERLAY -> overlayManager.hideAssistantOverlay()
            ACTION_STOP_RECORDING -> overlayManager.performStopScreenRecordingWithToast("notification")
            ACTION_TCC_KEY -> {
                val keyActionDown = intent.getBooleanExtra(EXTRA_KEY_ACTION_DOWN, false)
                val isLongPress = intent.getBooleanExtra(EXTRA_IS_LONG_PRESS, false)
                handleTccButtonEvent(keyActionDown = keyActionDown, isLongPress = isLongPress)
            }
        }
        return START_STICKY
    }

    private fun scheduleFallbackBroadcastRefreeze(appCtx: Context) {
        fallbackBroadcastRefreezeJob?.cancel()
        fallbackBroadcastRefreezeJob = serviceScope.launch {
            delay(MODERN_BROADCAST_REFREEZE_FALLBACK_MS)
            withContext(Dispatchers.IO) {
                PrivilegedBridge.setStockProcessFrozenForBroadcast(freeze = true, context = appCtx)
            }
        }
    }

    private fun schedulePostScreenBroadcastStockCheck() {
        postBroadcastStockCheckJob?.cancel()
        val checkDelayMs = if (usesModernParallelBroadcastQueue()) {
            POST_SCREEN_STOCK_CHECK_DELAY_MODERN_MS
        } else {
            POST_SCREEN_STOCK_CHECK_DELAY_LEGACY_MS
        }
        postBroadcastStockCheckJob = serviceScope.launch {
            delay(checkDelayMs)
            enforceStockTakeoverIfPreferred()
        }
    }

    private fun enforceStockTakeoverIfPreferred() {
        val replacePreferred = PServerClient.isReplaceStockPreferred(applicationContext)
        PrivilegedBridge.setReplaceStockPreferred(replacePreferred)
        if (replacePreferred) {
            serviceScope.launch(Dispatchers.IO) {
                if (PServerClient.isStockAssistantEnabled(applicationContext)) {
                    PServerClient.setStockAssistantEnabled(false, applicationContext)
                } else {
                    PServerClient.ensureStockPackageEnabledOnDisk(applicationContext)
                }
            }
        }
    }

    /**
     * Replicates `TccKeyReceiver.java` and `TccServices.java` (`a()`, `b()`, `c()`):
     * - `keyActionDown == true && !isLongPress` (`b()`): Initial button press down -> no-op.
     * - `keyActionDown == true && isLongPress` (`c()`): Long-press threshold reached while holding ->
     *   sets `longPressConsumed = true` (`f6460m`), hides overlay if open, and switches to Mode 1
     *   (blanks/turns off the bottom screen).
     * - `keyActionDown == false` (`a()`): Button released ->
     *   - If `longPressConsumed == true`, resets flag and returns without waking or toggling.
     *   - If bottom screen is blanked (`Mode 1`), wakes it back into Dual-Screen (`Mode 0`).
     *   - Otherwise toggles the assistant overlay (with 200ms debounce `f6451w`).
     */
    fun handleTccButtonEvent(keyActionDown: Boolean, isLongPress: Boolean) {
        if (!PServerClient.isReplaceStockPreferred(applicationContext) &&
            PServerClient.isStockAssistantEnabled(applicationContext)
        ) {
            Log.d(TAG, "Ignoring TCC hardware key because stock DualScreenAssistant is enabled")
            return
        }

        // Ensure stock process stays suppressed if it somehow woke up
        enforceStockTakeoverIfPreferred()

        val currentMode = repository.dualScreenDisplayMode.value

        if (keyActionDown) {
            if (isLongPress) {
                // Replicates TccServices.c(): Long-press turns off the bottom screen on AYN Thor,
                // and toggles the bottom screen (wake <-> blank) on Retroid Pocket Duo.
                longPressConsumed = true
                val profile = com.stormpanda.betterdualscreenassistant.system.DeviceProfile.current(
                    applicationContext
                )
                if (profile.longPressTogglesBottom &&
                    (overlayManager.isBottomScreenBlanked || currentMode == 1)
                ) {
                    overlayManager.wakeBottomScreen()
                } else if (!overlayManager.isBottomScreenBlanked && currentMode != 1) {
                    overlayManager.hideAssistantOverlay(immediate = true)
                    repository.setDualScreenDisplayMode(1)
                }
            }
            // Initial key down (keyActionDown == true && !isLongPress) is a no-op (TccServices.b())
            return
        }

        // Key UP (keyActionDown == false) -> Replicates TccServices.a()
        if (longPressConsumed) {
            longPressConsumed = false
            return
        }

        if (overlayManager.isBottomScreenBlanked || currentMode == 1) {
            overlayManager.wakeBottomScreen()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastTccToggleElapsedMs > TCC_TOGGLE_DEBOUNCE_MS) {
            lastTccToggleElapsedMs = now
            overlayManager.toggleAssistantOverlay()
            serviceScope.launch(Dispatchers.IO) {
                repository.syncUnlockedImePinModeSnapshot()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed; keeping AssistantForegroundService active")
        PrivilegedBridge.ensureInitialized(applicationContext)
        enforceStockTakeoverIfPreferred()
    }

    override fun onDestroy() {
        instance = null
        postBroadcastStockCheckJob?.cancel()
        fallbackBroadcastRefreezeJob?.cancel()
        try {
            unregisterReceiver(preOrderedScreenReceiver)
        } catch (_: Throwable) {
        }
        try {
            unregisterReceiver(postOrderedScreenReceiver)
        } catch (_: Throwable) {
        }
        try {
            unregisterReceiver(systemEventsReceiver)
        } catch (_: Throwable) {
        }
        try {
            unregisterReceiver(pluginPackageReceiver)
        } catch (_: Throwable) {
        }
        try {
            unregisterReceiver(userUnlockedReceiver)
        } catch (_: Throwable) {
        }
        overlayManager.hideAssistantOverlay(immediate = true)
        telemetryMonitor.stop()
        overviewProxyClient.unbind()
        mappingServiceClient.unbind()
        repository.unregister()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newLocaleTags = newConfig.locales.toLanguageTags()
        if (newLocaleTags != lastLocaleTags) {
            lastLocaleTags = newLocaleTags
            if (::pluginRegistry.isInitialized) {
                pluginRegistry.refreshPlugins()
            }
            if (::repository.isInitialized) {
                updateForegroundNotification(
                    promoted = repository.promotedToManager.value,
                    recordingState = repository.screenRecordingState.value
                )
            }
            if (::overlayManager.isInitialized) {
                overlayManager.onLocaleChanged()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopActiveRecordingOnPowerTransition(reason: String) {
        if (!::repository.isInitialized) return
        if (!repository.screenRecordingState.value.isRecording) return
        serviceScope.launch(Dispatchers.IO) {
            repository.stopScreenRecording(reason)
        }
    }

    internal fun updateForegroundNotification(
        promoted: Boolean,
        recordingState: ScreenRecordingState = ScreenRecordingState()
    ) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createForegroundNotification(promoted, recordingState))
    }

    internal fun createForegroundNotification(
        promoted: Boolean = false,
        recordingState: ScreenRecordingState = ScreenRecordingState()
    ): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channelTitle = getString(
            if (promoted) {
                R.string.notification_channel_manager_name
            } else {
                R.string.notification_channel_assistant_name
            }
        )
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelTitle,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        nm?.createNotificationChannel(channel)

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val appTitleRes = if (promoted) R.string.app_name_manager else R.string.app_name
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(appTitleRes))
            .setSmallIcon(
                if (recordingState.isRecording) {
                    R.drawable.ic_qs_screen_record
                } else {
                    R.drawable.ic_dual_screen_display
                }
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (recordingState.isRecording) {
            val recordingContentRes = when (recordingState.targetMode) {
                0 -> R.string.notification_recording_top_text
                1 -> R.string.notification_recording_bottom_text
                else -> R.string.notification_recording_both_text
            }
            val elapsedMs = (SystemClock.elapsedRealtime() - recordingState.startedElapsedRealtimeMs)
                .coerceAtLeast(0L)
            val stopIntent = Intent(this, AssistantForegroundService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder
                .setContentText(getString(recordingContentRes))
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - elapsedMs)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_qs_screen_record),
                        getString(R.string.notification_action_stop_recording),
                        stopPendingIntent
                    ).build()
                )
        } else {
            builder.setContentText(getString(R.string.notification_ready_text))
        }

        return builder.build()
    }
}
