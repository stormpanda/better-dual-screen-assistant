package com.maikthomalla.betterdualscreenassistant.service

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
import com.maikthomalla.betterdualscreenassistant.MainActivity
import com.maikthomalla.betterdualscreenassistant.R
import com.maikthomalla.betterdualscreenassistant.overlay.ScreenOverlayManager
import com.maikthomalla.betterdualscreenassistant.privileged.PServerClient
import com.maikthomalla.betterdualscreenassistant.privileged.PrivilegedBridge
import com.maikthomalla.betterdualscreenassistant.system.MappingServiceClient
import com.maikthomalla.betterdualscreenassistant.system.OverviewProxyClient
import com.maikthomalla.betterdualscreenassistant.system.SystemSettingsRepository
import com.maikthomalla.betterdualscreenassistant.system.TelemetryMonitor
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

        const val ACTION_START = "com.maikthomalla.betterdualscreenassistant.action.START"
        const val ACTION_TOGGLE_OVERLAY = "com.maikthomalla.betterdualscreenassistant.action.TOGGLE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "com.maikthomalla.betterdualscreenassistant.action.SHOW_OVERLAY"
        const val ACTION_SHOW_MODE = "com.maikthomalla.betterdualscreenassistant.action.SHOW_MODE"
        const val ACTION_SHOW_SETTINGS = "com.maikthomalla.betterdualscreenassistant.action.SHOW_SETTINGS"
        const val ACTION_HIDE_OVERLAY = "com.maikthomalla.betterdualscreenassistant.action.HIDE_OVERLAY"
        const val ACTION_TCC_KEY = "com.maikthomalla.betterdualscreenassistant.action.TCC_KEY"
        const val EXTRA_IS_LONG_PRESS = "isLongPress"

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

    private var lastTccEventElapsedMs: Long = 0L

    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "action.tcc.button.key.event" -> {
                    val isLongPress = intent.getBooleanExtra("isLongPress", false)
                    handleTccButtonEvent(isLongPress)
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

        repository = SystemSettingsRepository(applicationContext).also { it.register() }
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
        registerReceiver(systemEventsReceiver, filter, Context.RECEIVER_EXPORTED)
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
                val isLongPress = intent.getBooleanExtra(EXTRA_IS_LONG_PRESS, false)
                handleTccButtonEvent(isLongPress)
            }
        }
        return START_STICKY
    }

    private fun enforceStockTakeoverIfPreferred() {
        if (PServerClient.isReplaceStockPreferred(applicationContext)) {
            serviceScope.launch(Dispatchers.IO) {
                if (PServerClient.isStockAssistantEnabled(applicationContext)) {
                    PServerClient.setStockAssistantEnabled(false, applicationContext)
                }
            }
        }
    }

    fun handleTccButtonEvent(isLongPress: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTccEventElapsedMs < 150L) {
            return
        }
        lastTccEventElapsedMs = now

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
        if (isLongPress) {
            if (currentMode != 1 && !overlayManager.isBottomScreenBlanked) {
                repository.setDualScreenDisplayMode(1)
            }
        } else {
            if (overlayManager.isBottomScreenBlanked || currentMode == 1) {
                overlayManager.wakeBottomScreen()
            } else {
                overlayManager.toggleAssistantOverlay()
            }
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
