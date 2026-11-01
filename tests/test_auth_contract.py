import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class AuthenticationContractTests(unittest.TestCase):
    def test_ios_probe_never_reuses_prompt_input_fallbacks_as_auth_evidence(self):
        source = (
            ROOT / "profiles/imanager/distribution/ios/ExternalAIBrowserView.swift"
        ).read_text()
        probe = source.split("static func authStatusProbeScript", 1)[1].split(
            "private static func selectors", 1
        )[0]
        self.assertNotIn("const inputSelectors", probe)
        self.assertNotIn("queryFirstVisible(inputSelectors)", probe)
        self.assertIn("config.authenticated", probe)

    def test_claude_logged_out_generic_editor_fixture_requires_login(self):
        fixture = json.loads(
            (ROOT / "fixtures/providers/claude-logged-out-generic-editor.json").read_text()
        )
        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        self.assertIn("div[contenteditable='true']", fixture["visibleGenericElements"])
        self.assertEqual(fixture["visibleAuthenticatedElements"], [])
        self.assertEqual(
            fixture["expected"],
            {"authenticated": False, "hasLogin": True, "hasChallenge": False},
        )

    def test_android_probe_requires_visible_provider_specific_account_evidence(self):
        auth_status = (
            ROOT
            / "profiles/imanager/distribution/android/ExternalAIAuthStatus.kt"
        ).read_text()
        scripts = (
            ROOT / "profiles/imanager/distribution/android/ExternalAIScripts.kt"
        ).read_text()
        auth_probe = scripts.split("fun checkAuthStatusScript", 1)[1].split(
            "// MARK: - 파싱 유틸리티", 1
        )[0]

        self.assertIn("requireVisible = true", auth_status)
        self.assertIn("ExternalAIAuthState.UNKNOWN", auth_status)
        self.assertNotIn("leftMargin = -10_000", auth_status)
        self.assertIn("side-nav-menu-button", auth_status)
        self.assertIn("strongAuthMarkerEl !== null", auth_probe)
        self.assertIn("var hasPositiveEvidence", auth_probe)
        self.assertLess(auth_probe.index("var loginEl"), auth_probe.index("hasPositiveEvidence"))

        provider_auth = scripts.split("DirectAIProvider.GEMINI ->", 1)[1]
        for generic_marker in (
            'authMarkers = toJsonArray(listOf("textarea"',
            '"div.ProseMirror[contenteditable=\'true\']",\n                        "button[data-testid=\'user-menu-button\']"',
            '"#prompt-textarea",\n                        "div#prompt-textarea",\n                        "button[data-testid=\'profile-button\']"',
        ):
            self.assertNotIn(generic_marker, provider_auth)

    def test_gemini_account_link_is_not_treated_as_login_required(self):
        fixture = json.loads(
            (ROOT / "fixtures/providers/gemini-authenticated-account-link.json").read_text()
        )
        scripts = (
            ROOT / "profiles/imanager/distribution/android/ExternalAIScripts.kt"
        ).read_text()
        provider_selectors = scripts.split("private fun providerSelectors", 1)[1]
        gemini = provider_selectors.split("DirectAIProvider.GEMINI ->", 1)[1].split(
            "DirectAIProvider.OPEN_AI ->", 1
        )[0]
        login_block = gemini.split("login = toJsonArray", 1)[1].split(
            "challenge = toJsonArray", 1
        )[0]
        auth_block = gemini.split("authMarkers = toJsonArray", 1)[1].split(
            "attachTrigger = toJsonArray", 1
        )[0]

        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        self.assertNotIn("a[href*='accounts.google.com']", login_block)
        self.assertIn("SignOutOptions", auth_block)
        self.assertEqual(
            fixture["expected"],
            {"authenticated": True, "hasLogin": False, "hasChallenge": False},
        )

    def test_chatgpt_collapsed_sidebar_uses_current_account_marker(self):
        fixture = json.loads(
            (ROOT / "fixtures/providers/chatgpt-authenticated-sidebar-account.json").read_text()
        )
        scripts = (
            ROOT / "profiles/imanager/distribution/android/ExternalAIScripts.kt"
        ).read_text()
        auth_status = (
            ROOT / "profiles/imanager/distribution/android/ExternalAIAuthStatus.kt"
        ).read_text()

        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        self.assertIn("[data-testid='accounts-profile-button']", scripts)
        self.assertIn("open-sidebar-button", auth_status)
        self.assertEqual(
            fixture["expected"],
            {"authenticated": True, "hasLogin": False, "hasChallenge": False},
        )

    def test_ios_chatgpt_sidebar_open_is_bounded_retry_not_single_shot(self):
        source = (
            ROOT / "profiles/imanager/distribution/ios/ExternalAIBrowserView.swift"
        ).read_text()
        check = source.split("func checkAuthStatus(provider: ExternalAIProvider)", 1)[1].split(
            "private static func evaluateBoundedSidebarOpen", 1
        )[0]
        self.assertNotIn("sidebarOpenAttempted", source)
        self.assertIn("private static let maxSidebarOpenAttempts = 6", source)
        self.assertIn("sidebarOpenAttempts = 0", source)
        # Only an ambiguous result may click, and only a confirmed click consumes an attempt.
        self.assertIn("!result.authenticated, !result.hasLogin, !result.hasChallenge", check)
        self.assertIn("sidebarOpenAttempts < Self.maxSidebarOpenAttempts", check)
        self.assertLess(check.index("guard didOpen"), check.index("sidebarOpenAttempts += 1"))
        # Authentication still comes only from the provider-specific probe after opening.
        self.assertIn("let afterOpen = await Self.evaluateBounded(script, in: webView, timeout: 5)", check)
        self.assertIn("return afterOpen", check)
        sidebar = source.split("static func openChatGPTSidebarScript", 1)[1].split(
            "private static func selectors", 1
        )[0]
        self.assertIn("visible.length === 1", sidebar)
        self.assertIn("[aria-expanded='false']", sidebar)

        # Claude: exact semantic sidebar markers only; a stateless toggle is clicked at most once per page.
        self.assertIn("guard let openScript = ExternalAIBrowserScripts.openSidebarScript(for: provider, allowStatelessToggle: allowStatelessToggle)", check)
        self.assertIn("let allowStatelessToggle = !statelessSidebarToggleUsed && ambiguousAuthChecks >= 2", check)
        self.assertIn("if clickKind == 2 { statelessSidebarToggleUsed = true }", check)
        self.assertNotIn("provider == .openAI", check)
        start = source.split("fileprivate func didStartNavigation() {", 1)[1].split("\n    }\n", 1)[0]
        for reset in ("statelessSidebarToggleUsed = false", "ambiguousAuthChecks = 0", "controlDiagnosticsRecorded = false"):
            self.assertIn(reset, start)
        dispatch = source.split("static func openSidebarScript(for provider", 1)[1].split(
            "private static let claudeSidebarOpenLabels", 1
        )[0]
        self.assertIn("case .openAI: openChatGPTSidebarScript()", dispatch)
        self.assertIn("case .claude: openClaudeSidebarScript(allowStatelessToggle: allowStatelessToggle)", dispatch)
        self.assertIn("case .gemini, .grok: nil", dispatch)
        markers = source.split("private static let claudeSidebarOpenLabels", 1)[1].split(
            "static func openClaudeSidebarScript", 1
        )[0]
        claude = source.split("static func openClaudeSidebarScript", 1)[1].split(
            "static func claudeSidebarControlCountsScript", 1
        )[0]
        fixture = json.loads(
            (ROOT / "fixtures/providers/claude-ios-collapsed-sidebar-hypothesis.json").read_text()
        )
        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        # r3 iPhone numeric probe: one visible exact open label, explicit aria-expanded=false, kind-1 click,
        # account marker afterwards. Only that path is device-observed; integers only.
        self.assertTrue(fixture["observedOnDevice"])
        probe = fixture["deviceProbe"]["metrics"]
        self.assertTrue(all(isinstance(value, int) for value in probe.values()))
        self.assertEqual(
            {key: probe[key] for key in ("sidebar_click_kind", "sidebar_expanded", "sidebar_open_label_count",
                                         "sidebar_candidate_count", "auth_marker_present", "sidebar_close_label_count",
                                         "sidebar_pin_count", "sidebar_testid_count", "sidebar_controls_count")},
            {"sidebar_click_kind": 1, "sidebar_expanded": 1, "sidebar_open_label_count": 1, "sidebar_candidate_count": 1,
             "auth_marker_present": 1, "sidebar_close_label_count": 0, "sidebar_pin_count": 0,
             "sidebar_testid_count": 0, "sidebar_controls_count": 0},
        )
        for key in ("sidebarTriggerAcceptedLabels", "sidebarToggleLabels", "sidebarToggleTestIds", "sidebarCloseLabels"):
            for marker in fixture[key]:
                self.assertIn(f'"{marker}"', markers)
        # Android port: only the device-observed kind-1 form (exact open label + explicit aria-expanded=false),
        # bounded in the Settings probe and the login sheet, never Gemini's stateless toggle in the sheet.
        android = (ROOT / "profiles/imanager/distribution/android/ExternalAIAuthStatus.kt").read_text()
        surface = (ROOT / "profiles/imanager/distribution/android/ExternalAISurface.kt").read_text()
        android_claude = android.split("private fun claudeClosedSidebarScript", 1)[1]
        for key in ("sidebarTriggerAcceptedLabels", "sidebarCloseLabels"):
            for marker in fixture[key]:
                self.assertIn(f'"{marker}"', android.split("private val claudeSidebarOpenLabels", 1)[1])
        self.assertIn("if (matches.length !== 1) return false;", android_claude)
        self.assertIn("if (target.getAttribute('aria-expanded') !== 'false') return false;", android_claude)
        self.assertIn("if (isClose) return false;", android_claude)
        self.assertNotIn("toggle", android_claude.lower())
        self.assertIn("provider == DirectAIProvider.OPEN_AI || provider == DirectAIProvider.CLAUDE", android)
        self.assertIn("hasExplicitClosedSidebarReveal(provider) &&\n                                    pollCount < 16 && pollCount % 2 == 0", android)
        sheet = surface.split("var sidebarClicks = 0", 1)[1].split("delay(500L)", 1)[0]
        self.assertLess(sheet.index("} else if (check.hasLogin) {"), sheet.index("sidebarOpenScript(provider)"))
        self.assertIn("hasExplicitClosedSidebarReveal(provider) && sidebarClicks < 6", sheet)
        self.assertIn("if (closeVisible || visible.length !== 1) return JSON.stringify({ clicked: false });", claude)
        self.assertIn("if (expanded === 'true') return JSON.stringify({ clicked: false });", claude)
        self.assertIn("if (c.pin) continue;", claude)
        self.assertIn("if (expanded === 'false') {\n              kind = 1;", claude)
        self.assertIn("(c.open || c.toggle || c.testid) && allowStatelessToggle && document.readyState === 'complete'", claude)
        classifier = markers.split("private static func claudeSidebarClassifierJS", 1)[1].split('"""')[1]
        self.assertIn("[aria-hidden='true'], [inert]", classifier)
        for generic in ("menu", "hamburger", "contenteditable", "ProseMirror", "indexOf('sidebar')", "includes("):
            self.assertNotIn(generic, claude.split('"""')[1])
            self.assertNotIn(generic, classifier)
        for generic in ("'menu'", "'메뉴'", "hamburger"):
            for key in ("sidebarTriggerAcceptedLabels", "sidebarToggleLabels", "sidebarToggleTestIds"):
                self.assertNotIn(generic.strip("'"), fixture[key])
        # Read-only count diagnostics: never click, integers only, login sheet only, separate store record.
        counts = source.split("static func claudeSidebarControlCountsScript", 1)[1].split(
            "static func openChatGPTSidebarScript", 1
        )[0]
        self.assertNotIn(".click()", counts)
        self.assertIn("bridge.recordsControlDiagnostics = true", source.split("private func pollLoginStatus", 1)[1].split("\n    }\n", 1)[0])
        self.assertEqual(source.count("recordsControlDiagnostics = true"), 1)
        store = (ROOT / "profiles/imanager/distribution/ios/AIBIDiagnosticsStore.swift").read_text()
        probe = store.split("func recordProbeSnapshot", 1)[1].split("\n    }\n", 1)[0]
        self.assertNotIn("currentRunID", probe)
        self.assertNotIn("runs[", probe)
        self.assertIn("Self.allowedMetrics(metrics)", probe)
        self.assertIn(
            fixture["visibleAuthenticatedElementsAfterOpen"][0],
            source.split("case .claude:\n            Selectors(", 1)[1].split("login: [", 1)[0],
        )
        self.assertEqual(
            fixture["expected"],
            {"authenticated": True, "hasLogin": False, "hasChallenge": False},
        )

    def test_android_reference_routes_user_gesture_oauth_popups_through_allowlist(self):
        source = (ROOT / "packages/android/AIBIEngine.kt").read_text()
        self.assertIn("settings.setSupportMultipleWindows(true)", source)
        self.assertIn("override fun onCreateWindow", source)
        self.assertIn("if (!isUserGesture) return false", source)
        self.assertIn("originAllowed(url, config.allowedAuthOrigins)", source)
        self.assertIn("popupView?.destroy()", source)

    def test_android_explicit_logout_remains_login_required_until_login_flow_starts(self):
        auth_status = (
            ROOT
            / "profiles/imanager/distribution/android/ExternalAIAuthStatus.kt"
        ).read_text()

        self.assertIn("wasExplicitlyLoggedOut(context, provider)", auth_status)
        self.assertIn("return@withContext ExternalAIAuthState.REQUIRES_LOGIN", auth_status)
        self.assertIn("putBoolean(explicitLogoutKey(DirectAIProvider.GEMINI), true)", auth_status)
        self.assertIn("putBoolean(explicitLogoutKey(DirectAIProvider.OPEN_AI), true)", auth_status)
        self.assertIn("putBoolean(explicitLogoutKey(DirectAIProvider.CLAUDE), true)", auth_status)
        self.assertIn("fun markLoginFlowStarted", auth_status)

    def test_denimdex_ios_probe_requires_visible_account_marker_and_ends_unknown(self):
        probe = (ROOT / "profiles/denimdex/distribution/apple/AIBILoginProbe.swift").read_text()
        models = (ROOT / "profiles/denimdex/distribution/apple/AIBIModels.swift").read_text()
        consumer = json.loads((ROOT / "consumers/denimdex.json").read_text())
        fixture = json.loads(
            (ROOT / "fixtures/providers/chatgpt-denimdex-ios-auth-probe.json").read_text()
        )

        # Composer readiness is never authentication evidence for this host.
        self.assertNotIn(".checkReadiness(", probe)
        self.assertNotIn("__AIBI_RUNTIME__", probe)
        self.assertIn("var authenticatedIndicator: [String]?", models)
        self.assertIn('"authenticated": config.selectors.authenticatedIndicator ?? []', probe)
        state = probe.split("static let authStateFunction", 1)[1].split("static let revealSidebarFunction", 1)[0]
        self.assertLess(state.index("visibleIn(selectors.challenge)"), state.index("visibleIn(selectors.login)"))
        self.assertLess(state.index("visibleIn(selectors.login)"), state.index("visibleIn(selectors.authenticated)"))
        self.assertLess(state.index("document.readyState === 'loading'"), state.index("visibleIn(selectors.authenticated)"))
        self.assertIn("[aria-busy='true']", state)
        self.assertNotIn("promptInput", state)
        # Closed-sidebar reveal: exact Stargram selectors, unique visible enabled closed control only, bounded.
        reveal = probe.split("static let revealSidebarFunction", 1)[1].split("private struct AIBILoginProbeWebView", 1)[0]
        self.assertIn("if (visible.length !== 1) return false;", reveal)
        self.assertIn("target.getAttribute('aria-disabled') === 'true'", reveal)
        self.assertIn("target.getAttribute('aria-expanded') === 'true'", reveal)
        self.assertIn("[aria-expanded='false']", reveal)
        self.assertIn("private static let maxSidebarOpenAttempts = 6", probe)
        self.assertIn("if sidebarOpenAttempts < Self.maxSidebarOpenAttempts, await revealSidebar(webView: webView)", probe)
        # Bounded check ends explicitly unknown; the store never reports it as logged in.
        self.assertIn("Date().addingTimeInterval(waitsForAuthentication ? 600 : 8)", probe)
        self.assertIn("if !Task.isCancelled { finishInconclusive() }", probe)
        self.assertIn("case .inconclusive: store.setStatus(.unknown)", probe)
        self.assertIn("if result == .authenticated {", probe)
        self.assertNotIn("logout", probe.lower())
        # Registered so future sync keeps the host fix.
        files = [item for repo in consumer["repositories"] for item in repo["files"]]
        self.assertIn(
            {"source": "profiles/denimdex/distribution/apple/AIBILoginProbe.swift",
             "destination": "DenimDex/AIBI/AIBILoginProbe.swift", "scope": "host-adapted-auth-probe"},
            files,
        )

        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        expected = {item["id"]: item["expected"] for item in fixture["states"]}
        self.assertEqual(expected["account-marker-visible"]["state"], "authenticated")
        self.assertEqual(expected["generic-composer-only"]["state"], "unknown")
        self.assertEqual(expected["login-visible-with-marker"]["state"], "login")
        self.assertEqual(expected["challenge-visible"]["state"], "challenge")
        self.assertEqual(expected["collapsed-sidebar-unique-control"]["stateAfterClick"], "authenticated")
        for ambiguous in ("collapsed-sidebar-ambiguous-controls", "collapsed-sidebar-disabled-control",
                          "collapsed-sidebar-hidden-clone-only"):
            self.assertEqual(expected[ambiguous], {"state": "unknown", "sidebarClick": False})


if __name__ == "__main__":
    unittest.main()
