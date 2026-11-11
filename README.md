<p align="center">
  <img src="docs/assets/showcase.png" alt="Better Dual Screen Assistant Showcase" />
</p>

# Better Dual Screen Assistant

**Better Dual Screen Assistant** (`com.stormpanda.betterdualscreenassistant`) is a user-installable drop-in replacement and feature-packed upgrade for the built-in system `DualScreenAssistant` (`com.odin.dualscreen.assistant` / `com.retroidpocket.dualscreen.assistant`) on the **AYN Thor** (`kalama` / Snapdragon 8 Gen 2, Android 13) and **Retroid Pocket Duo** (`sun` / `CQ7790S`, Android 16) dual-screen handhelds.

It preserves 1:1 visual and functional parity with each device's stock assistant while adding interchangeable **AYN Smooth** & **Retroid Pixel** UI looks across all themes, a gamepad-first **Top-Screen Companion Settings App**, **24 customizable & reorderable Quick Setting tiles**, **Hardware-Accelerated Dual-Screen & Internal Audio Recording**, an **Accurate Per-Task Game FPS Counter**, **Screen Task Swapping**, **Stick LED Brightness control**, **Megingiard Theme Palettes & Live Sync**, **Android 13–16 Monet OS Theme Injection**, **In-App Update Checks & Obtainium Integration**, and a **sub-millisecond `UID 0` Binder Helper Daemon** with Direct Boot support, persistent service supervision, and automatic uninstall recovery.

<p>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/stormpanda/better-dual-screen-assistant">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54" />
  </a>
</p>

---

## Supported Devices

| Device | SoC & Firmware | Top Display | Bottom Display | Stock Package Replaced | Default UI Look |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[AYN Thor](docs/devices/AYN_THOR.md)** | Snapdragon 8 Gen 2 (`kalama`), Android 13 (`SDK 33`) | `displayId = 0` (`1920 × 1080`, `16:9` 120 Hz AMOLED) | `displayId = 4` (`1240 × 1080`, 120 Hz AMOLED, OLED layer blanking) | `com.odin.dualscreen.assistant` (physical **AYN** key) | **AYN Smooth** |
| **[Retroid Pocket Duo](docs/devices/RETROID_POCKET_DUO.md)** | Moorechip `CQ7790S` (`sun` / Adreno 722), Android 16 (`SDK 36`) | `displayId = 0` (`1920 × 1080`, `16:9` 120 Hz) | `displayId = 2` (`1280 × 960`, `4:3` 120 Hz, `bl_power` backlight blanking) | `com.retroidpocket.dualscreen.assistant` (physical **Dual Screen** key) | **Retroid Pixel** |

- **Cross-Device UI Looks**: Both **AYN Smooth** and **Retroid Pixel** (with 50+ custom pixel-art vector icons, chamfered pixel geometry, bundled pixel fonts, and an optional **Improve Retroid Readability** boost) are available on **both** handhelds across every theme.
- **Automatic Hardware Adaptation**: `DeviceProfile` automatically adapts display IDs, SurfaceFlinger physical ports, kernel `panel{0,1}-backlight/bl_power` vs. OLED blanking, `IOverviewProxy` / `ILauncherProxy` bottom-screen Recents, vendor key-mapper IPC (`com.odin.mapping` / `com.rp.mapping`), controller style labels (*Odin* vs. *Retro*), and device-specific capabilities (such as joystick RGB *LED Lights* and *Home Lock* on AYN Thor).

---

## Documentation Index

Full functional and technical documentation is available in [`docs/`](docs/):

