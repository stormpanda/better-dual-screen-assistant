> [!CAUTION]
> **Pull Requests are only reviewed for open issues explicitly labeled `pr-welcome` by `@stormpanda`.**
>
> Reviewing external PRs across strict dual-device hardware invariants (AYN Thor & Retroid Pocket Duo) takes significantly more maintainer time than implementing changes directly with agentic coding workflows.
>
> - **Asking clarifying questions or discussing an issue is NOT an invitation to submit a PR** — please answer questions in the issue thread instead.
> - Uninvited PRs are automatically flagged with `needs-pr-welcome` and may be closed without review.
> - Want to add a custom Quick Setting tile or feature right away? Check out the Zero-SDK [Third-Party Plugin API](https://github.com/stormpanda/better-dual-screen-assistant/blob/main/docs/PLUGIN_API.md).

## Linked `pr-welcome` Issue

<!-- Add the open issue number labeled `pr-welcome` after # (e.g., Closes #42) -->
Closes #

## Summary of Changes

<!-- Describe what changed and why. -->

## Checklist

- [ ] This PR links to an **open** issue explicitly labeled `pr-welcome` by `@stormpanda`.
- [ ] Preserves architectural & hardware invariants for **both** AYN Thor (`displayId = 4`, Android 13) and Retroid Pocket Duo (`displayId = 2`, Android 16) as documented in `AGENTS.md`.
- [ ] `./gradlew testDebugUnitTest` and `./scripts/debug.sh build` pass locally.
- [ ] Tested on **real physical hardware** on at least one supported device (specify which: AYN Thor / Retroid Pocket Duo).
- [ ] Relevant documentation in `docs/` and `README.md` is updated if behavior, settings, or architecture changed.
- [ ] No compiled APKs or binaries are attached publicly (see `CONTRIBUTING.md`).
- [ ] I agree that my contribution is governed by Section 3 (Contributions) of `LICENSE`.
