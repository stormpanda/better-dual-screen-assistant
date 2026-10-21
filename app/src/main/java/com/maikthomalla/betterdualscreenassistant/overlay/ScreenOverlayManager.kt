package com.maikthomalla.betterdualscreenassistant.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.maikthomalla.betterdualscreenassistant.R
import com.maikthomalla.betterdualscreenassistant.privileged.PServerClient
import com.maikthomalla.betterdualscreenassistant.privileged.PrivilegedBridge
import com.maikthomalla.betterdualscreenassistant.system.OverviewProxyClient
import com.maikthomalla.betterdualscreenassistant.system.SystemSettingsRepository
import com.maikthomalla.betterdualscreenassistant.system.TelemetryMonitor
import com.maikthomalla.betterdualscreenassistant.ui.overlay.AssistantOverlayScreen
import com.maikthomalla.betterdualscreenassistant.ui.overlay.CloseScreenTipOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

/**
 * Manages the multi-display overlays on the AYN Thor:
 * - Main Assistant Compose overlay on Display 4 (bottom screen, 1240x1080)
 * - Top screen (Display 0, 1920x1080) 200ms fade-in/out black blanking overlay
 * - Bottom screen (Display 4, 1240x1080) 200ms fade-in/out black blanking overlay
 * - Custom rounded toast on Display 4
 */
