package com.stormpanda.betterdualscreenassistant

import android.app.Application
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService

class BetterDualScreenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivilegedBridge.ensureInitialized(this)
        AssistantForegroundService.startService(this)
    }
}
