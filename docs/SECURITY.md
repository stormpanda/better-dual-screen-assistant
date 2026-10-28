# Security & Privacy Overview

This document addresses common security, privacy, and hardware-safety questions about **Better Dual Screen Assistant** (`com.stormpanda.betterdualscreenassistant`) and explains how its security posture compares to the built-in stock `DualScreenAssistant` (`com.odin.dualscreen.assistant`) on the **AYN Thor**.

---

## 1. Key Takeaways (TL;DR)

- **100% Offline by Design (Zero Network Access)**: Unlike the stock `DualScreenAssistant`—which requests `INTERNET`, `ACCESS_FINE_LOCATION`, `CAMERA`, `CALL_PHONE`, and `READ_PHONE_STATE`—**Better Dual Screen Assistant** does **not** request the `android.permission.INTERNET` permission. Android's kernel sandbox prevents the app from opening network sockets or transmitting any telemetry, analytics, or user data off your device.
- **No Root / Bootloader Unlock Required**: The app installs as a standard user APK. To control dual-screen hardware (per-display brightness, touch blanking, fan speeds, and sysfs thermal sensors), it uses the same built-in firmware service (`PServerBinder`) that the stock `DualScreenAssistant` uses, without modifying `/system` or `/vendor`.
- **Smaller Attack Surface Than Stock**: The stock `DualScreenAssistant` runs permanently as `android.uid.system` (`UID 1000`) and includes a third-party library (`com.tamsiree.rxui`) with an exported `ActivityWebView` (`android:exported="true"`). **Better Dual Screen Assistant** contains zero third-party ad/utility SDKs, no WebViews, and protects all of its services, broadcast receivers, and Binder interfaces against access by other installed apps.
- **Zero Risk of Permanent Screen Lockout or Broken Stock App**: The stock `DualScreenAssistant` is paused **ephemerally in RAM** (`SIGSTOP`) rather than left disabled on disk. If you ever uninstall or force-stop **Better Dual Screen Assistant**—even while one screen is turned off or touch-locked—an automatic watchdog immediately unblanks both displays, restores touch input (`sys.temp.disabled.touch = "0,0"`), removes any injected OS theme overlays, and wakes the stock `DualScreenAssistant`.
- **100% Open-Source & Auditable**: Every line of Kotlin code, Binder transaction, and shell command in this repository can be inspected, built from source, or verified against release SHA-256 checksums.

---

## 2. Comparison: Stock `DualScreenAssistant` vs. `BetterDualScreenAssistant`

The table below compares the built-in `/system/app/DualScreenAssistant/DualScreenAssistant.apk` (`v1.3`) shipped on the AYN Thor firmware against **Better Dual Screen Assistant**:

| Security & Privacy Factor | Stock `DualScreenAssistant` (`com.odin.dualscreen.assistant`) | `BetterDualScreenAssistant` (`com.stormpanda.betterdualscreenassistant`) |
| :--- | :--- | :--- |
| **Source Code Availability** | Closed-source proprietary APK pre-installed in `/system/app` | **100% Open-Source** (Kotlin / Jetpack Compose) with reproducible builds |
| **Internet / Network Access** | Requests `android.permission.INTERNET` | **None** (`INTERNET` permission is not requested; strictly offline) |
| **Location, Camera & Phone Permissions** | Requests `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`, `CALL_PHONE`, `READ_PHONE_STATE` | **None** (requests zero location, camera, microphone, or telephony permissions) |
| **Package Install Permission** | Requests `REQUEST_INSTALL_PACKAGES` | **None** |
| **Bundled WebViews / 3rd-Party SDKs** | Bundles `RxTool` (`com.tamsiree.rxui`) with an exported `ActivityWebView` (`exported="true"`) running as `UID 1000` | **None** (zero WebViews, zero analytics/crash-reporting SDKs) |
| **Privileged Hardware Access Mechanism** | Runs as `android.uid.system` (`UID 1000`) + invokes firmware `PServerBinder` (`UID 0` root shell) via `J0.c` | Runs as a standard app UID + communicates with an authenticated in-memory `PrivilegedHelperMain` (`UID 0`) daemon spawned via `PServerBinder` |
| **FPS & Telemetry Polling Overhead** | Spawns a new root `/system/bin/sh` process (`service call SurfaceFlinger 1013`) via `PServerBinder` **every 1 second** while Settings is open | Uses direct in-memory Binder transactions (`TRANSACTION_GET_SF_FLIPS`, `TRANSACTION_READ_FILE`) with sub-millisecond latency and zero shell spawning |
| **Caller Authentication on IPC** | Relies on `PServerBinder` without caller UID validation | Enforces `Binder.getCallingUid()` verification on `PrivilegedHelperMain` and `PrivilegedBinderProvider` |
| **File & Property Access Scope** | Unrestricted root shell execution in `J0.c` | `HelperBinder` restricts file reads strictly to read-only `/sys/` & `/proc/stat` telemetry nodes and restricts property writes strictly to `sys.temp.disabled.touch` |
| **Protection Against Other Installed Apps** | Exports `MainActivity` and `ActivityWebView` without permission checks | Protects `AssistantForegroundService`, `TccKeyReceiver`, and `BootReceiver` with `android.permission.DUMP` and `PrivilegedBinderProvider` with `INTERNAL_SYSTEM_WINDOW` |
| **Application Backup Exposure** | `android:allowBackup="true"` | `android:allowBackup="false"` (prevents ADB backup extraction or state injection) |
| **Uninstall & Recovery Safety** | Pre-installed persistent system app (`android:persistent="true"`) | Ephemerally pauses stock app in RAM; automatic watchdog restores screens, touch, and stock assistant upon uninstall or force-stop |

