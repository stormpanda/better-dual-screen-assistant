# UI Architecture, Custom Widgets & Theming

This document specifies the visual layout geometry, custom non-Material canvas widgets, theme token system, and gamepad-first UI components used across **Better Dual Screen Assistant**.

---

## Table of Contents

1. [Bottom-Screen Overlay Geometry (`840dp × 732dp`)](#1-bottom-screen-overlay-geometry-840dp--732dp)
   - [1.1 Vertical Stack & Fixed Heights](#11-vertical-stack--fixed-heights)
   - [1.2 Mode Tab (`Tab 0`) Layout Proportions](#12-mode-tab-tab-0-layout-proportions)
   - [1.3 Settings Tab (`Tab 1`) Layout Proportions](#13-settings-tab-tab-1-layout-proportions)
2. [Custom Non-Material Canvas & Touch Widgets (`TccWidgets.kt`)](#2-custom-non-material-canvas--touch-widgets-tccwidgetskt)
   - [2.1 `VerticalProgressBar` & `TccSliderRow`](#21-verticalprogressbar--tccsliderrow)
   - [2.2 `TccButton` (Dual-Mode Action & Tile Button)](#22-tccbutton-dual-mode-action--tile-button)
   - [2.3 `CurrentFrameRateDisplay` (Native Canvas FPS Readout)](#23-currentframeratedisplay-native-canvas-fps-readout)
   - [2.4 `HardwareRingGauge` (270° Circular Telemetry Gauge)](#24-hardwareringgauge-270-circular-telemetry-gauge)
   - [2.5 `BatteryIconCanvas` (Status Bar Battery & Charging Bolt)](#25-batteryiconcanvas-status-bar-battery--charging-bolt)
3. [Theme Architecture (`TccTheme.kt` & `AppPalettes.kt`)](#3-theme-architecture-tccthemekt--apppaletteskt)
   - [3.1 Complete `AppThemeOption` Catalogue (12 Options)](#31-complete-appthemeoption-catalogue-12-options)
   - [3.2 `TccThemeColors` Token Mapping](#32-tccthemecolors-token-mapping)
   - [3.3 Vector Drawable Tinting (`tccIconTint`)](#33-vector-drawable-tinting-tccicontint)
4. [Embedded Megingiard Gamepad UI System (`com.stormpanda.megingiard`)](#4-embedded-megingiard-gamepad-ui-system-comstormpandamegingiard)
   - [4.1 `GamepadTwoPaneScaffold` & `GamepadDeck`](#41-gamepadtwopanescaffold--gamepaddeck)
   - [4.2 `PrimaryOverlayInputBridge` (Controller Input & Repeat Engine)](#42-primaryoverlayinputbridge-controller-input--repeat-engine)
   - [4.3 Interactive `QuickTogglesVisualReorderCard`](#43-interactive-quicktogglesvisualreordercard)

---

## 1. Bottom-Screen Overlay Geometry (`840dp × 732dp`)

The AYN Thor's bottom screen (`displayId = 4`) has a physical resolution of `1240 × 1080` at `369 dpi` (~`840dp × 732dp`). To preserve 1:1 visual parity with the stock `DualScreenAssistant`, all dimensions in [`AssistantOverlayScreen.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/ui/overlay/AssistantOverlayScreen.kt) use the stock `@dimen/dp_*` resource table (`app/src/main/res/values/dimens.xml`).

### 1.1 Vertical Stack & Fixed Heights

```
┌──────────────────────────────────────────────────────────────────────────┐
│ StatusBarRow (height = @dimen/dp_44, paddingHorizontal = @dimen/dp_12)  │
├──────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ Main Panel Card (height = @dimen/dp_578, radius = @dimen/dp_16)      │ │
│ │ paddingHorizontal = @dimen/dp_15, paddingTop = @dimen/dp_8           │ │
│ │                                                                      │ │
│ │   [ Tab 0: ScreenControlTab ]  OR  [ Tab 1: SystemSettingsTab ]      │ │
│ │                                                                      │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────────┤
│      ┌────────────────────────────────────────────────────────────┐      │
│      │ NavigationBarSection Pill (height = @dimen/dp_80)          │      │
│      │ [ Mode ]      [ Task ]      [ Settings ]      [ Edit* ]    │      │
│      └────────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Mode Tab (`Tab 0`) Layout Proportions

Inside the `578dp` Main Panel Card (`padding = 15dp`), `ScreenControlTab` arranges three sub-cards (`TccPanelCard`, corner radius `@dimen/dp_16`):
- **Top Row (`height = @dimen/dp_282`)**:
  - **Screen Mode Card** (`width = 100%`): Contains 3 (or 4 with **Swap Screens**) horizontal `TccButton` cards (`height = @dimen/dp_252`, spaced by `@dimen/dp_15`).
- **Bottom Row (`height = @dimen/dp_281`, top spacer `@dimen/dp_15`)**:
  - **Left (`weight = 1f`, right spacer `@dimen/dp_15`)**: **Brightness Sliders Card** containing 3 (or 4 with **Stick LED**) horizontal `TccSliderRow` bars (`height = @dimen/dp_70` when 3 sliders, or `@dimen/dp_52` when 4 sliders).
  - **Right (`weight = 1f`)**: **Volume Sliders Card** containing 3 horizontal `TccSliderRow` bars (`height = @dimen/dp_70`).

### 1.3 Settings Tab (`Tab 1`) Layout Proportions

Inside the `578dp` Main Panel Card (`padding = 15dp`), `SystemSettingsTab` uses weighted rows and columns:
- **Top Row (`weight = 7f`, `70%` height)**:
  - **Top-Left (`weight = 4f`, `40%` width)**: `FrameRatePanelCard` containing the `60 FPS MODE` / `120 FPS MODE` pill button (`171dp × 48dp`), `CurrentFrameRateDisplay` (`150dp × 106dp`), and `CURRENT FPS` caption.
  - **Top-Right (`weight = 6f`, `60%` width, left spacer `@dimen/dp_15`)**: Quick Settings `TccPanelCard` hosting a `HorizontalPager` with a `2 × 4` grid of `TccButton` tiles (spaced by `@dimen/dp_15`) and `6dp` circular page indicator dots (`18dp` container height).
- **Bottom Row (`weight = 3f`, `30%` height, top spacer `@dimen/dp_15`)**:
  - **TEMP Card** (`weight = 2f`, `20%` width)
  - **FAN Card** (`weight = 2f`, `20%` width, left spacer `@dimen/dp_15`)
  - **4 Ring Gauges Card** (`weight = 6f`, `60%` width, left spacer `@dimen/dp_15`): `CPU`, `GPU`, `PWR`, and `RAM` `HardwareRingGauge`s arranged in an evenly spaced row.

---

## 2. Custom Non-Material Canvas & Touch Widgets (`TccWidgets.kt`)

Standard Material 3 sliders, cards, and progress indicators are intentionally **not** used in the bottom-screen overlay. Instead, [`TccWidgets.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/ui/components/TccWidgets.kt) faithfully re-implements every custom view from the stock decompiled APK (`VerticalProgressBar`, `CustomSeekBar`, `CurrentFrameRateView`, `PercentageRingView`).

### 2.1 `VerticalProgressBar` & `TccSliderRow`

Replicates `com.odin.dualscreen.assistant.view.VerticalProgressBar` + `CustomSeekBar`:
- **Rendering**: Draws a rounded background track (`cornerRadius = 12dp` / `@dimen/dp_12`) in `colors.sliderTrack`, clips to the rounded path, and draws a left-to-right progress fill rect (`width * (progress / max)`) in `colors.sliderFill` (or `alpha = 0.38f` when `isActive == false`, such as when Dual Brightness/Volume is unlinked or Ambient Light is off).
- **Touch Handling (`awaitEachGesture`)**:
  - Tracks horizontal pointer drag across the full bar width (`0..100`).
  - Calls `requestDisallowInterceptTouchEvent(true)` on the underlying Android `ViewParent` so parent scroll/pager containers never steal slider drags.
  - Emits `onValueChange(pct)` continuously during drag and `onValueChangeFinished(pct)` on pointer release (`up` or cancellation).
- **Overlay Content**: Renders the mode icon (`28dp`) on the left (`16dp` start padding) and the numeric percentage (`18sp`) on the right (`16dp` end padding).

### 2.2 `TccButton` (Dual-Mode Action & Tile Button)

Used for both the large Screen Mode buttons (`iconSize = 36dp`, `textSize = 18sp`, `cornerRadius = 12dp`) and the `2 × 4` Quick Setting tiles (`iconSize = 28dp`, `textSize = 11sp`, `cornerRadius = 8dp`):
- **State Colors**:
  - **Selected (`isSelected = true`)**: Background `colors.buttonSelectedBackground`, content/icon tint `colors.buttonSelectedContent`.
  - **Pressed (`isPressed = true`)**: Background `colors.buttonPressedBackground`, content/icon tint `colors.buttonPressedContent`.
  - **Normal**: Background `colors.buttonUnselectedBackground`, content/icon tint `colors.buttonUnselectedContent`.
- **Border Accent**: In Megingiard themes (`colors.cardBorder != Transparent`), draws a `1dp` border around the button (`colors.buttonSelectedBackground` when active, `colors.cardBorder` when inactive).

### 2.3 `CurrentFrameRateDisplay` (Native Canvas FPS Readout)

Replicates `com.odin.dualscreen.assistant.view.CurrentFrameRateView`:
- Uses `drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(...) }` with an Android `Paint` configured with `Typeface.DEFAULT_BOLD` and `textSize = 95sp` (`@dimen/sp_95`), centered horizontally and baseline-aligned via `Paint.FontMetrics`.

### 2.4 `HardwareRingGauge` (270° Circular Telemetry Gauge)

Replicates `com.odin.dualscreen.assistant.view.PercentageRingView`:
- **Geometry**: Draws a `270°` open circular arc starting at `135°` (bottom-left) and sweeping clockwise to `45°` (bottom-right) with `StrokeCap.Round` and stroke width `16f * (size / 200f)`.
- **Animation**: Animates percentage transitions over `300ms` using `FastOutSlowInEasing` (matching stock `DecelerateInterpolator`).
- **Bidirectional Power Support (`minValue = -20f, maxValue = 25f`)**: For the `PWR` gauge, splits the `270°` sweep at the `0W` origin angle so negative values (charging) sweep counter-clockwise while positive values (discharging) sweep clockwise.
- **Center Typography**: Draws the primary metric label (e.g. `2.8GHz`, `680MHz`, `4.2W`, `6.1GB`) centered inside the ring, and the gauge title (`CPU`, `GPU`, `PWR`, `RAM`) in the bottom `90°` gap of the ring.

### 2.5 `BatteryIconCanvas` (Status Bar Battery & Charging Bolt)

Replicates `com.odin.dualscreen.assistant.view.BatteryView` in [`StatusBarRow.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/ui/statusbar/StatusBarRow.kt#L329-L414):
- Uses a `24×24` virtual viewport rotated `-90°` around `(12, 12)` so the vertical battery specification renders horizontally.
- Draws the positive battery terminal cap, rounded outer frame (`strokeWidth = 1.5`), and inner fill rect scaled by `level / 100f`.
- **Low-Battery Color Interpolation**: When `level < 50%` (and not charging), interpolates the fill color linearly toward alert red (`#F44336`) using `lerp(Color(0xFFF44336), normalColor, level / 50f)`.
- **Charging Lightning Bolt**: When `isCharging == true`, draws a 6-vertex vector lightning polygon filled with `colors.lightning` (`#FCE922`).

---

## 3. Theme Architecture (`TccTheme.kt` & `AppPalettes.kt`)

Defined in [`TccTheme.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/ui/theme/TccTheme.kt) and [`AppPalettes.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/megingiard/ui/AppPalettes.kt).

### 3.1 Complete `AppThemeOption` Catalogue (12 Options)

| Enum Constant | Persisted `id` | Display Name | Supports Custom Accent | Category |
| :--- | :---: | :--- | :---: | :--- |
| `STOCK_SYSTEM` | `1` | `Follow System (Stock)` | No | Stock AYN |
| `STOCK_LIGHT` | `3` | `Light (Stock)` | No | Stock AYN |
| `STOCK_DARK` | `4` | `Dark (Stock)` | No | Stock AYN |
| `SYNC_MEGINGIARD` | `100` | `Sync with Megingiard` | Via Megingiard | Megingiard Sync *(only when installed)* |
| `MEGINGIARD_DARK` | `101` | `Dark` | **Yes** | Custom Palette |
| `MEGINGIARD_DARK_OLED` | `102` | `Dark OLED` | **Yes** | Custom Palette |
| `MEGINGIARD` | `103` | `Norse Forest` | No | Custom Palette |
| `MJOLNIR` | `104` | `Mjölnir Steel` | No | Custom Palette |
| `VALHALLA` | `105` | `Valhalla Sunset` | No | Custom Palette |
| `AURORA` | `106` | `Aurora Borealis` | No | Custom Palette |
| `RETRO_PHOSPHOR` | `107` | `Retro Phosphor` | No | Custom Palette |
| `ROYAL_ASGARD` | `108` | `Royal Asgard` | No | Custom Palette |

*(Note: Legacy stock ID `2` is automatically migrated to `1` (`STOCK_SYSTEM`) in `AppThemeOption.fromId()`).*

### 3.2 `TccThemeColors` Token Mapping

| Token | Stock Dark (`id = 4`) | Stock Light (`id = 3`) | Megingiard Palette Derivation (`tccColorsFromMegingiard`) |
| :--- | :--- | :--- | :--- |
| `overlayBackdrop` | `#BF000000` (`75%` black) | `#BF000000` (`75%` black) | `palette.background.copy(alpha = 0.88f)` (`0.94f` on OLED) |
| `panelBackground` | `#FF222222` | `#FFE0E0E0` | `palette.background` |
| `cardBackground` | `#FF2D2D2D` | `#FFF5F5F5` | `palette.surface` |
| `cardBorder` | `Transparent` | `Transparent` | `palette.border` (`1dp` stroke) |
| `buttonUnselectedBackground` | `#FF575757` | `#FFFFFFFF` | `lerp(palette.surfaceVariant, palette.primary, 0.08f)` |
| `buttonSelectedBackground` | `#FFFFFFFF` | `#FF474747` | `palette.primary` |
| `buttonSelectedContent` | `#FF2E2E2E` | `#FFF2F2F2` | `palette.onPrimary` |
| `sliderTrack` | `#FF575757` | `#FFFFFFFF` | `lerp(palette.surfaceVariant, palette.primary, 0.08f)` |
| `sliderFill` | `#FFFFFFFF` | `#FF474747` | `palette.primary` |
| `cpuRing` / `gpuRing` / `pwrRing` / `ramRing` | `#EE188C` / `#70E7FF` / `#E26E01` / `#76CA40` | Same as Stock Dark | Harmonized with `palette.primary` and `palette.accent` |

### 3.3 Vector Drawable Tinting (`tccIconTint`)

Stock vector drawables (`ic_mode_dual`, `ic_brightness_top`, `ic_volume_bottom`, etc.) are authored with white `#FFFFFFFF` fills for dark mode. Rather than duplicating every XML drawable for light mode and custom palettes, [`tccIconTint(color)`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/ui/theme/TccTheme.kt#L604) applies `ColorFilter.tint(color, BlendMode.SrcIn)` so every icon adapts dynamically to Stock Dark, Stock Light, and all 8 Megingiard palettes.

---

## 4. Embedded Megingiard Gamepad UI System (`com.stormpanda.megingiard`)

The top-screen Companion App (`MainActivity`) is built on the **Megingiard** gamepad design system (`app/src/main/java/com/stormpanda/megingiard/ui/`).

### 4.1 `GamepadTwoPaneScaffold` & `GamepadDeck`

[`GamepadTwoPaneScaffold`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt) structures the screen into:
- **Left Sidebar (`180dp` width)**: Vertical list of `GamepadSidebarCategory` items. Shows a `3dp × 18dp` vertical accent indicator bar when selected, and a `2dp` primary border + subtle scale (`1.02f`) when focused by the gamepad.
- **Right Content Pane (`weight = 1f`)**:
  - **Breadcrumb Header**: Displays `Category` or `Category  ›  Sub-Page` with a touch/clickable Back pill when inside a sub-page.
  - **`GamepadDeck`**: Scrollable vertical column of settings cards where each item is wrapped in a `FocusBringIntoViewWrapper` (`onFocusedBoundsChanged` + `scrollTo`) so D-pad navigation automatically keeps the focused card comfortably inside the viewport with `40dp` top/bottom padding.

### 4.2 `PrimaryOverlayInputBridge` (Controller Input & Repeat Engine)

[`PrimaryOverlayInputBridge`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt) wraps the root view's hardware key and generic motion events:
- **D-Pad & Left Analog Stick**: Converts stick deflection (`deadzone = 0.5f`) and D-pad presses into `FocusDirection.{Up, Down, Left, Right}` with an initial repeat delay of `320ms` and repeat interval of `95ms`.
- **`L2` Fine-Step Trigger (`LocalFineStepActive`)**: Tracks `KEYCODE_BUTTON_L2` and `MotionEvent.AXIS_LTRIGGER` (`> 0.5f`) via a `CompositionLocal` so focused sliders (`Hue`, `Saturation`, `Brightness`) automatically switch from coarse (`5°` / `5%`) steps to fine (`1°` / `1%`) steps while holding `L2`.

### 4.3 Interactive `QuickTogglesVisualReorderCard`

Implemented in [`MainActivity.kt`](file:///Users/maikthomalla/Development/better-dual-screen-assistant/app/src/main/java/com/stormpanda/betterdualscreenassistant/MainActivity.kt#L1125-L1579), this component embeds an authentic `470dp × 246dp` interactive preview of the bottom-screen Quick Settings panel inside the top-screen settings deck:
- **Dual Input Model**:
  - **Touch**: Tap any tile (`selectedTileForSwap`) to highlight it, then tap any second tile to swap their positions in `qs_tile_order`.
  - **Controller**: Press `A` on the card to enter `isGridActive` mode -> navigate slots with D-Pad or `L1`/`R1` -> press `A` to toggle `isGrabbing = true` -> moving cursor with D-Pad/`L1`/`R1` immediately calls `repository.moveQuickSettingTile(tile, targetIndex)` so both the top-screen preview and bottom-screen live overlay reorder in real time -> press `A` or `B` to drop.
