package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Hardware, multi-display, capability, and vendor-package profile abstracting differences
 * between the **AYN Thor** (`kalama`, Android 13 / SDK 33, Display 4 `1240x1080 @ 369dpi`)
 * and **Retroid Pocket Duo** (`sun` / `CQ7790S`, Android 16 / SDK 36, Display 2 `1280x960 @ 320dpi`).
 *
 * Kept free of Compose dependencies so it can be safely referenced from both the app process
 * and the `UID 0` `app_process` supervisor ([com.stormpanda.betterdualscreenassistant.privileged.PrivilegedHelperMain]).
 */
enum class DeviceProfile(
    val displayName: String,
    val topDisplayId: Int,
    val bottomDisplayId: Int,
    val bottomFallbackWidth: Int,
    val bottomFallbackHeight: Int,
    val bottomFallbackDensityDpi: Int,
    val overlayUiScale: Float,
    val useLocalFocusMode: Boolean,
    val blankViaBacklightSysfs: Boolean,
    val stockAssistantPackage: String,
    val mappingPackages: List<String>,
    val supportsAmbientLight: Boolean,
    val standardControllerLabel: String,
    val topPanelTeClockRatio: Float,
    val longPressTogglesBottom: Boolean,
    val dismissOverlayOnBackMapping: Boolean
) {
    AYN_THOR(
        displayName = "AYN Thor",
        topDisplayId = 0,
        bottomDisplayId = 4,
        bottomFallbackWidth = 1240,
        bottomFallbackHeight = 1080,
        bottomFallbackDensityDpi = 369,
        overlayUiScale = 1.0f,
        useLocalFocusMode = true,
        blankViaBacklightSysfs = false,
        stockAssistantPackage = "com.odin.dualscreen.assistant",
        mappingPackages = listOf("com.odin.mapping", "com.rp.mapping"),
        supportsAmbientLight = true,
        standardControllerLabel = "Odin",
        topPanelTeClockRatio = 118.412f / 120f,
        longPressTogglesBottom = false,
        dismissOverlayOnBackMapping = false
    ),
    RETROID_POCKET_DUO(
        displayName = "Retroid Pocket Duo",
        topDisplayId = 0,
        bottomDisplayId = 2,
        bottomFallbackWidth = 1280,
        bottomFallbackHeight = 960,
        bottomFallbackDensityDpi = 320,
        overlayUiScale = 2f / 3f,
        useLocalFocusMode = false,
        blankViaBacklightSysfs = true,
        stockAssistantPackage = "com.retroidpocket.dualscreen.assistant",
        mappingPackages = listOf("com.rp.mapping", "com.odin.mapping"),
        supportsAmbientLight = false,
        standardControllerLabel = "Retro",
        topPanelTeClockRatio = 1.0f,
        longPressTogglesBottom = true,
        dismissOverlayOnBackMapping = true
    );

    companion object {
        const val ODIN_STOCK_ASSISTANT_PACKAGE = "com.odin.dualscreen.assistant"
        const val RETROID_STOCK_ASSISTANT_PACKAGE = "com.retroidpocket.dualscreen.assistant"

        @Volatile
        internal var testOverride: DeviceProfile? = null

        @Volatile
        private var cachedProfile: DeviceProfile? = null

        internal fun resetCacheForTesting() {
            cachedProfile = null
        }

        fun isRetroidPocketDuoModel(model: String? = Build.MODEL): Boolean {
            val trimmed = model?.trim().orEmpty()
            return trimmed.startsWith("Retroid Pocket Duo", ignoreCase = true)
        }

        /**
         * Context-free detection suitable for `UID 0` `app_process` (`PrivilegedHelperMain`).
         */
        fun detectFromSystem(model: String? = Build.MODEL): DeviceProfile {
            testOverride?.let { return it }
            cachedProfile?.let { return it }
            return if (isRetroidPocketDuoModel(model)) {
                RETROID_POCKET_DUO
            } else {
                AYN_THOR
            }
        }

        /**
         * Resolves the active [DeviceProfile] using `Build.MODEL` first and falling back to
         * checking whether `com.retroidpocket.dualscreen.assistant` is installed on the system.
         */
        fun current(context: Context? = null): DeviceProfile {
            testOverride?.let { return it }
            cachedProfile?.let { return it }

            if (isRetroidPocketDuoModel(Build.MODEL)) {
                return RETROID_POCKET_DUO.also { cachedProfile = it }
            }

            if (context != null) {
                val resolved = if (isPackageInstalled(context, RETROID_STOCK_ASSISTANT_PACKAGE)) {
                    RETROID_POCKET_DUO
                } else {
                    AYN_THOR
                }
                cachedProfile = resolved
                return resolved
            }

            return AYN_THOR
        }

        private fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                )
                info.packageName == packageName
            } catch (_: Throwable) {
                false
            }
        }
    }
}
