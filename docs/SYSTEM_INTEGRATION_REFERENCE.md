# System Integration & Hardware Reference

This document serves as the low-level hardware, firmware, and Android OS integration reference for **Better Dual Screen Assistant** on the **AYN Thor** (`kalama` / Snapdragon 8 Gen 2, Android 13 / API 33). It catalogues every Settings provider key, SharedPreferences key, system property, broadcast intent, vendor Binder interface, sysfs telemetry node, and OS overlay resource used by the application.

---

## Table of Contents

1. [Android Settings Provider Keys](#1-android-settings-provider-keys)
   - [1.1 `Settings.System` Keys](#11-settingssystem-keys)
   - [1.2 `Settings.Global` Keys](#12-settingsglobal-keys)
   - [1.3 `Settings.Secure` Keys](#13-settingssecure-keys)
2. [Application SharedPreferences (`better_dsa_prefs`)](#2-application-sharedpreferences-better_dsa_prefs)
3. [Android System Properties (`SystemProperties`)](#3-android-system-properties-systemproperties)
4. [Broadcast Intents & Extras](#4-broadcast-intents--extras)
5. [AYN Vendor Binder Services (`VendorServices.kt`)](#5-ayn-vendor-binder-services-vendorserviceskt)
   - [5.1 Fan Control Provider (`IFanControlProvider`)](#51-fan-control-provider-ifancontrolprovider)
   - [5.2 Bottom-Screen Recents (`IOverviewProxy`)](#52-bottom-screen-recents-ioverviewproxy)
   - [5.3 Hardware Key Mapping Service (`IMappingService`)](#53-hardware-key-mapping-service-imappingservice)
6. [Hardware Telemetry Sysfs Nodes & SurfaceFlinger IPC](#6-hardware-telemetry-sysfs-nodes--surfaceflinger-ipc)
7. [Android 13 Monet OS Theme Injection (`FabricatedOverlay`)](#7-android-13-monet-os-theme-injection-fabricatedoverlay)
8. [Megingiard Cross-App Theme Provider Schema](#8-megingiard-cross-app-theme-provider-schema)

---

## 1. Android Settings Provider Keys

Managed primarily in [`SystemSettingsRepository.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/system/SystemSettingsRepository.kt) and written via [`PrivilegedBridge.putSetting()`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedBridge.kt).

### 1.1 `Settings.System` Keys

| Key Name | Value Range / Enum | Default | Description |
| :--- | :--- | :---: | :--- |
| `dual_screen_display_mode` | `0` = Dual-screen display<br>`1` = Only top screen (Bottom blanked)<br>`2` = Only bottom screen (Top blanked) | `0` | Controls active dual-screen blanking mode on the AYN Thor. |
| `screen_focus_lock` | `0` = Touch Switchable<br>`1` = Lock Upper Screen Focus (`displayId = 0`)<br>`2` = Lock Lower Screen Focus (`displayId = 4`) | `0` | Controls which display retains gamepad/keyboard input focus. |
| `screen_brightness` | `0..255` | `128` | Primary (Top, `displayId = 0`) screen brightness level. |
| `sub_screen_brightness` | `0..255` | `128` | Secondary (Bottom, `displayId = 4`) screen brightness level. |
| `secondary_screen_volume_level` | `0..15` | `8` | Independent audio volume level for apps running on the Bottom Screen (`displayId = 4`). |
| `performance_mode` | `0` = Normal (Standard)<br>`1` = Performance<br>`2` = High Performance | `0` | Selects system CPU/GPU governor and thermal profile. |
| `fan_Service_mode` | `0` = Off<br>`1` = Quiet<br>`4` = Smart<br>`5` = Sport<br>`6` = Custom | `4` | Active cooling fan mode. *(Note: capital `S` in `fan_Service_mode` matches stock firmware).* |
| `fan_custom_speed_percentage` | `35..100` | `50` | Manual fan duty cycle percentage when `fan_Service_mode == 6`. |
| `controller_style_type` | `0` = Xbox<br>`1` = Odin<br>`2` = Disconnect (Disabled) | `0` | Built-in gamepad ABXY/trigger mapping profile. |
| `is_charging_separation` | `0` = Disabled, `1` = Enabled | `0` | Bypass charging (powers system directly from USB-C without charging battery). |
| `percent_80_charge_limit` | `0` = Disabled, `1` = Enabled | `0` | Stops battery charging at 80% capacity. |
| `vibrate_on` | `0` = Disabled, `1` = Enabled | `1` | System haptic vibration toggle. |
| `joystick_light_enabled` | `0` = Disabled, `1` = Enabled | `1` | Fallback/persistent state for joystick RGB ambient lighting. |
| `led_light_brightness_percent` | `0..100` | `100` | Brightness percentage of the joystick RGB LEDs. |
| `force_landscape` | `0` = Disabled, `1` = Enabled | `0` | Forces portrait-only applications to rotate into landscape. |
| `trigger_input_mode` | `0` = Digital only<br>`1` = Analog only<br>`2` = Both Analog & Digital | `2` | L2/R2 Hall-effect shoulder trigger output mode. |
| `global_gamepad_to_mouse_mode` | `0` = Disabled, `1` = Enabled | `0` | Virtual gamepad-to-mouse pointer mode. |
| `key_adapter_enable` | `0` = Disabled, `1` = Enabled | `1` | Master enable flag for AYN's built-in on-screen key mapper service. |
| `is_show_floatingIcon` | `0` = Hidden, `1` = Visible | `0` | Shows or hides the floating Key Mapper overlay icon. |
| `keep_screen_on` | `0` = Disabled, `1` = Enabled | `0` | Prevents the screen from sleeping due to inactivity. |
| `prevent_press_home_accidentally` | `0` = Single press, `1` = Double-tap required | `0` | Requires a double-tap on the physical HOME button. |
| `min_refresh_rate` | `60.0` or `120.0` | `60.0` | Minimum display refresh rate in Hz. |
| `peak_refresh_rate` | `60.0` or `120.0` | `60.0` | Peak display refresh rate in Hz. |
| `accelerometer_rotation` | `0` = Locked, `1` = Auto-rotate | `0` | System auto-rotation setting. |
| `user_rotation` | `0..3` (`Surface.ROTATION_*`) | `0` | Locked display rotation angle. |

### 1.2 `Settings.Global` Keys

| Key Name | Value Range / Enum | Default | Description |
| :--- | :--- | :---: | :--- |
| `zen_mode` | `0` = Off<br>`1` = Priority Only<br>`2` = Total Silence<br>`3` = Alarms Only | `0` | Android Do Not Disturb (Zen Mode) state. Also synchronized via `cmd notification set_dnd on/off`. |
| `airplane_mode_on` | `0` = Off, `1` = On | `0` | Android Airplane Mode state. Also synchronized via `cmd connectivity airplane-mode enable/disable`. |

### 1.3 `Settings.Secure` Keys

| Key Name | Value Format | Description |
| :--- | :--- | :--- |
| `ui_night_mode` | `1` = Light (`MODE_NIGHT_NO`)<br>`2` = Dark (`MODE_NIGHT_YES`) | System-wide dark/light theme mode written during OS Theme Injection. |
| `theme_customization_overlay_packages` | JSON Object String | Material You / Monet customization timestamp and package metadata updated when `inject_os_theme` is toggled so SystemUI reloads dynamic colors immediately. |

---

## 2. Application SharedPreferences (`better_dsa_prefs`)

Stored in `context.getSharedPreferences("better_dsa_prefs", Context.MODE_PRIVATE)`:

| Key Name | Type | Default | Description |
| :--- | :---: | :---: | :--- |
| `replace_stock_assistant` | `Boolean` | `true` | Whether `com.odin.dualscreen.assistant` is ephemerally suppressed and replaced by this app. |
| `show_edit_button` | `Boolean` | `true` | Shows the 4th **Edit** button in the overlay's bottom navigation bar. |
| `remember_last_tab` | `Boolean` | `true` | Restores `last_active_tab` when reopening the overlay. |
| `last_active_tab` | `Int` | `0` | Last selected overlay tab (`0` = Mode, `1` = Settings). |
| `show_swap_screens_button` | `Boolean` | `true` | Shows the 4th **Swap Screens** button in the Mode tab's upper card. |
| `show_led_brightness_slider` | `Boolean` | `true` | Shows the 4th **Stick LED** brightness slider in the Mode tab's brightness card. |
| `no_more_close_tip` | `Boolean` | `false` | When `true`, skips the confirmation dialog before blanking the bottom screen (`mode = 1`). |
| `last_focus_lock` | `Int` | `0` | Saved `screen_focus_lock` value before entering single-screen mode (`1` or `2`), restored when returning to Dual Mode (`0`). |
| `dual_brightness_percent` | `Int` | `50` | Last set Dual Screen linked brightness percentage (`0..100`). |
| `dual_brightness_active` | `Boolean` | `false` | `true` when Top and Bottom brightness were last adjusted together via the Dual slider. |
| `dual_volume_percent` | `Int` | `50` | Last set Dual Screen linked volume percentage (`0..100`). |
| `dual_volume_active` | `Boolean` | `false` | `true` when Top and Bottom volume were last adjusted together via the Dual slider. |
| `theme_mode` | `Int` | `1` (`STOCK_SYSTEM`) | Active `AppThemeOption` ID (`1`, `3`, `4`, `100`, `101..108`). |
| `last_stock_theme_mode` | `Int` | `1` | Last selected Stock AYN theme ID (`1`, `3`, `4`) for Theme Source cycling. |
| `last_megingiard_palette_mode` | `Int` | `101` | Last selected Megingiard palette ID (`101..108`) for Theme Source cycling. |
| `accent_color` | `Long` | `0xFF3D5AFE` | Selected 32-bit ARGB accent color for customizable Megingiard themes (`101`, `102`). |
| `custom_accent_color` | `Long` | `0xFF3D5AFE` | User's saved custom HSV accent color (`0xFF000000..0xFFFFFFFF`). |
| `inject_os_theme` | `Boolean` | `false` | Whether to inject the active theme's 60-color Monet palette into Android OS via `FabricatedOverlay`. |
| `bottom_touch_locked` | `Boolean` | `false` | State of the **Bottom Touch Lock** Quick Setting tile. |
| `qs_tile_order` | `String` | Comma-separated keys | Ordered list of all 22 `QuickSettingTileId` keys. Automatically appends any newly added enum entries on upgrade. |
| `qs_enabled_tiles` | `String` | 10 Stock AYN keys | Comma-separated list of currently enabled `QuickSettingTileId` keys. |

---

## 3. Android System Properties (`SystemProperties`)

Read and written via `PrivilegedBridge.getSystemProperty()` / `setSystemProperty()`:

| Property Key | Values | Description |
| :--- | :--- | :--- |
| `sys.temp.disabled.touch` | `"0,0"` = Both touchscreens active<br>`"0,1"` = Bottom touchscreen (`displayId = 4`) disabled<br>`"1,0"` = Top touchscreen (`displayId = 0`) disabled | Kernel/inputflinger property that selectively disables touch input on either physical display without affecting display output. |
| `sys.light.curr.is.show` | `"1"` / `"true"` = Ambient LEDs On<br>`"0"` / `"false"` = Ambient LEDs Off | Live state property maintained by the AYN ambient light controller service. |

---

## 4. Broadcast Intents & Extras

| Action String | Direction | Extras & Types | Description |
| :--- | :---: | :--- | :--- |
| `"action.tcc.button.key.event"` | System -> App (`TccKeyReceiver`) | `key_action_down: Boolean`<br>`key_isLongPress: Boolean` | Broadcast by the AYN Thor framework whenever the physical AYN / TCC hardware button is pressed, held past the long-press threshold, or released. |
| `"com.ro.external.request.light.adj.action"` | Bidirectional (`PrivilegedBridge` <-> System) | `isShow: Boolean` *(toggle state)*<br>`light_brightness: Int (0..255)` *(brightness)* | Controls or notifies changes to the joystick RGB ambient LEDs. Target package for toggle: `"com.android.settings"`. |
| `Intent.ACTION_AIRPLANE_MODE_CHANGED` | App -> System | `state: Boolean` | Broadcast when toggling Airplane Mode. |

---

## 5. AYN Vendor Binder Services (`VendorServices.kt`)

Implemented in [`VendorServices.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/system/VendorServices.kt).

### 5.1 Fan Control Provider (`IFanControlProvider`)

Obtained via two-stage Binder lookup in `ServiceManager`:
1. `ServiceManager.getService("SettingsController")` -> descriptor `"com.ro.settings.ISettingsService"`.
2. Transaction `FIRST_CALL_TRANSACTION + 0` (`getProvider("FanProvider")`) -> returns `IBinder` with descriptor `"com.ro.settings.IFanControlProvider"`.

| Transaction Code | Method Equivalent | Request Parcel | Reply Parcel | Description |
| :---: | :--- | :--- | :--- | :--- |
| `FIRST_CALL_TRANSACTION + 0` (`1`) | `getMode()` | `interfaceToken` | `int` (`0, 1, 4, 5, 6`) | Returns current hardware fan mode. |
| `FIRST_CALL_TRANSACTION + 1` (`2`) | `setMode(mode)` | `interfaceToken`, `mode: Int` | *(none)* | Sets active fan mode (`0`=Off, `1`=Quiet, `4`=Smart, `5`=Sport, `6`=Custom). |
| `FIRST_CALL_TRANSACTION + 2` (`3`) | `getSpeedPercentage()` | `interfaceToken` | `int` (`0..100`) | Reads live fan PWM duty cycle percentage for the Telemetry panel. |
| `FIRST_CALL_TRANSACTION + 9` (`10`) | `setManualSpeed(percent)` | `interfaceToken`, `percent: Int` | *(none)* | Sets manual fan speed (`35..100%`) when mode is `6` (Custom). |

### 5.2 Bottom-Screen Recents (`IOverviewProxy`)

Bound via service intent:
- **Action**: `"android.intent.action.QUICKSTEP_SERVICE"`
- **Package**: `"com.android.systemui"`
- **Descriptor**: `"com.android.systemui.shared.recents.IOverviewProxy"`
- **Transaction**: `FIRST_CALL_TRANSACTION + 5` (`6`)
- **Request Parcel**: `writeInterfaceToken(DESCRIPTOR)`, `writeInt(4)` (`displayId = 4`), `writeString(context.packageName)` (`FLAG_ONEWAY`).
- **Effect**: Opens/toggles the Android Recent Apps overview screen specifically on the Bottom Screen (`displayId = 4`).

### 5.3 Hardware Key Mapping Service (`IMappingService`)

Bound via service intent:
- **Action**: `"com.ro.mapping.action.MAPPING_SERVICE"`
- **Package**: `"com.ro.mapping"`
- **Descriptor**: `"com.ro.mapping.IMappingService"`
- **Registration**: Transaction `FIRST_CALL_TRANSACTION + 0` registers our `IKeyEventListener` (`"com.ro.mapping.IKeyEventListener"`) with `inputIds = intArrayOf(109)` (`InputId.HOME = 109`).
- **Callback**: When `onKeyEvent(keyCode = 109, action = 1 [ACTION_UP])` fires on the listener stub, `AssistantForegroundService` dismisses the overlay if open.

---

## 6. Hardware Telemetry Sysfs Nodes & SurfaceFlinger IPC

Polled by [`TelemetryMonitor.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/system/TelemetryMonitor.kt) via `PrivilegedBridge.readFileText()` (`UID 0` in-memory file read):

| Telemetry Metric | Sysfs Path / IPC Source | Raw Unit | Conversion Formula |
| :--- | :--- | :--- | :--- |
| **CPU Current Frequency** | `/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq` | kHz | `kHz / 1000 = MHz` (or `/ 1000.0 = GHz` if `≥ 1000 MHz`) |
| **CPU Max Frequency** | `/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq` | kHz | `cur_freq * 100 / max_freq = CPU %` |
| **GPU Current Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` | Hz | `Hz / 1,000,000 = MHz` |
| **GPU Max Frequency** | `/sys/class/kgsl/kgsl-3d0/devfreq/max_freq` | Hz | `cur_freq * 100 / max_freq = GPU %` |
| **SoC Temperature** | `/sys/devices/virtual/thermal/thermal_zone48/temp` | m°C (`millidegrees`) | `round(raw / 1000.0) = ℃` |
| **Battery Current** | `/sys/class/power_supply/battery/current_now` | µA (`microamperes`) | `current_now / 1,000,000.0 = Amperes` |
| **Battery Voltage** | `/sys/class/power_supply/battery/voltage_now` | µV (`microvolts`) | `-(Amperes * Volts) = Watts` mapped onto `-20W..+25W` ring |
| **System RAM** | `ActivityManager.getMemoryInfo()` | Bytes | `(totalMem - availMem) / 1024³ = Used GB` |
| **Live Frame Rate (FPS)** | `ServiceManager.getService("SurfaceFlinger")` transaction `1013` (`android.ui.ISurfaceComposer`) | Cumulative page-flip count (`int32`) | `(flipsDelta * 1e9) / nanoTimeDelta`, clamped to current display refresh rate (`60` or `120`) |

---

## 7. Android 13 Monet OS Theme Injection (`FabricatedOverlay`)

When **Inject Theme Colors into OS** (`inject_os_theme`) is enabled, [`SystemSettingsRepository.buildMonetPalette()`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/system/SystemSettingsRepository.kt#L1562-L1626) generates **60 Android 13 Monet color resources** (`TypedValue.TYPE_INT_COLOR_ARGB8 = 0x1c`) across 5 tonal families (`12` shades each: `0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000`):

- `android:color/system_neutral1_*` (Derived from theme background/surface colors; controls SystemUI Quick Settings shade, notification background, Settings app surfaces)
- `android:color/system_neutral2_*` (Derived from theme surfaceVariant/border colors; controls secondary surfaces, cards, dividers)
- `android:color/system_accent1_*` (Derived from theme primary/accent color; controls active QS tiles, switches, sliders, primary buttons)
- `android:color/system_accent2_*` (Derived from theme secondary accent; controls tonal chips, secondary containers)
- `android:color/system_accent3_*` (Hue-shifted `+30°` accent; controls tertiary highlights)

[`PrivilegedHelperMain.applyOsThemeInternal()`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/privileged/PrivilegedHelperMain.kt#L815-L980) then commits this palette to Android's `OverlayManagerService`:
1. Disables any pre-existing `com.android.systemui:neutral` and `com.android.systemui:accent` overlays so they do not shadow the new palette, or replaces them directly when using the `2-arg` `FabricatedOverlay(String, String)` constructor.
2. Registers and enables `FabricatedOverlay("com.android.shell", "os_theme")` targeting package `"android"` under `userId = 0`.
3. Synchronizes system night mode via `IUiModeManager.setNightMode(isDark ? 2 : 1)` and `Settings.Secure["ui_night_mode"]`.
4. Updates `Settings.Secure["theme_customization_overlay_packages"]` with a fresh `_applied_timestamp` so SystemUI immediately reloads its dynamic resources without restarting.

---

## 8. Megingiard Cross-App Theme Provider Schema

When `AppThemeOption.SYNC_MEGINGIARD` (`100`) is active, [`SystemSettingsRepository.queryMegingiardProvider()`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/system/SystemSettingsRepository.kt#L1401-L1453) queries the standalone Megingiard application's `ContentProvider`:

- **Production URI**: `content://com.stormpanda.megingiard.provider/theme`
- **Debug URI (Fallback)**: `content://com.stormpanda.megingiard.debug.provider/theme`

| Cursor Column Name | Type | Example Value | Description |
| :--- | :---: | :--- | :--- |
| `theme_mode` | `String` | `"DARK_OLED"`, `"MJOLNIR"`, `"VALHALLA"` | Name of the active `ThemeMode` enum constant in Megingiard. |
| `accent_color` | `Long` | `0xFF3D5AFE` (`4282213118`) | Active 32-bit ARGB accent color selected in Megingiard. |
| `custom_accent_color` | `Long` *(optional)* | `0xFF00E5FF` | Custom HSV accent color saved in Megingiard. |
