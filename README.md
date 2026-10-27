# Better Dual Screen Assistant

**Better Dual Screen Assistant** (`com.stormpanda.betterdualscreenassistant`) is a user-installable drop-in replacement and feature-packed upgrade for the built-in system app `DualScreenAssistant` (`com.odin.dualscreen.assistant`) on the **AYN Thor** (`kalama` / Snapdragon 8 Gen 2, Android 13) dual-screen handheld.

It preserves 1:1 visual and functional parity with the stock assistant while adding a gamepad-first **Top-Screen Companion Settings App**, **22 customizable & reorderable Quick Setting tiles** across three categories, **Screen Task Swapping**, **Stick LED Brightness control**, **Megingiard Theme Palettes & Live Sync**, **Android 13 Monet OS Theme Injection**, and a **sub-millisecond `UID 0` Binder Helper Daemon** with automatic uninstall/force-stop recovery.

---

## Documentation Index

Full functional and technical documentation is available in [`docs/`](docs/):

| Document | Scope |
| :--- | :--- |
| **[Security & Privacy Overview](docs/SECURITY.md)** | Executive summary, side-by-side security & privacy comparison against the stock `DualScreenAssistant`, explanation of `PServerBinder` & `PrivilegedHelperMain`, permission breakdown, and automatic uninstall recovery guarantees. |
| **[Functional Specification](docs/FUNCTIONAL_SPECIFICATION.md)** | Complete user-facing guide covering the physical AYN button state machine, bottom-screen overlay tabs (**Mode** & **Settings**), all **22 Quick Setting tiles**, modal dialogs, live hardware telemetry, and the top-screen **Companion Settings App** (`MainActivity`). |
| **[Technical Architecture](docs/TECHNICAL_ARCHITECTURE.md)** | Deep dive into the 3-tier privileged execution model (`PrivilegedBridge` -> `PrivilegedHelperMain` `UID 0` daemon `v9` -> `PServerBinder`), ephemeral stock `DualScreenAssistant` suppression, the uninstall/force-stop recovery watchdog, and multi-display windowing invariants on `displayId = 0` & `displayId = 4`. |
| **[System Integration & Hardware Reference](docs/SYSTEM_INTEGRATION_REFERENCE.md)** | Complete reference tables for all `Settings.System`/`Global`/`Secure` keys, `SharedPreferences` (`better_dsa_prefs`), system properties (`sys.temp.disabled.touch`, `sys.light.curr.is.show`), vendor Binder services (`IFanControlProvider`, `IOverviewProxy`, `IMappingService`), sysfs telemetry paths, and `FabricatedOverlay` Monet keys. |
| **[UI Architecture, Custom Widgets & Theming](docs/UI_AND_THEMING.md)** | Specification of the `840dp × 732dp` bottom-screen overlay layout, custom non-Material canvas widgets (`VerticalProgressBar`, `TccSliderRow`, `HardwareRingGauge`, `CurrentFrameRateDisplay`, `BatteryIconCanvas`), all 12 `AppThemeOption` palettes, and the embedded `com.stormpanda.megingiard` gamepad design system. |
| **[Build, Release & Developer Guide](docs/BUILD_AND_RELEASE.md)** | Developer instructions for keystore signing (`local.properties`), debug builds (`scripts/debug.sh`), release git-flow automation (`scripts/release.sh`), SHA-256 verification (`scripts/generate_checksum.sh`), and ADB inspection commands. |

---

## Key Features

### 1. Bottom-Screen Assistant Overlay (`displayId = 4`, `1240 × 1080`)
- **Mode Tab (`Tab 0`)**:
  - **Screen Mode Control**: Switch between **Dual-screen display** (`0`), **Only top screen** (`1`), and **Only bottom screen** (`2`), plus a one-tap **Swap Screens** button that swaps the top visible app tasks between Display `0` and Display `4`.
  - **Brightness Sliders**: Linked **Dual Screen**, **Top Screen**, **Bottom Screen** (with zero-lag hardware `setTemporaryBrightness` during drag and external SystemUI slider tracking), plus an optional **Stick LED** brightness slider (`0%–100%`).
  - **Volume Sliders**: Linked **Dual Screen**, **Top Screen** (`STREAM_MUSIC`), and **Bottom Screen** (`secondary_screen_volume_level`).
