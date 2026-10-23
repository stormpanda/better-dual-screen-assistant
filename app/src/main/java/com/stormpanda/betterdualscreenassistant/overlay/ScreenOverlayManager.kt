package com.stormpanda.betterdualscreenassistant.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
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
import com.stormpanda.betterdualscreenassistant.MainActivity
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.privileged.PServerClient
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import com.stormpanda.betterdualscreenassistant.system.OverviewProxyClient
import com.stormpanda.betterdualscreenassistant.system.SystemSettingsRepository
import com.stormpanda.betterdualscreenassistant.system.TelemetryMonitor
import com.stormpanda.betterdualscreenassistant.ui.overlay.AssistantOverlayScreen
import com.stormpanda.betterdualscreenassistant.ui.overlay.CloseScreenTipOverlay
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
 * Fullscreen black blanking view modeled after stock `T0.c`:
 * - Applies `systemUiVisibility = 4102` (`IMMERSIVE_STICKY | FULLSCREEN | HIDE_NAVIGATION`) and
 *   [WindowInsetsController.hide] for status/navigation/gesture bars on attach and focus change.
 * - Excludes system gesture rects across the entire view bounds (`T0.c.setSystemGestureExclusion`).
 */
private class BlankOverlayView(context: Context) : View(context) {
    init {
        setBackgroundColor(Color.BLACK)
        setOnTouchListener { _, _ -> true }
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applySystemBarsHidden()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            applySystemBarsHidden()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            systemGestureExclusionRects = listOf(Rect(-w, -h, w, h))
        }
    }

    fun applySystemBarsHidden() {
        @Suppress("DEPRECATION")
        systemUiVisibility = 4102 or
            SYSTEM_UI_FLAG_LAYOUT_STABLE or
            SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val controller = windowInsetsController
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(
                WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.systemBars()
            )
        }
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
    private var exitingAssistantView: ComposeView? = null
    private var exitingAssistantLifecycle: OverlayLifecycleOwner? = null
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

    private fun cleanupExitingAssistant() {
        val oldView = exitingAssistantView
        val oldLifecycle = exitingAssistantLifecycle
        exitingAssistantView = null
        exitingAssistantLifecycle = null
        if (oldView != null) {
            try {
                oldView.animate().cancel()
                getWindowManagerForDisplay(BOTTOM_DISPLAY_ID)?.removeViewImmediate(oldView)
            } catch (_: Throwable) {
            }
        }
        oldLifecycle?.onPause()
        oldLifecycle?.onDestroy()
    }

    fun showAssistantOverlay(initialTab: Int? = null) {
        mainHandler.post {
            if (initialTab != null) {
                activeTabState.value = initialTab.coerceIn(0, 1)
            } else if (!isAssistantShowing && !repository.rememberLastTab.value) {
                activeTabState.value = 0
            }
            if (isAssistantShowing) return@post
            cleanupExitingAssistant()

            repository.refreshAll()
            if (repository.dualScreenDisplayMode.value == 0 && repository.touchLockEnabled.value) {
                // Temporarily allow bottom-screen touch while the Assistant Overlay is open
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,0")
            }
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
                // Start at 90%p Y offset & 0 alpha to match pop_win_enter_anim.xml
                translationY = realSize.y * 0.90f
                alpha = 0f
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
                        onSwapScreensRequest = {
                            performSwapScreensWithToast()
                        },
                        onToggleTouchLockRequest = {
                            performToggleTouchLockWithToast()
                        },
                        onCaptureScreenshotRequest = { targetMode ->
                            performScreenshotWithToast(targetMode)
                        },
                        onOpenEditRequest = {
                            openMainActivityOnTopScreen()
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
                windowAnimations = R.style.tcc_anim
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
                composeView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(FADE_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                assistantView = composeView
                assistantLifecycle = lifecycleOwner
                isAssistantShowing = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to add assistant overlay on display 4", t)
                lifecycleOwner.onDestroy()
            }
        }
    }

    fun hideAssistantOverlay(immediate: Boolean = false) {
        mainHandler.post {
            val view = assistantView ?: return@post
            val lifecycle = assistantLifecycle
            assistantView = null
            assistantLifecycle = null
            isAssistantShowing = false
            telemetryMonitor.stop()
            repository.reapplyTouchDisableForCurrentMode()

            if (immediate) {
                try {
                    view.animate().cancel()
                    getWindowManagerForDisplay(BOTTOM_DISPLAY_ID)?.removeViewImmediate(view)
                } catch (_: Throwable) {
                }
                lifecycle?.onPause()
                lifecycle?.onDestroy()
                return@post
            }

            cleanupExitingAssistant()
            exitingAssistantView = view
            exitingAssistantLifecycle = lifecycle

            val slideOutDistance = (view.height.takeIf { it > 0 } ?: 1080) * 0.90f
            view.animate()
                .translationY(slideOutDistance)
                .alpha(0f)
                .setDuration(FADE_DURATION_MS)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (exitingAssistantView === view) {
                        cleanupExitingAssistant()
                    } else {
                        try {
                            getWindowManagerForDisplay(BOTTOM_DISPLAY_ID)?.removeViewImmediate(view)
                        } catch (_: Throwable) {
                        }
                        lifecycle?.onPause()
                        lifecycle?.onDestroy()
                    }
                }
                .start()
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
                repository.restoreDualScreenFocusLock()
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
                hideAssistantOverlay(immediate = true)
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
                    topBlankView?.let { applyImmersiveFullscreen(it) }
                    PrivilegedBridge.setTopScreenBlanked(context, blank = true, showCoverWindow = true)
                    PrivilegedBridge.setDisplayBrightness(context, TOP_DISPLAY_ID, -1.0f, false)
                }
                if (isBottomScreenBlanked) {
                    bottomBlankView?.let { applyImmersiveFullscreen(it) }
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

    fun performSwapScreensWithToast() {
        hideAssistantOverlay()
        mainHandler.postDelayed({
            Thread {
                val swapped = repository.swapScreens()
                mainHandler.post {
                    if (swapped) {
                        showBottomToast("Swapped apps between screens")
                    } else {
                        showBottomToast("No movable foreground apps to swap")
                    }
                }
            }.start()
        }, 220L)
    }

    fun performToggleTouchLockWithToast() {
        mainHandler.post {
            val locked = repository.toggleTouchLock()
            if (locked) {
                hideAssistantOverlay()
                showBottomToast("Bottom screen touch locked (open Assistant to unlock)")
            } else {
                PrivilegedBridge.setSystemProperty("sys.temp.disabled.touch", "0,0")
                showBottomToast("Bottom screen touch unlocked")
            }
        }
    }

    fun performScreenshotWithToast(targetMode: Int) {
        hideAssistantOverlay()
        mainHandler.postDelayed({
            Thread {
                val ok = PrivilegedBridge.captureScreenshot(context, targetMode)
                val targetName = when (targetMode) {
                    0 -> "Top screen"
                    1 -> "Bottom screen"
                    else -> "Both screens"
                }
                mainHandler.post {
                    if (ok) {
                        showBottomToast("Saved $targetName screenshot to Pictures/Screenshots")
                    } else {
                        showBottomToast("Failed to capture $targetName screenshot")
                    }
                }
            }.start()
        }, 280L)
    }

    fun openMainActivityOnTopScreen() {
        mainHandler.post {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                val options = ActivityOptions.makeBasic().apply {
                    launchDisplayId = TOP_DISPLAY_ID
                }
                context.startActivity(intent, options.toBundle())
            } catch (t: Throwable) {
                Log.w(TAG, "Direct startActivity on display 0 failed, using root fallback", t)
            }
            // Ensure the activity task is moved to Display 0 even if it was already open on Display 4
            PServerClient.exec(
                "am start --display 0 --activity-single-top -n ${context.packageName}/.MainActivity",
                wait = false
            )
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
                PrivilegedBridge.updateSavedBrightness(top = cur)
            }
            PrivilegedBridge.setTopScreenBlanked(context, blank = true, showCoverWindow = false)
            topBlankView?.let { stale ->
                try {
                    wm.removeViewImmediate(stale)
                } catch (_: Throwable) {
                }
                topBlankView = null
            }
            val view = BlankOverlayView(getDisplayContext(TOP_DISPLAY_ID)).apply {
                alpha = 0f
            }
            val lp = createBlankOverlayLayoutParams(TOP_DISPLAY_ID, 1920, 1080)
            try {
                wm.addView(view, lp)
                applyImmersiveFullscreen(view)
                view.requestFocus()
                topBlankView = view
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    duration = FADE_DURATION_MS
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (isTopScreenBlanked) {
                                PrivilegedBridge.setTopScreenBlanked(
                                    context,
                                    blank = true,
                                    showCoverWindow = true
                                )
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
            PrivilegedBridge.setTopScreenBlanked(context, blank = false, showCoverWindow = false)
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
                PrivilegedBridge.updateSavedBrightness(bottom = cur)
            }
            bottomBlankView?.let { stale ->
                try {
                    wm.removeViewImmediate(stale)
                } catch (_: Throwable) {
                }
                bottomBlankView = null
            }
            val view = BlankOverlayView(getDisplayContext(BOTTOM_DISPLAY_ID)).apply {
                alpha = 0f
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
        // Replicate stock ScreenUtil.c()/d() flags (132608 = FLAG_ALT_FOCUSABLE_IM | FLAG_FULLSCREEN |
        // FLAG_LAYOUT_NO_LIMITS) without FLAG_NOT_FOCUSABLE so DisplayPolicy/InsetsPolicy allows this
        // fullscreen window to hide the Status Bar and Gesture Bar on Display 0.
        // Never include FLAG_LAYOUT_NO_LIMITS on Display 4 (BOTTOM_DISPLAY_ID).
        var flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE
        if (displayId == TOP_DISPLAY_ID) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return WindowManager.LayoutParams(
            if (displayId == TOP_DISPLAY_ID) WindowManager.LayoutParams.MATCH_PARENT else size.x,
            if (displayId == TOP_DISPLAY_ID) WindowManager.LayoutParams.MATCH_PARENT else size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            title = "primaryScreenTopLayout"
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
        if (view is BlankOverlayView) {
            view.applySystemBarsHidden()
        }
        view.post {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = 4102 or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val controller = view.windowInsetsController
            if (controller != null) {
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.systemBars()
                )
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