| Document | Scope |
| :--- | :--- |
| **[Security & Privacy Overview](docs/SECURITY.md)** | Executive summary, side-by-side security & privacy comparison against the stock `DualScreenAssistant`, explanation of `PServerBinder` & `PrivilegedHelperMain`, permission breakdown, and automatic uninstall recovery guarantees. |
| **[Functional Specification](docs/FUNCTIONAL_SPECIFICATION.md)** | Complete user-facing guide covering the physical assistant button state machine, bottom-screen overlay tabs (**Mode** & **Settings**), all **24 Quick Setting tiles**, modal dialogs, live hardware telemetry, and the top-screen **Companion Settings App** (`MainActivity`). |
| **[Technical Architecture](docs/TECHNICAL_ARCHITECTURE.md)** | Deep dive into the 3-tier privileged execution model (`PrivilegedBridge` -> `PrivilegedHelperMain` `UID 0` daemon -> `PServerBinder`), ephemeral stock `DualScreenAssistant` suppression, persistent service supervision & uninstall recovery watchdog, and multi-display windowing invariants across AYN Thor (`displayId = 4`) & Retroid Pocket Duo (`displayId = 2`). |
| **[System Integration & Hardware Reference](docs/SYSTEM_INTEGRATION_REFERENCE.md)** | Complete reference tables for all `Settings.System`/`Global`/`Secure` keys, `SharedPreferences` (`better_dsa_prefs`), system properties (`sys.temp.disabled.touch`, `sys.light.curr.is.show`), vendor Binder services (`IFanControlProvider`, `IOverviewProxy`, `ILauncherProxy`, `IMappingService`), sysfs telemetry paths, and `FabricatedOverlay` Monet keys. |
| **[AYN Thor Hardware Reference](docs/devices/AYN_THOR.md)** | Complete hardware, AMOLED multi-display (`displayId = 0` & `4`), DDIC TE clock ratio, OLED blanking, thermal/fan/RGB sysfs, Binder IPC, and `DeviceProfile.AYN_THOR` specification. |
| **[Retroid Pocket Duo Hardware Reference](docs/devices/RETROID_POCKET_DUO.md)** | Complete hardware, Android 16 (`SDK 36`), `4:3` secondary display (`displayId = 2`), `bl_power` backlight blanking, `CQ7790S` thermal/sysfs, `ILauncherProxy` / `com.rp.*` IPC, and `DeviceProfile.RETROID_POCKET_DUO` specification. |
| **[Device Hardware Profile Template](docs/devices/DEVICE_PROFILE_TEMPLATE.md)** | Standardized 10-section hardware & firmware documentation template (plus read-only ADB discovery commands) for onboarding future dual-screen handhelds. |
| **[UI Architecture, Custom Widgets & Theming](docs/UI_AND_THEMING.md)** | Specification of the canonical `720dp` `OverlayViewport` + `TccLookSpec` (`AYN Smooth` & `Retroid Pixel`), custom non-Material canvas widgets (`HorizontalSlidingBar`, `HardwareRingGauge`, `FrameRatePanelCard`, `BatteryIconCanvas`), all 12 `AppThemeOption` palettes, and the embedded `com.stormpanda.megingiard` gamepad design system. |
| **[Build, Release & Developer Guide](docs/BUILD_AND_RELEASE.md)** | Developer instructions for keystore signing (`local.properties`), debug builds (`scripts/debug.sh`), release git-flow automation (`scripts/release.sh`), SHA-256 verification (`scripts/generate_checksum.sh`), and ADB inspection commands. |
| **[Contributing Guidelines](CONTRIBUTING.md)** | Architectural rules, multi-display & privileged hardware invariants, coding conventions, and licensing compliance instructions for contributors. |
| **[Agent Guidelines](AGENTS.md)** | Essential context, architectural invariants, hardware quirks, and workflow rules for AI coding agents working on this project. |

---

## Key Features

### 1. Bottom-Screen Assistant Overlay (`displayId = 4` `1240 × 1080` on AYN Thor • `displayId = 2` `1280 × 960` on Retroid Pocket Duo)
- **Mode Tab (`Tab 0`)**:
  - **Screen Mode Control**: Switch between **Dual-screen display** (`0`), **Only top screen** (`1`), and **Only bottom screen** (`2`) with full-bleed status/gesture bar coverage during blanking, plus an optional one-tap **Swap Screens** button that swaps active foreground app tasks between the Top and Bottom screens.
  - **Brightness Sliders**: Linked **Dual Screen**, **Top Screen**, **Bottom Screen** (with zero-lag hardware `setTemporaryBrightness` during drag and external SystemUI quick-settings slider sync), plus an optional **Stick LED** brightness slider (`0%–100%` on AYN Thor).
  - **Volume Sliders**: Linked **Dual Screen**, **Top Screen** (`STREAM_MUSIC`), and **Bottom Screen** (`secondary_screen_volume_level`).