- **Settings Tab (`Tab 1`)**:
  - **Frame Rate Panel**: One-tap **`60 FPS MODE` / `120 FPS MODE`** toggle paired with a live native-canvas **`SurfaceFlinger` FPS readout**.
  - **22 Customizable Quick Setting Tiles** in a paged `2 × 4` grid (swipeable by touch or **`L1` / `R1`** shoulder buttons):
    - **Stock AYN Toggles (10 — Enabled by default)**: *Focus Lock*, *Performance Mode*, *Fan Control* (Off/Quiet/Smart/Sport/Custom `35%–100%`), *Clear Memory*, *Controller Style* (Disconnect/Xbox/Odin), *Charging Separation*, *Vibration*, *Ambient Light*, *Force Landscape*, and *Theme Mode*.
    - **Android System Toggles (4 — Optional)**: *Wi-Fi*, *Bluetooth*, *Do Not Disturb*, and *Airplane Mode*.
    - **Tools (8 — Optional)**: *Bottom Touch Lock*, *Screenshot* (Top / Bottom / Both screens saved to `/sdcard/Pictures/Screenshots`), *L2/R2 Trigger Mode* (Analog / Digital / Both), *80% Charge Limit*, *Virtual Mouse*, *Key Mapper Overlay*, *Keep Screen On*, and *Double-Tap HOME*.
  - **Live Hardware Telemetry Panel**: Real-time **SoC Temperature** (`thermal_zone48` with dynamic thermal tinting), **Fan Speed %** (`IFanControlProvider`), and 4 animated `270°` circular ring gauges for **CPU Frequency** (`cpu7`), **GPU Frequency** (`kgsl-3d0`), **Battery Power Draw** (`-20W..+25W`), and **RAM Usage**.
- **Top Status Bar & Bottom Navigation Bar**:
  - Status bar with live clock (tap to clean memory), Bluetooth indicator, live network RX speed (`KB/S` / `MB/S`), Wi-Fi signal bars, and custom battery icon with charging bolt.
  - Bottom navigation pill with **Mode**, **Task** (opens bottom-screen Recents via `IOverviewProxy`), **Settings**, and **Edit** (launches the Companion App on the Top Screen).

### 2. Top-Screen Companion Settings App (`MainActivity` on `displayId = 0`)
- **Gamepad-First Two-Pane UI**: Navigate with **D-Pad / Left Stick**, switch categories with **`L1` / `R1`**, adjust sliders with **`L2` fine-step modifier**, and preview every change live on the bottom screen (`displayId = 4`).
- **Visual Quick Toggles Reordering**: Interactive `2 × 4` paged layout preview supporting both touch tap-to-swap and gamepad grab-and-move (`A` to grab, D-Pad/`L1`/`R1` to move in real time, `B` to drop), plus categorized toggle visibility management.
- **Rich Theming & OS Monet Injection**:
  - Choose between **Stock AYN** (`Follow System`, `Light`, `Dark`), **8 Built-in Custom Palettes** (`Dark`, `Dark OLED`, `Norse Forest`, `Mjölnir Steel`, `Valhalla Sunset`, `Aurora Borealis`, `Retro Phosphor`, `Royal Asgard`) with **11 Accent Presets** and a **Custom HSV Color Picker**, or **Sync with Megingiard** (silently revealed when Megingiard is installed on the device).
  - **Inject Theme Colors into OS**: Generates a 60-color Android 13 Monet `FabricatedOverlay` to theme Android SystemUI, Quick Settings, Notifications, and Material You apps system-wide.
- **Live System Diagnostics**: Real-time status indicators and one-tap reconnection for `PrivilegedHelperMain (UID 0)`, `PServerBinder`, Stock `DualScreenAssistant` suppression state, and (when installed) the Megingiard Theme Provider.

### 3. Safe Ephemeral Stock Suppression & Automatic Uninstall Recovery
- Freezes the persistent stock `com.odin.dualscreen.assistant` in RAM (`SIGSTOP`) without leaving its package or `TccServices` disabled on disk.
- If **Better Dual Screen Assistant** is ever **uninstalled** or **force-stopped**, the `UID 0` watchdog in `PrivilegedHelperMain` automatically unblanks both screens, restores display brightness and touch (`sys.temp.disabled.touch = "0,0"`), removes any injected OS theme overlay, and wakes/restarts the stock `DualScreenAssistant`.

---

## Quick Start (Build & Install)

Ensure your AYN Thor is connected via USB debugging (`adb devices`):

```bash
# Build and install the Debug APK in one step:
./scripts/debug.sh build-install

# Or build and install the minified Release APK:
./scripts/release.sh build-install

# Start the foreground service immediately via ADB:
adb shell am start-foreground-service \
  -n com.stormpanda.betterdualscreenassistant/.service.AssistantForegroundService
```

See [`docs/BUILD_AND_RELEASE.md`](docs/BUILD_AND_RELEASE.md) for keystore setup, release branch automation, and troubleshooting commands.
