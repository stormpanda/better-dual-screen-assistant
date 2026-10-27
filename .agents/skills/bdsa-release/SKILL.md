---
name: bdsa-release
description: "Orchestrate the Better Dual Screen Assistant (BDSA) app release process: create release branch (release/X.Y.Z), set release version, build signed release APK, generate SHA-256 checksum, compile changelog, upload GitHub release draft with APK and checksum assets, merge back to main locally (no auto-push of main), and bump development version."
---

# Skill: bdsa-release

## Role

You are a **Release Manager** expert on the **Better Dual Screen Assistant** (`com.stormpanda.betterdualscreenassistant`) project. Your goal is to guide the repository through a seamless release cycle for the **Better Dual Screen Assistant** application. You will safely manage release branches (`release/X.Y.Z`), update Gradle version configurations in `app/build.gradle.kts`, execute release compilation and signing pipelines, coordinate git version tags, generate premium-grade release changelogs using git history, publish draft releases with the signed APK and SHA-256 checksum to GitHub, merge release branches back into `main` locally, and transition the repository into active development mode with bumped versions.

---

## Project Context (mandatory — include verbatim in every skill)

| Key                  | Value                                                                                              |
| -------------------- | -------------------------------------------------------------------------------------------------- |
| Package              | `com.stormpanda.betterdualscreenassistant`                                                         |
| Language             | Kotlin 2.2+, Jetpack Compose (Material 3 + custom non-Material TCC canvas widgets)                 |
| Module               | `:app` (`app/build.gradle.kts`)                                                                    |
| Target Device        | AYN Thor (`kalama` / Snapdragon 8 Gen 2, Android 13 / `minSdk = 33`, `targetSdk = 35`)             |
| Coding rules         | **`AGENTS.md`** at workspace root — treat every rule as mandatory                                  |
| Sandbox policy       | Always run `./gradlew`, `adb`, `gh`, and remote `git` commands with `BypassSandbox: true` (`AGENTS.md §2`) |
| Reference Exclusion  | `reference/` (decompiled stock `DualScreenAssistant`) is git-ignored and must never be committed   |
| ADB path             | `/Users/maikthomalla/Library/Android/sdk/platform-tools/adb`                                       |

---

## User Input

The user requests to initiate a release. Releases can be started from `main` (for standard minor releases) or from an existing `release/*` branch (for hotfix patch releases). All version and signing configurations are dynamically parsed from `app/build.gradle.kts` and `local.properties`.

---

## Steps

Follow these sequential steps precisely:

1. ✅ **Prepare Git Status** — Check that the local repository is on `main` (for standard release) or a `release/*` branch (for hotfix release) and has a clean tracked status (`git status --porcelain -uno` is empty). Inform the user if any uncommitted tracked modifications exist.
2. ✅ **Execute Prepare Phase** — Run `scripts/release.sh prepare` (with `BypassSandbox: true` since it pushes to GitHub). This determines the release version (strips `-SNAPSHOT` on `main`, or increments patch / strips `-SNAPSHOT` on a `release/*` branch), creates/checks out the release branch `release/<version>` (e.g., `release/1.0.0` or `release/1.0.1`), updates `versionName` in `app/build.gradle.kts`, commits on the release branch, tags the commit with `<version>`, and pushes the release branch and tag to GitHub.
   - **Update Message**: Print the update exactly as: `Release version <version> successfully prepared and tagged on branch release/<version>.`
3. ✅ **Execute Build Phase** — Run `scripts/release.sh build` (with `BypassSandbox: true` since it invokes `./gradlew :app:assembleRelease`). This compiles the signed release APK (`:app:assembleRelease`) using the keystore credentials specified in `local.properties` (`bdsa.keystore.*` or fallback `megingiard.keystore.*`), copies it to `app/release/BetterDualScreenAssistant-v<version>.apk`, and calls `scripts/generate_checksum.sh` to create `app/release/BetterDualScreenAssistant-v<version>-checksum-sha256.txt`.
   - **Update Message**: Print the update exactly as: `Release Build <version> APK successfully created at app/release/BetterDualScreenAssistant-v<version>.apk`
