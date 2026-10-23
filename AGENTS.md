# AGENTS.md — Better Dual Screen Assistant

This file provides essential context, architectural invariants, hardware quirks, and workflow rules for AI coding agents working on `better-dual-screen-assistant` (`com.stormpanda.betterdualscreenassistant`).

---

## 1. Project Overview & Reference Material

- **Target Device**: **AYN Thor** (`kalama` / Snapdragon 8 Gen 2, Android 13 / SDK 33).
- **Goal**: A standard user-installable APK that replaces the built-in system app `DualScreenAssistant` (`com.odin.dualscreen.assistant`) with 1:1 visual and functional parity (and future enhancements).
- **Decompiled Stock Reference**:
  - Stock APK: `reference/DualScreenAssistant/DualScreenAssistant.apk`
  - Decompiled Java/Smali sources: `reference/decompiled/DualScreenAssistant/sources/`
  - Decompiled XML layouts, drawables, dimens, strings: `reference/decompiled/DualScreenAssistant/resources/res/`
  - Always consult `reference/decompiled/DualScreenAssistant/` when verifying stock system keys, binder calls, layout dimensions, or animations.
  - **Note**: `reference/` is listed in `.gitignore` and must never be committed to git.

---

## 2. Build, ADB & Sandbox Execution Rules (macOS)

- **Always Use `BypassSandbox: true` for `adb` and `./gradlew`**:
  - `/Users/maikthomalla/Library/Android/sdk/platform-tools/adb` lives outside the workspace and communicates with the ADB server over localhost sockets. Running it in the standard sandbox fails with `zsh: operation not permitted`.
  - `./gradlew` requires localhost TCP sockets to communicate with the Gradle daemon and access to `~/.gradle`.
  - Do **not** attempt `BypassSandbox: false` first for `adb` or `./gradlew` commands; execute them directly with `BypassSandbox: true`.
- **Device Testing Rule (Do Not Simulate App Usage)**:
  - Do **not** attempt to simulate usage of the app on the device (e.g., waking the screen, injecting input events, starting overlay intents to inspect UI, or taking screenshots) to test your implementation.
  - Instead, after building (and optionally installing) your changes, **ask the user to perform the testing** on the device if verification is needed.
- **Common Commands**:
  - Build debug APK (`app/debug/`): `./scripts/debug.sh build` (or `./gradlew assembleDebug`)
  - Install debug APK on device: `./scripts/debug.sh install`
  - Build & install debug APK in one step: `./scripts/debug.sh build-install`
  - Build / install release APK (`app/release/`): `./scripts/release.sh {build|install|build-install}`
  - Start foreground service: `/Users/maikthomalla/Library/Android/sdk/platform-tools/adb shell am start-foreground-service -n com.stormpanda.betterdualscreenassistant/.service.AssistantForegroundService`
- **Capturing Display Screenshots via ADB (Only When Explicitly Requested)**:
  - Physical display IDs (`screencap -d <id>`) are assigned per boot/reconnect by SurfaceFlinger. Query current IDs with:
    `adb shell dumpsys SurfaceFlinger --display-id` (`port=131` = Top `1920×1080`, `port=132` = Bottom `1240×1080`).
  - Capture screenshot:
    `adb exec-out screencap -p -d <physical_display_id> > /tmp/bottom.png`

---

## 3. Git & Version Control Conventions

