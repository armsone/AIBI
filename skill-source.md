---
name: aibi
description: "Implement or update an AI Browser Interface (AIBI): an in-app browser integration on Apple (iOS/macOS) or Android that reuses the user's authenticated AI-service web session (Gemini, ChatGPT, Claude), submits prompt tasks, monitors generation stability, and returns extracted text to the host app with seamless visible takeover. Use when the user says AIBI, AI Browser Interface, or asks to connect external AI websites via an internal browser; do not use for official REST API integrations."
---

# AIBI — AI Browser Interface Implementation Kit

An **AI Browser Interface (AIBI)** is a client-side architecture that leverages the user's authenticated web session on an external AI provider's official portal (Google Gemini, OpenAI ChatGPT, Anthropic Claude). AIBI injects prompt tasks, monitors streaming output via structured DOM observation, verifies multi-poll completion stability, and extracts cleaned text into the host application—without requiring third-party API keys or intercepting user passwords, and providing immediate visible user takeover whenever automation stalls.

## Canonical Project and Scope Boundary

The canonical, continuously evolving AIBI knowledge project is `/Users/armsone/git/AIBI`. At the start of an AIBI implementation or provider-maintenance task, read:

1. `/Users/armsone/git/AIBI/docs/portable-contract.md`
2. `/Users/armsone/git/AIBI/docs/provider-change-playbook.md`
3. the matching `/Users/armsone/git/AIBI/profiles/<host>.md`, when present

Treat this installed skill as the executable snapshot and the AIBI project as the maintained source of truth. After learning a reusable fix, update the project first and synchronize the relevant skill reference, runtime, provider registry, fixture, or asset before declaring the work complete.

Keep these scopes strictly separate:

- **Portable AIBI**: session reuse, three-state login truth, hidden/visible execution, safe takeover, prompt/media transport capability, verified submission, streaming stability, extraction, cancellation, security, and provider-change adaptation.
- **Host profile**: provider order, exact wording, character limits, result validation, button topology, screen placement, theme, icons, camera workflow, preview, and other product behavior.
- **Out of AIBI**: unrelated app features such as software-update UI. Do not add them to the portable contract merely because they were discussed while implementing AIBI.

When the user gives mixed instructions in one conversation, classify each instruction into these three scopes before editing. A host-specific detail may be recorded in that host profile but must not silently become the default for the next app.

## Mandatory User-Visible Outcome

An AIBI integration is complete only when the target app delivers all applicable outcomes from the canonical portable contract: truthful login state and automatic login dismissal, hidden mode that remains hidden with host progress, visible mode from task start, user-action-only takeover, verified submission, stable final-result extraction, exactly-once host commit, an always-visible cancel action during execution, a finite post-submission timeout (portable default 119 seconds) shown as a `1:59 → 0:00` countdown and decreasing progress bar, safe cancellation/error handling, ordered atomic attachment of up to twenty normalized image copies when a host explicitly opts in (eight remains the conservative default), and provider drift isolation. Build success alone is never completion.

---

## 1. Quick Resource Router

Select the appropriate resource based on your implementation objective:

| Implementation Goal | Primary Reference & Assets | Key Actions |
|---|---|---|
| **Adding AIBI to a New App** (Apple / Android) | • [Normative Core Contract](references/technical-contract.md)<br>• [Apple Adapter Guide](references/apple-platform-adapter.md) / [Android Adapter Guide](references/android-platform-adapter.md)<br>• [Host Integration Guide](references/host-integration-guide.md) | 1. Inspect app placement.<br>2. Copy & adapt [`assets/apple/AIBIEngine.swift`](assets/apple/AIBIEngine.swift) or [`assets/android/AIBIEngine.kt`](assets/android/AIBIEngine.kt).<br>3. Connect host result sink. |
| **Android Parity with StarManager iPhone** | • [StarManager iPhone Reference Profile](references/starmanager-reference-profile.md)<br>• [Android Platform Adapter](references/android-platform-adapter.md)<br>• [`assets/android/AIBIEngine.kt`](assets/android/AIBIEngine.kt) | 1. Review the 17 verified iPhone behaviors.<br>2. Follow the 1:1 Android Parity Checklist.<br>3. Match UI progress row and timings. |
| **Using the Verified StarManager Android Pattern** | • [StarManager Android Implementation Reference](references/starmanager-android-reference.md) | Reuse the tested file split and keep device/browser evidence explicitly separate from JVM/build verification. |
| **Updating DOM Selectors or Providers** | • [`assets/providers/aibi-providers.json`](assets/providers/aibi-providers.json)<br>• [`assets/runtime/aibi-browser-runtime.js`](assets/runtime/aibi-browser-runtime.js) | 1. Update semantic selector chains.<br>2. Verify quirks (Quill, ProseMirror, late hydration).<br>3. Keep core orchestrators untouched. |
| **Adding Ordered Multi-Image Tasks** | • [Multi-Image Attachment Contract](references/media-attachments.md)<br>• [`assets/apple/AIBIMediaPipeline.swift`](assets/apple/AIBIMediaPipeline.swift) / [`assets/android/AIBIMediaPipeline.kt`](assets/android/AIBIMediaPipeline.kt) | 1. Snapshot up to the host limit (eight by default, twenty opt-in).<br>2. Normalize copies sequentially.<br>3. Require every provider preview before submission. |
| **Responding to Provider UI/Result Drift** | • `/Users/armsone/git/AIBI/docs/provider-change-playbook.md` | 1. Identify the failed stage without logging private content.<br>2. Add sanitized state fixtures.<br>3. Change only the provider adapter where possible.<br>4. Verify hidden and visible device paths. |
| **Updating Installed Host Apps** | • `/Users/armsone/git/AIBI/docs/distribution-and-updates.md`<br>• `/Users/armsone/git/AIBI/consumers/<host>.json` | 1. Update the canonical package and host distribution.<br>2. Increment the AIBI version.<br>3. Run conflict-safe sync.<br>4. Run the host verification gate and record its level. |
| **Verifying & Testing an Integration** | • [Verification & Testing Reference](references/verification-and-testing.md) | 1. Run deterministic test scenarios T01–T13.<br>2. Audit logs for zero prompt/cookie leaks. |

