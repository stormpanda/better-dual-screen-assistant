package com.maikthomalla.betterdualscreenassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maikthomalla.betterdualscreenassistant.service.AssistantForegroundService

/**
 * Manifest receiver for `"action.tcc.button.key.event"` (sent by the AYN Thor framework
 * when the physical TCC / Home button on the bottom housing is pressed or long-pressed).
 */
class TccKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "action.tcc.button.key.event") return
        // When AssistantForegroundService is already running, its dynamically registered
        // systemEventsReceiver handles "action.tcc.button.key.event" directly.
        if (AssistantForegroundService.instance != null) return

        val keyActionDown = if (intent.hasExtra("key_action_down")) {
            intent.getBooleanExtra("key_action_down", true)
        } else {
            false
        }
        val isLongPress = intent.getBooleanExtra("key_isLongPress", false) ||
            intent.getBooleanExtra("isLongPress", false)

        val startIntent = Intent(context, AssistantForegroundService::class.java).apply {
            action = AssistantForegroundService.ACTION_TCC_KEY
            putExtra(AssistantForegroundService.EXTRA_KEY_ACTION_DOWN, keyActionDown)
            putExtra(AssistantForegroundService.EXTRA_IS_LONG_PRESS, isLongPress)
        }
        context.startForegroundService(startIntent)
    }
}
