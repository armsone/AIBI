import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class AuthenticationContractTests(unittest.TestCase):
    def test_ios_probe_never_reuses_prompt_input_fallbacks_as_auth_evidence(self):
        source = (
            ROOT / "profiles/starmanager/distribution/ios/ExternalAIBrowserView.swift"
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
            / "profiles/starmanager/distribution/android/ExternalAIAuthStatus.kt"
        ).read_text()
        scripts = (
            ROOT / "profiles/starmanager/distribution/android/ExternalAIScripts.kt"
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
            ROOT / "profiles/starmanager/distribution/android/ExternalAIScripts.kt"
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
            ROOT / "profiles/starmanager/distribution/android/ExternalAIScripts.kt"
        ).read_text()
        auth_status = (
            ROOT / "profiles/starmanager/distribution/android/ExternalAIAuthStatus.kt"
        ).read_text()

        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        self.assertIn("[data-testid='accounts-profile-button']", scripts)
        self.assertIn("open-sidebar-button", auth_status)
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
            / "profiles/starmanager/distribution/android/ExternalAIAuthStatus.kt"
        ).read_text()

        self.assertIn("wasExplicitlyLoggedOut(context, provider)", auth_status)
        self.assertIn("return@withContext ExternalAIAuthState.REQUIRES_LOGIN", auth_status)
        self.assertIn("putBoolean(explicitLogoutKey(DirectAIProvider.GEMINI), true)", auth_status)
        self.assertIn("putBoolean(explicitLogoutKey(DirectAIProvider.OPEN_AI), true)", auth_status)
        self.assertIn("putBoolean(explicitLogoutKey(DirectAIProvider.CLAUDE), true)", auth_status)
        self.assertIn("fun markLoginFlowStarted", auth_status)


if __name__ == "__main__":
    unittest.main()
