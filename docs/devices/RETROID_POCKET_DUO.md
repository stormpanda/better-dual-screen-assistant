# Retroid Pocket Duo — Hardware & Firmware Integration Reference

The **Retroid Pocket Duo** (`sun` / `CQ7790S`) is a clamshell dual-screen Android gaming handheld manufactured by **Moorechip** running **Android 16 (`Baklava`, `SDK 36`)**. It pairs a `1920 × 1080` (`16:9`) 120 Hz top display (`displayId = 0`) with a `1280 × 960` (`4:3`) 120 Hz bottom display (`displayId = 2`), blanks panels at the kernel driver level via hardware backlight power sysfs (`/sys/class/backlight/panel{0,1}-backlight/bl_power`) + `sys.temp.disabled.touch`, ships with the persistent system app `com.retroidpocket.dualscreen.assistant`, and defaults to the **Retroid Pixel** (`TccUiLook.RETROID`) UI look in **Better Dual Screen Assistant** (`BDSA`).

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
| **Marketing Name** | Vendor spec | `Retroid Pocket Duo` |
| **Manufacturer** | `ro.product.manufacturer` | `"Moorechip"` |
| **Brand** | `ro.product.brand` | `"qti"` |
| **Model String** | `ro.product.model` (`Build.MODEL`) | `"Retroid Pocket Duo"` (stock APK also checks `"Retroid Pocket Duo Lite"`) |
| **Device / Board Codename** | `ro.product.device` / `ro.board.platform` | `"sun"` |
| **SoC Manufacturer & Model** | `ro.soc.manufacturer` / `ro.soc.model` | `"Moorechip"` / **`"CQ7790S"`** (`Adreno 722` GPU) |
| **Android Version & SDK** | `ro.build.version.release` / `ro.build.version.sdk` | **Android 16 (`Baklava`, `SDK 36`)**, security patch `2026-04-05` |
| **Supported ABIs** | `ro.product.cpu.abilist` | `arm64-v8a` (64-bit only; `ro.product.cpu.abilist32 = ""`) |
| **Kernel Page Size** | `ro.product.page_size` | `4096` (`4 KB`) |
| **Reference Firmware Build** | `ro.build.display.id` | `RPDuo_V1.0.0.108_20261018_123919_user` |
| **Form Factor** | Physical chassis | Clamshell dual-screen handheld (`16:9` top + `4:3` bottom) with Hall-effect sticks & triggers, active cooling fan, `mlid` Hall lid sensor, and dedicated **Dual Screen** button |
| **BDSA Detection Rule** | [`DeviceProfile.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/system/DeviceProfile.kt) | `Build.MODEL.trim().startsWith("Retroid Pocket Duo", ignoreCase = true)` with fallback check for installed package `com.retroidpocket.dualscreen.assistant` |

---

## 2. SoC, CPU, GPU, Memory & Power Architecture

### 2.1 CPU Cluster Topology (`cpufreq`)

| Cluster / Policy | Cores | Min Frequency | Max Frequency | Governor | BDSA Telemetry Node |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `policy0` (Efficiency) | `0, 1, 2` | `441,600 kHz` | `1,843,200 kHz` (`1.84 GHz`) | `walt` | — |
| `policy3` (Performance) | `3, 4, 5, 6` | `480,000 kHz` | `2,400,000 kHz` (`2.40 GHz`) | `walt` | — |
| `policy7` (Prime) | `7` | `480,000 kHz` | `2,803,200 kHz` (`2.80 GHz`) | `walt` | `/sys/devices/system/cpu/cpu7/cpufreq/{cpuinfo_cur_freq,cpuinfo_max_freq}` |

### 2.2 GPU (`kgsl-3d0` / Devfreq)

| Attribute | Sysfs Node | Value / Format |
| :--- | :--- | :--- |
| **GPU Model** | `/sys/class/kgsl/kgsl-3d0/gpu_model` | `Adreno722` (Qualcomm Adreno 722) |
| **Current Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` (`gpuclk`) | Raw `Hz` (dynamically formatted as `MHz` below `1,000,000,000 Hz` e.g. `796 MHz`, and `GHz` at/above `1.00 GHz` e.g. `1.15 GHz`) |
| **Max Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/max_freq` (`max_gpuclk`) | `1,150,000,000` (`1.15 GHz`; boost table up to `1.26 GHz`) |
| **Utilization** | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` (`gpubusy`) | `0..100 %` |

### 2.3 Memory & Battery Power Telemetry

| Subsystem | Sysfs / API Source | Raw Unit & Notes |
| :--- | :--- | :--- |
| **System RAM** | `ActivityManager.getMemoryInfo()` | `8 GB` / `12 GB` LPDDR5 SKU configurations |
| **Battery Current** | `/sys/class/power_supply/battery/current_now` | `µA` (also exposes USB-PD `usb` and `ucsi-source-psy-*` power supplies) |
| **Battery Voltage** | `/sys/class/power_supply/battery/voltage_now` | `µV` (`Watts = -(A * V)` mapped onto bidirectional `-20W..+25W` ring gauge) |
| **Bypass Charging** | `Settings.System["is_charging_separation"]` | **Supported** (`0` = Disabled, `1` = Enabled) |
| **80% Charge Limit** | `Settings.System["percent_80_charge_limit"]` | **Supported** (`0` = Disabled, `1` = Enabled) |

---

## 3. Multi-Display Topology & Windowing Invariants

### 3.1 Physical & Logical Display Matrix

| Property | Top Screen (Primary) | Bottom Screen (Secondary) |
| :--- | :--- | :--- |
| **Logical `displayId`** | **`0`** (`Display.DEFAULT_DISPLAY`) | **`2`** *(Note: `2`, not `4`!)* |
| **Panel Technology** | `16:9` panel (`panel0-backlight`, `max=8191`) | `4:3` native landscape panel (`panel1-backlight`, `max=3445`) |
| **SurfaceFlinger Port & ID** | `port=146` (`uniqueId = "local:4630947039571902866"`) | `port=147` (`uniqueId = "local:4630946671160541331"`) |
| **Native Scanout & Rotation** | `1080 × 1920` portrait rotated `ROTATION_90` | `1280 × 960` native landscape @ `ROTATION_0` |
| **Logical Landscape Resolution** | **`1920 × 1080`** (`16:9`, `1.778 : 1`) | **`1280 × 960`** (`4:3`, `1.333 : 1`) |
| **Firmware `densityDpi`** | `369 dpi` (`2.30625x`) | `320 dpi` (`2.00000x`) |
| **Native Android `dp` Bounds** | `sw468dp w833dp h468dp` (`mBounds=[0,0][1920,1080]`) | `sw480dp w640dp h480dp` (`mBounds=[0,0][1280,960]`) |
| **BDSA `720dp` `OverlayViewport`** | — | `normalizedDensity = 1.3333x` (`960 / 720`), **`960.0dp × 720.0dp`** (`aspectFraction = 1.0f`) |
| **Layer Stack** | `0` | `2` |
| **Refresh Rate Modes** | Mode `1`: `120.00001 Hz`, Mode `2`: `60.000004 Hz` | Mode `3`: `120.00001 Hz`, Mode `4`: `60.000004 Hz` |
| **Panel TE VSYNC Clock Ratio** | **`1.0f`** (exact `120.00 Hz` / `60.00 Hz` VSYNC timing) | **`1.0f`** |
| **SystemUI Bars / Insets** | `StatusBar` (`[0,0][1920,55]`) + `NavigationBar0` (`[0,1025][1920,1080]`, `55px`) | Active `NavigationBar2` (`[0,912][1280,960]`, `48px` / `24dp` tall) + `69px` left/right gesture insets |

### 3.2 WindowManager & Multi-Display Recording Invariants

- **Overlay Window Sizing & Flags** ([`ScreenOverlayManager.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/overlay/ScreenOverlayManager.kt)):
  - Context: Obtained via `context.createDisplayContext(display2)` with explicit `1280 × 960` `getRealSize()` pixel dimensions, `TYPE_APPLICATION_OVERLAY`, and `Gravity.CENTER`.
  - `FLAG_LAYOUT_NO_LIMITS`: **Never set on Display `2`** (prevents primary-display surface width overflow).
  - `lp.fitInsetsTypes`: **Must be explicitly set to `0`**. On Android 16 (`SDK 36`), `WindowManager.LayoutParams` defaults `fitInsetsTypes` to `WindowInsets.Type.systemBars()`, which otherwise shrinks Display `2` overlay windows from `1280 × 960` down to `1280 × 912` above `NavigationBar2`.
  - `FLAG_LOCAL_FOCUS_MODE`: **Omitted (`useLocalFocusMode = false`)**, matching stock Retroid `ProductRuntimeProvider` (`flags = 0x80020600`) so Android 16 dual-display `FocusLock` routing works cleanly.
- **Multi-Display Task Swap & Screen Recording** ([`ScreenRecorderEngine.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/ScreenRecorderEngine.kt)):
  - Task organization: Although `DisplayInfo` on Display `2` reports `FLAG_PRESENTATION`, `WindowManager` organizes a full `DefaultTaskDisplayArea` on `mDisplayId=2 (organized)` (`SecondaryDisplayLauncher` + standard user app tasks), so `IActivityTaskManager.getAllRootTaskInfosOnDisplay(2)` and `moveRootTaskToDisplay(taskId, 2)` work directly.
  - Surface mirroring API (`SDK 36`): Android 16 `services.jar` replaced `DisplayControl.createDisplay` with **`DisplayControl.createVirtualDisplay(String, boolean): IBinder`** + `destroyVirtualDisplay(IBinder)` and moved `setDisplaySurface`, `setDisplayProjection`, and `setDisplayLayerStack` onto **`SurfaceControl$Transaction`** (with `VirtualDisplayConfig$Builder.setDisplayIdToMirror(int)` available in `framework.jar` as fallback).
  - Screenshot capture (`SDK 36`): `screencap -d` requires the 64-bit SurfaceFlinger physical ID resolved via `DisplayManager.getDisplay(logicalId)?.uniqueId?.removePrefix("local:")` (`4630947039571902866` / `4630946671160541331`).
  - Dual-screen combined recording canvas: **`1920 × 2040`** (`1920 × 1080` Top Screen above centered `1280 × 960` Bottom Screen).

---

## 4. Backlight, Brightness & Screen Blanking Mechanics

| Aspect | Top Screen (`displayId = 0`) | Bottom Screen (`displayId = 2`) |
| :--- | :--- | :--- |
| **`DisplayManager` Range** | `[0.0 .. 1.0]` (`min=0.0` / `6.4 nits`, `max=1.0` / `782.4 nits`, `default=0.3976`) | `[0.0 .. 1.0]` (linear `[0..1]` spline, `default=0.5`) |
| **Sysfs Backlight Node** | `/sys/class/backlight/panel0-backlight` (`brightness` `0..8191`, `bl_power` `0`/`1`) | `/sys/class/backlight/panel1-backlight` (`brightness` `0..3445`, `bl_power` `0`/`1`) |
| **Blanking Strategy** | **Kernel Backlight Power Rail** (`blankViaBacklightSysfs = true`): writes `"1"` (off) / `"0"` (on) to `/sys/class/backlight/panel0-backlight/bl_power` without setting `DisplayManager` brightness to `-1.0f` | **Kernel Backlight Power Rail** (`blankViaBacklightSysfs = true`): writes `"1"` (off) / `"0"` (on) to `/sys/class/backlight/panel1-backlight/bl_power` without setting `DisplayManager` brightness to `-1.0f` |
| **Touch Disable Property** | `sys.temp.disabled.touch = "1,0"` | `sys.temp.disabled.touch = "0,1"` (`"0,0"` = both active) |

- **Sleep / Wake & Lid Sensor Behavior**: Kernel suspend/resume and the native `mlid` Hall-sensor daemon (`init.svc.mlid = running`, `persist.lid.close.to.forbid.power`) reset `bl_power` to `0` on wake; `ScreenOverlayManager.onDeviceScreenInteractiveChanged(true)` re-asserts `panel0` / `panel1` `bl_power = 1` for the active `dual_screen_display_mode` on `Intent.ACTION_SCREEN_ON`.
- **Watchdog Recovery Requirement** ([`PrivilegedHelperMain.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt)): **Critical hardware invariant** — if the app crashes or is uninstalled while `bl_power == 1`, `DisplayManager.setBrightness` alone will **not** turn the backlight back on. `PrivilegedHelperMain.restoreDisplayAndStockAssistant()` unconditionally writes `"0"` to both `/sys/class/backlight/panel0-backlight/bl_power` and `/sys/class/backlight/panel1-backlight/bl_power`, resets `dual_screen_display_mode = 0`, and writes `sys.temp.disabled.touch = "0,0"`.

---

## 5. Thermal Zones, Active Cooling & Ambient Lighting

### 5.1 Thermal Sysfs Mapping (`/sys/devices/virtual/thermal/`)

| Zone(s) | `type` String | Subsystem | Used by BDSA `TelemetryMonitor`? |
| :--- | :--- | :--- | :---: |
| `thermal_zone7`, `thermal_zone4` | `cpuss-0`, `cpuss-1` | `CQ7790S` CPU Subsystem *(Note: `thermal_zone48` is non-CPU on `sun`!)* | **Primary** (`thermal_zone7` resolved dynamically by `resolveCpuThermalPath()`, `m°C / 1000.0`) |
| `thermal_zone23..25` | `cpu-0-0-0` .. `cpu-0-2-0` | Efficiency Cores (`policy0`) | Fallback |
| `thermal_zone6,8,12,14,16,18,19,30` | `cpu-1-*` | Performance Cores (`policy3`) | Fallback |
| `thermal_zone20,21` | `cpu-2-0-0`, `cpu-2-0-1` | Prime Core (`policy7`) | Fallback |
| `thermal_zone10,31` | `gpuss-0`, `gpuss-1` | Adreno 722 GPU Subsystem | No |
| `thermal_zone57` | `battery` | Battery Pack | No |

### 5.2 Active Cooling Fan (`SettingsController` -> `FanProvider`)

- **Service Lookup**: `ServiceManager.getService("SettingsController")` (`com.ro.settings.IExternalControlManager`, tx `1` `"FanProvider"`) -> `com.ro.settings.IFanControlProvider` (100% identical Binder protocol to AYN Thor).
- **Supported Modes (`Settings.System["fan_mode"]`)**: `0` = Off, `1` = Quiet, `4` = Smart, `5` = Sport, `6` = Custom (`Settings.System["fan_speed"]`).
- **Custom Duty Range**: **`35% .. 100%`** (`IFanControlProvider` tx `1` = `35`, tx `2` = `100`, live duty float `[0.0..1.0]` via tx `3`, RPM/raw value via tx `10`).

### 5.3 LED & Ambient RGB Lighting

- **Joystick / Ambient RGB Hardware**: **Unsupported (`supportsAmbientLight = false`)** — thumbsticks do not have RGB LED rings.
- **Sysfs LED Nodes (`/sys/class/leds/`)**: Contains only `red`, `green`, `blue` (status/charging indicator controlled by `RPSettings` `ChargingLEDBrightnessPreference`), `aw_vibrator`, and camera flash/torch nodes.
- **BDSA UI Adaptation**: Automatically hides `QuickSettingTileId.AMBIENT_LIGHT` from Quick Settings / Edit Tiles and hides the 4th **Stick LED** brightness slider toggle.

---

## 6. Input Subsystem, Physical Assistant Key & Gamepad Controls

### 6.1 Kernel Input Devices (`/dev/input/event*`)

| Device Name | Event Node | Role & Display Viewport Binding |
| :--- | :---: | :--- |
| `fts_ts` | `/dev/input/event5` | Top `1920 × 1080` touchscreen (`Viewport INTERNAL: displayId=0`) |
| `fts_ts_sec` | `/dev/input/event6` | Bottom `1280 × 960` touchscreen (`Viewport EXTERNAL: displayId=2`; gated by `sys.temp.disabled.touch`) |
| `hall_switch` | `/dev/input/event3` | Clamshell lid Hall sensor (`/sys/devices/platform/soc/soc:hall@0`, managed by `mlid`) |
| Built-in Retroid Controller | Virtual / MCU Gamepad | Hall-effect sticks & L2/R2 analog/digital triggers (`Xbox` / `Retro` / `Disconnect` profiles) |

### 6.2 Physical Assistant Button State Machine

- **Broadcast Action**: `"action.tcc.button.key.event"`
- **Extras**: `key_action_down: Boolean`, `key_isLongPress: Boolean`
- **Initial Key Down (`key_action_down == true && !key_isLongPress`)**: No-op (`onTccKeyDown()`).
- **Long Press (`key_action_down == true && key_isLongPress`)**: **Toggles bottom screen power (`longPressTogglesBottom = true`)** matching stock `TccLongPressBehavior.TOGGLE_SECONDARY`: if `dual_screen_display_mode == 1` or the bottom screen is blanked, wakes the bottom screen (`setDualScreenDisplayMode(0)`); otherwise hides any open overlay and blanks the bottom screen (`setDualScreenDisplayMode(1)`).
- **Short Press Release (`key_action_down == false`)**: If `dual_screen_display_mode == 1` or bottom screen is blanked, wakes the bottom screen (`setDualScreenDisplayMode(0)`); otherwise toggles the Assistant Overlay (`200ms` debounce).

### 6.3 Gamepad & Key Mapping Specifics

- **Controller Style (`temp_abxy_layout_mode`)**: `0` = `"Xbox"`, `1` = **`"Retro"`** (`standardControllerLabel = "Retro"`), `2` = `"Disconnect"` (Disabled).
- **Trigger Mode (`trigger_input_mode`)**: `0` = Digital Only, `1` = Analog Only, `2` = Both (Analog + Digital).
- **Overlay Dismissal via `MappingServiceClient`**: Binds `com.rp.mapping` (`RsMapping.apk`) and registers **`["HOME", "BACK"]`** (`dismissOverlayOnBackMapping = true`).

---

## 7. Multi-Display Audio Routing & Hardware Volume Keys

- **Secondary App Audio Scaling Service**: `com.rp.gameassistant` (`com.ro.gameassistant.service.MultiDisplayVolumeControl`) monitors audio focus (`IAudioService.getCurrAudioFocusInfo`) and tasks running on Display `2` (excluding `com.android.systemui` and `com.android.launcher3`), writes their comma-separated UIDs into **`sys.audio.uids`**, and triggers `AudioManager.setStreamVolume` so the framework scales those UIDs by **`Settings.System["secondary_screen_volume_level"]`** (`0..15`, default `7`).
- **Headphone Split Key**: Unused on Retroid (`secondary_screen_volume_level` applies uniformly across built-in speakers, wired 3.5mm/USB-C audio, and Bluetooth).
- **Hardware Volume Key Broadcast**: **`"dual_screen_action.volume.key.event"`** (`SDK > 33`, extra `"key_type"` = `"KEYCODE_VOLUME_UP"` | `"KEYCODE_VOLUME_DOWN"`), used to step `secondary_screen_volume_level` when `STREAM_MUSIC` is already at `0` or `15`.

---

## 8. Privileged Root, Vendor Binder Services & Stock Assistant Package

| Subsystem | Package / Service Name | Interface Descriptor / Protocol | Device-Specific Notes |
| :--- | :--- | :--- | :--- |
| **Root Shell Binder** | `PServerBinder` (`pservice` daemon) | Tx `0`: `writeStringArray([cmd, wait])` -> `byte[]` | Identical Moorechip root bootstrap (`UID 0`) |
| **Stock Assistant** | **`com.retroidpocket.dualscreen.assistant`** | `/system/app/DualScreenAssistant/DualScreenAssistant.apk` (`.services.TccServices`) | `persistent="true"`, `android.uid.system`; suppressed via 4-step ephemeral `SIGSTOP` |
| **Bottom Recents IPC** | `com.android.launcher3` / `com.android.systemui` | **`com.android.systemui.shared.recents.ILauncherProxy`** (tx `6`, `SDK >= 36`) | Android 16 renamed `IOverviewProxy` to `ILauncherProxy`; passes `(displayId = 2, packageName)` |
| **Key Mapping IPC** | **`com.rp.mapping`** (`RsMapping.apk`) | `com.ro.mapping.sdk.IServerApi` (tx `2` register / tx `3` unregister) | Listener `OnInputEventListener` (tx `1`) for `["HOME", "BACK"]` |
| **Live Game FPS IPC** | `window` (`IWindowManager`) | `IWindowManager.registerTaskFpsCallback(int, ITaskFpsCallback)` | Preferred over raw `SurfaceFlinger` tx codes on `SDK >= 34`; `topPanelTeClockRatio = 1.0f` |
| **Vendor Companion Apps** | `com.retroidpocket.dualscreen.assistant`, `com.rp.mapping`, `com.rp.gameassistant`, `com.rp.settings`, `com.retroidpocket.gamelauncher` | Protected from `cleanMemory()` background process termination | Declared in `<queries>` in `AndroidManifest.xml` |

---

## 9. `Settings.System` Keys & Capability Support Matrix

| Feature / Capability | Supported? | Underlying Key / Node | Notes |
| :--- | :---: | :--- | :--- |
| **Dual-Screen Display Mode (`0/1/2`)** | **Yes** | `dual_screen_display_mode` | Hardware `panel{0,1}-backlight/bl_power` blanking |
| **Screen Focus Lock (`0/1/2`)** | **Yes** | `screen_focus_lock`, `enable_system_key_focus_lock` | Locks input to Display `0` or Display `2` |
| **60 / 120 Hz Refresh Rate Toggle** | **Yes** | `min_refresh_rate`, `peak_refresh_rate` | Exact `60.00 Hz` / `120.00 Hz` modes on both panels |
| **Performance Modes (`0/1/2`)** | **Yes** | `performance_mode` | Standard / Performance / High Performance |
| **Active Fan Control (`0/1/4/5/6`)** | **Yes** | `fan_mode`, `fan_speed`, `IFanControlProvider` | Off / Quiet / Smart / Sport / Custom (`35%–100%`) |
| **Joystick RGB Ambient Light & Slider** | **No** | — | Hidden via `supportsAmbientLight = false` |
| **Bypass Charging & 80% Limit** | **Yes** | `is_charging_separation`, `percent_80_charge_limit` | Both supported in hardware |
| **OS Monet Theme Injection** | **Yes** | `FabricatedOverlay("com.android.shell", "os_theme")` | Verified live on Android 16 `OverlayManagerService` |
| **Default Native `TccUiLook`** | — | **`TccUiLook.RETROID`** (`Retroid Pixel`) | Can also switch live to `TccUiLook.AYN` (`AYN Smooth`) |

---

## 10. BDSA `DeviceProfile` Configuration Summary

Exact `RETROID_POCKET_DUO` enum entry in [`DeviceProfile.kt`](../../app/src/main/java/com/stormpanda/betterdualscreenassistant/system/DeviceProfile.kt):

| `DeviceProfile` Field | Value |
| :--- | :--- |
| `displayName` / `vendorName` | `"Retroid Pocket Duo"` / `"Retroid"` |
| `wakeKeyName` / `physicalButtonLabel` | `"Dual Screen key"` / `"physical Dual Screen button"` |
| `topDisplayId` / `bottomDisplayId` | `0` / `2` |
| `topSfPort` / `bottomSfPort` | `"146"` / `"147"` |
| `bottomFallbackWidth` × `bottomFallbackHeight` | `1280 × 960` |
| `bottomFallbackDensityDpi` | `320` |
| `defaultUiLookPreferenceId` | `2` (`TccUiLookPreference.RETROID`) |
| `useLocalFocusMode` | `false` |
| `blankViaBacklightSysfs` | `true` |
| `stockAssistantPackage` | `"com.retroidpocket.dualscreen.assistant"` |
| `mappingPackages` | `["com.rp.mapping", "com.odin.mapping"]` |
| `protectedVendorPackages` | `["com.retroidpocket.dualscreen.assistant", "com.retroidpocket.gamelauncher", "com.rp.gameassistant", "com.rp.mapping", "com.rp.settings"]` |
| `supportsAmbientLight` | `false` |
| `standardControllerLabel` / `controllerStyleOptionLabel` | `"Retro"` / `"Retro"` |
| `topPanelTeClockRatio` | `1.0f` |
| `longPressTogglesBottom` | `true` |
| `dismissOverlayOnBackMapping` | `true` |
