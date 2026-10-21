package com.maikthomalla.betterdualscreenassistant

import android.app.Application
import com.maikthomalla.betterdualscreenassistant.privileged.PrivilegedBridge
import com.maikthomalla.betterdualscreenassistant.service.AssistantForegroundService

class BetterDualScreenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivilegedBridge.ensureInitialized(this)
        AssistantForegroundService.startService(this)
    }
}
