# Contributing to Better Dual Screen Assistant

Thank you for your interest in **Better Dual Screen Assistant** for the **AYN Thor** and **Retroid Pocket Duo** dual-screen handhelds!

---

## TL;DR — Contribution Policy (Please Read First)

> [!IMPORTANT]
> **Pull Requests are ONLY accepted for open issues explicitly labeled `pr-welcome` by `@stormpanda`.**
>
> - **Why?** This repository is actively developed using agentic coding workflows and enforces strict dual-device hardware, multi-display, and `UID 0` invariants across AYN Thor and Retroid Pocket Duo. Reviewing, commenting on, and amending external PRs takes significantly more maintainer time than implementing changes directly.
> - **Questions ≠ PR Approval:** When the maintainer replies to an issue or asks clarifying questions, **that is for scoping — it is NOT an invitation to open a PR.** Please answer the question in the issue thread instead of submitting a PR.
> - **Uninvited PRs:** PRs that do not link an open `pr-welcome` issue are automatically flagged with `needs-pr-welcome` by our PR policy check, are not guaranteed review, and may be closed without merge.

### Highest-Value Ways to Contribute (No PR Needed!)

1. **Report Bugs with Diagnostics:** Open a [Bug Report](https://github.com/stormpanda/better-dual-screen-assistant/issues/new?template=bug_report.yml) with your device model, firmware/Android version, reproduction steps, and `adb logcat` output.
2. **Propose Features & Answer Scoping Questions:** Open a [Feature Request](https://github.com/stormpanda/better-dual-screen-assistant/issues/new?template=feature_request.yml) with clear behavior and edge cases, and reply directly when follow-up questions are asked.
3. **Hardware Testing & Device Onboarding:** Help verify builds on hardware or capture device diagnostics for new dual-screen handhelds using [`docs/devices/DEVICE_PROFILE_TEMPLATE.md`](docs/devices/DEVICE_PROFILE_TEMPLATE.md).
4. **Build Standalone Third-Party Plugins:** Want to add a custom Quick Setting tile, live timer, or picker dialog without modifying this repo? Build an independent plugin using our Zero-SDK [Third-Party Plugin API](docs/PLUGIN_API.md) (see [bdsa-plugin-api-demo](https://github.com/stormpanda/bdsa-plugin-api-demo)).

---

## Important: Licensing Policy & Code Usage

Better Dual Screen Assistant is a proprietary, source-available project. It is **not** an open-source project under the Open Source Initiative (OSI) definition.

Please note the following terms regarding the code and how you are permitted to work with it:

1. **License Agreement:** All contributions (including code, documentation, graphics, and issues) you submit will be governed by the [LICENSE](LICENSE) terms.
2. **Section 3 Agreement:** In accordance with **Section 3 (Contributions)** of our license, by opening a Pull Request or submitting modifications, you grant the Copyright Holder a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license to use, reproduce, modify, display, sublicense, and distribute your contribution.
3. **No Financial Compensation:** Contributions are completely voluntary and do not entitle the contributor to financial compensation, royalties, or ownership claims over the software.
4. **Source Code Modifications & Public Forks:**
   - You are permitted to modify the source code for your own personal, non-commercial use on hardware you personally own.
   - You may publish your modified source code in a public fork of the official repository on GitHub (or other hosting platform) **solely** for the purpose of preparing and submitting a Pull Request directly to the official upstream Better Dual Screen Assistant repository.
   - Publishing or sharing modified source code for any other reason is strictly prohibited.
5. **No Binary/APK Distribution:**
   - You are **strictly prohibited** from compiling and distributing binaries (such as APKs, DEX files, or native libraries) of modified versions of this software.
   - You may not share custom-compiled builds with anyone else for any reason.
   - **Exception:** You are permitted to share compiled test binaries privately and directly with the original repository maintainer solely for review and testing. Custom binaries must **never** be uploaded or attached publicly (such as directly in GitHub PR comments or issues).
6. **Personal Use Limits:** If you want to use custom modifications long-term, you must compile the code yourself and run it strictly for your own personal, non-commercial use. You may not distribute that build to other users.

---

## AI Agentic Development Workflow (Google Antigravity & Gemini)

The Better Dual Screen Assistant codebase and its development workflows are heavily optimized for **Google Antigravity** using **Gemini models**. The repository contains specific configurations, system prompts, rules, and scripts designed to enable seamless pair-programming with AI coding agents.

### Agent Rules & Custom Skills
* **[AGENTS.md](AGENTS.md):** Defines the mandatory first-time contributor check (`.bdsa-contrib-notice-shown`), build/ADB rules, and all multi-display and `UID 0` architectural invariants. (`CLAUDE.md` and `.github/copilot-instructions.md` also point here.)
* **[.agents/skills/](.agents/skills):** Workspace-level release and changelog skills (`bdsa-release`, `bdsa-release-changelog`).

> [!NOTE]
> If you are using an AI coding agent on a `pr-welcome` issue (or for personal builds), ensure it reads [AGENTS.md](AGENTS.md) before modifying code. Unaligned AI-generated PRs that violate hardware or UI invariants create heavy review overhead and will be declined.

---

## Code Guidelines & Hardware Invariants

If you are working on an approved `pr-welcome` issue (or a personal build), adhere to the standards in [AGENTS.md](AGENTS.md):

### 1. Architectural Integrity
Better Dual Screen Assistant is organized into clear package responsibilities within `:app`:
* **`com.stormpanda.betterdualscreenassistant` (App Entry & Companion Settings):** `BetterDualScreenApp` and the gamepad-first Top-Screen Companion Settings App on `displayId = 0` (`MainActivity` / `BetterDualScreenSettingsScreen`).
* **`com.stormpanda.betterdualscreenassistant.overlay` (Bottom-Screen Overlay Window Layer):** Display-context window lifecycle and blanking overlays on `profile.bottomDisplayId` (`ScreenOverlayManager`).
* **`com.stormpanda.betterdualscreenassistant.ui` (Overlay UI, Custom Canvas Widgets & Theming Layer):** Bottom-screen overlay UI (`ui.overlay`: `AssistantOverlayScreen`, `OverlayDialogs`), custom non-Material canvas widgets (`ui.components`: `TccWidgets`), status bar (`ui.statusbar`: `StatusBarRow`), and canonical `720dp` viewport, look specs, and Monet `FabricatedOverlay` palette builders (`ui.theme`: `TccLookSpec`, `TccTheme`) preserving 1:1 visual and dimensional parity across **AYN Smooth** and **Retroid Pixel** looks.
* **`com.stormpanda.betterdualscreenassistant.privileged` (Privileged Execution, Recording & Watchdog Layer):** 3-tier privileged execution model (`PrivilegedBridge` -> `PrivilegedHelperMain` `UID 0` daemon -> `PServerClient` root service), Android 13–16 Monet OS theme overlay injection/cleanup, hardware screen recording (`ScreenRecorderEngine`), ephemeral stock `DualScreenAssistant` RAM suppression (`SIGSTOP`), and `IBinder.DeathRecipient` uninstall/force-stop recovery.
* **`com.stormpanda.betterdualscreenassistant.system` (Hardware & System Integration Layer):** `DeviceProfile` hardware abstraction, `Settings.System`/`Global`/`Secure` state repository (`SystemSettingsRepository`), `DirectBootPrefs`, `QuickSettingTileId`, sysfs/FPS telemetry (`TelemetryMonitor`), and vendor Binder services (`VendorServices`: `IFanControlProvider`, `IOverviewProxy` / `ILauncherProxy`, `IServerApi`).
* **`com.stormpanda.betterdualscreenassistant.plugin` (Zero-SDK Plugin API Layer):** Manifest/XML discovery, icon loading, and `ContentProvider`/broadcast IPC for standalone third-party plugins (`BdsaPluginContract`, `PluginRegistry`, `BdsaPluginProvider`, `PluginIconLoader`, `PluginModels`).
* **`com.stormpanda.betterdualscreenassistant.service` & `.receiver` (Lifecycle & Input State Machine):** Persistent foreground service (`AssistantForegroundService`), physical AYN / Dual Screen (`TCC`) button 3-state machine, and broadcast receivers (`TccKeyReceiver`, `BootReceiver`).
* **`com.stormpanda.megingiard` (Shared Design System & Utilities):** Gamepad-first design system and 2D controller focus navigation (`GamepadScaffold`, `GamepadLayoutContainers`, `GamepadKeyHandlers`, `AppTheme`, `AppPalettes`), logging facade (`AppLog`), and GitHub/Obtainium update checker (`UpdateManager`).

### 2. Dual-Device & Privileged Hardware Invariants
* **Dual-Device Support:** Preserve behavior across both **AYN Thor** (`displayId = 4`, `1240×1080`, Android 13 / SDK 33, `com.odin.dualscreen.assistant`, OLED layer + `-1.0f` brightness blanking) and **Retroid Pocket Duo** (`displayId = 2`, `1280×960`, Android 16 / SDK 36, `com.retroidpocket.dualscreen.assistant`, kernel `panel{0,1}-backlight/bl_power` blanking) via `DeviceProfile`.
* **Bottom-Display Overlay Sizing & Windowing:** Obtain the `WindowManager` from `context.createDisplayContext(bottomDisplay)` with explicit pixel dimensions (`display.getRealSize()`), `Gravity.CENTER`, and privileged window type `2021` (`PRIVILEGED_OVERLAY_WINDOW_TYPE`, with `TYPE_APPLICATION_OVERLAY` `2038` fallback). **Never** use `FLAG_LAYOUT_NO_LIMITS` on the bottom display.
* **Canonical Viewport & Custom Canvas Parity:** Render bottom-screen overlay UI inside `OverlayViewport` (canonical `720dp` short-edge coordinate space) driven by `LocalTccLookSpec` (`AynLookSpec` / `RetroidLookSpec` / `RetroidReadableLookSpec`) and custom canvas widgets in `TccWidgets.kt` rather than substituting standard Material 3 sliders or cards.
* **Safe Ephemeral Stock Suppression:** Never leave `com.odin.dualscreen.assistant`, `com.retroidpocket.dualscreen.assistant`, or `.services.TccServices` permanently `pm disable`d on disk. Always follow the ephemeral `SIGSTOP` sequence documented in [AGENTS.md](AGENTS.md) and preserve automatic recovery in `PrivilegedHelperMain` upon uninstall, disable, opt-out, or force-stop.
* **Physical TCC Button State Machine:** Maintain strict 3-state handling (key-down no-op, long-press screen blank/toggle, key-up wake or `200ms` debounced overlay toggle) in `AssistantForegroundService` and single-receiver delegation in `TccKeyReceiver`.

### 3. Kotlin & Compose Conventions
* **Kotlin 2.0+:** Use `enum.entries` (never `enum.values()`) and `kotlin.math.min`/`max` (never `java.lang.Math.*`).
* **Imports:** Always use explicit, fully-qualified imports. **Do not use wildcard/star imports** (`import foo.*`).
* **Constants:** Extract magic numbers, system setting keys, and sysfs paths to well-named `SCREAMING_SNAKE_CASE` constants.

### 4. Logging Guidelines
* Route shared component logging through `com.stormpanda.megingiard.AppLog` or use consistent component tags (`AssistantFgService`, `ScreenOverlayManager`, `PrivilegedBridge`, `PrivilegedHelperMain`, `PServerClient`, `ScreenRecorderEngine`, `PluginRegistry`, `FanControlClient`, `OverviewProxyClient`, `MappingServiceClient`, `UpdateManager`).
* Add informative logs at major lifecycle milestones, privileged Binder transactions, state mutations, and error fallback pathways.

---

## Submitting a Pull Request (Only for `pr-welcome` Issues)

> [!CAUTION]
> Do **not** fork and open a Pull Request unless an open GitHub issue already has the **`pr-welcome`** label applied by `@stormpanda`. Uninvited PRs will automatically receive the `needs-pr-welcome` label from `.github/workflows/pr-gate.yml` and may be closed without review.

### Step 1: Confirm `pr-welcome` Approval
Verify that the target issue is **open** and carries the **`pr-welcome`** label applied by `@stormpanda`.

### Step 2: Fork and Branch
1. Fork the official Better Dual Screen Assistant repository.
2. Clone your fork locally.
3. Create a descriptive branch from `main`:
   ```bash
   git checkout -b feature/my-approved-feature
   # or
   git checkout -b fix/my-approved-fix
   ```

### Step 3: Implement, Synchronize, and Verify
1. **Sync with Upstream:** Ensure your branch is rebased onto or merged with the latest upstream `main`:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```
2. **Implement:** Follow [AGENTS.md](AGENTS.md) and keep changes strictly scoped to the `pr-welcome` issue.
3. **Mandatory Testing & Build Verification:**
   ```bash
   # Run all unit tests
   ./gradlew testDebugUnitTest

   # Build debug APK
   ./scripts/debug.sh build
   ```
4. **Mandatory Documentation Sync:**
   - If your changes affect user-facing features, Quick Setting tiles, or Companion Settings options, update [`docs/FUNCTIONAL_SPECIFICATION.md`](docs/FUNCTIONAL_SPECIFICATION.md), [`docs/UI_AND_THEMING.md`](docs/UI_AND_THEMING.md), and [`README.md`](README.md).
   - If your changes affect privileged execution, system settings keys, vendor Binder calls, device profiles, or security properties, update [`docs/TECHNICAL_ARCHITECTURE.md`](docs/TECHNICAL_ARCHITECTURE.md), [`docs/SYSTEM_INTEGRATION_REFERENCE.md`](docs/SYSTEM_INTEGRATION_REFERENCE.md), [`docs/SECURITY.md`](docs/SECURITY.md), and the relevant [`docs/devices/`](docs/devices/) doc.
5. **Mandatory Real-Hardware Verification:** All code changes **must** be tested on real physical hardware on **at least one of the supported devices** (**AYN Thor** or **Retroid Pocket Duo**) before submitting a PR. Untested or emulator-only PRs will not be reviewed.

### Step 4: Open the Pull Request
1. Commit changes using [Conventional Commits](https://www.conventionalcommits.org/).
2. Push your branch to your fork and open a PR against `main`.
3. Link the approved issue in the PR body (e.g., `Closes #<issue-number>`) so the automated `PR Policy Gate` passes and does not flag the PR with `needs-pr-welcome`.
4. **Never** attach compiled APKs or binaries publicly in GitHub PRs or issues; if a test build is requested by the maintainer, arrange to share it privately.
