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

    def test_imanager_ios_distribution_uses_selected_count_up_to_eight(self):
        runtime = (ROOT / "profiles/imanager/distribution/ios/ExternalAIBrowserView.swift").read_text()
        pipeline = (ROOT / "profiles/imanager/distribution/ios/AIBIMediaPipeline.swift").read_text()
        self.assertIn("var attachments: [AIBIMediaAttachment] = []", runtime)
        self.assertIn("(1...8).contains(attachments.count)", runtime)
        self.assertIn("window.__starManagerBeginAttachmentBatch", runtime)
        self.assertIn("window.__starManagerCommitAttachmentBatch", runtime)
        self.assertIn("waitForAttachmentCount(expectedCount)", runtime)
        self.assertIn("maximumImageCount: Int = 8", pipeline)
        self.assertIn("aibi-%02d.jpg", pipeline)

    def test_imanager_ios_superseded_navigation_never_marks_page_ready(self):
        runtime = (ROOT / "profiles/imanager/distribution/ios/ExternalAIBrowserView.swift").read_text()
        store = (ROOT / "profiles/imanager/distribution/ios/AIBIDiagnosticsStore.swift").read_text()
        failure = runtime.split("private func handleNavigationFailure", 1)[1].split(
            "func webViewWebContentProcessDidTerminate", 1
        )[0]
        self.assertIn("latestNavigation = navigation", runtime)
        self.assertLess(failure.index("guard !isSuperseded(navigation)"), failure.index("markNavigationFinished(loaded: false)"))
        self.assertIn('"failure_kind": cancelled ? 1 : 3', failure)
        self.assertIn("if loaded { log(\"browser_loaded\") }", runtime)
        self.assertIn("prompt_present: promptMatchesLastFilled(editor) ? 1 : 0", runtime)
        self.assertIn('logFillFailure("prompt_failed")', runtime)
        self.assertIn('logFillFailure("bridge_failed", failureKind: 4)', runtime)
        self.assertIn('logFillFailure("bridge_failed", failureKind: Self.evaluationFailureKind(error))', runtime)
        self.assertIn('"failure_kind": 1...4', store)
        for stage in ("navigation_started", "navigation_committed", "bridge_ready", "prompt_failed", "bridge_failed"):
            self.assertIn(f'"{stage}"', store)
        # Exactly-once send protection is unchanged.
        self.assertIn("if (window.__starManagerSubmitDispatched) return true;", runtime)
        self.assertIn("guard !Task.isCancelled, let webView, !hasAttemptedSubmission,\n              !automatic || !isAutomaticFillBudgetExhausted else { return false }", runtime)
        # Same-document didFinish/failure must not restart the fill task; only a new commit or
        # renderer termination advances the generation.
        finished = runtime.split("fileprivate func markNavigationFinished(loaded: Bool = true)", 1)[1].split(
            "fileprivate func markRendererTerminated", 1
        )[0]
        self.assertNotIn("navigationGeneration += 1", finished)
        self.assertEqual(runtime.count("navigationGeneration += 1"), 2)
        commit = runtime.split("final class ExternalAIBrowserBridge", 1)[1].split(
            "fileprivate func didCommitNavigation()", 1
        )[1].split("let epoch = docEpoch", 1)[0]
        self.assertIn("navigationGeneration += 1", commit)
        self.assertIn("bridge.markRendererTerminated()", runtime)
        # A restarted task after a send attempt only verifies; a cancelled task never reports failure.
        self.assertEqual(runtime.count("if bridge.isPageReady, bridge.hasAttemptedSubmission {"), 2)
        self.assertGreaterEqual(runtime.count("guard !Task.isCancelled else { return }\n                    if bridge.hasAttemptedSubmission"), 2)

        trace = json.loads(
            (ROOT / "fixtures/providers/gemini-ios-text-only-load-race-trace.json").read_text()
        )
        self.assertTrue(trace["sanitized"])
        self.assertFalse(trace["containsPrivateContent"])
        stages = [event["stage"] for event in trace["events"]]
        self.assertLess(stages.index("prompt_inserted"), stages.index("browser_loaded"))
        self.assertNotIn("send_attempted", stages)
        for event in trace["events"]:
            self.assertTrue(all(isinstance(value, int) for value in event["metrics"].values()))

    def test_imanager_ios_gemini_hydration_replaced_prompt_refills_before_any_send(self):
        runtime = (ROOT / "profiles/imanager/distribution/ios/ExternalAIBrowserView.swift").read_text()
        fill = runtime.split("window.__starManagerFillPrompt = function(text, force) {", 1)[1].split(
            "window.__starManagerSendPrompt = function()", 1
        )[0]
        send = runtime.split("window.__starManagerSendPrompt = function()", 1)[1].split(
            "window.__starManagerDidSubmit", 1
        )[0]
        # Never fill after a click; the send gate (normalized full match) is unchanged.
        first = [line.strip() for line in fill.splitlines() if line.strip() and not line.strip().startswith("//")][0]
        self.assertEqual(first, "if (window.__starManagerSubmitDispatched || generationSeen()) return false;")
        self.assertIn("if (!current || current !== normalize(window.__starManagerLastFilled || '')) return false;", send)
        # Quill: native insertText, then Quill paragraph DOM, then a normalized full-match check.
        quill = fill.split("el.classList.contains('ql-editor')) {", 1)[1].split("} else if (el.isContentEditable) {", 1)[0]
        self.assertLess(quill.index("execCommand('insertText'"), quill.index("el.replaceChildren(...quillParagraphs(text))"))
        self.assertIn("if (normalizedPromptText(el.innerText) !== normalizedPromptText(text)) return false;", quill)
        self.assertNotIn("execCommand('insertHTML'", quill)
        self.assertNotIn(".innerHTML", quill)
        # A throwing HTML sink no longer escapes the helper (runtime/Android parity).
        self.assertIn("try { inserted = document.execCommand('insertHTML', false, html); } catch (_) { inserted = false; }", fill)
        # Replacement is acted on only before any send attempt, after load, and bounded.
        state = runtime.split("window.__starManagerPromptState = function()", 1)[1].split("};", 1)[0]
        self.assertLess(state.index("return 3;"), state.index("return promptMatchesLastFilled(editor) ? 1 : 2;"))
        self.assertIn("document.readyState !== 'complete'", state)
        replaced = runtime.split("func isPromptReplacedBeforeSend() async -> Bool {", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("!hasAttemptedSubmission,\n              (1..<Self.maxPromptFillsBeforeSend).contains(promptFillCount)", replaced)
        self.assertIn('log("send_blocked", ["prompt_present": 0, "attempt": promptFillCount])', replaced)
        self.assertIn("static let maxPromptFillsBeforeSend = 3", runtime)
        self.assertEqual(runtime.count("if await bridge.isPromptReplacedBeforeSend() { return false }"), 2)
        for loop in runtime.split("private func submitPromptWhenReady() async -> Bool {")[1:]:
            body = loop.split("\n    }\n", 1)[0]
            self.assertLess(body.index("await bridge.submitPrompt()"), body.index("isPromptReplacedBeforeSend"))
        # fillPrompt itself enforces the bound for automatic fills; only the user's manual refill bypasses it.
        bridge_fill = runtime.split("func fillPrompt(_ prompt: String, force: Bool, automatic: Bool = true) async -> Bool {", 1)[1].split("\n    }\n", 1)[0]
        self.assertTrue(bridge_fill.lstrip().startswith(
            "guard !Task.isCancelled, let webView, !hasAttemptedSubmission,\n              !automatic || !isAutomaticFillBudgetExhausted else { return false }"
        ))
        self.assertIn("var isAutomaticFillBudgetExhausted: Bool { promptFillCount >= Self.maxPromptFillsBeforeSend }", runtime)
        self.assertEqual(runtime.count("automatic: false"), 1)
        self.assertIn("if await fillPrompt(force: true, automatic: false) {", runtime)
        self.assertEqual(runtime.count("await fillPrompt(force: true) {"), 1)
        self.assertEqual(runtime.count("await bridge.fillPrompt(prompt, force: true)"), 1)
        hidden = runtime.split("let filled = await bridge.fillPrompt(prompt, force: true)", 1)[1].split("try? await Task.sleep", 1)[0]
        self.assertLess(hidden.index("bridge.isAutomaticFillBudgetExhausted"), hidden.index("checkCount >= 12"))
        self.assertIn("onFallback(.timeout)", hidden.split("checkCount >= 12", 1)[0])

        trace = json.loads(
            (ROOT / "fixtures/providers/gemini-ios-hydration-prompt-replaced-trace.json").read_text()
        )
        self.assertTrue(trace["sanitized"])
        self.assertFalse(trace["containsPrivateContent"])
        events = trace["events"]
        stages = [event["stage"] for event in events]
        snapshots = [event["metrics"] for event in events if event["stage"] == "bridge_snapshot"]
        self.assertEqual([snapshot["prompt_present"] for snapshot in snapshots], [1, 0])
        self.assertEqual(stages.count("navigation_committed"), 1)
        self.assertLess(stages.index("send_timeout"), stages.index("bridge_failed"))
        self.assertNotIn("send_attempted", stages)
        for event in events:
            self.assertTrue(all(isinstance(value, int) for value in event["metrics"].values()))
        # The trace proves the normalized mismatch only; mechanisms stay labelled as hypotheses.
        self.assertTrue(trace["hypothesesNotPhysicallyConfirmed"])
        self.assertFalse(any("Trusted Types" in fact or "Quill" in fact for fact in trace["sourceFacts"]))

    def test_imanager_ios_gemini_first_send_waits_for_stable_ready_composer(self):
        runtime = (ROOT / "profiles/imanager/distribution/ios/ExternalAIBrowserView.swift").read_text()
        store = (ROOT / "profiles/imanager/distribution/ios/AIBIDiagnosticsStore.swift").read_text()
        trace = json.loads(
            (ROOT / "fixtures/providers/gemini-ios-premature-send-stability-trace.json").read_text()
        )
        gate = trace["gateUnderTest"]
        self.assertIn(f"const geminiSendStableSamples = {gate['stableSamples']};", runtime)
        self.assertIn(f"const geminiSendStableMs = {gate['stableMs']};", runtime)
        waiting = runtime.split("private func submitPromptWhenReady() async -> Bool {", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("Date().addingTimeInterval(15)", waiting)
        self.assertIn(f"Task.sleep(nanoseconds: {gate['pollIntervalMs']}_000_000)", waiting)

        send = runtime.split("window.__starManagerSendPrompt = function()", 1)[1].split(
            "window.__starManagerDidSubmit", 1
        )[0]
        # Every evaluation first drops the previous readiness, so any failed check or change restarts the count.
        self.assertLess(send.index("if (window.__starManagerSubmitDispatched) return true;"), send.index("sendReadiness = null;"))
        self.assertLess(send.index("sendReadiness = null;"), send.index("if (generationSeen()) return false;"))
        # Existing exact text gate is unchanged and still runs before the Gemini stability gate.
        exact = "if (!current || current !== normalize(window.__starManagerLastFilled || '')) return false;"
        self.assertLess(send.index(exact), send.index("if (isGeminiProvider) {"))
        gemini = send.split("if (isGeminiProvider) {", 1)[1].split("window.__starManagerSubmitAttempts", 1)[0]
        self.assertIn("document.readyState !== 'complete' || sendCandidateCountInScope() > 1", gemini)
        self.assertIn("input.isConnected && button.isConnected", gemini)
        for same in ("previousReadiness.editor === input", "previousReadiness.button === button",
                     "previousReadiness.raw === raw", "previousReadiness.attachments === attachments"):
            self.assertIn(same, gemini)
        self.assertIn("sendReadiness.samples < geminiSendStableSamples || now - sendReadiness.since < geminiSendStableMs) return false;", gemini)
        self.assertNotIn("setTimeout", send)
        self.assertLess(send.index("return false;\n              stableSamples = sendReadiness.samples;"), send.index("button.click();"))
        # Other providers keep the boolean result; only Gemini reports a numeric sample count.
        self.assertIn("return isGeminiProvider ? stableSamples : true;", send)
        # A real fill restarts stability before mutating the editor; nothing refills after a click.
        fill = runtime.split("window.__starManagerFillPrompt = function(text, force) {", 1)[1].split(
            "window.__starManagerSendPrompt = function()", 1
        )[0]
        self.assertLess(fill.index("if (window.__starManagerSubmitDispatched || generationSeen()) return false;"), fill.index("sendReadiness = null;"))
        # Generation seen once on this document (maybe the user's own send) suppresses refill and click
        # through completion: the one-way flag is set by every generation check, never cleared.
        self.assertIn("let generationObserved = false;", runtime)
        self.assertIn("if (active) generationObserved = true;", runtime)
        self.assertIn("return isGeneratingNow() || generationObserved;", runtime)
        self.assertNotIn("generationObserved = false;\n", runtime.split("let generationObserved = false;", 1)[1])
        state = runtime.split("window.__starManagerPromptState = function()", 1)[1].split("};", 1)[0]
        self.assertIn("if (window.__starManagerSubmitDispatched || generationSeen()) return 3;", state)
        # The native callers' existing one-way latch: a visible generation marks the task submitted.
        self.assertIn("guard isGenerating, submittedAt == nil else { return }\n                hasSubmittedPrompt = true", runtime)
        self.assertIn("if isGen, !hasSubmitted {\n                    hasSubmitted = true", runtime)
        self.assertLess(fill.index("sendReadiness = null;"), fill.index("el.focus();"))
        self.assertIn("CFGetTypeID(samples) != CFBooleanGetTypeID()", runtime)
        self.assertIn('attemptMetrics["stable_samples"] = samples.intValue', runtime)
        self.assertIn('"stable_samples": 0...1000', store)
        self.assertIn('"send_attempted": "The single automatic send click', store)

        self.assertTrue(trace["sanitized"])
        self.assertFalse(trace["containsPrivateContent"])
        self.assertIn("Not established causation", trace["inferenceNotPhysicallyConfirmed"])
        # Earliest click the gate allows after the first ready poll: the later of the sample count at the
        # existing cadence and the minimum duration. It is bounded well inside the 15 s submit window.
        earliest = max((gate["stableSamples"] - 1) * gate["pollIntervalMs"], gate["stableMs"])
        self.assertLessEqual(gate["stableMs"], (gate["stableSamples"] - 1) * gate["pollIntervalMs"])
        self.assertLess(earliest * 4, gate["submitWindowMs"])
        runs = {run["name"]: run for run in trace["runs"]}
        for name, run in runs.items():
            self.assertEqual((run["appVersion"], run["appBuild"], run["adapterVersion"]), ("2.6.8", "202610311912", "aibi-stargram-0.5.5"))
            stages = [event["stage"] for event in run["events"]]
            ms = {event["stage"]: event["ms"] for event in reversed(run["events"])}
            self.assertEqual(stages.count("send_attempted"), 1)
            self.assertEqual(stages.count("prompt_inserted"), 1)
            self.assertLess(stages.index("prompt_inserted"), stages.index("send_attempted"))
            gap = ms["send_attempted"] - ms["prompt_inserted"]
            for event in run["events"]:
                self.assertTrue(all(isinstance(value, int) for value in event["metrics"].values()))
            if name.endswith("_premature"):
                # Premature-ready rejection: these clicks happened before the gate could pass.
                self.assertLess(gap, earliest)
                self.assertIn("send_observed", stages)
                self.assertNotIn("generation_completed", stages)
                self.assertNotIn("result_applied", stages)
                self.assertEqual(stages[-1], "run_failed")
            else:
                # Eventual-ready acceptance: a completed run is reachable without refill or a second click.
                self.assertIn("result_applied", stages)
                self.assertEqual(stages[-1], "run_completed")
                self.assertLess(ms["browser_loaded"], ms["send_attempted"])
        image = runs["image_run1_premature"]["events"]
        image_ms = {event["stage"]: event["ms"] for event in image}
        # Document load alone does not separate the runs: the image failure clicked after browser_loaded.
        self.assertLess(image_ms["browser_loaded"], image_ms["send_attempted"])
        self.assertLess(image_ms["attachment_ready"], image_ms["send_attempted"])
        text_ms = {event["stage"]: event["ms"] for event in runs["text_run1_premature"]["events"]}
        self.assertLess(text_ms["send_attempted"], text_ms["browser_loaded"])

        # r4 iPhone acceptance: one click with the stable sample count, at most the bounded fills before it.
        for run in trace["deviceAcceptance"]["runs"]:
            stages = [event["stage"] for event in run["events"]]
            attempts = [event for event in run["events"] if event["stage"] == "send_attempted"]
            self.assertEqual(len(attempts), 1)
            self.assertGreaterEqual(attempts[0]["metrics"]["stable_samples"], gate["stableSamples"])
            fills = [event["metrics"]["attempt"] for event in run["events"] if event["stage"] == "prompt_inserted"]
            self.assertLessEqual(len(fills), 3)
            fill_indexes = [i for i, stage in enumerate(stages) if stage == "prompt_inserted"]
            self.assertLess(max(fill_indexes), stages.index("send_attempted"))
            self.assertEqual(fills, list(range(1, len(fills) + 1)))
            self.assertEqual(stages[-2:], ["result_applied", "run_completed"])

        # Ported gate (same constants, no timer, one-shot) in the portable runtime, its Mac profile copy and
        # Stargram Android's native claim. Trace coverage only; not physical proof on those platforms.
        for path in ("packages/runtime/aibi-browser-runtime.js",
                     "profiles/mailtranslator/distribution/macos/aibi-browser-runtime.js"):
            portable = (ROOT / path).read_text()
            submit = portable.split("RUNTIME.submitPrompt = function", 1)[1].split("RUNTIME.verifySubmission", 1)[0]
            self.assertIn(f"const GEMINI_SEND_STABLE_SAMPLES = {gate['stableSamples']};", portable)
            self.assertIn(f"const GEMINI_SEND_STABLE_MS = {gate['stableMs']};", portable)
            self.assertIn("const MAX_PROMPT_FILLS_BEFORE_SEND = 3;", portable)
            self.assertLess(submit.index("if (submitDispatched) return"), submit.index("writeQuillEditor(inputEl, lastInjectedPrompt)"))
            # Visible generation (e.g. the user sent manually) stops before any hydration rewrite.
            guard = "if (generationObserved || generationVisible(config)) {\n        generationObserved = true;\n        return notReady;"
            self.assertLess(submit.index(guard), submit.index("writeQuillEditor(inputEl, lastInjectedPrompt)"))
            self.assertLess(submit.index(guard), submit.index("submitBtn.click();"))
            self.assertEqual(portable.count("generationObserved = true;"), 1)
            self.assertNotIn("generationObserved = false;\n", portable.split("let generationObserved = false;", 1)[1])
            self.assertIn("promptFillCount >= 1 && promptFillCount < MAX_PROMPT_FILLS_BEFORE_SEND", submit)
            self.assertIn("document.readyState !== 'complete' || sendCandidateCount(config) > 1", submit)
            self.assertLess(submit.index("current !== normalize(lastInjectedPrompt)) {\n        return notReady;"),
                            submit.index("if (isGemini) {"))
            self.assertLess(submit.index("now - sendReadiness.since < GEMINI_SEND_STABLE_MS) return notReady;"),
                            submit.index("submitBtn.click();"))
            self.assertNotIn("setTimeout", submit)
            quill = portable.split("function writeQuillEditor(el, text) {", 1)[1].split("RUNTIME.injectPrompt", 1)[0]
            self.assertIn("execCommand('insertText'", quill)
            self.assertNotIn("insertHTML", quill)
            self.assertIn("if (normalizePromptText(el.innerText) !== normalizePromptText(text)) return false;", quill)
        scripts = (ROOT / "profiles/imanager/distribution/android/ExternalAIScripts.kt").read_text()
        claim = scripts.split("private fun geminiClaimGate(", 1)[1].split('""".trimIndent()', 1)[0]
        self.assertLess(claim.index("if (smGenerating) return JSON.stringify({found:false});"), claim.index("smWriteQuill(input, smExpected)"))
        self.assertIn("if (window.__sm_cancelled || window.__sm_submit_dispatched) return JSON.stringify({found:false});", claim)
        self.assertIn("if (smFills >= 1 && smFills < 3) {", claim)
        self.assertIn("composer && findSendCandidates(composer).length > 1", claim)
        self.assertIn(f"window.__sm_send_readiness.samples < {gate['stableSamples']} || smNow - window.__sm_send_readiness.since < {gate['stableMs']}", claim)
        self.assertNotIn("setTimeout", claim)
        self.assertNotIn(".click()", claim)
        self.assertIn("${if (provider == DirectAIProvider.GEMINI) geminiClaimGate(providerSelectors(provider).generating) else \"\"}", scripts)
        # Android needs no extra latch: after every claim the caller verifies (new answer or visible
        # generation) and a positive result ends claiming for the task, so a refill after generation stops
        # is unreachable. Kept as the documented guard.
        surface = (ROOT / "profiles/imanager/distribution/android/ExternalAISurface.kt").read_text()
        claiming = surface.split("suspend fun submitPromptWhenReady(", 1)[1].split("\n    }\n", 1)[0]
        self.assertLess(claiming.index("dispatchAIBISubmitOnce"), claiming.index("verifySubmissionScript"))
        self.assertLess(claiming.index("verifySubmissionScript"), claiming.index("hasSubmittedPrompt = true"))
        self.assertIn("var submitted = !window.__sm_cancelled && (countIncreased || isGenerating);", scripts)
        android_quill = scripts.split("private val quillWriteHelpers", 1)[1].split('""".trimIndent()', 1)[0]
        self.assertNotIn("insertHTML", android_quill)
        self.assertIn("if (smNorm(el.innerText) !== smNorm(text)) return false;", android_quill)

    def test_imanager_android_distribution_uses_bounded_atomic_batch(self):
        runtime = (ROOT / "profiles/imanager/distribution/android/ExternalAIScripts.kt").read_text()
        pipeline = (ROOT / "profiles/imanager/distribution/android/ExternalAIMediaPipeline.kt").read_text()
        models = (ROOT / "profiles/imanager/distribution/android/ExternalAIModels.kt").read_text()
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
        bridge = (ROOT / "profiles/imanager/distribution/android/ExternalAINativeFileBridge.kt").read_text()
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