- **Conventional Commits**: Always format commit messages according to the [Conventional Commits](https://www.conventionalcommits.org/) specification:
  `<type>[optional scope]: <description>`
  - Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
  - Keep the description imperative, concise, and lowercase after the prefix (e.g., `fix: handle AYN button short/long-press state machine and add slide-up overlay animation`).
- **Always Propose a Commit Message**: Whenever you finish a task that modifies tracked or new project files (and haven't already committed them as part of the request), always end your response with a **Proposed commit message** formatted as a Conventional Commit inside a fenced code block.

---

## 4. Privileged Architecture (`PServerBinder` & Hidden APIs)

Because the app is installed as a regular user app without `android:sharedUserId="android.uid.system"`, it achieves system-level privileges through two mechanisms:

1. **`PServerBinder` Root Execution Service (`PServerClient.kt`)**:
   - The AYN Thor firmware exposes a root shell binder service registered in `ServiceManager` under `"PServerBinder"`.
   - **Protocol**:
     - Transaction code: `0` (`IBinder.FIRST_CALL_TRANSACTION - 1`)
     - Request `Parcel`: `data.writeStringArray(arrayOf(command, if (wait) "1" else "0"))`
     - Reply `Parcel`: `reply.createByteArray()` (UTF-8 stdout bytes)
   - Used in `PServerClient.ensurePermissionsGranted()` to self-grant `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `GET_USAGE_STATS`, `WRITE_SECURE_SETTINGS`, `PACKAGE_USAGE_STATS`, `BLUETOOTH_CONNECT`, and Doze whitelist on startup.
2. **Hidden API Bypass (`PrivilegedBridge.kt` & `BetterDualScreenApp.kt`)**:
   - `HiddenApiBypass.addHiddenApiExemptions("")` is called in `BetterDualScreenApp.attachBaseContext()` so the app can reflectively invoke hidden Android 13 APIs in-process (`DisplayManager.setBrightness`, `DisplayManager.setTemporaryBrightness`, `Display.getBrightnessInfo`, `ServiceManager.getService`, `WindowManagerGlobal`) with automatic fallback to `PServerClient.exec()`.
3. **Vendor Binder Services (`VendorServices.kt`)**:
   - **Fan Control**: `ServiceManager.getService("SettingsController")` (`com.ro.settings.ISettingsService`) -> `getProvider("FanProvider")` (`com.ro.settings.IFanControlProvider`).
   - **Bottom-Screen Recents**: Binds to `android.intent.action.QUICKSTEP_SERVICE` (`com.android.systemui` `IOverviewProxy`, transaction `6` with `(displayId = 4, packageName)`).
   - **HOME Key Interception**: Binds to `com.ro.mapping.action.MAPPING_SERVICE` (`com.ro.mapping` `IMappingService`) to dismiss the overlay when `InputId.HOME` is pressed.

---

## 5. Stock `DualScreenAssistant` Ephemeral Suppression & Uninstall Recovery

The built-in `com.odin.dualscreen.assistant` (`/system/app/DualScreenAssistant/DualScreenAssistant.apk`) is declared with `android:persistent="true"` and `android:sharedUserId="android.uid.system"`.

- **Pitfall 1**: `am force-stop com.odin.dualscreen.assistant` is **ignored** by `ActivityManagerService` for persistent system apps.
- **Pitfall 2**: Sending `kill -STOP` (`SIGSTOP`) while its `TccServices` overlay window is attached freezes the stock overlay window on the screen permanently because `WindowManagerService` does not remove windows belonging to stopped processes.
- **Pitfall 3**: Leaving `com.odin.dualscreen.assistant` or `.services.TccServices` permanently `pm disable`d on disk leaves the stock assistant broken if `BetterDualScreenAssistant` is uninstalled.
- **Required Ephemeral Suppression Sequence (`PServerClient.setStockAssistantEnabled(false)`)**:
  1. Stop and **transiently** disable `TccServices` (`am stopservice -n com.odin.dualscreen.assistant/.services.TccServices`, `pm disable com.odin.dualscreen.assistant/.services.TccServices`) while ensuring the package itself is enabled (`pm enable com.odin.dualscreen.assistant`).
  2. Kill (`kill -9`) the running PID so `WindowManagerService` immediately destroys any open stock overlay window and unbinds `TccServices`.
  3. Sleep ~350ms for `ActivityManagerService` to respawn the persistent process (whose `ThisApplication.onCreate()` only attempts `bindService(TccServices)` once at startup and fails while `TccServices` is transiently disabled), then send `kill -STOP` to the new windowless PID so its `Application` receivers remain frozen in RAM.
  4. **Immediately re-enable `TccServices` (`pm enable com.odin.dualscreen.assistant/.services.TccServices`)** while the process is frozen so the stock package and service are **never left disabled on disk** in `PackageManager`.
- **Automatic Uninstall / Force-Stop Recovery (`PrivilegedHelperMain.kt`)**:
  - `PrivilegedBridge` registers a client `IBinder` token with `PrivilegedHelperMain` (`UID 0`).
  - When our app process dies (`IBinder.DeathRecipient.binderDied()`), `PrivilegedHelperMain` checks whether our package was uninstalled (`!isPackageInstalled`) or force-stopped (`ApplicationInfo.FLAG_STOPPED` after a 1.5s grace window that ignores in-place app updates).
  - On uninstall or force-stop, `PrivilegedHelperMain` automatically resets `dual_screen_display_mode = 0`, restores any blanked screen's brightness and touch (`sys.temp.disabled.touch = "0,0"`), kills the frozen stock PID (`kill -9`), starts `com.odin.dualscreen.assistant/.services.TccServices`, and exits.
- **Health Check (`PServerClient.isStockAssistantEnabled`)**: Checks `/proc/<pid>/stat` for state `) T ` (stopped) in addition to `PackageManager` enabled state.

---

## 6. Physical AYN / TCC Button State Machine

The AYN Thor framework broadcasts `"action.tcc.button.key.event"` when the physical AYN button is pressed, held, and released.

- **Broadcast Extras** (exact names):
  - `"key_action_down"` (`Boolean`): `true` on initial press and long-press trigger; `false` on release.
  - `"key_isLongPress"` (`Boolean`): `true` when held past the long-press threshold while `key_action_down == true`.
- **Required 3-State Handling (`AssistantForegroundService.kt`)**:
  1. **Initial Key Down (`key_action_down == true && !key_isLongPress`)**: Must be a **no-op** (`onTccKeyDown()`). Never toggle the overlay on key-down, or the subsequent key-up will immediately close it.
  2. **Long Press (`key_action_down == true && key_isLongPress`)**: Set `longPressConsumed = true`, hide the overlay if open, and set `dual_screen_display_mode = 1` to shut off (blank) the bottom screen (`onTccKeyLongPress()`).
  3. **Key Up (`key_action_down == false`)**:
     - If `longPressConsumed == true`: reset `longPressConsumed = false` and return immediately (do not wake the screen or toggle the overlay).
     - Else if `dual_screen_display_mode == 1` or the bottom screen is blanked: wake the bottom screen (`setDualScreenDisplayMode(0)`).
     - Else: toggle the assistant overlay with a `200ms` debounce (`onTccKeyShortPressUp()`).
- **Single Receiver Rule**: `TccKeyReceiver` (manifest receiver) must delegate directly to `AssistantForegroundService.instance` when the service is running; do not process `"action.tcc.button.key.event"` in both a manifest receiver and a dynamic service receiver simultaneously.

---

## 7. Multi-Display Topology, Window Manager & UI Invariants

### Display Topology
- **Top Screen (`displayId = 0`)**: `1920×1080` landscape (`369 dpi`).
- **Bottom Screen (`displayId = 4`)**: `1240×1080` landscape (`369 dpi`, ~`840dp × 732dp`).

### Overlay Window Rules (`ScreenOverlayManager.kt`)
- **Display Context & Sizing**: Always obtain the `WindowManager` from `context.createDisplayContext(display4)` and pass explicit pixel dimensions (`1240×1080` via `display.getRealSize()`) with `Gravity.CENTER` and `TYPE_APPLICATION_OVERLAY`.
- **Never Use `FLAG_LAYOUT_NO_LIMITS` on Display 4**: Including `FLAG_LAYOUT_NO_LIMITS` causes `WindowManagerService` to lay out the surface at the primary display's width (`1920px`), clipping the right ~680px of the UI off the bottom screen.
- **Enter / Exit Animations**: `WindowManagerService` ignores custom app-resource `windowAnimations` (`R.style.tcc_anim`) on `TYPE_APPLICATION_OVERLAY` windows for non-system apps. Always animate `composeView` directly via `ViewPropertyAnimator` (`200ms`, `translationY` between `height * 0.90f` and `0f`, `alpha` between `0f` and `1f`) and only call `removeViewImmediate` in the exit animation's end action.

### How Screen "Off" (Blanking) Works on AYN Thor
Turning off the Top or Bottom screen via `dual_screen_display_mode` (`0` = Dual, `1` = Only Top, `2` = Only Bottom) does **not** power off the display panel at the HWC level:
1. Writes `Settings.System["dual_screen_display_mode"]`.
2. Sets `sys.temp.disabled.touch` to `"0,0"` (both active), `"0,1"` (bottom touch disabled), or `"1,0"` (top touch disabled).
3. Fades in a full-screen solid black overlay view (`200ms` alpha animation) on the blanked display and sets its display brightness to `-1.0f` (`setBrightness(displayId, -1.0f)`), restoring the saved brightness prior to device sleep or unblanking.

### UI & Layout Parity (`AssistantOverlayScreen.kt`, `TccWidgets.kt`, `StatusBarRow.kt`)
- The stock UI uses custom non-Material widgets (`VerticalProgressBar`, `CustomSeekBar`, `SpeedView`, `PercentageRingView`) and a custom `@dimen/dp_*` resource table scaled for `840dp × 732dp`.
- Maintain the exact vertical heights (`44dp` status bar, `578dp` main panel card, `80dp` bottom navigation bar pill) and custom canvas components in `TccWidgets.kt` rather than substituting standard Material 3 sliders or cards.
