# ChatGPT collapsed-sidebar login recognition (source fix, device verification pending)

- Date: 2026-10-17
- Level: source only. No build, no test run, no install, no Git action taken as part of this entry.
- Scope: Stargram `ExternalAIBrowserView.swift` ChatGPT login-status probe (hidden settings probe
  and visible `ExternalAILoginSheet`), both of which share `ExternalAILoginProbeBridge.checkAuthStatus`.

## User report invalidates the prior causal claim

A prior change in this file (unknown-state handling, bounded JS evaluation, renderer-terminated
handling) was previously believed to fix ChatGPT login recognition. Direct user feedback on a real
device shows this is false: on the official mobile ChatGPT page, the left account/history sidebar
renders collapsed by default (matching the user's native screenshot: collapsed sidebar, Chat/Work
tabs, composer). The login sheet stayed open and the account was only recognized **after the user
manually opened the collapsed sidebar themselves**. The earlier fix did not cause any improvement
in this scenario and that claim is retracted here.

## Observed dependency and hypotheses

ChatGPT's authenticated-marker list (`profile-button`, `user-menu-button`, `nav a[href*='/c/']`,
`nav[aria-label='Chat history']`, `/settings/general`) lives inside the left sidebar DOM subtree.
On mobile, that sidebar is collapsed by default and those elements are not visible
(`offsetWidth`/`offsetHeight` are 0), so `authStatusProbeScript` finds no positive evidence even
for a fully authenticated session, and the probe is stuck reporting no-evidence/unknown until a
human opens the sidebar by hand.

## Change

- `ExternalAIBrowserScripts.openChatGPTSidebarScript()` (new): a bounded, read-only JS probe that
  looks for the unique visible control matching an exact "open sidebar" selector set
  (`[data-testid='open-sidebar-button']`, `aria-label`/`title` of `Open sidebar` /
  `사이드바 열기` / `사이드바열기`), explicitly excluding anything whose label contains
  `close`/`닫기`. It never falls back to a generic menu button. If zero or more than one visible
  candidate matches, it clicks nothing and reports `clicked: false`.
- `ExternalAILoginProbeBridge.checkAuthStatus` (ChatGPT only): if the normal auth probe finds no
  authenticated marker, no login marker, and no challenge marker (the ambiguous state), and the
  sidebar-open attempt has not already run for the current page load, it runs the above script
  once, waits 300ms for the (CSS-transition) sidebar to finish opening, and re-runs the existing
  `authStatusProbeScript` once. It never infers login from the generic composer, cookies, URL, or
  the Chat/Work tabs — only from the pre-existing provider-specific authenticated markers, now
  given a chance to become visible.
- A `sidebarOpenAttempted` flag is reset only in `didStartNavigation`, so the sidebar-open click
  happens at most once per page load, not once per poll tick.
- All previously existing unknown-state, bounded-JS-timeout, and renderer-terminated handling in
  this file is unchanged.
- `fixtures/chatgpt-collapsed-auth.html` (new, sanitized, synthetic DOM only): covers a logged-in
  collapsed-sidebar state (must open sidebar once, then detect the now-visible marker), a
  logged-out state with a visible login button (must not touch any sidebar control), and a
  duplicate-label ambiguous state (two elements both matching the exact "Open sidebar" label, must
  click neither).

## Remaining device verification

Per the task, no build/test/install was performed here; TM will rebuild the 2.6.7 candidate. Before
claiming this is fixed, a cold app relaunch (not a warm foreground resume) must show the real
account recognized on the actual collapsed mobile ChatGPT sidebar in both the hidden settings probe
and the visible login sheet, without the user ever touching the sidebar themselves, and without
regressing the logged-out/login-button and challenge/captcha paths.

## 2.6.7 latest-source consistency update (not a root-cause claim)

- The Stargram host `Selectors` for `.openAI.authenticated` lacked the current common
  `authenticatedIndicator` entries. Ported (existing trusted entries preserved): `user-menu`,
  `accounts-profile-button`, `profile menu` / `프로필 메뉴` aria-label buttons and `role=button`
  variants. Claude gained `button[data-testid='user-menu']` from the same common list. No generic
  composer selector and no selector from unseen DOM was added; the strict "Open sidebar" fallback
  is unchanged (unique match only, no arbitrary menu).
- `fixtures/chatgpt-collapsed-auth.html` gained synthetic states: newer localized profile marker
  (genuine positive), signed-out composer + login (negative), collapsed then manually opened.
- Not device-proven: whether the hidden settings now report ChatGPT/Claude as logged in must be
  checked on the real device UI by TM. No tests were run.

## Follow-up observations

At 09:43–09:44 KST, the rebuilt readiness candidate still returned ChatGPT unknown and kept its visible login sheet open with the sidebar collapsed. TM manually opened only the sidebar; the native login-confirmed banner immediately appeared. Thus readiness alone is not a proven root cause or fix. The actual source also lacked newer positive profile-menu markers already present in the common provider registry; these are being synchronized before another untouched cold restart. Generic composers were removed from positive authentication evidence so a signed-out input cannot be reported logged in.
