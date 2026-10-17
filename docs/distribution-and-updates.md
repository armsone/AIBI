# AIBI Distribution and Host Updates

`packages/` is the portable reference engine. `profiles/<host>/distribution/` contains the host-adapted files that are safe to install as whole files. `consumers/*.json` is the explicit allowlist connecting those files to app repositories.

## Stargram (iManager compatibility profile)

Stargram Apple and Android are registered in `consumers/imanager.json`; the profile identifier remains `imanager` for compatibility.

- Apple receives the host-adapted browser, media pipeline, and diagnostic store.
- Android receives isolated runtime, media, security, cleaner, timing, authentication, and diagnostic adapters. The host-adapted `ExternalAISurface.kt` is also registered for renderer lifecycle maintenance. `ComposerScreen` and the host result sink remain app-owned and are not overwritten by the sync tool.
- DenimDex Apple and Android retain their split native host engines; their runtime, providers and host-adapted diagnostic/session files are registered separately.

## Commands

From the AIBI project root:

```sh
python3 tools/aibi_sync.py status imanager
python3 tools/aibi_sync.py apply imanager
python3 tools/aibi_sync.py check imanager
python3 tools/aibi_sync.py apply all
```

`status` is read-only. `apply` copies only allowlisted files and records their SHA-256 hashes in each app's `.aibi-lock.json`. `check` exits non-zero if a file is missing, modified locally, diverged, or has an update waiting.

The first `apply` adopts an existing file only when it already equals the distribution source; a missing allowlisted destination may be created. Later updates are installed only when the app file still equals the last recorded hash. A local app edit is never overwritten: the command stops before writing any managed file. `apply all` preflights every registered consumer for conflicts before installing any update.

## Updating the engine

1. Change portable source in `packages/` and add the required sanitized fixture or device trace for provider changes.
2. Port the change into each affected host distribution without adding host policy to `packages/`.
3. Increment `aibi-version.json`.
4. Run `python3 tools/aibi_sync.py status all`. Run test suites only when the user explicitly authorizes testing; otherwise record that tests were not run.
5. Run `apply`, then the verification command recorded for each repository in the consumer manifest.
6. Run `check`; it must report every managed file as `current`.
7. Record the verification level under `verification/` and synchronize the installed Codex AIBI skill.

The sync tool does not run builds, devices, Git commands, or releases. Those remain explicit verification and delivery steps in the affected app repository.

## Current Barobogi host (0.5.4)

`consumers/mailtranslator.json` now points to `../Barobogi-MacOS`, where mail and screen translation share the current native AIBI runner. The standalone MailTranslator checkout is historical. Refresh distribution snapshots from the current host project first; do not copy historical standalone files over this host. Existing app-specific modifications remain owned by that app.

Barobogi iOS currently uses its own Google/DeepL browser adapter; Windows currently uses a local model rather than the common AI-site runtime. Do not silently install the common runtime into either path or claim provider parity.

## Provider structure verification (0.5.4)

The 2026-10-17 ChatGPT mobile control, deep composer and current image-preview changes are backed by sanitized official-page observations and static fixtures. DenimDex iOS completed a one-image task through attachment confirmation, one observed submission, stable response and host commit. Each other native host still needs its own stated verification level. Mac mail-specific runtime policies remain in its profile; portable composer changes must be merged without replacing those policies.
