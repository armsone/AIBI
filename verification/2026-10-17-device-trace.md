# Sanitized device observations — 2026-10-17 KST

No prompts, full answers, raw DOM, account identifiers, cookies, session values, private media or provider conversation URLs are stored.

- 09:25–09:26: Stargram 2.6.7 candidate rebuilt and data-preserving installed, process terminated/relaunched. Untouched settings probe: Gemini and Claude logged in; ChatGPT unknown. Opening ChatGPT login sheet displayed a rendered page and collapsed sidebar without dismissing. This disproves completion of the first sidebar repair. Readiness lifecycle changes are a source-based hypothesis until another cold device run proves behavior.
- 09:28: DenimDex 0.3.4 final common resources build installed without uninstall. Native settings recognized ChatGPT logged in.
- 09:37–09:39: DenimDex free-capture flow selected exactly one synthetic 512×512 solid-blue PNG created only for this inspection. The app moved from loading ChatGPT to attaching one photo, then returned its generic attachment failure. No generated answer or successful send was observed. This is NOT an end-to-end success. Investigation assigned to Claude; no automatic repeat send performed.
- 09:42: Synthetic image removed from DenimDex selection using its own remove control. Count returned to 0/30. No archive result was created. The temporary synthetic Photo Library asset remains until remaining image checks finish and must be removed through normal recoverable Photos deletion.
- Mac Barobogi installed bundle and running process verified as 0.7.95. Android Stargram and DenimDex same-source development builds installed with data preserved and launched through their actual package entry points; remote AI behavior is not yet verified on Android UI.

Test runners and forced renderer-crash instrumentation were not used.
- 09:51–09:52: DenimDex preserved the failed browser correctly. The visible composer contained two copies of the synthetic blue image with an empty prompt. Both attachments were removed with the provider's own X controls; composer returned to zero attachments/empty prompt. This proves the native-to-fallback replay duplicated successful media when preview recognition failed. It does not prove any AI prompt submission.
- The visible browser's Cancel changed the stage to cancelled but left the sheet open. Source: QuickValueRunner.cancel calls session.cancelCurrentTask(), which intentionally preserves a visible view; session.dismissVisibleBrowserForCancel() already exists and closes it. This one-call host wiring defect is included in the next correction.

- 10:00: DenimDex 0.3.5 current-preview candidate completed one synthetic-image run. Diagnostics confirm attachment_ready count=expected=1, send attempt=1 observed, generation completed at 27.2s with three stable samples, result_applied and run_completed. Native UI showed a judgement-held result, appropriate for a solid blue non-denim image. No full answer retained.

### 10:13–10:15 KST Stargram final mobile-control candidate

After replacement and relaunch, settings background probes had not yet confirmed ChatGPT. Opening the ChatGPT login row showed the official login sheet, which then automatically recognized the existing session and dismissed without manually opening its sidebar. The row became logged in. This confirms the visible login-sheet path; it does not establish background-probe success.

A subsequent single synthetic blue-image task stopped before attachment upload with the prior-attachment preparation error. Sanitized diagnostics recorded eight reset attempts with composer_ready=0/root_present=0, followed by attachment_failed and run_failed. No prompt or image was automatically submitted by that failed attempt. Cancel returned to the host and preserved the synthetic selection. Hidden-editor lookup alignment is being corrected and requires a new device check.

## Root cause: Swift string regex escape (syntax only, no personal data)

The expanded observer failed `node --check` (invalid regex, missing `/`). The HTML-conversion regex was written with single Swift backslashes (`/\r\n|\r|\n/g`), so the compiled Swift string put real newlines inside the JS regex literal and the whole observer failed to parse (no helper, no snapshot). Fix: double backslashes in the Swift source (`/\\r\\n|\\r|\\n/g`). Verified by expanding the Swift string literals and running `node --check` on the full observer.