---

## 3. Why Privileged Access Is Needed (and How It Works)

Because **Better Dual Screen Assistant** is a full replacement for a hardware control system app, it must perform operations that normal Android APIs restrict to system processes:

1. **Independent Per-Display Brightness & Blanking**: Android's public `Settings.System.SCREEN_BRIGHTNESS` only controls the primary display (`displayId = 0`). Controlling the bottom screen (`displayId = 4`) and blanking individual panels (`setBrightness(displayId, -1.0f)`) requires privileged `IDisplayManager` calls.
2. **Per-Screen Touch Controller Disabling**: Turning off touch input on a blanked screen (or activating the *Bottom Touch Lock* quick tile) requires setting the vendor system property `sys.temp.disabled.touch` (`"0,0"`, `"0,1"`, or `"1,0"`).
3. **Hardware Telemetry**: Reading real-time SoC temperature (`/sys/devices/virtual/thermal/thermal_zone48/temp`), CPU/GPU clock speeds, battery power draw (`W`), and `SurfaceFlinger` page-flip counts (`1013`) requires read access to kernel `/sys/` nodes and `SurfaceFlinger`.
4. **System-Wide Monet Theme Injection**: Applying custom Material You color palettes to Android SystemUI requires registering a `FabricatedOverlay` with `IOverlayManager`.

### How `PServerBinder` and `PrivilegedHelperMain` Work Together

The AYN Thor's stock Android 13 firmware includes a built-in system service named `PServerBinder` (used by the stock `DualScreenAssistant` and `GameAssistant` apps to run privileged commands).

Rather than executing slow shell commands through `PServerBinder` on every UI interaction, **Better Dual Screen Assistant** uses a hardened 3-tier architecture (see [`PrivilegedBridge.kt`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedBridge.kt) and [`PrivilegedHelperMain.kt`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt)):

```mermaid
flowchart LR
    App["BetterDualScreenAssistant\n(Standard App UID)"] -->|"1. Spawns once at startup"| PServer["PServerBinder\n(AYN Thor Firmware Service)"]
    PServer -->|"2. Starts app_process"| Helper["PrivilegedHelperMain\n(In-Memory UID 0 Helper)"]
    Helper -->|"3. Hands off IBinder token"| Provider["PrivilegedBinderProvider\n(UID 0 / 1000 only)"]
    App <-->|"4. Direct Binder IPC\n(Caller UID Verified + Allowlisted)"| Helper
```

---

## 4. Defense-in-Depth Security Controls

Even though the underlying AYN Thor firmware exposes `PServerBinder` locally, **Better Dual Screen Assistant** applies strict defense-in-depth controls so that **no third-party app on your device can abuse our components or helper daemon**:

