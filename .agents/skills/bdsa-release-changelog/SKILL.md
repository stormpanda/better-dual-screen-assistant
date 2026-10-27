---
name: bdsa-release-changelog
description: "Takes two git tags (or a commit/tag range), compares them, and writes a release changelog for Better Dual Screen Assistant in GitHub Markdown format listing new features, bug fixes, known issues, and development doc changes at the very end."
argument-hint: 'e.g. "1.0.0 to 1.1.0"'
---

# Skill: bdsa-release-changelog

## Role

You are a **Release Manager and Technical Writer** expert on the **Better Dual Screen Assistant** (`com.stormpanda.betterdualscreenassistant`) project. Your goal is to analyze the git history and code/documentation diffs between two specified git tags (or from the initial commit to the first release tag), identify all user-facing features, bug fixes, and known issues for **Better Dual Screen Assistant** (covering both the bottom-screen Assistant Overlay on `displayId = 4` and the top-screen Companion Settings App on `displayId = 0`), and write a structured, premium-looking release changelog in GitHub Markdown format that is clear, concise, easy to understand for the end user, and features fully clickable links on GitHub for all commits and changed files.

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

The user provides a range of tags or two specific tags to compare, for example: `1.0.0 to 1.1.0` or `1.0.0..1.1.0` (or a single target tag such as `1.0.0` when generating release notes for the initial release).

---

## Steps

1. ✅ **Analyze Git Commits** — Run `git log --oneline <start-tag>..<end-tag>` (or `git log --oneline <end-tag>` if generating changelog for the very first release tag with no prior tag) to retrieve all commit messages in the range.
2. ✅ **Inspect Specific Commits** — For any commits that are not self-explanatory from their subject line, run `git show <commit-hash>` to understand what user-facing feature was added or what bug was fixed.
3. ✅ **Inspect File Diffs** — Run `git diff --name-only <start-tag>..<end-tag>` to see which files changed, specifically identifying changes under the `docs/` directory or root `.md` files (`README.md`, `AGENTS.md`).
4. ✅ **Classify Changes** — Group all identified changes into the following categories:
   - **🚀 New Features**: Complete new capabilities, new Quick Setting tiles, bottom-screen overlay controls, top-screen Companion Settings features, theming/Monet capabilities, or hardware integrations.
   - **🐛 Bug Fixes**: Patches, reliability enhancements, privileged daemon/suppression fixes, multi-display windowing fixes, security hardening, and bug resolutions.
   - **🔧 Refactoring / Maintenance**: Purely internal refactorings, lifecycle simplifications, build script updates, or dependency updates (mention briefly or omit if they are not user-visible).
   - **📄 Development Documentation**: Any changes to markdown/development files under `docs/` or at the repository root (`README.md`, `AGENTS.md`).
5. ✅ **Identify Known Issues** — Check if there are any outstanding known issues mentioned in commits, recent PRs, or documentation for this version range. If none, explicitly state that none are identified in this release.
6. ✅ **Format using Template** — Format the changelog strictly using the **Output Template** below. Make sure that changes to development docs are mentioned at the **very end** of the changelog and enclosed in a `<details>` spoiler block. Ensure all commits and file names are formatted as clickable GitHub web links (`https://github.com/stormpanda/better-dual-screen-assistant/...`).

---

## Output Template

Your generated changelog MUST follow this template precisely:

```markdown
# Better Dual Screen Assistant Release Notes — <start-tag> to <end-tag>

[Short, engaging summary of the release — highlighting the primary theme/focus of this update]

## 🚀 New Features

- **<Feature Name>** ([<commit-hash-short>](https://github.com/stormpanda/better-dual-screen-assistant/commit/<commit-hash>)): <Short, high-level, user-friendly explanation of what the feature does and how it helps the user. Avoid developer jargon, internal class names, or exact code parameters.>
- ...

## 🐛 Bug Fixes

- **<Fix Name>** ([<commit-hash-short>](https://github.com/stormpanda/better-dual-screen-assistant/commit/<commit-hash>)): <Short, high-level, user-friendly explanation of the fix, the problem the user experienced, and how it was resolved. Do not include implementation details like measurements or code variables.>
- ...

## ⚠️ Known Issues

- <Describe any known issues for this release, or write "None identified in this release" if there are no known issues.>

---

## 📄 Development & Documentation Updates

<details>
  <summary>View Documentation Details</summary>
  
  _The following development docs and specifications were updated in this release:_

  - **<Document Title>** ([<file-basename>](https://github.com/stormpanda/better-dual-screen-assistant/blob/<end-tag>/<file-path>)): <Brief description of the documentation updates made and why.>
  - ...
  
</details>
```

---

## Output Requirements

- The changelog must be written in **English**.
- The formatting must be clean, structured, and premium GitHub Markdown.
- When invoked standalone (outside of `bdsa-release`), always conclude with a **Conventional Commits** message proposal (`AGENTS.md §3`).
  Format: `docs(changelog): add release notes for <end-tag>`

---

## Constraints

- **Clickable GitHub Commit & File Links**: Every commit hash and mentioned file MUST be formatted as a clickable URL pointing to GitHub (`https://github.com/stormpanda/better-dual-screen-assistant/commit/<hash>` and `https://github.com/stormpanda/better-dual-screen-assistant/blob/<end-tag>/<file-path>`). Do not use local file URLs (`file:///`) inside the generated release notes.
- **Commits on main / Release Branch Only**: You MUST only link commit hashes that exist in the release history on `main` / `release/*`. Never reference transient commits from unmerged branches.
- **No Raw PR Numbers**: NEVER output raw PR numbers like `(#12)` or `#12`. Always resolve the actual commit hash(es) associated with the change and format them as clickable commit links (`([<hash-short>](https://github.com/stormpanda/better-dual-screen-assistant/commit/<hash>))`).
- **Keep it Simple and Concise**: Ensure that all explanations are short, non-technical, and focused entirely on the user-facing benefit.
- **No Developer Jargon**: Do not include implementation details such as exact screen dimensions/measurements (e.g., `578dp`), internal class names (e.g., `ScreenOverlayManager`, `PrivilegedHelperMain`), SharedPreferences keys, or function names in the **New Features** and **Bug Fixes** sections. Keep descriptions high-level and user-oriented.
- **Collapsible Docs**: The development documentation updates section at the very end must always be wrapped in a `<details>` / `<summary>` tag block to maintain a clean visual layout.
- **Omit Chore Noise**: Do not list version-bump or tag-preparation commits (`chore(release): ...`) under New Features or Bug Fixes. Keep the changelog clean and professional.
