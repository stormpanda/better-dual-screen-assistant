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
    val vendorName: String,
    val wakeKeyName: String,
    val physicalButtonLabel: String,
    val topDisplayId: Int,
    val bottomDisplayId: Int,
    val topSfPort: String,
    val bottomSfPort: String,
    val bottomFallbackWidth: Int,
    val bottomFallbackHeight: Int,
    val bottomFallbackDensityDpi: Int,
    val defaultUiLookPreferenceId: Int,
    val useLocalFocusMode: Boolean,
    val blankViaBacklightSysfs: Boolean,
    val stockAssistantPackage: String,
    val mappingPackages: List<String>,
    val protectedVendorPackages: List<String>,
    val supportsAmbientLight: Boolean,
    val supportsPreventAccidentalHome: Boolean,
    val standardControllerLabel: String,
    val controllerStyleOptionLabel: String,
    val topPanelTeClockRatio: Float,
    val longPressTogglesBottom: Boolean,
    val dismissOverlayOnBackMapping: Boolean
) {
    AYN_THOR(
        displayName = "AYN Thor",
        vendorName = "AYN",
        wakeKeyName = "AYN key",
        physicalButtonLabel = "physical AYN button",
        topDisplayId = 0,
        bottomDisplayId = 4,
        topSfPort = "131",
        bottomSfPort = "132",
        bottomFallbackWidth = 1240,
        bottomFallbackHeight = 1080,
        bottomFallbackDensityDpi = 369,
        defaultUiLookPreferenceId = 1,
        useLocalFocusMode = true,
        blankViaBacklightSysfs = false,
        stockAssistantPackage = "com.odin.dualscreen.assistant",
        mappingPackages = listOf("com.odin.mapping", "com.rp.mapping"),
        protectedVendorPackages = listOf(
            "com.odin.dualscreen.assistant",
            "com.odin.gameassistant",
            "com.odin.mapping",
            "com.odin.settings"
        ),
        supportsAmbientLight = true,
        supportsPreventAccidentalHome = true,
        standardControllerLabel = "Odin",
        controllerStyleOptionLabel = "Standard Style",
        topPanelTeClockRatio = 118.412f / 120f,
        longPressTogglesBottom = false,
        dismissOverlayOnBackMapping = false
    ),
    RETROID_POCKET_DUO(
        displayName = "Retroid Pocket Duo",
        vendorName = "Retroid",
        wakeKeyName = "Dual Screen key",
        physicalButtonLabel = "physical Dual Screen button",
        topDisplayId = 0,
        bottomDisplayId = 2,
        topSfPort = "146",
        bottomSfPort = "147",
        bottomFallbackWidth = 1280,
        bottomFallbackHeight = 960,
        bottomFallbackDensityDpi = 320,
        defaultUiLookPreferenceId = 2,
        useLocalFocusMode = false,
        blankViaBacklightSysfs = true,
        stockAssistantPackage = "com.retroidpocket.dualscreen.assistant",
        mappingPackages = listOf("com.rp.mapping", "com.odin.mapping"),
        protectedVendorPackages = listOf(
            "com.retroidpocket.dualscreen.assistant",
            "com.retroidpocket.gamelauncher",
            "com.rp.gameassistant",
            "com.rp.mapping",
            "com.rp.settings"
        ),
        supportsAmbientLight = false,
        supportsPreventAccidentalHome = false,
        standardControllerLabel = "Retro",
        controllerStyleOptionLabel = "Retro",
        topPanelTeClockRatio = 1.0f,
        longPressTogglesBottom = true,
        dismissOverlayOnBackMapping = true
    );

    val closeBottomTipText: String
        get() = if (longPressTogglesBottom) {
            "When the secondary screen is turned off, you can long press or press the $wakeKeyName to wake up the screen."
        } else {
            "When the secondary screen is turned off, you can press the $wakeKeyName to wake up the screen."
        }

    companion object {
        const val ODIN_STOCK_ASSISTANT_PACKAGE = "com.odin.dualscreen.assistant"
        const val RETROID_STOCK_ASSISTANT_PACKAGE = "com.retroidpocket.dualscreen.assistant"

        val allStockAssistantPackages: Set<String> =
            entries.mapTo(LinkedHashSet()) { it.stockAssistantPackage }

        val allProtectedVendorPackages: Set<String> =
            entries.flatMapTo(LinkedHashSet()) { it.protectedVendorPackages }

        val allTopSfPorts: Set<String> =
            entries.mapTo(LinkedHashSet()) { it.topSfPort }

        val allBottomSfPorts: Set<String> =
            entries.mapTo(LinkedHashSet()) { it.bottomSfPort }

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

        private fun isAynThorSystemIdentifier(
            model: String? = Build.MODEL,
            board: String? = Build.BOARD,
            device: String? = Build.DEVICE
        ): Boolean {
            val trimmedModel = model?.trim().orEmpty()
            return trimmedModel.contains("Thor", ignoreCase = true) ||
                board?.trim().equals("kalama", ignoreCase = true) ||
                device?.trim().equals("kalama", ignoreCase = true)
        }

        private fun isRetroidPocketDuoHardware(
            device: String? = Build.DEVICE,
            socModel: String? = Build.SOC_MODEL
        ): Boolean {
            return device?.trim().equals("sun", ignoreCase = true) ||
                socModel?.trim().equals("CQ7790S", ignoreCase = true)
        }

        /**
         * Context-free detection suitable for `UID 0` `app_process` (`PrivilegedHelperMain`).
         */
        fun detectFromSystem(model: String? = Build.MODEL): DeviceProfile {
            testOverride?.let { return it }
            if (model == Build.MODEL) {
                cachedProfile?.let { return it }
            }
            return if (
                isRetroidPocketDuoModel(model) ||
                (model == Build.MODEL &&
                    !isAynThorSystemIdentifier(model) &&
                    isRetroidPocketDuoHardware())
            ) {
                RETROID_POCKET_DUO
            } else {
                AYN_THOR
            }
        }

        /**
         * Resolves the active [DeviceProfile] using hardware/model identifiers first and falling
         * back to checking whether `com.retroidpocket.dualscreen.assistant` is installed.
         */
        fun current(context: Context? = null): DeviceProfile {
            testOverride?.let { return it }
            cachedProfile?.let { return it }

            if (isRetroidPocketDuoModel(Build.MODEL) ||
                (!isAynThorSystemIdentifier() && isRetroidPocketDuoHardware())
            ) {
                return RETROID_POCKET_DUO.also { cachedProfile = it }
            }

            if (isAynThorSystemIdentifier()) {
                return AYN_THOR.also { cachedProfile = it }
            }

            if (context != null) {
                return when (checkPackageInstalled(context, RETROID_STOCK_ASSISTANT_PACKAGE)) {
                    true -> RETROID_POCKET_DUO.also { cachedProfile = it }
                    false -> AYN_THOR.also { cachedProfile = it }
                    null -> AYN_THOR
                }
            }

            return AYN_THOR
        }

        private fun checkPackageInstalled(context: Context, packageName: String): Boolean? {
            return try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                )
                if (info.packageName == packageName) true else null
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Throwable) {
                null
            }
        }
    }
}
