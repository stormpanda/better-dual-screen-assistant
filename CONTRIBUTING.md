# Contributing to Better Dual Screen Assistant

First of all, thank you for taking the time to contribute to Better Dual Screen Assistant! We are excited to collaborate with the community to build a state-of-the-art dual-screen assistant and system control experience for the **AYN Thor** Android handheld.

By contributing to this repository, you help make Better Dual Screen Assistant more robust and powerful for everyone.

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

### Custom Skills & Automated Workflows
We define custom workspace-level agentic instructions and automation scripts inside the [.agents/skills/](.agents/skills) directory. These are structured as "skills" that can be executed to automate or verify work:
* `bdsa-release`: Orchestrates the Better Dual Screen Assistant release lifecycle (creating `release/X.Y.Z` branches, setting release versions, building signed release APKs, generating SHA-256 checksums, compiling changelogs, uploading GitHub release drafts, merging back to `main`, and bumping development snapshot versions).
* `bdsa-release-changelog`: Compares git tags or commit ranges and generates structured release changelogs in GitHub Markdown format.

### Recommending Google Antigravity & Gemini
We **strongly recommend** that all contributors adopt Google Antigravity paired with Gemini models for developing contributions in this codebase.
> [!IMPORTANT]
> Because our architectural invariants, multi-display windowing rules, privileged execution safeguards, and coding conventions are strictly enforced via the `.agents/` workflows and [AGENTS.md](AGENTS.md) rules, contributions developed using alternative tooling may produce significant code alignment and style review overhead. To avoid delays in reviewing and merging your Pull Requests, aligning your agentic stack with the project's native Antigravity setup is highly encouraged.

---

## Code Guidelines & Conventions

To maintain codebase health, hardware safety, and high execution performance, please adhere to the following standards defined in [AGENTS.md](AGENTS.md):

### 1. Architectural Integrity
Better Dual Screen Assistant is organized into clear package responsibilities within `:app`:
* **`com.stormpanda.betterdualscreenassistant.overlay` (Bottom-Screen Overlay Layer):** Window lifecycle on `displayId = 4` (`ScreenOverlayManager`) and custom non-Material canvas UI (`AssistantOverlayScreen`, `TccWidgets`, `StatusBarRow`) preserving 1:1 visual and dimensional parity (`840dp × 732dp`, `1240×1080`) with the stock assistant.
* **`com.stormpanda.betterdualscreenassistant.ui` (Top-Screen Companion & Theming Layer):** Gamepad-first Companion Settings App on `displayId = 0` (`CompanionSettingsScreen`), modal dialogs, theme palettes (`AppTheme`), and Android 13 Monet OS theme injection (`OsThemeOverlayManager`).
* **`com.stormpanda.betterdualscreenassistant.privileged` (Privileged Execution & Watchdog Layer):** 3-tier privileged execution model (`PrivilegedBridge` -> `PrivilegedHelperMain` `UID 0` Binder daemon -> `PServerClient` root service), ephemeral stock `DualScreenAssistant` RAM suppression (`SIGSTOP`), and `IBinder.DeathRecipient` uninstall/force-stop recovery.
* **`com.stormpanda.betterdualscreenassistant.system` (Hardware & System Integration Layer):** `Settings.System`/`Global`/`Secure` state repository (`SystemSettingsRepository`), sysfs hardware telemetry, and vendor Binder services (`VendorServices`: `IFanControlProvider`, `IOverviewProxy`, `IMappingService`).
* **`com.stormpanda.betterdualscreenassistant.service` & `.receiver` (Lifecycle & Input State Machine):** Persistent foreground service (`AssistantForegroundService`), physical AYN/TCC button 3-state machine, and broadcast receivers (`TccKeyReceiver`, `BootReceiver`).
* **`com.stormpanda.megingiard` (Shared Design System & Utilities):** Gamepad-first design system, 2D controller focus navigation (`SettingsFocusArea`), logging facade (`AppLog`), and GitHub/Obtainium update checker (`UpdateRepository`).

