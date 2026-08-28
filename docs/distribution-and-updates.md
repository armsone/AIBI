# AIBI Distribution and Host Updates

`packages/` is the portable reference engine. `profiles/<host>/distribution/` contains the host-adapted files that are safe to install as whole files. `consumers/*.json` is the explicit allowlist connecting those files to app repositories.

## StarManager

StarManager is registered as the first consumer in `consumers/starmanager.json`.

- iOS currently receives one host-adapted runtime file. Its AIBI engine and StarManager UI are still monolithic, so the distribution snapshot deliberately lives under the StarManager profile rather than the portable package.
- Android receives eight isolated engine, provider-runtime, security, cleaner, timing, and authentication-adapter files. Compose UI, `ComposerScreen`, and the host result sink remain app-owned and can never be overwritten by the sync tool.

## Commands

From the AIBI project root:

```sh
python3 tools/aibi_sync.py status starmanager
python3 tools/aibi_sync.py apply starmanager
python3 tools/aibi_sync.py check starmanager
python3 tools/aibi_sync.py apply all
```

`status` is read-only. `apply` copies only allowlisted files and records their SHA-256 hashes in each app's `.aibi-lock.json`. `check` exits non-zero if a file is missing, modified locally, diverged, or has an update waiting.

The first `apply` adopts an existing file only when it already equals the distribution source; a missing allowlisted destination may be created. Later updates are installed only when the app file still equals the last recorded hash. A local app edit is never overwritten: the command stops before writing any managed file. `apply all` preflights every registered consumer for conflicts before installing any update.

## Updating the engine

1. Change portable source in `packages/` and add the required sanitized fixture or device trace for provider changes.
2. Port the change into each affected host distribution without adding host policy to `packages/`.
3. Increment `aibi-version.json`.
4. Run `python3 -m unittest discover -s tests` and `python3 tools/aibi_sync.py status starmanager`.
5. Run `apply`, then the verification command recorded for each repository in the consumer manifest.
6. Run `check`; it must report every managed file as `current`.
7. Record the verification level under `verification/` and synchronize the installed Codex AIBI skill.

The sync tool does not run builds, devices, Git commands, or releases. Those remain explicit verification and delivery steps in the affected app repository.
