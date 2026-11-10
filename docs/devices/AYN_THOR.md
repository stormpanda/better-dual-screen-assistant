# AYN Thor — Hardware & Firmware Integration Reference

The **AYN Thor** (`kalama`) is a clamshell dual-AMOLED Android gaming handheld powered by the **Qualcomm Snapdragon 8 Gen 2** running **Android 13 (`SDK 33`)**. It pairs a `1920 × 1080` (`16:9`) 120 Hz top AMOLED display (`displayId = 0`) with a `1240 × 1080` (~`8:7`) bottom AMOLED display (`displayId = 4`), blanks self-emissive OLED panels via software black-out overlays + `DisplayManager.setBrightness(displayId, -1.0f)` + `sys.temp.disabled.touch`, ships with the persistent system app `com.odin.dualscreen.assistant`, and defaults to the **AYN Smooth** (`TccUiLook.AYN`) UI look in **Better Dual Screen Assistant** (`BDSA`).

---

## Table of Contents

1. [Platform Identity & Firmware Overview](#1-platform-identity--firmware-overview)
2. [SoC, CPU, GPU, Memory & Power Architecture](#2-soc-cpu-gpu-memory--power-architecture)
3. [Multi-Display Topology & Windowing Invariants](#3-multi-display-topology--windowing-invariants)
4. [Backlight, Brightness & Screen Blanking Mechanics](#4-backlight-brightness--screen-blanking-mechanics)
5. [Thermal Zones, Active Cooling & Ambient Lighting](#5-thermal-zones-active-cooling--ambient-lighting)
6. [Input Subsystem, Physical Assistant Key & Gamepad Controls](#6-input-subsystem-physical-assistant-key--gamepad-controls)
7. [Multi-Display Audio Routing & Hardware Volume Keys](#7-multi-display-audio-routing--hardware-volume-keys)
8. [Privileged Root, Vendor Binder Services & Stock Assistant Package](#8-privileged-root-vendor-binder-services--stock-assistant-package)
9. [`Settings.System` Keys & Capability Support Matrix](#9-settingssystem-keys--capability-support-matrix)
10. [BDSA `DeviceProfile` Configuration Summary](#10-bdsa-deviceprofile-configuration-summary)

---

## 1. Platform Identity & Firmware Overview

| Property | System Key / Source | Value |
| :--- | :--- | :--- |
| **Marketing Name** | Vendor spec | `AYN Thor` |
| **Manufacturer** | `ro.product.manufacturer` | `AYN` / `Odin` (Moorechip software lineage) |
| **Brand** | `ro.product.brand` | `AYN` / `qti` |
| **Model String** | `ro.product.model` (`Build.MODEL`) | `"AYN Thor"` (`model:AYN_Thor` in `adb devices -l`) |
| **Device / Board Codename** | `ro.product.device` / `ro.board.platform` | `"kalama"` |
| **SoC Manufacturer & Model** | `ro.soc.manufacturer` / `ro.soc.model` | `QTI` / `SM8550-AB` (Qualcomm **Snapdragon 8 Gen 2**) |
| **Android Version & SDK** | `ro.build.version.release` / `ro.build.version.sdk` | **Android 13 (`SDK 33`)** |
| **Supported ABIs** | `ro.product.cpu.abilist` | `arm64-v8a` |
| **Kernel Page Size** | `getconf PAGESIZE` | `4096` (`4 KB`) |
| **Reference Firmware Build** | Android 13 user build | `kalama` Android 13 stock firmware |
| **Form Factor** | Physical chassis | Clamshell dual-AMOLED handheld with Hall-effect sticks (RGB LED rings), Hall triggers, active fan, Hall lid sensor, and dedicated physical **AYN** button |
| **BDSA Detection Rule** | [`DeviceProfile.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/system/DeviceProfile.kt) | Resolves to `DeviceProfile.AYN_THOR` whenever `Build.MODEL` does not start with `"Retroid Pocket Duo"` and `com.retroidpocket.dualscreen.assistant` is absent |

---

## 2. SoC, CPU, GPU, Memory & Power Architecture

### 2.1 CPU Cluster Topology (`cpufreq`)

| Cluster / Policy | Cores | Min Frequency | Max Frequency | Governor | BDSA Telemetry Node |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `policy0` (Efficiency — Cortex-A510) | `0, 1, 2` | `307,200 kHz` | `2,016,000 kHz` (`2.02 GHz`) | `walt` | — |
| `policy3` (Performance — Cortex-A715/A710) | `3, 4, 5, 6` | `499,200 kHz` | `2,803,200 kHz` (`2.80 GHz`) | `walt` | — |
| `policy7` (Prime — Cortex-X3) | `7` | `595,200 kHz` | `3,187,200 kHz` (`3.20 GHz`) | `walt` | `/sys/devices/system/cpu/cpu7/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |

### 2.2 GPU (`kgsl-3d0` / Devfreq)

| Attribute | Sysfs Node | Value / Format |
| :--- | :--- | :--- |
| **GPU Model** | `/sys/class/kgsl/kgsl-3d0/gpu_model` | `Adreno740` (Qualcomm Adreno 740) |
| **Current Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` | Raw `Hz` (always `< 1,000,000,000`, dynamically formatted in `MHz`, e.g. `680 MHz`) |
| **Max Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/max_freq` | `680,000,000` (`680 MHz`) |
| **Utilization** | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` | `0..100 %` |

### 2.3 Memory & Battery Power Telemetry

| Subsystem | Sysfs / API Source | Raw Unit & Notes |
| :--- | :--- | :--- |
| **System RAM** | `ActivityManager.getMemoryInfo()` | `8 GB` / `12 GB` / `16 GB` LPDDR5x SKU configurations |
| **Battery Current** | `/sys/class/power_supply/battery/current_now` | `µA` (negative while charging, positive while discharging; negated to `-(A * V)` in `TelemetryMonitor`) |
| **Battery Voltage** | `/sys/class/power_supply/battery/voltage_now` | `µV` (`Watts` mapped onto bidirectional `-20W..+25W` ring gauge) |
| **Bypass Charging** | `Settings.System["is_charging_separation"]` | **Supported** (`0` = Disabled, `1` = Enabled) |
| **80% Charge Limit** | `Settings.System["percent_80_charge_limit"]` | **Supported** (`0` = Disabled, `1` = Enabled) |

---

## 3. Multi-Display Topology & Windowing Invariants

### 3.1 Physical & Logical Display Matrix

| Property | Top Screen (Primary) | Bottom Screen (Secondary) |
| :--- | :--- | :--- |
| **Logical `displayId`** | **`0`** (`Display.DEFAULT_DISPLAY`) | **`4`** |
| **Panel Technology** | `6.0"` AMOLED (MIPI-DSI Command Mode) | `3.92"` AMOLED |
| **SurfaceFlinger Port & ID** | `port=131` (64-bit physical ID assigned per boot; query `Display.uniqueId` / `SurfaceFlinger --display-id`) | `port=132` (64-bit physical ID assigned per boot) |
| **Native Scanout & Rotation** | `1080 × 1920` portrait rotated `ROTATION_90` | `1080 × 1240` portrait rotated `ROTATION_90` |
| **Logical Landscape Resolution** | **`1920 × 1080`** (`16:9`, `1.778 : 1`) | **`1240 × 1080`** (~`8:7` / `31:27`, `1.148 : 1`) |
| **Firmware `densityDpi`** | `369 dpi` (`2.30625x`) | `369 dpi` (`2.30625x`) |
| **Native Android `dp` Bounds** | `sw468dp w833dp h444dp` (`mBounds=[0,0][1920,1080]`, `mAppBounds=[0,0][1920,1025]`) | `sw468dp w538dp h444dp` (`mBounds=[0,0][1240,1080]`, `mAppBounds=[0,0][1240,1025]`) |
| **BDSA `720dp` `OverlayViewport`** | — | `normalizedDensity = 1.500x` (`1080 / 720`), **`826.7dp × 720.0dp`** (`aspectFraction = 0.0f`) |
| **Layer Stack** | `0` | `4` |
| **Refresh Rate Modes** | `60 Hz` / `120 Hz` | `60 Hz` / `120 Hz` (panel hardware runs `60 Hz` / `120 Hz` nominal) |
| **Panel TE VSYNC Clock Ratio** | **`118.412f / 120f`** (on-panel DDIC TE oscillator runs at `~8.4451 ms` = `118.412 Hz` in 120 Hz mode and `~16.890 ms` = `59.206 Hz` in 60 Hz mode) | `1.0f` |
| **SystemUI Bars / Insets** | `StatusBar` (`[0,0][1920,55]`) + `NavigationBar0` (`[0,1025][1920,1080]`, `55px`) | `NavigationBar4` (`visible=false`, `mSourceFrame=[0,1025][1240,1080]`, `60px` left/right gesture zones) |

### 3.2 WindowManager & Multi-Display Recording Invariants

- **Overlay Window Sizing & Flags** ([`ScreenOverlayManager.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/overlay/ScreenOverlayManager.kt)):
  - Context: Obtained via `context.createDisplayContext(display4)` with explicit `1240 × 1080` `getRealSize()` pixel dimensions, `TYPE_APPLICATION_OVERLAY`, and `Gravity.CENTER`.
  - `FLAG_LAYOUT_NO_LIMITS`: **Never set on Display `4`**. Setting `FLAG_LAYOUT_NO_LIMITS` causes Android 13 `WindowManagerService` to lay out the surface at Display `0`'s `1920px` width, clipping the right ~`680px` of the UI off-screen.
  - `lp.fitInsetsTypes`: Set to `0` so decor insets never shrink the fullscreen overlay.
  - `FLAG_LOCAL_FOCUS_MODE`: **Enabled (`useLocalFocusMode = true`)** so Display `4` overlay windows manage independent focus cleanly without stealing Display `0` game focus.
  - Animations: `WindowManagerService` ignores custom `windowAnimations` on `TYPE_APPLICATION_OVERLAY` for non-system apps; BDSA animates `composeView` directly via `ViewPropertyAnimator` (`200ms`, `translationY = height * 0.90f <-> 0f`, `alpha = 0f <-> 1f`).
- **Multi-Display Task Swap & Screen Recording** ([`ScreenRecorderEngine.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/ScreenRecorderEngine.kt)):
  - Task organization: `DefaultTaskDisplayArea` on `mDisplayId=4` hosts standard fullscreen activities; `IActivityTaskManager.getAllRootTaskInfosOnDisplay(4)` and `moveRootTaskToDisplay(taskId, 4)` swap tasks between Display `0` and Display `4`.
  - Surface mirroring API: Uses Android 13 hidden `SurfaceControl.createDisplay` / `DisplayControl.createDisplay` + static `SurfaceControl.setDisplaySurface`, `setDisplayProjection`, and `setDisplayLayerStack`.
  - Dual-screen combined recording canvas: **`1920 × 2160`** (`1920 × 1080` Top Screen above centered `1240 × 1080` Bottom Screen).

---

## 4. Backlight, Brightness & Screen Blanking Mechanics

| Aspect | Top Screen (`displayId = 0`) | Bottom Screen (`displayId = 4`) |
| :--- | :--- | :--- |
| **`DisplayManager` Range** | `[0.0 .. 1.0]` (`Settings.System["screen_brightness"]` `0..255`) | `[0.0 .. 1.0]` (`Settings.System["sub_screen_brightness"]` `0..255`) |
| **Sysfs Backlight Node** | N/A for blanking (self-emissive AMOLED panel) | N/A for blanking (self-emissive AMOLED panel) |
| **Blanking Strategy** | **Software OLED Blanking** (`blankViaBacklightSysfs = false`): `BlankOverlayView` (`200ms` fade) + `TYPE_BOOT_PROGRESS` (`2021`) cover window + `IStatusBarService.disable` + `DisplayManager.setBrightness(0, -1.0f)` | **Software OLED Blanking** (`blankViaBacklightSysfs = false`): `BlankOverlayView` (`200ms` fade) + `DisplayManager.setBrightness(4, -1.0f)` |
| **Touch Disable Property** | `sys.temp.disabled.touch = "1,0"` | `sys.temp.disabled.touch = "0,1"` (`"0,0"` = both active) |

- **Sleep / Wake & Lid Sensor Behavior**: Because `setBrightness(displayId, -1.0f)` overrides the active `DisplayManager` brightness float, `ScreenOverlayManager.onDeviceScreenInteractiveChanged(false)` restores the saved linear brightness prior to device sleep (`ACTION_SCREEN_OFF`) and re-applies `-1.0f` + immersive fullscreen on wake (`ACTION_SCREEN_ON`).
- **Watchdog Recovery Requirement** ([`PrivilegedHelperMain.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt)): On crash circuit-breaker, package disable, opt-out, or uninstall, `PrivilegedHelperMain` removes any Display `0` `TYPE_BOOT_PROGRESS` cover window, clears `IStatusBarService.disable(0)`, restores saved linear brightness on Display `0` and Display `4` if `< 0.01f`, resets `dual_screen_display_mode = 0`, and writes `sys.temp.disabled.touch = "0,0"`.

---

## 5. Thermal Zones, Active Cooling & Ambient Lighting

### 5.1 Thermal Sysfs Mapping (`/sys/devices/virtual/thermal/`)

| Zone(s) | `type` String | Subsystem | Used by BDSA `TelemetryMonitor`? |
| :--- | :--- | :--- | :---: |
| `thermal_zone48` | `cpuss-0-usr` | Snapdragon 8 Gen 2 CPU Subsystem | **Primary** (fast-pathed first by `resolveCpuThermalPath()`, `m°C / 1000.0`) |
| `thermal_zone*` (`cpu-*` / `cpuss-*`) | `cpu-*` | Per-cluster / per-core Kryo sensors | Automatic fallback |
| `thermal_zone*` (`gpuss-*`) | `gpuss-*` | Adreno 740 GPU sensors | No |
| `thermal_zone*` (`battery`) | `battery` | Battery pack thermistor | No |

### 5.2 Active Cooling Fan (`SettingsController` -> `FanProvider`)

- **Service Lookup**: `ServiceManager.getService("SettingsController")` (`com.ro.settings.IExternalControlManager`, tx `1` `"FanProvider"`) -> `com.ro.settings.IFanControlProvider`.
- **Supported Modes (`Settings.System["fan_mode"]`)**: `0` = Off, `1` = Quiet, `4` = Smart, `5` = Sport, `6` = Custom (`Settings.System["fan_speed"]`). *(Note: High Performance mode `2` forces fan out of `0` Off into `4` Smart).*
- **Custom Duty Range**: **`35% .. 100%`** (`getMinSpeed()` tx `1` = `35`, `getMaxSpeed()` tx `2` = `100`, live duty float `[0.0..1.0]` via tx `3`, raw fan value via tx `10`).

### 5.3 LED & Ambient RGB Lighting

- **Joystick / Ambient RGB Hardware**: **Supported (`supportsAmbientLight = true`)** — dual RGB LED rings around the left and right Hall-effect thumbsticks plus side ambient accents.
- **Control Mechanism**:
  - Live state property: `sys.light.curr.is.show` (`"1"` / `"true"` = On, `"0"` / `"false"` = Off).
  - Persistent settings: `Settings.System["joystick_light_enabled"]` (`0`/`1`) and `Settings.System["led_light_brightness_percent"]` (`0..100`).
  - Broadcast IPC: `"com.ro.external.request.light.adj.action"` (`isShow: Boolean` to package `"com.android.settings"`, `light_brightness: Int` `0..255`).

---

## 6. Input Subsystem, Physical Assistant Key & Gamepad Controls

### 6.1 Kernel Input Devices (`/dev/input/event*`)

| Device Name | Event Node | Role & Display Viewport Binding |
| :--- | :---: | :--- |
| Primary Touchscreen | `/dev/input/event*` (Internal) | Top `6.0"` touchscreen (`Viewport INTERNAL: displayId=0`) |
| Secondary Touchscreen | `/dev/input/event*` (External/Secondary) | Bottom `3.92"` touchscreen (`displayId=4`; gated by `sys.temp.disabled.touch`) |
| Clamshell Hall Sensor | `/dev/input/event*` (`SW_LID`) | Magnetic Hall sensor detecting clamshell open/close |
| Built-in Odin Controller | Virtual / MCU Gamepad | Hall-effect sticks & L2/R2 analog/digital triggers (`Xbox` / `Odin` / `Disconnect` profiles) |

### 6.2 Physical Assistant Button State Machine

- **Broadcast Action**: `"action.tcc.button.key.event"`
- **Extras**: `key_action_down: Boolean`, `key_isLongPress: Boolean`
- **Initial Key Down (`key_action_down == true && !key_isLongPress`)**: No-op (`onTccKeyDown()`).
- **Long Press (`key_action_down == true && key_isLongPress`)**: **Always blanks the bottom screen** (`longPressTogglesBottom = false`): hides the overlay if open, sets `longPressConsumed = true`, and sets `dual_screen_display_mode = 1`.
- **Short Press Release (`key_action_down == false`)**: If `dual_screen_display_mode == 1` or the bottom screen is blanked, wakes the bottom screen (`setDualScreenDisplayMode(0)`); otherwise toggles the Assistant Overlay (`200ms` debounce).

### 6.3 Gamepad & Key Mapping Specifics

- **Controller Style (`temp_abxy_layout_mode`)**: `0` = `"Xbox"`, `1` = **`"Odin"`**, `2` = `"Disconnect"` (Disabled).
- **Trigger Mode (`trigger_input_mode`)**: `0` = Digital Only, `1` = Analog Only, `2` = Both (Analog + Digital).
- **Overlay Dismissal via `MappingServiceClient`**: Binds `com.odin.mapping` and registers `["HOME"]` only (`dismissOverlayOnBackMapping = false`).

---

## 7. Multi-Display Audio Routing & Hardware Volume Keys

- **Secondary App Audio Scaling Service**: `com.odin.gameassistant` monitors tasks running on Display `4` (`sys.audio.uids`) and scales their audio independently from Display `0`.
- **Settings Keys**: Uses `Settings.System["secondary_screen_volume_level"]` (`0..15`) for speakers and `Settings.System["secondary_screen_volume_level_for_headphones"]` (`0..15`) when wired/Bluetooth headphones are routed.
- **Hardware Volume Key Broadcast**: `"action.volume.key.event"` (`SDK == 33`) broadcast by `system_server` when hardware volume keys are pressed at `STREAM_MUSIC` min/max boundaries.

---

## 8. Privileged Root, Vendor Binder Services & Stock Assistant Package

| Subsystem | Package / Service Name | Interface Descriptor / Protocol | Device-Specific Notes |
| :--- | :--- | :--- | :--- |
| **Root Shell Binder** | `PServerBinder` (`ServiceManager`) | Tx `0`: `writeStringArray([cmd, wait])` -> `byte[]` | Self-grants permissions & spawns `PrivilegedHelperMain` (`UID 0`) |
| **Stock Assistant** | **`com.odin.dualscreen.assistant`** | `/system/app/DualScreenAssistant/DualScreenAssistant.apk` (`.services.TccServices`) | `persistent="true"`, `android.uid.system`; suppressed via 4-step ephemeral `SIGSTOP` |
| **Bottom Recents IPC** | `com.android.systemui` / `com.android.launcher3` | **`com.android.systemui.shared.recents.IOverviewProxy`** (tx `6`) | Passes `(displayId = 4, packageName)` (`SDK 33`) |
| **Key Mapping IPC** | **`com.odin.mapping`** | `com.ro.mapping.sdk.IServerApi` (tx `2` register / tx `3` unregister) | Listener `OnInputEventListener` (tx `1`) for `["HOME"]` |
| **Live Game FPS IPC** | `SurfaceFlinger` / `window` | `ISurfaceComposer` / `IWindowManager.registerTaskFpsCallback` | Normalized by `topPanelTeClockRatio = 118.412f / 120f` |
| **Vendor Companion Apps** | `com.odin.dualscreen.assistant`, `com.odin.mapping`, `com.odin.gameassistant`, `com.odin.settings` | Protected from `cleanMemory()` background process termination | Declared in `<queries>` in `AndroidManifest.xml` |

---

## 9. `Settings.System` Keys & Capability Support Matrix

| Feature / Capability | Supported? | Underlying Key / Node | Notes |
| :--- | :---: | :--- | :--- |
| **Dual-Screen Display Mode (`0/1/2`)** | **Yes** | `dual_screen_display_mode` | OLED software blanking + `-1.0f` brightness |
| **Screen Focus Lock (`0/1/2`)** | **Yes** | `screen_focus_lock`, `enable_system_key_focus_lock` | Locks input to Display `0` or Display `4` |
| **60 / 120 Hz Refresh Rate Toggle** | **Yes** | `min_refresh_rate`, `peak_refresh_rate` | Calibrated for `118.412 Hz` DDIC TE clock |
| **Performance Modes (`0/1/2`)** | **Yes** | `performance_mode` | Standard / Performance / High Performance |
| **Active Fan Control (`0/1/4/5/6`)** | **Yes** | `fan_mode`, `fan_speed`, `IFanControlProvider` | Off / Quiet / Smart / Sport / Custom (`35%–100%`) |
| **Joystick RGB Ambient Light & Slider** | **Yes** | `joystick_light_enabled`, `led_light_brightness_percent`, `sys.light.curr.is.show` | Enables **Ambient Light** quick tile & 4th **Stick LED** slider |
| **Bypass Charging & 80% Limit** | **Yes** | `is_charging_separation`, `percent_80_charge_limit` | Both supported in hardware |
| **OS Monet Theme Injection** | **Yes** | `FabricatedOverlay("com.android.shell", "os_theme")` | Android 13 dynamic 65-color Monet resource table |
| **Default Native `TccUiLook`** | — | **`TccUiLook.AYN`** (`AYN Smooth`) | Can also switch live to `TccUiLook.RETROID` (`Retroid Pixel`) |

---

## 10. BDSA `DeviceProfile` Configuration Summary

Exact `AYN_THOR` enum entry in [`DeviceProfile.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/system/DeviceProfile.kt):

| `DeviceProfile` Field | Value |
| :--- | :--- |
| `displayName` / `vendorName` | `"AYN Thor"` / `"AYN"` |
| `wakeKeyName` / `physicalButtonLabel` | `"AYN key"` / `"physical AYN button"` |
| `topDisplayId` / `bottomDisplayId` | `0` / `4` |
| `topSfPort` / `bottomSfPort` | `"131"` / `"132"` |
| `bottomFallbackWidth` × `bottomFallbackHeight` | `1240 × 1080` |
| `bottomFallbackDensityDpi` | `369` |
| `defaultUiLookPreferenceId` | `1` (`TccUiLookPreference.AYN`) |
| `useLocalFocusMode` | `true` |
| `blankViaBacklightSysfs` | `false` |
| `stockAssistantPackage` | `"com.odin.dualscreen.assistant"` |
| `mappingPackages` | `["com.odin.mapping", "com.rp.mapping"]` |
| `protectedVendorPackages` | `["com.odin.dualscreen.assistant", "com.odin.gameassistant", "com.odin.mapping", "com.odin.settings"]` |
| `supportsAmbientLight` | `true` |
| `standardControllerLabel` / `controllerStyleOptionLabel` | `"Odin"` / `"Standard Style"` |
| `topPanelTeClockRatio` | `118.412f / 120f` (`0.9867667f`) |
| `longPressTogglesBottom` | `false` |
| `dismissOverlayOnBackMapping` | `false` |