- **Settings Tab (`Tab 1`)**:
  - **Frame Rate Panel & Accurate Game FPS**: One-tap **`60 HZ MODE` / `120 HZ MODE`** toggle paired with an **Accurate Game FPS Counter** that measures real foreground game/app frame delivery (`ITaskFpsCallback` on Android 14–16 and per-layer `SurfaceFlinger` timestamps calibrated for AYN Thor's AMOLED DDIC TE clock), with an option to switch back to the stock global `SurfaceFlinger` counter.
  - **24 Customizable Quick Setting Tiles** in a paged `2 × 4` grid (swipeable by touch or **`L1` / `R1`** shoulder buttons, with optional **Remember Last Open Tab** and **Remember Last Quick Toggle Panel** memory):
    - **Stock Vendor Toggles (Up to 10 — Enabled by default)**: *Key Focus Lock* (preserves `HOME`/`BACK` focus lock across screen-mode changes), *Performance Mode*, *Fan Control* (Off/Quiet/Smart/Sport/Custom `35%–100%`), *Clear Memory*, *Controller Style* (Disabled/Xbox/Odin or Retro), *Charging Separation*, *Vibration*, *LED Lights* (AYN Thor), *Force Landscape*, and *Theme Mode*.
    - **Android System Toggles (4 — Optional)**: *Wi-Fi*, *Bluetooth*, *Do Not Disturb*, and *Airplane Mode*.
    - **Tools (Up to 10 — Optional)**: *Touch Lock*, *Screenshot* (Top / Bottom / Both screens saved to `/sdcard/Pictures/Screenshots`), *Screen Record* (hardware `MediaCodec` H.264 + `48 kHz` AAC recorder for Top, Bottom, or OpenGL ES GPU-composited **Both Screens** at `30`/`60 FPS`, with privileged **Internal Game Audio** loopback + **Microphone** mixing saved to `/sdcard/Movies/ScreenRecordings`), *Trigger Mode* (Analog / Digital / Both), *80% Charge Limit*, *Mouse Mode*, *Key Mapper*, *Keep Screen On*, *Home Lock* (AYN Thor), and *Clear All Tasks*.
  - **Live Hardware Telemetry Panel**: Real-time **SoC Temperature** (with dynamic thermal tinting and **`°C` / `°F`** switching via Settings or by tapping the `TEMP` readout), **Fan Speed %** (`IFanControlProvider`), and 4 animated `270°` circular ring gauges for **CPU Frequency** (configurable between **Hardware Maximum** and active **Performance Mode Limit**), **GPU Frequency** (`kgsl-3d0`), **Battery Power Draw** (`-20W..+25W`), and **RAM Usage**.
- **Top Status Bar, Bottom Navigation & Privileged Windowing**:
  - Status bar with live clock (tap to clean memory), interactive **recording timer/stop pill** while capturing video, Bluetooth indicator, live network RX speed (`KB/S` / `MB/S`), Wi-Fi signal bars, and custom battery icon with charging bolt.
  - Bottom navigation bar with **Mode**, **Task** (opens bottom-screen Recents via `IOverviewProxy` / `ILauncherProxy`), **Settings**, and optional **Edit** (launches the Companion App on the Top Screen while keeping the overlay open).
  - Renders via privileged window type `2021` so Android's `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` security flag (Files, Settings, permission dialogs) never hides the assistant or unblanks a turned-off screen, while coexisting cleanly with vendor `GameAssistant` edge-swipe gestures.

### 2. Top-Screen Companion Settings App (`MainActivity` on `displayId = 0`)
- **Gamepad-First Two-Pane UI**: Navigate with **D-Pad / Left Stick**, switch categories with **`L1` / `R1`**, adjust sliders with **`L2` fine-step modifier**, and preview every change live on the bottom screen (`displayId = 4` / `2`). Excludes itself from Recents and auto-finishes on exit to keep background RAM free.
- **Visual Quick Toggles Reordering**: Compact interactive `2 × 4` paged layout preview supporting both touch tap-to-move and gamepad grab-and-move (`A` to grab, D-Pad/`L1`/`R1` to move in real time, `B` to drop), plus categorized toggle visibility management.
- **Cross-Device UI Looks, Rich Theming & Android 13–16 Monet Injection**:
  - **Overlay UI Look**: Choose between `Auto`, **AYN Smooth**, and **Retroid Pixel** across any color palette, plus **Improve Retroid Readability** to boost pixel typography sizes across tiles, sliders, telemetry readouts, and dialogs.
  - **Theme Palettes**: Choose between **Stock** (`Follow System`, `Light`, `Dark`), **8 Built-in Custom Palettes** (`Dark`, `Dark OLED`, `Norse Forest`, `Mjölnir Steel`, `Valhalla Sunset`, `Aurora Borealis`, `Retro Phosphor`, `Royal Asgard`) with **13 Accent Presets** and a **Custom HSV Color Picker**, or **Sync with Megingiard** (silently revealed when Megingiard is installed on the device).
  - **Inject Theme Colors into OS**: Generates a 221-color Android 13–16 dynamic semantic Monet `FabricatedOverlay` set (preserving full Quick Settings accent saturation) to theme Android SystemUI, Quick Settings, Notifications, Settings, and Material You apps system-wide.
- **In-App Updates, App Promotion & Licenses**:
  - **Updates Tab**: Automatic 24-hour GitHub Release checks (with an in-app update banner), manual **Check for Updates**, and one-tap **Add to Obtainium** deep-link setup.
  - **Promote to Manager**: Optional toggle in General Settings (*"Good job, assistant! You get a promotion!"*) that renames the app across the launcher and UI to **Better Dual Screen Manager**.
  - **In-App Licenses & Debug Diagnostics**: Gamepad-scrollable document reader for the app license and bundled third-party font/library notices, plus a **Live System Diagnostics** tab (in debug builds) with real-time status badges and one-tap reconnection for `PrivilegedHelperMain (UID 0)`, `PServerBinder`, Stock `DualScreenAssistant` suppression, and Megingiard IPC.

### 3. Direct Boot, Safe Ephemeral Stock Suppression & Automatic Uninstall Recovery
- **Direct Boot Support**: Starts automatically on `LOCKED_BOOT_COMPLETED` backed by device-encrypted storage (`DirectBootPrefs`), ensuring physical button handling, screen modes, and user preferences work immediately on boot even before lockscreen unlock.
- **Zero-Footprint Stock Suppression**: Freezes the persistent stock `com.odin.dualscreen.assistant` / `com.retroidpocket.dualscreen.assistant` in RAM (`SIGSTOP`) without leaving its package or `TccServices` disabled on disk.
- **Persistent Supervision & Automatic Recovery**: While active, `PrivilegedHelperMain` (`UID 0`) supervises and immediately respawns `AssistantForegroundService` across task clears, RAM cleaners, or `force-stop`. If **Better Dual Screen Assistant** is ever **uninstalled**, **disabled (`pm disable`)**, or **opted out of *Replace Stock Assistant***, the watchdog automatically unblanks both screens (including kernel `bl_power` on Retroid Pocket Duo), restores display brightness and touch (`sys.temp.disabled.touch = "0,0"`), removes any injected OS theme overlay, and wakes/restarts the stock `DualScreenAssistant`.

---

## Quick Start (Build & Install)

Ensure your AYN Thor or Retroid Pocket Duo is connected via USB debugging (`adb devices`):

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

---

## License

Better Dual Screen Assistant is a proprietary, source-available project. It is licensed under the custom **Better Dual Screen Assistant Source-Available License (Version 1.0)**.

- **Source Code & Binaries:** You are permitted to view the source code, compile it, and run the application solely for your own **personal, non-commercial use**.
- **Prohibitions:** Any commercial exploitation, redistribution of modified source/binaries, and pre-installation or bundling on commercial hardware/devices without prior written consent are strictly prohibited.
- **Open Source Transition Commitment:** The Copyright Holder commits to transitioning the entire codebase to a fully permissive, OSI-approved open-source license (such as MIT or Apache 2.0) with full redistribution rights in the event that active development is permanently discontinued without a successor.

For the full terms and conditions, please refer to the [LICENSE](LICENSE) file at the root of the repository, [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for bundled open-source font and library attributions, and [CONTRIBUTING.md](CONTRIBUTING.md) before submitting contributions.
