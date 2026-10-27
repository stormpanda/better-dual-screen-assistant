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
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.stormpanda.betterdualscreenassistant.MainActivity
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.overlay.ScreenOverlayManager
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.MappingServiceClient
import com.stormpanda.betterdualscreenassistant.system.OverviewProxyClient
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.system.TelemetryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        const val ACTION_TCC_KEY = "com.stormpanda.betterdualscreenassistant.action.TCC_KEY"
        const val EXTRA_KEY_ACTION_DOWN = "key_action_down"
        const val EXTRA_IS_LONG_PRESS = "isLongPress"
        private const val TCC_TOGGLE_DEBOUNCE_MS = 200L

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

    private var lastTccToggleElapsedMs: Long = 0L
    private var longPressConsumed: Boolean = false

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
                    overlayManager.onDeviceScreenInteractiveChanged(false)
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    overlayManager.onDeviceScreenInteractiveChanged(true)
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        PrivilegedBridge.ensureInitialized(applicationContext)
        enforceStockTakeoverIfPreferred()

        repository = SystemSettingsRepository.getInstance(applicationContext).also { it.register() }
        telemetryMonitor = TelemetryMonitor(applicationContext)
        overviewProxyClient = OverviewProxyClient(applicationContext).also { it.bind() }
        mappingServiceClient = MappingServiceClient(applicationContext) {
            overlayManager.hideAssistantOverlay()
        }.also { it.bind() }
        overlayManager = ScreenOverlayManager(
            context = applicationContext,
            repository = repository,
            telemetryMonitor = telemetryMonitor,
            overviewProxyClient = overviewProxyClient
        )

        serviceScope.launch {
            repository.dualScreenDisplayMode.collectLatest { mode ->
                overlayManager.applyDisplayModeOverlays(mode)
            }
        }

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
            ACTION_TCC_KEY -> {
                val keyActionDown = intent.getBooleanExtra(EXTRA_KEY_ACTION_DOWN, false)
                val isLongPress = intent.getBooleanExtra(EXTRA_IS_LONG_PRESS, false)
                handleTccButtonEvent(keyActionDown = keyActionDown, isLongPress = isLongPress)
            }
        }
        return START_STICKY
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

        if (mappingServiceClient.isHomeMapped) {
            return
        }

        val currentMode = repository.dualScreenDisplayMode.value

        if (keyActionDown) {
            if (isLongPress) {
                // Replicates TccServices.c(): Long-press turns off the bottom screen
                longPressConsumed = true
                if (!overlayManager.isBottomScreenBlanked && currentMode != 1) {
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
        }
    }

    override fun onDestroy() {
        instance = null
        try {
            unregisterReceiver(systemEventsReceiver)
        } catch (_: Throwable) {
        }
        overlayManager.hideAssistantOverlay()
        telemetryMonitor.stop()
        overviewProxyClient.unbind()
        mappingServiceClient.unbind()
        repository.unregister()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createForegroundNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dual Screen Assistant Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the dual-screen overlay and TCC button handler ready"
            setShowBadge(false)
        }
        nm?.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Ready for TCC button & dual-screen controls")
            .setSmallIcon(R.drawable.ic_dual_screen_display)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
