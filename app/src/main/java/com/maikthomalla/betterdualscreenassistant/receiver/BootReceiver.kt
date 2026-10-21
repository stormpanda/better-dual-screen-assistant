package com.maikthomalla.betterdualscreenassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maikthomalla.betterdualscreenassistant.service.AssistantForegroundService

/**
 * Starts [AssistantForegroundService] automatically on device boot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AssistantForegroundService.startService(context)
            }
        }
    }
}
