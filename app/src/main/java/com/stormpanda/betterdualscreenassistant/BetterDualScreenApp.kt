package com.stormpanda.betterdualscreenassistant

import android.app.Application
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.service.AssistantForegroundService
import com.stormpanda.megingiard.update.UpdateManager

class BetterDualScreenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivilegedBridge.ensureInitialized(this)
        AssistantForegroundService.startService(this)
        UpdateManager.init(this)
        UpdateManager.checkForUpdates(force = false)
    }
}
