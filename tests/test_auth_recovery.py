import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class AuthRecoveryContractTests(unittest.TestCase):
    def test_login_loop_fixture_is_sanitized_and_scoped(self):
        fixture_path = ROOT / "fixtures/providers/chatgpt-login-loop.json"
        self.assertTrue(fixture_path.exists(), "chatgpt-login-loop.json must exist")
        fixture = json.loads(fixture_path.read_text(encoding="utf-8"))

        self.assertEqual(fixture["provider"], "chatgpt")
        self.assertEqual(fixture["state"], "login-loop-recovery")
        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])

        scoped = fixture["scopedRemediation"]
        expected_origins = [
            "https://chatgpt.com/",
            "https://auth.openai.com/",
            "https://auth0.openai.com/",
        ]
        self.assertEqual(scoped["exactOrigins"], expected_origins)
        self.assertIn("chatgpt.com", scoped["domainVariants"])
        self.assertIn("auth.openai.com", scoped["domainVariants"])
        self.assertIn("auth0.openai.com", scoped["domainVariants"])

        known_paths = scoped["knownPaths"]
        self.assertIn("/", known_paths)
        self.assertIn("/auth", known_paths)
        self.assertIn("/api/auth", known_paths)
        self.assertIn("/u", known_paths)
        self.assertIn("/mfa", known_paths)
        self.assertIn("/api/accounts", known_paths)

        prohibitions = " ".join(scoped["prohibitions"])
        self.assertIn("Google", prohibitions)
        self.assertIn("Gemini", prohibitions)
        self.assertIn("Claude", prohibitions)

    def test_manifest_contains_login_recovery_helper(self):
        manifest_path = ROOT / "consumers/imanager.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

        android_repo = next(
            r for r in manifest["repositories"] if r["id"] == "imanager-android"
        )
        sources = [f["source"] for f in android_repo["files"]]
        destinations = [f["destination"] for f in android_repo["files"]]

        helper_source = "profiles/imanager/distribution/android/ExternalAILoginRecovery.kt"
        helper_dest = "app/src/main/java/com/armsone/imanagerai/ui/externalai/ExternalAILoginRecovery.kt"

        self.assertIn(helper_source, sources)
        self.assertIn(helper_dest, destinations)

    def test_login_recovery_helper_source_invariants(self):
        helper_code = (
            ROOT / "profiles/imanager/distribution/android/ExternalAILoginRecovery.kt"
        ).read_text(encoding="utf-8")

        # Origins scoping
        self.assertIn("https://chatgpt.com/", helper_code)
        self.assertIn("https://auth.openai.com/", helper_code)
        self.assertIn("https://auth0.openai.com/", helper_code)
        self.assertNotIn("claude.ai", helper_code)
        self.assertNotIn("gemini.google.com", helper_code)

        # Prohibits mass clear method identifiers in implementation
        self.assertNotIn("removeAllCookies()", helper_code)
        self.assertNotIn("deleteAllData()", helper_code)
        self.assertNotIn("clearFormData()", helper_code)

        # Strict token validation, bounds and paths
        self.assertIn("COOKIE_NAME_TOKEN_REGEX", helper_code)
        self.assertIn("MAX_COOKIE_NAME_LENGTH = 128", helper_code)
        self.assertIn("MAX_COOKIES_PER_ORIGIN = 100", helper_code)
        self.assertIn("RESET_TIMEOUT_MS = 10_000L", helper_code)
        self.assertIn("KNOWN_PATHS", helper_code)
        self.assertIn("__Host-", helper_code)
        self.assertIn("; Secure; HttpOnly", helper_code)
        self.assertIn("CancellationException", helper_code)
        self.assertIn("Dispatchers.Main", helper_code)
        self.assertIn("suspendCancellableCoroutine", helper_code)

    def test_surface_generation_guards_and_diagnostics(self):
        surface_code = (
            ROOT / "profiles/imanager/distribution/android/ExternalAISurface.kt"
        ).read_text(encoding="utf-8")

        # Generation advanced on page START as well as finish
        self.assertIn("override fun onPageStarted", surface_code)
        on_started_block = surface_code.split("override fun onPageStarted", 1)[1].split(
            "override fun onPageFinished", 1
        )[0]
        self.assertIn("navigationGeneration += 1", on_started_block)

        # Generation check & URL check guards auth callback
        login_loop_block = surface_code.split(
            "// LOGIN 모드: 내비게이션 완료 및 늦은 하이드레이션 중", 1
        )[1].split("// 메인 프레임 내비게이션 완료 시 자동 채우기", 1)[0]
        self.assertIn("navigationGeneration == currentGen", login_loop_block)
        self.assertIn("navigationGeneration != currentGen", login_loop_block)
        self.assertIn("postCheckUrl != checkUrl", login_loop_block)
        self.assertIn("isRecoveryInProgress", login_loop_block)

        # Diagnostics keys include prompt
        self.assertIn("remember(provider, mode, prompt) { diagnostics.start(provider.name) }", surface_code)
        self.assertIn('"login_started"', surface_code)
        self.assertIn('"positive_auth_observed"', surface_code)

        # Confirmation dialog contents and actions
        self.assertIn("ChatGPT 다시 연결", surface_code)
        self.assertIn("ChatGPT 로그인 정보만 초기화하고", surface_code)
        self.assertIn("Google 계정이나 다른 AI 로그인 상태는 유지돼요", surface_code)
        self.assertIn("clearChatGPTCookiesScoped", surface_code)
        self.assertIn("LOAD_NO_CACHE", surface_code)
        self.assertIn("stopLoading()", surface_code)

    def test_diagnostics_store_accepts_recovery_stages_and_metrics(self):
        store_code = (
            ROOT
            / "profiles/imanager/distribution/android/ExternalAIDiagnosticsStore.kt"
        ).read_text(encoding="utf-8")

        for stage in (
            "login_started",
            "positive_auth_observed",
            "auth_loop_detected",
            "auth_recovery_started",
            "auth_recovery_completed",
            "auth_recovery_failed",
        ):
            self.assertIn(f'"{stage}"', store_code)

        for metric in (
            "auth_roundtrips",
            "recovery_attempt",
            "cookie_callback_count",
            "recovery_result",
        ):
            self.assertIn(f'"{metric}"', store_code)

    def test_kotlin_tracker_test_file_exists(self):
        kt_test_path = ROOT / "tests/AuthRecoveryTrackerCheck.kt"
        self.assertTrue(kt_test_path.exists(), "AuthRecoveryTrackerCheck.kt must exist")
        kt_code = kt_test_path.read_text(encoding="utf-8")
        self.assertIn("fun main()", kt_code)
        self.assertIn("parseStrictHttpsUri", kt_code)
        self.assertIn("accounts.google.com", kt_code)
        self.assertIn("onAuthWindowOpened", kt_code)
        self.assertIn("onAuthWindowClosed", kt_code)


if __name__ == "__main__":
    unittest.main()
