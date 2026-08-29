import json
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class MediaContractTests(unittest.TestCase):
    def test_active_providers_advertise_twenty_image_ceiling(self):
        registry = json.loads((ROOT / "packages/providers/aibi-providers.json").read_text())
        for provider_id in ("gemini", "chatgpt", "claude"):
            provider = registry["providers"][provider_id]
            capability = provider["mediaCapabilities"]
            selectors = provider["selectors"]
            self.assertTrue(capability["supportsImages"])
            self.assertEqual(capability["maxImagesPerTask"], 20)
            self.assertTrue(selectors["attachmentInput"])
            self.assertTrue(selectors["attachmentTrigger"])
            self.assertTrue(selectors["attachmentPreview"])
        self.assertIn(
            "사진",
            registry["providers"]["chatgpt"]["selectors"]["attachmentMenuActionText"],
        )

    def test_provider_fixtures_are_sanitized_and_atomic(self):
        for path in sorted((ROOT / "fixtures/providers").glob("*-multi-image.json")):
            fixture = json.loads(path.read_text())
            self.assertTrue(fixture["sanitized"])
            self.assertEqual(fixture["requestedImageCount"], 8)
            self.assertEqual(fixture["expected"], "all-eight-previews-before-submit")

        nested = json.loads((ROOT / "fixtures/providers/gemini-mobile-nested-file-menu.json").read_text())
        self.assertTrue(nested["sanitized"])
        self.assertFalse(nested["containsPrivateContent"])
        self.assertEqual(nested["visibleSequence"], ["업로드 및 도구", "파일", "native-multiple-file-panel"])
        self.assertEqual(nested["requestedImageCount"], 5)

        android = json.loads((ROOT / "fixtures/providers/gemini-android-native-file-menu.json").read_text())
        self.assertTrue(android["sanitized"])
        self.assertFalse(android["containsPrivateContent"])
        self.assertEqual(android["requestedImageCount"], 5)
        self.assertEqual(android["visibleSequence"][-1], "WebChromeClient.onShowFileChooser")
        self.assertFalse(android["photoInputMultiple"])
        self.assertEqual(android["fileChooserMode"], "MODE_OPEN_MULTIPLE")
        self.assertEqual(android["legacyPreviewSelectorCount"], 0)
        self.assertEqual(android["semanticAttachmentCloseActionCount"], 5)
        self.assertEqual(
            android["acceptedFileActionSelector"],
            "images-files-uploader[data-test-id='uploader-images-files-button-advanced'] button",
        )
        self.assertEqual(
            android["expectedResult"],
            "semantic-close-action-count-confirms-exact-native-batch-before-submit",
        )

        answer_debug = json.loads(
            (ROOT / "fixtures/providers/gemini-android-answer-validation-debug.json").read_text()
        )
        self.assertTrue(answer_debug["sanitized"])
        self.assertFalse(answer_debug["containsPrivateContent"])
        self.assertEqual(
            answer_debug["expectedExtraction"],
            "content-after-last-standalone-Text-marker",
        )

        triple_quote_debug = json.loads(
            (ROOT / "fixtures/providers/gemini-android-answer-validation-triple-quote.json").read_text()
        )
        self.assertTrue(triple_quote_debug["sanitized"])
        self.assertFalse(triple_quote_debug["containsPrivateContent"])
        self.assertEqual(
            triple_quote_debug["expectedExtraction"],
            "content-inside-last-recognized-triple-quoted-assignment",
        )

        readiness = json.loads(
            (ROOT / "fixtures/providers/gemini-android-hidden-attachment-readiness.json").read_text()
        )
        self.assertTrue(readiness["sanitized"])
        self.assertEqual(readiness["forbiddenAction"], "immediate_visible_fallback")
        self.assertEqual(
            [item["expectedAction"] for item in readiness["observations"]],
            ["retry_hidden", "continue_hidden_attachment"],
        )

        chatgpt_menu = json.loads(
            (ROOT / "fixtures/providers/chatgpt-android-mobile-photo-menu.json").read_text()
        )
        self.assertTrue(chatgpt_menu["sanitized"])
        self.assertFalse(chatgpt_menu["containsPrivateContent"])
        self.assertEqual(chatgpt_menu["target"], {"role": "menuitem", "text": "사진"})
        self.assertEqual(
            chatgpt_menu["hiddenDeviceTrace"],
            {
                "directImageInputBeforeMenu": "not-hydrated",
                "menuFoundAttempt": 1,
                "menuDiscoveryCadenceMs": 500,
                "hiddenWebViewClickHandling": "enabled-behind-opaque-host",
                "hiddenWebViewRendering": "near-transparent-behind-opaque-host",
                "hiddenWebViewFocus": "touch-focus-during-trusted-attachment-gesture",
                "hiddenViewport": "clamped-to-attached-host-bounds",
                "trustedTouchLayering": "remain-behind-opaque-host",
                "triggerTransport": "trusted-touch-plus-then-trusted-touch-photo-menuitem",
                "nativeChooser": "MODE_OPEN_MULTIPLE",
                "preparedCount": 5,
                "observedPreviewCount": 5,
                "expectedAction": "keep-hidden-and-continue",
            },
        )
        self.assertEqual(
            chatgpt_menu["expectedSequence"],
            ["composer-plus", "photo-menuitem", "WebChromeClient.onShowFileChooser"],
        )

    def test_runtime_assigns_all_images_and_rejects_non_multiple_input(self):
        subprocess.run(
            ["node", str(ROOT / "tests/runtime_media_test.js")],
            cwd=ROOT,
            check=True,
        )

    def test_runtime_retries_late_dom_replacement_and_preserves_existing_text(self):
        subprocess.run(
            ["node", str(ROOT / "tests/runtime_prompt_injection_test.js")],
            cwd=ROOT,
            check=True,
        )

    def test_chatgpt_late_dom_replacement_fixture_matches_bounded_retry_contract(self):
        fixture = json.loads(
            (ROOT / "fixtures/providers/chatgpt-late-dom-replacement-injection.json").read_text()
        )
        self.assertTrue(fixture["sanitized"])
        self.assertFalse(fixture["containsPrivateContent"])
        self.assertEqual(fixture["quirk"], "lateDomReplacement")

        trace = fixture["hiddenDeviceTrace"]
        self.assertLessEqual(len(trace), fixture["expectedRetryBound"])
        # Every attempt but the last is a transient miss or an unverified "success" that must
        # be retried, never treated as terminal.
        for attempt in trace[:-1]:
            self.assertEqual(attempt["expectedAction"], "retry_hidden")
            self.assertFalse(attempt["injectSucceeded"] and attempt["verifiedMatch"])
        self.assertEqual(trace[-1]["expectedAction"], "proceed_to_submit")
        self.assertTrue(trace[-1]["injectSucceeded"] and trace[-1]["verifiedMatch"])

        existing_text = fixture["existingDifferentTextTrace"]
        self.assertEqual(existing_text["injectCode"], "EXISTING_TEXT_PRESERVED")
        self.assertEqual(existing_text["expectedAction"], "stop_immediately_no_retry_no_overwrite")

    def test_apple_engine_verifies_injection_before_trusting_success(self):
        engine = (ROOT / "packages/apple/AIBIEngine.swift").read_text()
        self.assertIn("promptInjectionRetryLimit", engine)
        self.assertIn("verifyPromptInjected", engine)
        self.assertIn("EXISTING_TEXT_PRESERVED", engine)
        runtime = (ROOT / "packages/runtime/aibi-browser-runtime.js").read_text()
        self.assertIn("RUNTIME.verifyPromptInjected", runtime)

    def test_platform_pipelines_share_limits_and_ordered_names(self):
        android = (ROOT / "packages/android/AIBIMediaPipeline.kt").read_text()
        apple = (ROOT / "packages/apple/AIBIMediaPipeline.swift").read_text()
        for source in (android, apple):
            self.assertIn("maximumImageCount", source)
            self.assertIn("2_000_000", source)
            self.assertIn("2_048", source)
            self.assertIn("aibi-", source)
            self.assertIn("20", source)

    def test_portable_engines_accept_opt_in_twenty_image_tasks(self):
        apple = (ROOT / "packages/apple/AIBIEngine.swift").read_text()
        android = (ROOT / "packages/android/AIBIEngine.kt").read_text()
        self.assertIn("task.attachments.count > 20", apple)
        self.assertNotIn("task.attachments.count > 8", apple)
        self.assertIn("task.attachments.size > 20", android)
        self.assertNotIn("task.attachments.size > 8", android)

    def test_starmanager_ios_distribution_uses_selected_count_up_to_eight(self):
        runtime = (ROOT / "profiles/starmanager/distribution/ios/ExternalAIBrowserView.swift").read_text()
        pipeline = (ROOT / "profiles/starmanager/distribution/ios/AIBIMediaPipeline.swift").read_text()
        self.assertIn("var attachments: [AIBIMediaAttachment] = []", runtime)
        self.assertIn("(1...8).contains(attachments.count)", runtime)
        self.assertIn("window.__starManagerBeginAttachmentBatch", runtime)
        self.assertIn("window.__starManagerCommitAttachmentBatch", runtime)
        self.assertIn("waitForAttachmentCount(expectedCount)", runtime)
        self.assertIn("maximumImageCount: Int = 8", pipeline)
        self.assertIn("aibi-%02d.jpg", pipeline)

    def test_starmanager_android_distribution_uses_bounded_atomic_batch(self):
        runtime = (ROOT / "profiles/starmanager/distribution/android/ExternalAIScripts.kt").read_text()
        pipeline = (ROOT / "profiles/starmanager/distribution/android/ExternalAIMediaPipeline.kt").read_text()
        models = (ROOT / "profiles/starmanager/distribution/android/ExternalAIModels.kt").read_text()
        self.assertIn("prepareAttachmentInputScript", runtime)
        self.assertIn("beginAttachmentBatchScript", runtime)
        self.assertIn("appendAttachmentToBatchScript", runtime)
        self.assertIn("commitAttachmentBatchScript", runtime)
        self.assertIn("batch.files.length !== batch.expectedCount", runtime)
        self.assertIn("maximumVisibleCount === $expectedCount", runtime)
        self.assertIn("Upload from device", runtime)
        self.assertIn("__sm_attachment_file_action_selected", runtime)
        self.assertIn("openAttachmentPanelScript", runtime)
        self.assertIn("attachmentMenuActionTargetScript", runtime)
        self.assertIn("attachmentImageInputTargetScript", runtime)
        self.assertIn("uploader-images-files-button-advanced", runtime)
        self.assertIn('listOf("사진", "Photos", "Upload photos")', runtime)
        self.assertIn("provider == DirectAIProvider.OPEN_AI", runtime)
        self.assertNotIn('"button[data-test-id*=\'upload-file\']"', runtime)
        self.assertIn("button[aria-label='첨부파일 닫기']", runtime)
        bridge = (ROOT / "profiles/starmanager/distribution/android/ExternalAINativeFileBridge.kt").read_text()
        self.assertIn("callback.onReceiveValue(arrayOf(next))", bridge)
        self.assertIn("nextSingleIndex += 1", bridge)
        self.assertIn("MODE_OPEN_MULTIPLE", bridge)
        self.assertIn("maximumImageCount: Int = 8", pipeline)
        self.assertIn("maximumLongEdgePixels: Int = 2_048", pipeline)
        self.assertIn("maximumBytesPerImage: Int = 2_000_000", pipeline)
        self.assertIn("ExifInterface", pipeline)
        self.assertIn("attachmentTimeoutMs: Long = 30_000L", models)

    def test_portable_android_engine_uses_native_file_chooser_before_data_transfer(self):
        engine = (ROOT / "packages/android/AIBIEngine.kt").read_text()
        self.assertIn("onShowFileChooser", engine)
        self.assertIn("openAttachmentPanel", engine)
        self.assertIn("FileProvider.getUriForFile", engine)
        self.assertLess(engine.index("openAttachmentPanel"), engine.index("beginAttachmentBatch"))
        self.assertIn("delay(timingProfile.attachmentCadenceMs)\n                continue", engine)


if __name__ == "__main__":
    unittest.main()