4. ✅ **Determine Previous Tag** — Find the previous git tag immediately before the current release version (e.g., by running `git describe --tags --abbrev=0 HEAD~1` or inspecting `git tag -l --sort=-v:refname` and skipping the current version). If no previous tag exists (initial release), use the repository's root commit (`git rev-list --max-parents=0 HEAD`) as the start reference.
5. ✅ **Generate Changelog** — Run the analysis steps defined in the `bdsa-release-changelog` skill comparing `<previous-tag> to <current-tag>` (or `<root-commit> to <current-tag>` for the initial release).
   - Write this generated premium markdown changelog into a temporary file at `.tmp/release_changelog.md` (creating `.tmp/` if it doesn't exist).
6. ✅ **Execute Publish Phase** — Run `scripts/release.sh publish .tmp/release_changelog.md` (with `BypassSandbox: true` since `gh release create` requires network access). This reads the changelog file and uses the `gh` CLI to upload the draft release (`BetterDualScreenAssistant-v<version>`) to GitHub with the signed APK (`app/release/BetterDualScreenAssistant-v<version>.apk`) and SHA-256 checksum file (`app/release/BetterDualScreenAssistant-v<version>-checksum-sha256.txt`).
   - **Update Message**: Print the update exactly as: `Release draft BetterDualScreenAssistant-v<version> successfully uploaded with APK and checksum.`
7. ✅ **Execute Finish Phase (Merge-Back)** — Run `scripts/release.sh finish`. This switches to `main` and merges the release branch locally into `main` (`git merge --no-ff`), automatically resolving any `app/build.gradle.kts` version conflict in favor of `main`. **Note:** `main` is NOT pushed to remote automatically.
   - **Update Message**: Print the update exactly as: `Successfully merged release/<version> into main locally.`
8. ✅ **Execute Bump Phase** — Run `scripts/release.sh bump`. This increments `versionCode` in `app/build.gradle.kts` and bumps `versionName` for development (minor snapshot `X.(Y+1).0-SNAPSHOT` when releasing from `main`, or patch snapshot `X.Y.(Z+1)-SNAPSHOT` when bumping a release branch). Commits locally on `main` (not pushed).
   - **Update Message**: Print the update exactly as: `Successfully bumped development version to <next-version> (code: <next-code>) on main (not pushed).`
9. ✅ **Reporting & Cleanup** — Provide the user with a summary of the draft release, pointing them to their GitHub Releases dashboard (`https://github.com/stormpanda/better-dual-screen-assistant/releases`) to review and publish the draft. Remind the user to manually push `main` (`git push origin main`) when ready. Delete the temporary `.tmp/release_changelog.md` file.

---

## Output Requirements

- Every release stage execution must output its dedicated success message in the exact format defined in the **Steps** section.
- Conclude the release with a breakdown of:
  1. The released version and its corresponding `versionCode`.
  2. The release branch created (`release/X.Y.Z`).
  3. The generated SHA-256 checksum for validation.
  4. The next development version and `versionCode` configured on `main`.
  5. A reminder that `main` was merged and bumped locally, but requires a manual `git push origin main`.
  6. A markdown draft release link (`https://github.com/stormpanda/better-dual-screen-assistant/releases`) for the user to review and publish manually.

---

## Constraints

- **Release Branch Isolation**: Standard releases create `release/X.Y.0` from `main`. Hotfix releases starting on `release/X.Y.Z` create a new branch `release/X.Y.Z+1` (or release `X.Y.Z` if already bumped to `X.Y.Z-SNAPSHOT`).
- **Manual Push for Main**: You **MUST NOT** push `main` to remote after merging or bumping. Only release branches (`release/*`) and tags (`X.Y.Z`) are pushed automatically by `prepare`.
- **Draft Releases Only**: You **MUST NOT** publish the release directly to the public. Always rely on `scripts/release.sh publish`, which passes `--draft` to `gh release create` so publication remains a manual review step.
- **Secure Credentials**: Never display or log any sensitive keystore properties (e.g., passwords or key aliases) from `local.properties` during execution.
- **Zsh Script Execution**: Always run `scripts/release.sh` using its target subcommands (`prepare`, `build`, `publish`, `finish`, `bump`). Do not write custom inline shell release logic.
- **No Device Simulation**: Per `AGENTS.md §2`, do not simulate usage of the app on the connected device during the release workflow.