---

## 2. Three-Layer Architecture

AIBI integrations are cleanly partitioned into three decoupled layers:

```
+-------------------------------------------------------------------------+
| Layer 3: Host Application & Reference Profiles                          |
|  - Host Composer, Provider Selector, Settings & Login UI                |
|  - Host Result Sink & Domain Validation Hook (e.g. character count)     |
|  - StarManager iPhone Reference Profile & Android Parity Checklist      |
|  - References: host-integration-guide.md, starmanager-reference-profile.md |
+-------------------------------------------------------------------------+
                                   │
                                   ▼
+-------------------------------------------------------------------------+
| Layer 1: Normative Portable AIBI Core                                   |
|  - Lifecycle State Machine & Idempotent Transitions                     |
|  - Configurable Timing Profile & Multi-Poll Stability Reducer           |
|  - Origin Security Allowlist & Telemetry Sanitization                   |
|  - Reference: technical-contract.md                                     |
+-------------------------------------------------------------------------+
                                   │
                                   ▼
+-------------------------------------------------------------------------+
| Layer 2: Platform Adapters & In-Browser Runtime                         |
|  - Apple: WKWebView + WKWebsiteDataStore.default()                      |
|  - Android: WebView + CookieManager                                     |
|  - In-Browser Runtime: aibi-browser-runtime.js                          |
|  - Providers Registry: aibi-providers.json                              |
|  - References: apple-platform-adapter.md, android-platform-adapter.md    |
+-------------------------------------------------------------------------+
```

---

## 3. Pre-Implementation Inspection Checklist

Before writing code in a target application, inspect and align on:

1. **Placement & User Choice**:
   - Locate the prompt creation/composer screen.
   - Record the reference app's exact execution topology: whether a provider button runs immediately or selects a provider for a separate Run action. Preserve that topology literally; never introduce an extra selection or confirmation step for implementation convenience.
   - Record provider order, button enabled/disabled rules, progress-row placement, pending-result action, and every element that appears or disappears during execution.
   - Confirm where provider login management and the presentation preference (`Always show the AI browser` vs `Show only when action is needed`) should live in user settings.
2. **Platform & Session Storage**:
   - **Apple**: Ensure all `WKWebView` instances share `WKWebsiteDataStore.default()`.
   - **Android**: Ensure `CookieManager` is initialized with standard cookies and DOM storage enabled.
   - Treat provider session status as observed state, not a remembered guess. Settings must distinguish `checking`, `authenticated`, `login required`, and bounded `unknown` using positive page evidence; cookie presence and absence of a login button are not authentication proof.
   - Mount every settings status probe as an attached, off-screen browser with a real reference viewport (default 375×667). Never probe a modern provider SPA in an unattached WebView: it may skip layout or hydration and leave the UI stuck on `checking` even though opening the visible browser resolves immediately.
3. **Security & Origins**:
   - Enforce script origin allowlists (`allowedScriptOrigins`) and visible authentication origins (`allowedAuthOrigins`).
   - Never inject automation runtime scripts on third-party OAuth authentication domains.
4. **Result Destination & Validation**:
   - Implement the `AIBIResultSink` hook to validate domain formatting before the browser is automatically dismissed.
   - Never lose generated text; keep a manual copy and paste fallback in the composer.

## 4. Host-Parity Completion Gate

The browser engine and DOM automation are only part of AIBI. An integration is incomplete if the host app starts, presents, progresses, cancels, falls back, or returns results differently from its reference product.

- When porting an existing app, create an atomic host-flow contract before implementation: `entry gesture → task start → progress → fallback/error → result commit → dismissal/reset`.
- Treat direct provider buttons and `select provider → Run` as different functional contracts. Do not substitute one for the other.
- Verify the initial composer, disabled/ready provider row, hidden progress, visible takeover, error, and completed-result states with paired post-change captures. Verify each entry action with a runtime behavior trace.
- Verify login management as a closed loop: settings status → login surface → positive authenticated evidence → exactly-once dismissal → refreshed settings status. A browser that shows an authenticated provider page while retaining a login-required banner or remaining open fails parity.
- Preserve the login-status recipe when porting: use the same persistent browser store as the visible login surface; attach each probe to a real on-screen layout behind the opaque host surface rather than placing it off-screen; wait for main-frame navigation plus SPA hydration; evaluate provider-specific account markers, visible login controls, and visible challenge markers; publish `unknown` when the bounded probe ends without positive evidence instead of leaving `checking` indefinitely; and destroy every probe. A generic composer, `contenteditable`, send button, or other control that can appear on a logged-out public page is never authentication proof. In the visible login surface, repeat the positive check after each allowed main-frame navigation and during bounded hydration polling; on positive evidence flush the browser store, report success once, cancel stale callbacks, and dismiss exactly once. Do not infer authentication from cookies, URL shape, or absence of a login button.
- Time the settings probe on a real device. An authenticated provider that resolves only after manually opening its row, or remains indefinitely on `checking`, fails parity even if the visible login surface can detect the same session.
- When Android physical-device verification is authorized, install the exact tested APK on the designated phone with app data preserved, restart it, keep the display awake during the inspection session, and capture the affected states. A passed build with no successful device installation remains source-only evidence.
- Source inspection, unit tests, and successful builds may be reported as implemented, but never as AIBI parity complete without those captures and traces.