### 2. Multi-Display & Privileged Hardware Invariants
* **Display 4 Overlay Sizing:** Always obtain the `WindowManager` from `context.createDisplayContext(display4)` with explicit pixel dimensions (`1240×1080` via `display.getRealSize()`), `Gravity.CENTER`, and `TYPE_APPLICATION_OVERLAY`. **Never** use `FLAG_LAYOUT_NO_LIMITS` on `displayId = 4`.
* **Safe Ephemeral Stock Suppression:** Never leave `com.odin.dualscreen.assistant` or `.services.TccServices` permanently `pm disable`d on disk. Always follow the ephemeral `SIGSTOP` sequence documented in [AGENTS.md](AGENTS.md) and preserve automatic recovery in `PrivilegedHelperMain` upon uninstall or force-stop.
* **Physical AYN Button State Machine:** Maintain strict 3-state handling (`onTccKeyDown` no-op, `onTccKeyLongPress` screen blanking, `onTccKeyShortPressUp` wake or `200ms` debounced overlay toggle) in `AssistantForegroundService` and single-receiver delegation in `TccKeyReceiver`.
* **Bottom-Screen UI Parity:** Maintain the exact vertical heights (`44dp` status bar, `578dp` main panel card, `80dp` bottom navigation bar pill) and custom canvas widgets in `TccWidgets.kt` rather than substituting standard Material 3 sliders or cards on the bottom-screen overlay.

### 3. Kotlin & Compose Conventions
* **Kotlin 2.0+:** Write modern Kotlin. Use `enum.entries` (never `enum.values()`). Use `kotlin.math.min`/`max` (never `java.lang.Math.*`).
* **Imports:** Always use explicit, fully-qualified imports. **Do not use wildcard/star imports** (`import foo.*`).
* **Constants:** Extract magic numbers, system setting keys, and sysfs paths to well-named `SCREAMING_SNAKE_CASE` constants.

### 4. Logging Guidelines
* Route shared component logging through `com.stormpanda.megingiard.AppLog` or use consistent component tags (`BetterDSAApp`, `AssistantFgService`, `ScreenOverlayManager`, `SystemSettingsRepo`, `PrivilegedBridge`, `PrivilegedHelperMain`, `PServerClient`).
* Add informative logs at major lifecycle milestones, privileged Binder transactions, state mutations, and error fallback pathways.

---

## How to Contribute

### Step 1: Open an Issue First
**You must open a GitHub issue describing your proposed change and receive explicit approval/feedback from the maintainer before starting work.** This prevents duplicate work, ensures your proposal aligns with the architectural design, and guarantees your PR will be considered.

### Step 2: Fork and Branch
1. Fork the official Better Dual Screen Assistant repository.
2. Clone your fork locally.
3. Create a new descriptive branch from `main`:
   ```bash
   git checkout -b feature/my-amazing-feature
   # or
   git checkout -b bugfix/fix-overlay-animation
   ```

### Step 3: Implement, Synchronize, and Verify
1. **Sync with Upstream:** Before implementing and before submitting a PR, ensure your branch is updated to the latest commit on the upstream `main` branch:
   ```bash
   git fetch upstream
   git rebase upstream/main
   # or merge
   git merge upstream/main
   ```
2. **Implement:** Write your code following the guidelines in [AGENTS.md](AGENTS.md).
3. **Mandatory Testing & Build Verification:** Run the unit tests and verify a clean build locally before submitting any contribution to ensure there are no regressions:
   ```bash
   # Run all unit tests
   ./gradlew testDebugUnitTest

   # Build debug APK
   ./scripts/debug.sh build
   ```
4. **Mandatory Documentation Sync:**
   - If your changes affect user-facing features, Quick Setting tiles, or Companion Settings options, you **must** update [`docs/FUNCTIONAL_SPECIFICATION.md`](docs/FUNCTIONAL_SPECIFICATION.md), [`docs/UI_AND_THEMING.md`](docs/UI_AND_THEMING.md), and [`README.md`](README.md).
   - If your changes affect privileged execution, system settings keys, vendor Binder calls, or security properties, you **must** update [`docs/TECHNICAL_ARCHITECTURE.md`](docs/TECHNICAL_ARCHITECTURE.md), [`docs/SYSTEM_INTEGRATION_REFERENCE.md`](docs/SYSTEM_INTEGRATION_REFERENCE.md), and [`docs/SECURITY.md`](docs/SECURITY.md).
5. **Hardware Verification:** If possible, verify your changes on physical hardware (**AYN Thor**).

### Step 4: Submit a Pull Request
1. Commit your changes with clear, descriptive commit messages matching [Conventional Commits](https://www.conventionalcommits.org/).
2. Push your branch to your GitHub fork.
3. Open a Pull Request (PR) against the `main` branch of the official repository.
4. Describe your changes clearly in the PR description. If a test binary is needed, contact the maintainer to arrange sending it privately; **never** attach compiled APKs or binaries publicly in GitHub comments or issues.

---

## Need Help?
If you have questions about the system architecture, need help getting your local build set up, or want to discuss a design, feel free to open a Discussion or Issue on GitHub. We're happy to collaborate!
