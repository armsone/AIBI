# AIBI existing-feature stability review — 2026-10-17

## Scope and baseline

User requested existing AIBI features inspected across available devices and confirmed defects repaired. The user subsequently authorized finishing, Git backup and public release across current local project sources. New providers, document inputs, account/security changes and test runners remain outside this work.

- Initial AIBI version: 0.5.3.
- Preserve existing AIBI AGENTS.md modification and untracked CLAUDE.md/GEMINI.md, and all consumer pre-existing changes.
- Jev advisory `aibi-stability-20261017-01` recommended TM review; TM first confirmed device and distribution state, then assigned disjoint Apple work to Claude Sonnet and Android work to Gemini.
- Official WebView termination handling reference was searched with Exa, cross-checked with general search, and fetched from Android Developers. Apple WKNavigationDelegate termination callback documentation was also located. No private project data supplied to public search.

## Observations before changes

- Physical iPhone 17 Pro and Android SM_F968N reachable via approved development tools. Physical iPad/Android tablet/Google TV not available in this inspection. No emulator automatically started.
- iPhone installed Stargram 2.6.6/build202610030015, DenimDex 0.3.3/build202610030015.
- Android installed Stargram 2.6.6/code412651, DenimDex 0.2.1/code346562.
- Conflict-safe status: Stargram managed files current; DenimDex Apple and Android JS/runtime provider resources update available. The original MailTranslator root was absent. The user explicitly identified Barobogi-MacOS as the current integrated mail host; its current source was inspected and the compatibility consumer was remapped to that project.
- iPhone native settings: Gemini/Claude resolved logged in; ChatGPT remained checking across 08:56–08:57 KST observations. Source confirms eight-second inconclusive probe resolves to `.checking`, with no unknown enum case, and probe is moved 10,000 points off-screen. This violates the finite unknown-state and real-viewport contract.
- Native adapters searched lacked renderer-termination callbacks. Source-level defect; no forced renderer crash or instrumentation executed.

## Work and verification

AIBI 0.5.4 adapters and all five registered consumers synchronized; installed skill SKILL.md and eight shared assets synchronized. Native interruption/late-callback fixes passed independent source review. DenimDex iOS 0.3.4 and Android 0.3.4 builds succeeded; iPhone data-preserving replacement and ChatGPT login recognition observed. Android same-source development-signed replacement installed without deleting data; public APK uses the prior release certificate (SHA-256 9fa4857ee94b8b33a44b4f91dabdab241121e7c8471ff8796511eca22cd0a59b). Stargram 2.6.7 cold restart still reports ChatGPT unknown, so the sidebar fix is NOT yet claimed successful; readiness investigation continues. Mac 0.7.95 built and submitted via existing notary profile. Git/release progress is recorded separately; builds and login checks do not prove AI task completion. Test runners were not executed.

Provider credentials, prompt text, full answers, raw DOM, personal media, account identifiers and URL content are not recorded here. No production post/comment or attachment has been created by this inspection.

## Later provider-structure correction and release gate

Official current mobile ChatGPT observations identified a different sidebar control from desktop, a deeper form-less composer and semantic image preview tiles. Exact closed mobile controls now exclude hidden clones; ambiguous editor/control scopes fail closed. Native attachment dispatch no longer replays via a second transport when preview confirmation is inconclusive. Literal newlines and pre/code text are preserved in the Stargram host adapter.

The final added Apple/Android sidebar and Android preview/literal/code hunks passed focused independent source review at 10:11 KST. Actual consumer files matched their canonical profile copies. This is source evidence; Stargram mobile automatic-login verification remains a separate device step.

DenimDex iOS 0.3.5 completed a synthetic one-image request on the physical iPhone: attachment expected/observed count one, one observed submission, stable generation and host result application. Only stage/count/timing metadata is retained in the sanitized success trace. The temporary image was removed from the host selection. A provider-side synthetic conversation may remain until reversible cleanup is confirmed.

DenimDex Android 0.3.5 was built, signed with its existing public certificate, committed/pushed and published at 10:09 KST. Its final device installation remains pending because the existing wireless device disappeared and one reconnection timed out. No uninstall, data reset or security change was attempted.