class ScreenOverlayManager(
    private val context: Context,
    private val repository: SystemSettingsRepository,
    private val telemetryMonitor: TelemetryMonitor,
    private val overviewProxyClient: OverviewProxyClient
) {
    companion object {
        private const val TAG = "ScreenOverlayManager"
        const val TOP_DISPLAY_ID = 0
        const val BOTTOM_DISPLAY_ID = 4
        private const val FADE_DURATION_MS = 200L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private var assistantView: ComposeView? = null
    private var assistantLifecycle: OverlayLifecycleOwner? = null
    var isAssistantShowing: Boolean = false
        private set

    private val activeTabState = MutableStateFlow(0)

    private var topBlankView: View? = null
    private var bottomBlankView: View? = null
    var isTopScreenBlanked: Boolean = false
        private set
    var isBottomScreenBlanked: Boolean = false
        private set

    var isTemporaryBottomClosed: Boolean = false
        private set

    private var tipDialogView: ComposeView? = null
    private var tipDialogLifecycle: OverlayLifecycleOwner? = null

    private var savedTopBrightnessLinear: Float? = null
    private var savedBottomBrightnessLinear: Float? = null

    fun toggleAssistantOverlay() {
        mainHandler.post {
            if (isAssistantShowing) {
                hideAssistantOverlay()
            } else {
                showAssistantOverlay()
            }
        }
    }

    fun showAssistantOverlay(initialTab: Int? = null) {
        mainHandler.post {
            if (initialTab != null) {
                activeTabState.value = initialTab.coerceIn(0, 1)
            } else if (!isAssistantShowing) {
                activeTabState.value = 0
            }
            if (isAssistantShowing) return@post
            repository.refreshAll()
            val wm = getWindowManagerForDisplay(BOTTOM_DISPLAY_ID) ?: return@post
            val displayContext = getDisplayContext(BOTTOM_DISPLAY_ID)
            val realSize = getDisplayRealSize(BOTTOM_DISPLAY_ID, fallbackWidth = 1240, fallbackHeight = 1080)

            val lifecycleOwner = OverlayLifecycleOwner().apply {
                onCreate()
                onResume()
            }
            val composeView = ComposeView(displayContext).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    AssistantOverlayScreen(
                        repository = repository,
                        telemetryMonitor = telemetryMonitor,
                        activeTabFlow = activeTabState.asStateFlow(),
                        onSelectTab = { tab -> activeTabState.value = tab.coerceIn(0, 1) },
                        onOpenRecentsRequest = {
                            hideAssistantOverlay()
                            overviewProxyClient.toggleRecentAppsOnBottomScreen()
                        },
                        onCleanMemoryRequest = {
                            performCleanMemoryWithToast()
                        },
                        onDismissOverlayRequest = {
                            hideAssistantOverlay()
                        }
                    )
                }
            }

            // Replicate TccServices.u(): exact pixel dimensions of Display 4 (1240x1080),
            // Gravity.CENTER, and no FLAG_LAYOUT_NO_LIMITS so it never overflows to 1920 width.
            val lp = WindowManager.LayoutParams(
                realSize.x,
                realSize.y,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            try {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    PServerClient.ensurePermissionsGranted(context)
                }
                wm.addView(composeView, lp)
                applyImmersiveFullscreen(composeView)
                composeView.requestFocus()
                assistantView = composeView
                assistantLifecycle = lifecycleOwner
                isAssistantShowing = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to add assistant overlay on display 4", t)
                lifecycleOwner.onDestroy()
            }
        }
    }

    fun hideAssistantOverlay() {
        mainHandler.post {
            val view = assistantView ?: return@post
            val wm = getWindowManagerForDisplay(BOTTOM_DISPLAY_ID)
            try {
                wm?.removeViewImmediate(view)
            } catch (_: Throwable) {
            }
            assistantLifecycle?.onPause()
            assistantLifecycle?.onDestroy()
            assistantLifecycle = null
            assistantView = null
            isAssistantShowing = false
            telemetryMonitor.stop()
        }
    }

    /**
     * Synchronizes the top and bottom black blanking overlays with `dual_screen_display_mode`:
     * - 0: Dual-Screen (unblank both unless temporary bottom close is active)
     * - 1: Upper Screen Display Only (blank bottom screen, unblank top screen)
     * - 2: Lower Screen Display Only (blank top screen, unblank bottom screen)
     */
    fun applyDisplayModeOverlays(mode: Int) {
        mainHandler.post {
            when (mode) {
                0 -> {
                    isTemporaryBottomClosed = false
                    setTopScreenBlanked(false)
                    setBottomScreenBlanked(false)
                }
                1 -> {
                    isTemporaryBottomClosed = false
                    hideAssistantOverlay()
                    setTopScreenBlanked(false)
                    setBottomScreenBlanked(true)
                }
                2 -> {
                    isTemporaryBottomClosed = false
                    setBottomScreenBlanked(false)
                    setTopScreenBlanked(true)
                }
            }
        }
    }

    /**
     * Wakes the bottom screen from either temporary close or Mode 1 (Upper Screen Display Only).
     */
    fun wakeBottomScreen() {
        mainHandler.post {
            if (repository.dualScreenDisplayMode.value == 1) {
                repository.setDualScreenDisplayMode(0)
            } else if (isTemporaryBottomClosed || isBottomScreenBlanked) {
                isTemporaryBottomClosed = false
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,0")
                PrivilegedBridge.putSystemSettingInt(
                    context,
                    SystemSettingsRepository.KEY_ENABLE_SYSTEM_KEY_FOCUS_LOCK,
                    0
                )
                setBottomScreenBlanked(false)
            }
        }
    }

    /**
     * Called when the device screen turns off or wakes up so brightness is restored prior to sleep.
     */
    fun onDeviceScreenInteractiveChanged(interactive: Boolean) {
        mainHandler.post {
            if (!interactive) {
                hideAssistantOverlay()
                dismissCloseScreenTip()
                savedTopBrightnessLinear?.let {
                    PrivilegedBridge.setDisplayBrightness(context, TOP_DISPLAY_ID, it, false)
                }
                savedBottomBrightnessLinear?.let {
                    PrivilegedBridge.setDisplayBrightness(context, BOTTOM_DISPLAY_ID, it, false)
                }
            } else {
                repository.reapplyTouchDisableForCurrentMode()
                if (isTopScreenBlanked) {
                    PrivilegedBridge.setDisplayBrightness(context, TOP_DISPLAY_ID, -1.0f, false)
                }
                if (isBottomScreenBlanked) {
                    PrivilegedBridge.setDisplayBrightness(context, BOTTOM_DISPLAY_ID, -1.0f, false)
                }
            }
        }
    }

    fun performCleanMemoryWithToast() {
        mainHandler.post {
            val (killedCount, availMemStr) = repository.cleanMemory()
            val message = if (killedCount > 0) {
                context.getString(R.string.toast_clean_process_result, killedCount, availMemStr)
            } else {
                context.getString(R.string.toast_clean_process_successful)
            }
            showBottomToast(message)
        }
    }

    fun showBottomToast(message: String) {
        mainHandler.post {
            val wm = getWindowManagerForDisplay(BOTTOM_DISPLAY_ID) ?: return@post
            val displayContext = getDisplayContext(BOTTOM_DISPLAY_ID)

            val container = FrameLayout(displayContext)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(displayContext, 20f).toFloat()
                setColor(Color.parseColor("#CC272727"))
            }
            val textView = TextView(displayContext).apply {
                text = message
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                background = bg
                val hPad = dpToPx(displayContext, 20f)
                val vPad = dpToPx(displayContext, 10f)
                setPadding(hPad, vPad, hPad, vPad)
            }
            container.addView(
                textView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dpToPx(displayContext, 80f)
            }

            try {
                wm.addView(container, lp)
                mainHandler.postDelayed({
                    try {
                        wm.removeViewImmediate(container)
                    } catch (_: Throwable) {
                    }
                }, 2200L)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to show bottom toast", t)
            }
        }
    }

    private fun setTopScreenBlanked(blank: Boolean) {
        if (blank == isTopScreenBlanked) return
        val wm = getWindowManagerForDisplay(TOP_DISPLAY_ID) ?: return
        if (blank) {
            isTopScreenBlanked = true
            val info = PrivilegedBridge.getDisplayBrightnessInfo(context, TOP_DISPLAY_ID)
            val cur = info.getOrElse(0) { 0.4f }
            if (cur >= 0f) {
                savedTopBrightnessLinear = cur
            }
            val view = View(getDisplayContext(TOP_DISPLAY_ID)).apply {
                setBackgroundColor(Color.BLACK)
                alpha = 0f
                setOnTouchListener { _, _ -> true }
            }
            val lp = createBlankOverlayLayoutParams(TOP_DISPLAY_ID, 1920, 1080)
            try {
                wm.addView(view, lp)
                applyImmersiveFullscreen(view)
                topBlankView = view
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    duration = FADE_DURATION_MS
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (isTopScreenBlanked) {
                                PrivilegedBridge.setDisplayBrightness(context, TOP_DISPLAY_ID, -1.0f, false)
                            }
                        }
                    })
                    start()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to add top blank overlay", t)
            }
        } else {
            isTopScreenBlanked = false
            val restore = savedTopBrightnessLinear ?: 0.4f
            PrivilegedBridge.setDisplayBrightness(context, TOP_DISPLAY_ID, restore, false)
            val view = topBlankView ?: return
            topBlankView = null
            ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
                duration = FADE_DURATION_MS
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            wm.removeViewImmediate(view)
                        } catch (_: Throwable) {
                        }
                    }
                })
                start()
            }
        }
    }

    private fun setBottomScreenBlanked(blank: Boolean) {
        if (blank == isBottomScreenBlanked) return
        val wm = getWindowManagerForDisplay(BOTTOM_DISPLAY_ID) ?: return
        if (blank) {
            isBottomScreenBlanked = true
            val info = PrivilegedBridge.getDisplayBrightnessInfo(context, BOTTOM_DISPLAY_ID)
            val cur = info.getOrElse(0) { 0.4f }
            if (cur >= 0f) {
                savedBottomBrightnessLinear = cur
            }
            val view = View(getDisplayContext(BOTTOM_DISPLAY_ID)).apply {
                setBackgroundColor(Color.BLACK)
                alpha = 0f
                setOnTouchListener { _, _ -> true }
            }
            val lp = createBlankOverlayLayoutParams(BOTTOM_DISPLAY_ID, 1240, 1080)
            try {
                wm.addView(view, lp)
                applyImmersiveFullscreen(view)
                bottomBlankView = view
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    duration = FADE_DURATION_MS
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (isBottomScreenBlanked) {
                                PrivilegedBridge.setDisplayBrightness(context, BOTTOM_DISPLAY_ID, -1.0f, false)
                            }
                        }
                    })
                    start()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to add bottom blank overlay", t)
            }
        } else {
            isBottomScreenBlanked = false
            val restore = savedBottomBrightnessLinear ?: 0.4f
            PrivilegedBridge.setDisplayBrightness(context, BOTTOM_DISPLAY_ID, restore, false)
            val view = bottomBlankView ?: return
            bottomBlankView = null
            ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
                duration = FADE_DURATION_MS
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            wm.removeViewImmediate(view)
                        } catch (_: Throwable) {
                        }
                    }
                })
                start()
            }
        }
    }

    private fun dismissCloseScreenTip() {
        val view = tipDialogView ?: return
        val wm = getWindowManagerForDisplay(TOP_DISPLAY_ID)
        try {
            wm?.removeViewImmediate(view)
        } catch (_: Throwable) {
        }
        tipDialogLifecycle?.onPause()
        tipDialogLifecycle?.onDestroy()
        tipDialogLifecycle = null
        tipDialogView = null
    }

    private fun createBlankOverlayLayoutParams(
        displayId: Int,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): WindowManager.LayoutParams {
        val size = getDisplayRealSize(displayId, fallbackWidth, fallbackHeight)
        return WindowManager.LayoutParams(
            size.x,
            size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun getDisplayRealSize(displayId: Int, fallbackWidth: Int, fallbackHeight: Int): Point {
        val point = Point(fallbackWidth, fallbackHeight)
        val display = displayManager?.getDisplay(displayId)
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealSize(point)
        }
        return point
    }

    private fun applyImmersiveFullscreen(view: View) {
        view.post {
            val controller = view.windowInsetsController
            if (controller != null) {
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        }
    }

    private fun getDisplayContext(displayId: Int): Context {
        val display = displayManager?.getDisplay(displayId)
            ?: displayManager?.getDisplay(TOP_DISPLAY_ID)
            ?: return context
        return context.createDisplayContext(display)
    }

    private fun getWindowManagerForDisplay(displayId: Int): WindowManager? {
        return getDisplayContext(displayId).getSystemService(WindowManager::class.java)
    }

    private fun dpToPx(ctx: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            ctx.resources.displayMetrics
        ).toInt()
    }
}