### 4.1 Caller UID Authentication (`PrivilegedHelperMain.kt`)
Every Binder transaction arriving at [`HelperBinder.onTransact()`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt#L438-L453) is authenticated via `Binder.getCallingUid()`. The helper resolves the installed UID of `com.stormpanda.betterdualscreenassistant` from `IPackageManager` and immediately rejects (`SecurityException`) any call that does not originate from `UID 0`, `Process.SYSTEM_UID` (`1000`), or `BetterDualScreenAssistant` itself.

### 4.2 Strict Capability Allowlists
Instead of exposing a generic "run arbitrary command" or "read arbitrary file" method over Binder, `HelperBinder` restricts its operations to the exact hardware controls required by the assistant:
- **Sysfs Telemetry Read Allowlist (`TRANSACTION_READ_FILE`)**: Only permits paths starting with `/sys/` or equal to `/proc/stat` and rejects any path containing `..`. It cannot be used to read `/data/`, `/sdcard/`, or private app storage.
- **System Property Write Allowlist (`TRANSACTION_SET_SYSTEM_PROP`)**: Only permits writing `sys.temp.disabled.touch`. All other system properties (`persist.*`, `ctl.*`, `ro.*`) are blocked.
- **Package Name Validation**: Any package name passed to `TRANSACTION_FORCE_STOP_PACKAGE` (used by the *Clear Memory* speed-clean feature) is validated against a strict Android package identifier regex (`^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$`), and protected system packages (`com.android.systemui`, `com.stormpanda.betterdualscreenassistant`, `com.odin.dualscreen.assistant`) are excluded from task swapping.

### 4.3 Locked-Down Exported Android Components (`AndroidManifest.xml`)
In [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml):
- [`AssistantForegroundService`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/service/AssistantForegroundService.kt), [`TccKeyReceiver`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/receiver/TccKeyReceiver.kt), and [`BootReceiver`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/receiver/BootReceiver.kt) require `android.permission.DUMP`—a privileged system permission held only by `system_server` (`UID 1000`), `root` (`UID 0`), and `adb shell` (`UID 2000`). Normal third-party apps cannot send fake AYN button presses, toggle the overlay, or blank your displays.
- [`PrivilegedBinderProvider`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedBinderProvider.kt) requires `android.permission.INTERNAL_SYSTEM_WINDOW` in the manifest and additionally enforces `callingUid == 0 || callingUid == SYSTEM_UID || callingUid == myUid()` in code.
- Dynamic broadcast receivers for volume, Wi-Fi, Bluetooth, and battery status in [`SystemSettingsRepository.kt`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/system/SystemSettingsRepository.kt) and [`StatusBarRow.kt`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/ui/statusbar/StatusBarRow.kt) are registered with `Context.RECEIVER_NOT_EXPORTED`.

### 4.4 Hardened Shell Fallbacks & Cross-App Sync
- **POSIX Single-Quote Escaping**: If `PrivilegedHelperMain` is ever restarting and `PrivilegedBridge` falls back to `PServerClient.exec()`, all dynamic arguments are wrapped with [`PServerClient.shellQuote()`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PServerClient.kt#L33) (`'${value.replace("'", "'\\''")}'`) to prevent shell token or command injection.
- **Verified `Megingiard` Theme Sync**: When syncing themes from the companion **Megingiard** app (`content://com.stormpanda.megingiard.provider/theme`), [`SystemSettingsRepository.isProviderAuthorityInstalled()`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/system/SystemSettingsRepository.kt#L994-L1010) verifies that the `ContentProvider` authority is owned by the authentic `com.stormpanda.megingiard` (or `.debug`) package, and `PrivilegedHelperMain` validates all theme JSON via `JSONObject` before writing to `Settings.Secure`.

---

## 5. Permissions Breakdown & Privacy Guarantee

**Better Dual Screen Assistant** collects **zero personal data**, has **no network permissions**, and stores its user preferences locally in private app storage (`shared_prefs/better_dsa_prefs.xml` with `android:allowBackup="false"`).

| Permission / AppOp | How It Is Granted | Why It Is Needed |
| :--- | :--- | :--- |
| `SYSTEM_ALERT_WINDOW` | Manifest + `appops` | Renders the bottom-screen assistant overlay (`displayId = 4`) and screen-blanking dimmer overlays. |
| `WRITE_SETTINGS` & `WRITE_SECURE_SETTINGS` | Manifest + `pm grant` | Updates system brightness, volume, dual-screen display mode (`dual_screen_display_mode`), performance mode, fan mode, controller style, and DND/location toggles. |
| `PACKAGE_USAGE_STATS` (`GET_USAGE_STATS`) | `appops` + `pm grant` | Used to inspect foreground app tasks when swapping apps between the top and bottom screens. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Manifest | Keeps `AssistantForegroundService` alive so the physical AYN button responds instantaneously at any time. |
| `RECEIVE_BOOT_COMPLETED` | Manifest | Automatically starts the assistant service and restores your preferred screen mode on device boot. |
| `ACCESS_WIFI_STATE` & `ACCESS_NETWORK_STATE` | Manifest | Displays the Wi-Fi signal strength icon in the overlay status bar and state of the Wi-Fi Quick Setting tile (read-only state check; **cannot** open network connections without `INTERNET`). |
| `BLUETOOTH` & `BLUETOOTH_CONNECT` | Manifest + `pm grant` | Displays the Bluetooth connection indicator in the overlay status bar and state of the Bluetooth Quick Setting tile. |
| `KILL_BACKGROUND_PROCESSES` | Manifest | Powers the **Clear Memory** quick tile and status bar clock tap-to-clean action. |
| `POST_NOTIFICATIONS` | Manifest + `pm grant` | Displays the required Android 13 foreground service notification. |
| `QUERY_ALL_PACKAGES` | Manifest | Enumerates non-system background packages during the **Clear Memory** action so active launcher/system packages are never killed. |

---

## 6. Hardware Safety & Automatic Uninstall Recovery

A common concern when replacing a built-in hardware assistant is: *"What happens if I turn off one screen, lock touch input, or inject an OS theme, and then uninstall or force-stop the app?"*

### 6.1 Ephemeral Stock Suppression (Never Disabled on Disk)
The stock `com.odin.dualscreen.assistant` is marked `android:persistent="true"` in the firmware. If a tool permanently runs `pm disable com.odin.dualscreen.assistant` and you later uninstall that tool, your physical AYN button stops working altogether.

To prevent this, [`PServerClient.setStockAssistantEnabled(false)`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PServerClient.kt#L162-L204) uses an **ephemeral in-memory pause**:
1. It briefly disables `TccServices`, restarts the stock process so its window unbinds cleanly, and sends `SIGSTOP` (`kill -STOP`) to freeze the windowless stock process in RAM.
2. It **immediately re-enables `com.odin.dualscreen.assistant` and `TccServices` on disk** while the process is frozen in RAM.
3. Because the stock app is never left disabled on disk, a simple reboot—or our automatic recovery watchdog—always leaves the stock assistant intact.

### 6.2 Persistent Service Supervisor & Automatic Uninstall Watchdog
[`PrivilegedHelperMain`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt) links a Binder `DeathRecipient` (`linkToDeath`) to our app process. While **Better Dual Screen Assistant** remains installed and *Replace Stock Assistant* is active, it acts as a persistent service supervisor (automatically respawning `AssistantForegroundService` across Recents task clears or RAM cleaners, with a rapid-crash circuit breaker). If **Better Dual Screen Assistant** is uninstalled, package-disabled, or trips the crash circuit breaker, `PrivilegedHelperMain` automatically executes [`restoreDisplayAndStockAssistant()`](../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt) before exiting:
1. Removes any top-screen blanking cover window and restores the status bar.
2. Restores both Top (`displayId = 0`) and Bottom (`displayId = 4`) screen brightness to your saved brightness level if either screen was blanked.
3. Resets `dual_screen_display_mode = 0` (Dual-Screen mode) and re-enables touch input on both screens (`sys.temp.disabled.touch = "0,0"`).
4. Unregisters any injected Material You OS theme overlays and restores your original system theme and dark/light mode.
5. Wakes and restarts the stock `com.odin.dualscreen.assistant/.services.TccServices` so the physical AYN button seamlessly reverts to stock behavior without requiring a reboot.

---

## 7. Verifying Release Integrity

Every official release of **Better Dual Screen Assistant** includes a companion `.sha256` checksum file (`BetterDualScreenAssistant-v<version>-checksum-sha256.txt`). You can verify any downloaded APK on macOS or Linux before installing:

```bash
shasum -a 256 -c BetterDualScreenAssistant-v<version>-checksum-sha256.txt
```

Or build and install the APK directly from this source repository yourself:

```bash
./scripts/debug.sh build-install
```
