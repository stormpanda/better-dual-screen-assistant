# Better Dual Screen Assistant

An open-source, user-installable replacement for the built-in `DualScreenAssistant` (`com.odin.dualscreen.assistant`) on the **AYN Thor** dual-screen handheld.

Built with Kotlin and Jetpack Compose, **Better Dual Screen Assistant** (`com.maikthomalla.betterdualscreenassistant`) provides 1:1 visual and functional parity with the stock system overlay on the bottom screen (`displayId = 4`, 1240×1080) while running as a standard user-installable APK.

---

## Features

### Hardware Button & Stock Takeover
- **Seamless Stock Replacement**: Uses the device's built-in `PServerBinder` privileged bridge to disable and suspend the persistent stock `com.odin.dualscreen.assistant` process so only the custom overlay responds to the physical **AYN / TCC** key—with a one-tap toggle in the companion app to restore the stock assistant at any time.
- **Physical Key Handling**:
  - **Short Press**: Toggles the assistant overlay on the bottom display (`displayId = 4`), or wakes the bottom display if currently blanked.
  - **Long Press**: Blanks the bottom display ("Only top screen" mode) and disables bottom-screen touch input.
  - **HOME Button Interception**: Binds to `com.ro.mapping.action.MAPPING_SERVICE` (`InputId.HOME`) to automatically dismiss the overlay when open.

### Mode Tab (Screen, Brightness & Volume Controls)
- **Display Modes**:
  - **Dual-screen display**: Both Top (`displayId = 0`, 1920×1080) and Bottom (`displayId = 4`, 1240×1080) displays active.
  - **Only top screen**: Smoothly fades the bottom screen to black, disables bottom touch input (`sys.temp.disabled.touch=0,1`), and pins focus to the top screen.
  - **Only bottom screen**: Smoothly fades the top screen to black, disables top touch input (`sys.temp.disabled.touch=1,0`), and pins focus to the bottom screen.
- **Per-Display Brightness**:
  - Linked **Dual Screen** brightness slider plus independent **Top Screen** (`displayId = 0`) and **Bottom Screen** (`displayId = 4`) sliders powered by Android 13's hidden per-display `DisplayManager.setBrightness` / `setTemporaryBrightness` APIs.
- **Per-Display Volume**:
  - Independent **Top Screen** (`AudioManager.STREAM_MUSIC`) and **Bottom Screen** (`secondary_screen_volume_level` / headphone split) volume sliders.

### Settings Tab (Frame Rate, Quick Settings & Telemetry)
- **Frame Rate Panel**:
  - One-tap **60 FPS / 120 FPS Mode** pill toggle (`min_refresh_rate` / `peak_refresh_rate`).
  - Real-time **Current FPS** readout calculated from SurfaceFlinger page-flip counters (`service call SurfaceFlinger 1013`).
- **Quick Settings Grid** (paged 2×4 grid with touch & `L1`/`R1` shoulder button navigation):
  1. **Focus Lock**: Auto-lock (touch switchable), Lock Top Screen, or Lock Bottom Screen (`screen_focus_lock`).
  2. **Performance Mode**: Standard, High Performance, or Max Performance (`performance_mode`).
  3. **Fan Control**: Off, Quiet, Smart, Sport, or Custom percentage slider via `SettingsController` (`IFanControlProvider`).
  4. **Clean Memory**: Frees background app processes while respecting `app_whiteList` and displays freed memory in a bottom-screen toast.
  5. **Controller Style**: Xbox Style, Normal (Odin) Style, or Disabled (`temp_abxy_layout_mode`, `flip_button_layout`, `no_create_gamepad_button_layout`).
  6. **Direct Power Supply**: Toggles bypass charging (`is_charging_separation`).
  7. **Vibration**: Toggles system haptics (`vibrate_on`).
  8. **Ambient LED**: Toggles joystick RGB lighting (`com.ro.external.request.light.adj.action`).
  9. **Force Landscape**: Forces landscape orientation (`force_landscape`).
  10. **Theme Mode**: Follow System, Pure Black, Light, or Dark (`tcc_theme_mode`).
- **Live Hardware Telemetry**:
  - **CPU Temperature** (`thermal_zone48`) and **Fan Speed** (`IFanControlProvider`).
  - **Ring Gauges**: Live **CPU** frequency (`cpu7` GHz), **GPU** frequency (`kgsl-3d0` MHz), **PWR** system power draw (Watts from battery current × voltage), and **RAM** usage (GB).

### Task Switcher & Status Bar
- **Bottom-Screen Recents**: Tapping **Task** binds to `android.intent.action.QUICKSTEP_SERVICE` (`IOverviewProxy`) and opens the secondary display Recents overview on `displayId = 4`.
- **Status Bar**: Live clock (tapping triggers memory cleanup), real-time network throughput (`KB/S` / `MB/S`), Bluetooth indicator, Wi-Fi signal strength, and charging/battery indicator.

---

## Architecture & Privileged Access

Although installed as a regular user APK without shared system UID keys, the app achieves full system-level integration on the AYN Thor by combining:
1. **`PServerBinder` Root Bridge** ([`PServerClient.kt`](app/src/main/java/com/maikthomalla/betterdualscreenassistant/privileged/PServerClient.kt)): Communicates with AYN's system `PServerBinder` service (`IBinder.transact(1, ...)`) to automatically grant runtime/appops permissions (`SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `WRITE_SECURE_SETTINGS`, `PACKAGE_USAGE_STATS`), query SurfaceFlinger, adjust sysfs node permissions, and manage the stock assistant lifecycle.
2. **Hidden API Bypass** ([`PrivilegedBridge.kt`](app/src/main/java/com/maikthomalla/betterdualscreenassistant/privileged/PrivilegedBridge.kt)): Uses `org.lsposed.hiddenapibypass` to invoke hidden `DisplayManager`, `WindowManager`, and `ServiceManager` APIs directly in-process with automatic fallback to `PServerBinder`.

---

## Building & Installing

### Prerequisites
- Android SDK (API 35) & JDK 17+
- An **AYN Thor** connected via ADB

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Once launched (or upon device boot), the foreground service automatically grants its required overlay/settings permissions via `PServerBinder`, suppresses the stock `DualScreenAssistant`, and begins listening for the physical TCC button.
