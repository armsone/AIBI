//
//  AIBIEngine.swift
//  AIBI — AI Browser Interface (Apple Platform Reference)
//
//  Platform: iOS/iPadOS 15.0+ and Mac Catalyst 15.0+
//  Framework: SwiftUI & WebKit
//  Description: Complete, copy-ready reference implementation of AIBI core orchestrator,
//               lifecycle state machine, and WKWebView adapter. Submission is one-shot per
//               task (dispatch once, then observe only) and every lifecycle stage is recorded
//               in the bounded, privacy-safe AIBIDiagnosticsStore.
//  Date: 2026-09-10
//

import Foundation
import WebKit
import Combine
import UIKit

// MARK: - Core Enums & Data Models

public enum AIBIPhase: String, Codable, Equatable {
    case idle = "IDLE"
    case initializing = "INITIALIZING"
    case navigating = "NAVIGATING"
    case readyChecking = "READY_CHECKING"
    case attachingMedia = "ATTACHING_MEDIA"
    case injectingPrompt = "INJECTING_PROMPT"
    case submitting = "SUBMITTING"
    case generating = "GENERATING"
    case stabilizing = "STABILIZING"
    case completed = "COMPLETED"
    case fallbackRequired = "FALLBACK_REQUIRED"
    case failed = "FAILED"
    case cancelled = "CANCELLED"
}

public enum AIBIFallbackReason: String, Codable, Equatable {
    case authenticationRequired = "AUTH_REQUIRED"
    case securityChallengePresented = "SECURITY_CHALLENGE_PRESENTED"
    case navigationDisallowed = "NAVIGATION_DISALLOWED"
    case inputMissing = "INPUT_NOT_FOUND"
    case attachmentFailed = "ATTACHMENT_FAILED"
    case readinessTimeout = "READINESS_TIMEOUT"
    case userInterventionRequested = "USER_INTERVENTION_REQUESTED"
}

public enum AIBIPresentationPreference: String, Codable, Equatable {
    case alwaysVisible = "ALWAYS_VISIBLE"
    case visibleWhenNeeded = "VISIBLE_WHEN_NEEDED"
}

public struct AIBITask: Identifiable, Equatable {
    public let id: UUID
    public let providerId: String
    public let promptText: String
    public let attachments: [AIBIMediaAttachment]
    public let presentation: AIBIPresentationPreference
    public let forceFill: Bool

    public init(
        id: UUID = UUID(),
        providerId: String,
        promptText: String,
        attachments: [AIBIMediaAttachment] = [],
        presentation: AIBIPresentationPreference = .visibleWhenNeeded,
        forceFill: Bool = false
    ) {
        self.id = id
        self.providerId = providerId
        self.promptText = promptText
        self.attachments = attachments
        self.presentation = presentation
        self.forceFill = forceFill
    }
}

public struct AIBIResult: Equatable {
    public let taskId: UUID
    public let providerId: String
    public let rawText: String
    public let cleanedText: String
    public let isComplete: Bool

    public init(taskId: UUID, providerId: String, rawText: String, cleanedText: String, isComplete: Bool) {
        self.taskId = taskId
        self.providerId = providerId
        self.rawText = rawText
        self.cleanedText = cleanedText
        self.isComplete = isComplete
    }
}

public struct AIBIProgress: Equatable {
    public let phase: AIBIPhase
    public let elapsedSeconds: Double
    public let statusMessage: String
    public let isWaiting: Bool

    public static let initial = AIBIProgress(
        phase: .idle,
        elapsedSeconds: 0,
        statusMessage: "Ready",
        isWaiting: false
    )
}

// MARK: - Host Result Sink & Validator Hook

public protocol AIBIResultSink: AnyObject {
    /// Validates and commits the imported result to the host application.
    /// Returning .success commits the data; .failure retains browser state for retry.
    func commitResult(_ result: AIBIResult) -> Result<Void, Error>
}

// MARK: - Provider Configuration Model

public struct AIBIProviderSelectors: Codable, Equatable {
    public let promptInput: [String]
    public let submitButton: [String]
    public let stopButton: [String]
    public let assistantMessage: [String]
    public let preCode: [String]?
    public let errorBanner: [String]
    public let loginIndicator: [String]
    public let challengeIndicator: [String]
    public var attachmentInput: [String]? = nil
    public var attachmentTrigger: [String]? = nil
    public var attachmentMenuAction: [String]? = nil
    public var attachmentMenuActionText: [String]? = nil
    public var attachmentPreview: [String]? = nil

    public init(
        promptInput: [String],
        submitButton: [String],
        stopButton: [String],
        assistantMessage: [String],
        preCode: [String]? = nil,
        errorBanner: [String],
        loginIndicator: [String],
        challengeIndicator: [String],
        attachmentInput: [String]? = nil,
        attachmentTrigger: [String]? = nil,
        attachmentMenuAction: [String]? = nil,
        attachmentMenuActionText: [String]? = nil,
        attachmentPreview: [String]? = nil
    ) {
        self.promptInput = promptInput
        self.submitButton = submitButton
        self.stopButton = stopButton
        self.assistantMessage = assistantMessage
        self.preCode = preCode
        self.errorBanner = errorBanner
        self.loginIndicator = loginIndicator
        self.challengeIndicator = challengeIndicator
        self.attachmentInput = attachmentInput
        self.attachmentTrigger = attachmentTrigger
        self.attachmentMenuAction = attachmentMenuAction
        self.attachmentMenuActionText = attachmentMenuActionText
        self.attachmentPreview = attachmentPreview
    }
}

public struct AIBIMediaCapabilities: Codable, Equatable {
    public let supportsImages: Bool
    public let maxImagesPerTask: Int
    public let requiresMultipleInputForBatch: Bool

    public init(supportsImages: Bool = false, maxImagesPerTask: Int = 0, requiresMultipleInputForBatch: Bool = true) {
        self.supportsImages = supportsImages
        self.maxImagesPerTask = maxImagesPerTask
        self.requiresMultipleInputForBatch = requiresMultipleInputForBatch
    }
}

public struct AIBIProviderConfig: Identifiable, Codable, Equatable {
    public let id: String
    public let displayName: String
    public let initialUrl: String
    public let allowedScriptOrigins: [String]
    public let allowedAuthOrigins: [String]
    public let selectors: AIBIProviderSelectors
    public var mediaCapabilities: AIBIMediaCapabilities? = nil

    public init(
        id: String,
        displayName: String,
        initialUrl: String,
        allowedScriptOrigins: [String],
        allowedAuthOrigins: [String],
        selectors: AIBIProviderSelectors,
        mediaCapabilities: AIBIMediaCapabilities? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.initialUrl = initialUrl
        self.allowedScriptOrigins = allowedScriptOrigins
        self.allowedAuthOrigins = allowedAuthOrigins
        self.selectors = selectors
        self.mediaCapabilities = mediaCapabilities
    }
}

// MARK: - Session Configuration & Timers Profile

public struct AIBITimingProfile {
    public var readinessTimeout: TimeInterval = 35.0
    public var readinessCadence: TimeInterval = 0.7
    public var maxReadinessMisses: Int = 12 // ~8.4s of consecutive misses
    public var attachmentTimeout: TimeInterval = 30.0
    public var attachmentCadence: TimeInterval = 0.35
    /// Some providers (e.g. ChatGPT) replace the composer DOM node right after attachment
    /// insertion or hydration. Bound the relocate/retry loop instead of failing on the first
    /// transient miss or on a verified-mismatched injection.
    public var promptInjectionRetryLimit: Int = 4
    public var promptInjectionRetryDelay: TimeInterval = 0.6
    public var submitTimeout: TimeInterval = 15.0
    public var submitCadence: TimeInterval = 0.5
    public var submitVerificationDelay: TimeInterval = 0.7
    public var visibleAutoFillTimeout: TimeInterval = 45.0
    public var observationCadence: TimeInterval = 0.7
    public var stabilityRequiredTicks: Int = 2 // 3 matching consecutive observations (~1.4s)
    /// Finite bound on result observation after a verified generation start (portable contract:
    /// 119 seconds). Reaching it ends the task with a clear failure instead of waiting forever.
    public var observationTimeout: TimeInterval = 119.0

    public static let `default` = AIBITimingProfile()
}

// MARK: - AIBISession (Core Orchestrator)

@MainActor
public final class AIBISession: NSObject, ObservableObject {
    @Published public private(set) var currentPhase: AIBIPhase = .idle
    @Published public private(set) var progress: AIBIProgress = .initial
    @Published public private(set) var isVisibleBrowserPresented: Bool = false
    @Published public private(set) var activeProviderId: String? = nil
    @Published public private(set) var pendingResult: AIBIResult? = nil
    @Published public private(set) var lastErrorMessage: String? = nil

    public var timingProfile: AIBITimingProfile = .default
    public weak var resultSink: AIBIResultSink?

    /// Bounded, privacy-safe on-device diagnostics. Hosts share `AIBIDiagnosticsStore.shared`
    /// with their Settings export action; set to nil to disable recording entirely.
    /// Diagnostics never change task outcome: storage failures are absorbed by the store.
    public var diagnosticsStore: AIBIDiagnosticsStore? = .shared
    public private(set) var diagnosticRunID: UUID?

    private var activeTask: AIBITask?
    private var activeConfig: AIBIProviderConfig?
    private var generationId: UInt64 = 0

    // WebViews
    private var hiddenWebView: WKWebView?
    public private(set) var visibleWebView: WKWebView?

    // Shared configuration
    private let webConfiguration: WKWebViewConfiguration

    // Timers and state counters
    private var stateTimer: Timer?
    private var elapsedTimer: Timer?
    private var taskStartTime: Date?
    private var consecutiveMisses: Int = 0
    private var submitAttemptCount: Int = 0
    private var baselineAssistantCount: Int = 0
    private var stabilityText: String? = nil
    private var stabilityTickCount: Int = 0
    private var observationStartTime: Date?
    private var pendingAttachmentURLs: [URL] = []
    private var nativeAttachmentPanelHandled = false

    // One-shot submission state. Once a send has been dispatched for this task, the session only
    // observes; it never re-clicks, re-submits, re-fills, or restarts a browser with text only.
    private var submissionDispatched = false
    private var submitAttemptInFlight = false
    /// Total composer preview count expected immediately before send (baseline + requested).
    private var expectedComposerAttachmentCount: Int? = nil

    // Runtime JS Cache
    private var runtimeJavaScript: String = ""
    private var runtimeInjectionFailureLogged = false

    public init(runtimeJs: String, configuration: WKWebViewConfiguration? = nil) {
        self.runtimeJavaScript = runtimeJs
        if let config = configuration {
            self.webConfiguration = config
        } else {
            let config = WKWebViewConfiguration()
            config.websiteDataStore = WKWebsiteDataStore.default()
            self.webConfiguration = config
        }
        super.init()
    }

    // MARK: - Public Task Entrypoints

    public func startTask(
        task: AIBITask,
        providerConfig: AIBIProviderConfig,
        hiddenContainer: UIView? = nil
    ) {
        cancelCurrentTask()

        self.activeTask = task
        self.activeConfig = providerConfig
        self.activeProviderId = task.providerId
        self.generationId &+= 1
        self.taskStartTime = Date()
        self.lastErrorMessage = nil
        self.pendingResult = nil
        self.submissionDispatched = false
        self.submitAttemptInFlight = false
        self.expectedComposerAttachmentCount = nil
        self.observationStartTime = nil
        self.runtimeInjectionFailureLogged = false
        self.diagnosticRunID = diagnosticsStore?.start(provider: task.providerId)

        let media = providerConfig.mediaCapabilities
        if task.attachments.count > 20 ||
            (!task.attachments.isEmpty &&
                (media?.supportsImages != true || task.attachments.count > (media?.maxImagesPerTask ?? 0))) {
            log("media_preparation_failed", ["expected_count": task.attachments.count])
            failWithError("Image attachments are not supported for this task.")
            return
        }
        // Attachments arrive already normalized by the host media pipeline; record counts only.
        log("media_prepared", ["prepared_count": task.attachments.count, "prompt_length": task.promptText.count])

        updatePhase(.initializing, message: "Connecting to \(providerConfig.displayName)...")
        startElapsedTimer()

        if task.presentation == .alwaysVisible {
            presentVisibleBrowser()
        } else if let hiddenContainer {
            mountHiddenBrowser(in: hiddenContainer)
        } else {
            // An unattached WKWebView is not a valid hidden automation surface.
            presentVisibleBrowser()
        }

        guard let targetUrl = URL(string: providerConfig.initialUrl) else {
            failWithError("Invalid provider URL: \(providerConfig.initialUrl)")
            return
        }

        let currentGen = self.generationId
        let webView = activeWebView
        let request = URLRequest(url: targetUrl)
        updatePhase(.navigating, message: "Loading \(providerConfig.displayName)...")
        webView?.load(request)

        // Begin readiness loop
        scheduleReadinessCheck(generation: currentGen)
    }

    public func manualCopyPrompt() {
        guard let prompt = activeTask?.promptText else { return }
        UIPasteboard.general.string = prompt
    }

    public func manualImportText(_ text: String) {
        guard let task = activeTask else { return }
        let cleaned = cleanOutputLocally(text, providerId: task.providerId)
        let result = AIBIResult(
            taskId: task.id,
            providerId: task.providerId,
            rawText: text,
            cleanedText: cleaned,
            isComplete: true
        )
        completeWithResult(result)
    }

    public func cancelCurrentTask() {
        stopAllTimers()
        generationId &+= 1
        if isRunActive { log("run_cancelled") }
        clearPendingAttachmentFiles()
        if currentPhase != .idle {
            updatePhase(.cancelled, message: "Cancelled")
        }
        destroyHiddenBrowser()
        // Note: activeProviderId is preserved so manual paste remains available if visible view is open
    }

    public func fullReset() {
        stopAllTimers()
        generationId &+= 1
        if isRunActive { log("run_cancelled") }
        clearPendingAttachmentFiles()
        // Tear down browsers while activeConfig is still set so the teardown drain can run.
        destroyHiddenBrowser()
        destroyVisibleBrowser()
        activeTask = nil
        activeConfig = nil
        activeProviderId = nil
        pendingResult = nil
        lastErrorMessage = nil
        diagnosticRunID = nil
        updatePhase(.idle, message: "Ready")
    }

    // MARK: - Diagnostics (privacy-safe, never affects task outcome)

    /// A run is active while a task may still produce a result. Terminal phases are excluded so
    /// starting the next task does not stamp a spurious cancellation on the previous run.
    private var isRunActive: Bool {
        switch currentPhase {
        case .idle, .completed, .failed, .cancelled: return false
        default: return true
        }
    }

    private func log(_ event: String, _ metrics: [String: Int] = [:]) {
        guard let store = diagnosticsStore, let runID = diagnosticRunID else { return }
        store.record(runID: runID, event: event, metrics: metrics)
    }

    /// Pulls the runtime's bounded diagnostic queue (request start/response/failure counters
    /// only) into the store. The runtime returns nothing but allowlisted stage names and integer
    /// metrics; the store validates every value again before persisting.
    private func drainRuntimeDiagnostics(webView: WKWebView) async {
        guard let store = diagnosticsStore, let runID = diagnosticRunID, let config = activeConfig else { return }
        let script = "window.__AIBI_RUNTIME__.drainDiagnostics(\(configJson(config)))"
        guard let result = try? await evaluateScript(script, on: webView),
              let json = parseJson(result),
              json["success"] as? Bool == true,
              let data = json["data"] as? [String: Any] else { return }
        for event in AIBIDiagnosticsStore.runtimeEvents(from: data["events"]) {
            store.record(runID: runID, event: event.stage, metrics: event.metrics)
        }
    }

    /// Best-effort drain before a WKWebView is torn down. The web view is captured strongly so
    /// the page survives until the callback fires; late results for a superseded run are dropped
    /// by the store because only the current run accepts events.
    private func drainRuntimeDiagnosticsBeforeTeardown(_ webView: WKWebView?) {
        guard let webView, diagnosticsStore != nil, let runID = diagnosticRunID, let config = activeConfig,
              let url = webView.url, originAllowed(url, in: config.allowedScriptOrigins) else { return }
        let script = "window.__AIBI_RUNTIME__.drainDiagnostics(\(configJson(config)))"
        webView.evaluateJavaScript(script) { [weak self] result, error in
            _ = webView
            guard error == nil, let string = result as? String else { return }
            Task { @MainActor [weak self] in
                guard let self, let store = self.diagnosticsStore,
                      let json = self.parseJson(string),
                      json["success"] as? Bool == true,
                      let data = json["data"] as? [String: Any] else { return }
                for event in AIBIDiagnosticsStore.runtimeEvents(from: data["events"]) {
                    store.record(runID: runID, event: event.stage, metrics: event.metrics)
                }
            }
        }
    }

    // MARK: - State Machine Transitions & Timers

    private func updatePhase(_ phase: AIBIPhase, message: String, isWaiting: Bool = false) {
        self.currentPhase = phase
        let elapsed = taskStartTime.map { Date().timeIntervalSince($0) } ?? 0
        self.progress = AIBIProgress(
            phase: phase,
            elapsedSeconds: elapsed,
            statusMessage: message,
            isWaiting: isWaiting
        )
    }

    private func startElapsedTimer() {
        elapsedTimer?.invalidate()
        elapsedTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self = self, self.taskStartTime != nil else { return }
                let elapsed = Date().timeIntervalSince(self.taskStartTime!)
                self.progress = AIBIProgress(
                    phase: self.currentPhase,
                    elapsedSeconds: elapsed,
                    statusMessage: self.progress.statusMessage,
                    isWaiting: self.progress.isWaiting
                )
            }
        }
    }

    private func stopAllTimers() {
        stateTimer?.invalidate()
        stateTimer = nil
        elapsedTimer?.invalidate()
        elapsedTimer = nil
    }

    // MARK: - Readiness Phase

    private func scheduleReadinessCheck(generation: UInt64) {
        stateTimer?.invalidate()
        consecutiveMisses = 0
        let startTime = Date()

        stateTimer = Timer.scheduledTimer(withTimeInterval: timingProfile.readinessCadence, repeats: true) { [weak self] timer in
            Task { @MainActor [weak self] in
                guard let self = self, self.generationId == generation else {
                    timer.invalidate()
                    return
                }

                // Check overall readiness timeout
                if Date().timeIntervalSince(startTime) > self.timingProfile.readinessTimeout {
                    timer.invalidate()
                    self.escalateToVisible(reason: .readinessTimeout)
                    return
                }

                await self.performReadinessProbe(generation: generation, timer: timer)
            }
        }
    }

    private func performReadinessProbe(generation: UInt64, timer: Timer) async {
        guard let config = activeConfig, let webView = activeWebView else { return }
        await ensureRuntimeInjected(webView: webView)

        let script = "window.__AIBI_RUNTIME__.checkReadiness(\(configJson(config)))"
        do {
            let result = try await evaluateScript(script, on: webView)
            guard generationId == generation else { return }
            guard let json = parseJson(result),
                  let success = json["success"] as? Bool, success,
                  let data = json["data"] as? [String: Any] else {
                return
            }

            let isReady = data["isReady"] as? Bool ?? false
            let isLoggedIn = data["isLoggedIn"] as? Bool ?? true
            let hasChallenge = data["hasChallenge"] as? Bool ?? false
            let reason = data["reason"] as? String

            if !isLoggedIn {
                timer.invalidate()
                escalateToVisible(reason: .authenticationRequired)
                return
            }

            if hasChallenge {
                timer.invalidate()
                escalateToVisible(reason: .securityChallengePresented)
                return
            }

            if isReady {
                timer.invalidate()
                log("composer_found", ["composer_present": 1])
                await recordBaselineAndInject(generation: generation)
            } else if reason == "INPUT_NOT_FOUND" {
                consecutiveMisses += 1
                if consecutiveMisses >= timingProfile.maxReadinessMisses {
                    timer.invalidate()
                    log("composer_missing", ["composer_present": 0, "attempt": consecutiveMisses])
                    escalateToVisible(reason: .inputMissing)
                }
            }
        } catch {
            // Silently continue until deadline
        }
    }

    // MARK: - Injection & Submission Phase

    private func recordBaselineAndInject(generation: UInt64) async {
        guard let config = activeConfig, let webView = activeWebView, let task = activeTask else { return }
        updatePhase(.injectingPrompt, message: "Preparing prompt...")

        // Record baseline
        let baselineScript = "window.__AIBI_RUNTIME__.getBaselineState(\(configJson(config)))"
        if let baselineResult = try? await evaluateScript(baselineScript, on: webView),
           let json = parseJson(baselineResult),
           let data = json["data"] as? [String: Any] {
            self.baselineAssistantCount = data["assistantCount"] as? Int ?? 0
        }
        guard generationId == generation else { return }

        if !task.attachments.isEmpty {
            let attached = await attachImagesAtomically(
                task.attachments,
                config: config,
                webView: webView,
                generation: generation
            )
            guard attached else {
                if generationId == generation {
                    log("attachment_failed", ["expected_count": task.attachments.count])
                    escalateToVisible(reason: .attachmentFailed)
                }
                return
            }
        }
        guard generationId == generation else { return }

        // Inject prompt, boundedly retrying transient misses caused by a provider replacing the
        // composer DOM node after attachments or hydration (see quirks.lateDomReplacement).
        let escapedPrompt = escapeJsString(task.promptText)
        let injectScript = "window.__AIBI_RUNTIME__.injectPrompt(\(configJson(config)), '\(escapedPrompt)', \(task.forceFill))"
        let verifyScript = "window.__AIBI_RUNTIME__.verifyPromptInjected(\(configJson(config)), '\(escapedPrompt)')"

        var attempt = 0
        while attempt < timingProfile.promptInjectionRetryLimit {
            attempt += 1
            guard generationId == generation else { return }

            guard let injectResult = try? await evaluateScript(injectScript, on: webView) else {
                try? await Task.sleep(nanoseconds: UInt64(timingProfile.promptInjectionRetryDelay * 1_000_000_000))
                continue
            }
            guard generationId == generation else { return }

            let injectJson = parseJson(injectResult)
            let injectSucceeded = injectJson?["success"] as? Bool ?? false
            let injectCode = injectJson?["code"] as? String

            // A different, non-empty user prompt is a terminal state: never overwrite it without
            // an explicit force retry, and never blindly retry it either.
            if injectCode == "EXISTING_TEXT_PRESERVED" {
                log("prompt_failed", ["attempt": attempt, "prompt_present": 1])
                escalateToVisible(reason: .inputMissing)
                return
            }

            if injectSucceeded {
                // A JS exception's absence does not prove the text landed: ChatGPT can swap the
                // composer node between injection and this verification call. Confirm the value.
                let verifyResult = try? await evaluateScript(verifyScript, on: webView)
                guard generationId == generation else { return }
                if let verifyResult,
                   let verifyJson = parseJson(verifyResult),
                   let verifyData = verifyJson["data"] as? [String: Any],
                   verifyData["matches"] as? Bool == true {
                    log("prompt_inserted", ["prompt_length": task.promptText.count, "attempt": attempt])
                    startSubmissionLoop(generation: generation)
                    return
                }
            }

            try? await Task.sleep(nanoseconds: UInt64(timingProfile.promptInjectionRetryDelay * 1_000_000_000))
        }

        guard generationId == generation else { return }
        log("prompt_failed", ["attempt": attempt, "prompt_present": 0])
        escalateToVisible(reason: .inputMissing)
    }

    private func attachImagesAtomically(
        _ attachments: [AIBIMediaAttachment],
        config: AIBIProviderConfig,
        webView: WKWebView,
        generation: UInt64
    ) async -> Bool {
        updatePhase(.attachingMedia, message: "Attaching \(attachments.count) photos...", isWaiting: true)
        log("attachment_started", ["expected_count": attachments.count])
        let encodedConfig = configJson(config)
        let stateScript = "window.__AIBI_RUNTIME__.getAttachmentState(\(encodedConfig))"
        let baseline = (try? await evaluateScript(stateScript, on: webView)).flatMap(parseAttachmentPreviewCount) ?? 0
        guard generationId == generation else { return false }
        // Count previews inside the current composer only; the same total is re-checked
        // immediately before the one-shot send.
        let expectedTotal = baseline + attachments.count
        expectedComposerAttachmentCount = expectedTotal

        if #available(iOS 18.4, *) {
            do {
                pendingAttachmentURLs = try makeTemporaryAttachmentFiles(attachments)
                nativeAttachmentPanelHandled = false
                for _ in 0..<3 where generationId == generation && !nativeAttachmentPanelHandled {
                    _ = try? await evaluateScript(
                        "window.__AIBI_RUNTIME__.prepareAttachmentInput(\(encodedConfig))",
                        on: webView
                    )
                    guard generationId == generation else { return false }
                    _ = try? await evaluateScript(
                        "window.__AIBI_RUNTIME__.openAttachmentPanel(\(encodedConfig))",
                        on: webView
                    )
                    try? await Task.sleep(nanoseconds: 700_000_000)
                }
                guard generationId == generation else { return false }
                if nativeAttachmentPanelHandled,
                   await waitForAttachmentCount(
                       expectedTotal,
                       stateScript: stateScript,
                       webView: webView,
                       generation: generation
                   ) {
                    // A preview only proves that the provider accepted the local file selection.
                    // Keep the source URLs alive through the full task: ChatGPT can continue
                    // reading them while the prompt is already visible in the conversation.
                    return true
                }
                guard generationId == generation else { return false }
                clearPendingAttachmentFiles()
            } catch {
                clearPendingAttachmentFiles()
            }
        }

        for _ in 0..<2 where generationId == generation {
            _ = try? await evaluateScript("window.__AIBI_RUNTIME__.prepareAttachmentInput(\(encodedConfig))", on: webView)
            try? await Task.sleep(nanoseconds: 700_000_000)
        }
        guard generationId == generation else { return false }

        let ordered = attachments.sorted { $0.sourceIndex < $1.sourceIndex }
        guard let beginResult = try? await evaluateScript(
            "window.__AIBI_RUNTIME__.beginAttachmentBatch(\(encodedConfig), \(ordered.count))",
            on: webView
        ), parseJson(beginResult)?["success"] as? Bool == true else {
            return false
        }
        for (index, attachment) in ordered.enumerated() {
            guard generationId == generation else { return false }
            let image = ["dataUrl": attachment.dataURL, "mimeType": attachment.mimeType, "filename": attachment.filename]
            guard let imageData = try? JSONSerialization.data(withJSONObject: image),
                  let imageJson = String(data: imageData, encoding: .utf8),
                  let staged = try? await evaluateScript(
                    "window.__AIBI_RUNTIME__.stageAttachment(\(imageJson), \(index))",
                    on: webView
                  ),
                  parseJson(staged)?["success"] as? Bool == true else {
                _ = try? await evaluateScript("window.__AIBI_RUNTIME__.clearAttachmentBatch()", on: webView)
                return false
            }
        }
        // A cancelled task must not commit a replacement upload into the provider composer.
        guard generationId == generation else {
            _ = try? await evaluateScript("window.__AIBI_RUNTIME__.clearAttachmentBatch()", on: webView)
            return false
        }
        guard let attachResult = try? await evaluateScript(
            "window.__AIBI_RUNTIME__.commitAttachmentBatch(\(encodedConfig))",
            on: webView
        ), parseJson(attachResult)?["success"] as? Bool == true else { return false }
        log("attachment_dispatched", ["attached_count": ordered.count])

        return await waitForAttachmentCount(
            expectedTotal,
            stateScript: stateScript,
            webView: webView,
            generation: generation
        )
    }

    private func waitForAttachmentCount(
        _ expectedCount: Int,
        stateScript: String,
        webView: WKWebView,
        generation: UInt64
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(timingProfile.attachmentTimeout)
        var lastObserved: Int? = nil
        while generationId == generation && Date() < deadline {
            if let state = try? await evaluateScript(stateScript, on: webView),
               let count = parseAttachmentPreviewCount(state) {
                guard generationId == generation else { return false }
                if count == expectedCount {
                    log("attachment_ready", ["attached_count": count, "expected_count": expectedCount])
                    return true
                }
                if count != lastObserved {
                    lastObserved = count
                    log("attachment_progress", ["preview_count": count, "expected_count": expectedCount])
                }
            }
            try? await Task.sleep(nanoseconds: UInt64(timingProfile.attachmentCadence * 1_000_000_000))
        }
        if generationId == generation {
            log("attachment_timeout", ["expected_count": expectedCount, "preview_count": lastObserved ?? 0])
        }
        return false
    }

    private func makeTemporaryAttachmentFiles(_ attachments: [AIBIMediaAttachment]) throws -> [URL] {
        clearPendingAttachmentFiles()
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("AIBIUploads-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        do {
            return try attachments.sorted { $0.sourceIndex < $1.sourceIndex }.map { attachment in
                let url = directory.appendingPathComponent(attachment.filename)
                try attachment.data.write(to: url, options: .atomic)
                return url
            }
        } catch {
            try? FileManager.default.removeItem(at: directory)
            throw error
        }
    }

    private func clearPendingAttachmentFiles() {
        let directories = Set(pendingAttachmentURLs.map { $0.deletingLastPathComponent() })
        pendingAttachmentURLs = []
        directories.forEach { try? FileManager.default.removeItem(at: $0) }
    }

    private func parseAttachmentPreviewCount(_ string: String) -> Int? {
        guard let data = parseJson(string)?["data"] as? [String: Any] else { return nil }
        return data["previewCount"] as? Int
    }

    /// One-shot submission. The send is dispatched at most once per task; every later tick only
    /// observes for a verified generation start (new assistant node or visible generation mark).
    /// An empty composer or a user message card is "pending", not proof that a response started.
    private func startSubmissionLoop(generation: UInt64) {
        stateTimer?.invalidate()
        submitAttemptCount = 0
        submissionDispatched = false
        submitAttemptInFlight = false
        let startTime = Date()
        updatePhase(.submitting, message: "Sending prompt...")
        log("send_ready", ["expected_count": expectedComposerAttachmentCount ?? 0])

        stateTimer = Timer.scheduledTimer(withTimeInterval: timingProfile.submitCadence + timingProfile.submitVerificationDelay, repeats: true) { [weak self] timer in
            Task { @MainActor [weak self] in
                guard let self = self, self.generationId == generation else {
                    timer.invalidate()
                    return
                }

                if Date().timeIntervalSince(startTime) > self.timingProfile.submitTimeout {
                    timer.invalidate()
                    self.handleSubmitTimeout(generation: generation)
                    return
                }

                await self.performSubmitAttempt(generation: generation, timer: timer)
            }
        }
    }

    private func handleSubmitTimeout(generation: UInt64) {
        guard generationId == generation else { return }
        if submissionDispatched {
            // The prompt (and any photos) may already be consumed by the provider. Re-sending,
            // refilling text only, or opening a fresh browser could duplicate or strip the
            // request, so end with a clear failure and a concrete user action instead.
            log("send_timeout", ["attempt": submitAttemptCount, "attachment_verified": 1])
            failPreservingBrowser("The prompt was sent, but no response started within the time limit. Check the provider page for the sent message, then share the AI diagnostics log from Settings and try again.")
        } else {
            // Nothing was dispatched: prompt and attachments are still in the composer, so the
            // visible browser can take over safely without a text-only resend.
            log("send_timeout", ["attempt": submitAttemptCount, "attachment_verified": 0])
            escalateToVisible(reason: .inputMissing)
        }
    }

    private func performSubmitAttempt(generation: UInt64, timer: Timer) async {
        guard let config = activeConfig, let webView = activeWebView else { return }
        // A tick can outlast the cadence while awaiting JS; never let two ticks race the send.
        guard !submitAttemptInFlight else { return }
        submitAttemptInFlight = true
        defer { submitAttemptInFlight = false }

        let encodedConfig = configJson(config)

        if !submissionDispatched {
            // Re-check that every requested attachment is still in the current composer right
            // before the click. Photos consumed or dropped after attach must block a text-only send.
            if let expected = expectedComposerAttachmentCount {
                let state = try? await evaluateScript("window.__AIBI_RUNTIME__.getAttachmentState(\(encodedConfig))", on: webView)
                guard generationId == generation else { return }
                let current = state.flatMap(parseAttachmentPreviewCount)
                guard current == expected else {
                    log("send_blocked", ["expected_count": expected, "preview_count": current ?? 0, "attachment_verified": 0])
                    return
                }
            }

            guard generationId == generation, !submissionDispatched else { return }
            submitAttemptCount += 1
            let submitScript = "window.__AIBI_RUNTIME__.submitPrompt(\(encodedConfig), \(submitAttemptCount))"
            let submitResult = try? await evaluateScript(submitScript, on: webView)
            guard generationId == generation else { return }

            if let submitResult,
               let json = parseJson(submitResult),
               json["success"] as? Bool == true,
               let data = json["data"] as? [String: Any],
               data["attempted"] as? Bool == true {
                submissionDispatched = true
                log("send_attempted", ["attempt": submitAttemptCount, "attachment_verified": expectedComposerAttachmentCount == nil ? 0 : 1])
            } else {
                // No send target yet (composer still hydrating, button disabled). Wait for the
                // next tick; the runtime guarantees it dispatches at most once per page anyway.
                return
            }
        }

        // Wait verification delay before checking
        try? await Task.sleep(nanoseconds: UInt64(timingProfile.submitVerificationDelay * 1_000_000_000))
        guard generationId == generation else { return }

        await drainRuntimeDiagnostics(webView: webView)
        guard generationId == generation else { return }

        let verifyScript = "window.__AIBI_RUNTIME__.verifySubmission(\(encodedConfig), \(baselineAssistantCount))"
        let verifyResult = try? await evaluateScript(verifyScript, on: webView)
        guard generationId == generation else { return }
        if let verifyResult,
           let json = parseJson(verifyResult),
           let data = json["data"] as? [String: Any],
           let submitted = data["submitted"] as? Bool, submitted {
            timer.invalidate()
            log("send_observed", ["attempt": submitAttemptCount])
            startObservationLoop(generation: generation)
        }
    }

    // MARK: - Observation & Stability Phase

    private func startObservationLoop(generation: UInt64) {
        stateTimer?.invalidate()
        stabilityText = nil
        stabilityTickCount = 0
        observationStartTime = Date()
        updatePhase(.generating, message: "Waiting for answer...", isWaiting: true)
        log("generation_started", ["generation_active": 1])

        stateTimer = Timer.scheduledTimer(withTimeInterval: timingProfile.observationCadence, repeats: true) { [weak self] timer in
            Task { @MainActor [weak self] in
                guard let self = self, self.generationId == generation else {
                    timer.invalidate()
                    return
                }

                if let started = self.observationStartTime,
                   Date().timeIntervalSince(started) > self.timingProfile.observationTimeout {
                    timer.invalidate()
                    self.log("generation_failed", ["generation_active": 0, "stable_samples": self.stabilityTickCount])
                    self.failPreservingBrowser("No answer arrived within the time limit. The current screen was preserved — check it, then try again.")
                    return
                }

                await self.performObservationTick(generation: generation, timer: timer)
            }
        }
    }

    private func performObservationTick(generation: UInt64, timer: Timer) async {
        guard let config = activeConfig, let webView = activeWebView, let task = activeTask else { return }

        await drainRuntimeDiagnostics(webView: webView)
        guard generationId == generation else { return }

        let script = "window.__AIBI_RUNTIME__.observeGeneration(\(configJson(config)), \(baselineAssistantCount))"
        do {
            let result = try await evaluateScript(script, on: webView)
            guard generationId == generation else { return }
            guard let json = parseJson(result),
                  let success = json["success"] as? Bool, success,
                  let data = json["data"] as? [String: Any] else {
                return
            }

            let phaseStr = data["phase"] as? String ?? "GENERATING"
            let isGenerating = data["isGenerating"] as? Bool ?? true
            let hasNewAnswer = data["hasNewAnswer"] as? Bool ?? false
            let rawText = data["rawText"] as? String ?? ""
            let errorMessage = data["errorMessage"] as? String

            if phaseStr == "FAILED", let err = errorMessage {
                timer.invalidate()
                log("generation_failed", ["generation_active": isGenerating ? 1 : 0, "response_length": rawText.count])
                failWithError(err)
                return
            }

            if phaseStr == "FALLBACK_REQUIRED" {
                timer.invalidate()
                escalateToVisible(reason: .securityChallengePresented)
                return
            }

            if hasNewAnswer && !isGenerating && !rawText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                // Stability Reducer
                if let prev = stabilityText, prev == rawText {
                    stabilityTickCount += 1
                    if stabilityTickCount >= timingProfile.stabilityRequiredTicks {
                        timer.invalidate()
                        log("generation_completed", ["response_length": rawText.count, "stable_samples": stabilityTickCount + 1])
                        let cleanScript = "window.__AIBI_RUNTIME__.cleanOutput('\(escapeJsString(rawText))', '\(task.providerId)')"
                        var cleaned = rawText
                        let cleanResult = try? await evaluateScript(cleanScript, on: webView)
                        guard generationId == generation else { return }
                        if let cleanResult,
                           let cleanJson = parseJson(cleanResult),
                           let cleanData = cleanJson["data"] as? [String: Any],
                           let cleanText = cleanData["cleanedText"] as? String {
                            cleaned = cleanText
                        }

                        let finalResult = AIBIResult(
                            taskId: task.id,
                            providerId: task.providerId,
                            rawText: rawText,
                            cleanedText: cleaned,
                            isComplete: true
                        )
                        completeWithResult(finalResult)
                    }
                } else {
                    stabilityText = rawText
                    stabilityTickCount = 0
                    log("generation_progress", ["response_length": rawText.count, "generation_active": 0])
                    updatePhase(.stabilizing, message: "Receiving answer...", isWaiting: true)
                }
            } else {
                stabilityText = nil
                stabilityTickCount = 0
                updatePhase(.generating, message: "Waiting for answer...", isWaiting: true)
            }
        } catch {
            // Silently continue observation
        }
    }

    // MARK: - Completion & Fallback Handling

    private func completeWithResult(_ result: AIBIResult) {
        stopAllTimers()
        // Terminal transition: invalidate any in-flight async step of this task so it cannot
        // click, attach, or report after the result has been committed.
        generationId &+= 1
        clearPendingAttachmentFiles()
        self.pendingResult = result

        // 1. Copy to clipboard
        UIPasteboard.general.string = result.cleanedText

        // 2. Commit to host result sink before dismissal
        if let sink = resultSink {
            let commitOutcome = sink.commitResult(result)
            switch commitOutcome {
            case .success:
                log("result_applied", ["response_length": result.cleanedText.count])
                log("run_completed")
                updatePhase(.completed, message: "Import completed")
                dismissVisibleBrowser()
                destroyHiddenBrowser()
            case .failure(let err):
                // Only the outcome is recorded; the host's error text never enters the log.
                log("response_rejected", ["response_length": result.cleanedText.count])
                // The rejected answer is already on the page; surface it instead of leaving it hidden.
                if !isVisibleBrowserPresented { promoteHiddenBrowserToVisible() }
                updatePhase(.failed, message: "Host import validation failed: \(err.localizedDescription)")
            }
        } else {
            log("result_applied", ["response_length": result.cleanedText.count])
            log("run_completed")
            updatePhase(.completed, message: "Result ready")
            dismissVisibleBrowser()
            destroyHiddenBrowser()
        }
    }

    private func failWithError(_ message: String) {
        stopAllTimers()
        generationId &+= 1
        clearPendingAttachmentFiles()
        self.lastErrorMessage = message
        // The message may quote provider UI text; only the failure stage is recorded.
        log("run_failed")
        updatePhase(.failed, message: message)
        destroyHiddenBrowser()
    }

    /// Same terminal transition as failWithError, but a conversation already exists on the page
    /// (a sent prompt or a generated answer) that the user may still need to see, so the running
    /// WebView is promoted instead of destroyed.
    private func failPreservingBrowser(_ message: String) {
        stopAllTimers()
        generationId &+= 1
        clearPendingAttachmentFiles()
        self.lastErrorMessage = message
        log("run_failed")
        if !isVisibleBrowserPresented { promoteHiddenBrowserToVisible() }
        updatePhase(.failed, message: message)
    }

    private func escalateToVisible(reason: AIBIFallbackReason) {
        stopAllTimers()
        // Hidden automation for this generation ends here; the visible browser is user-driven.
        generationId &+= 1
        log("manual_takeover")
        updatePhase(.fallbackRequired, message: "Opening browser for required action...")

        // Teardown hidden WebView and instantiate fresh visible WebView sharing website data store
        destroyHiddenBrowser()
        presentVisibleBrowser()

        guard let config = activeConfig, let targetUrl = URL(string: config.initialUrl) else { return }
        visibleWebView?.load(URLRequest(url: targetUrl))
    }

    // MARK: - Browser Lifecycle & Surface Helpers

    private var activeWebView: WKWebView? {
        isVisibleBrowserPresented ? visibleWebView : hiddenWebView
    }

    private func mountHiddenBrowser(in parent: UIView) {
        guard hiddenWebView == nil else { return }
        let webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 375, height: 667), configuration: webConfiguration)
        // The host container is already mounted off-screen. Keep the WKWebView itself fully
        // rendered so provider SPAs do not defer composer hydration because of near-zero alpha.
        webView.isOpaque = true
        webView.backgroundColor = .systemBackground
        webView.alpha = 1
        webView.isUserInteractionEnabled = false
        webView.accessibilityElementsHidden = true
        webView.navigationDelegate = self
        webView.uiDelegate = self
        parent.addSubview(webView)
        parent.sendSubviewToBack(webView)
        self.hiddenWebView = webView
    }

    private func destroyHiddenBrowser() {
        // Flush queued runtime request diagnostics before the page goes away.
        drainRuntimeDiagnosticsBeforeTeardown(hiddenWebView)
        hiddenWebView?.stopLoading()
        hiddenWebView?.navigationDelegate = nil
        hiddenWebView?.uiDelegate = nil
        hiddenWebView?.removeFromSuperview()
        hiddenWebView = nil
    }

    private func presentVisibleBrowser() {
        if visibleWebView == nil {
            let webView = WKWebView(frame: .zero, configuration: webConfiguration)
            webView.navigationDelegate = self
            webView.uiDelegate = self
            self.visibleWebView = webView
        }
        self.isVisibleBrowserPresented = true
    }

    /// Reuses the exact WKWebView that was running hidden automation instead of creating a fresh
    /// one, because a fresh WebView or a reload to initialUrl would discard the conversation
    /// (sent prompt or generated answer) this task already produced on the page.
    private func promoteHiddenBrowserToVisible() {
        guard let hiddenWebView else {
            presentVisibleBrowser()
            return
        }
        hiddenWebView.removeFromSuperview()
        hiddenWebView.isUserInteractionEnabled = true
        hiddenWebView.accessibilityElementsHidden = false
        hiddenWebView.frame = .zero
        self.visibleWebView = hiddenWebView
        self.hiddenWebView = nil
        self.isVisibleBrowserPresented = true
    }

    private func dismissVisibleBrowser() {
        self.isVisibleBrowserPresented = false
    }

    private func destroyVisibleBrowser() {
        drainRuntimeDiagnosticsBeforeTeardown(visibleWebView)
        visibleWebView?.stopLoading()
        visibleWebView?.navigationDelegate = nil
        visibleWebView?.uiDelegate = nil
        visibleWebView = nil
        isVisibleBrowserPresented = false
    }

    // MARK: - JavaScript Evaluation & Runtime Injection

    private func ensureRuntimeInjected(webView: WKWebView) async {
        guard !runtimeJavaScript.isEmpty,
              let url = webView.url,
              let config = activeConfig,
              originAllowed(url, in: config.allowedScriptOrigins) else { return }
        let checkScript = "typeof window.__AIBI_RUNTIME__ !== 'undefined'"
        if let exists = try? await webView.evaluateJavaScript(checkScript) as? Bool, exists {
            return
        }
        _ = try? await webView.evaluateJavaScript(runtimeJavaScript)
        if let injected = try? await webView.evaluateJavaScript(checkScript) as? Bool, injected {
            runtimeInjectionFailureLogged = false
            log("bridge_ready")
        } else if !runtimeInjectionFailureLogged {
            // Readiness probes retry injection every cadence tick; record the failure once.
            runtimeInjectionFailureLogged = true
            log("bridge_failed")
        }
    }

    private func evaluateScript(_ script: String, on webView: WKWebView) async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            webView.evaluateJavaScript(script) { result, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let str = result as? String {
                    continuation.resume(returning: str)
                } else if let result = result {
                    continuation.resume(returning: String(describing: result))
                } else {
                    continuation.resume(returning: "")
                }
            }
        }
    }

    // MARK: - Utilities

    private func configJson(_ config: AIBIProviderConfig) -> String {
        guard let data = try? JSONEncoder().encode(config),
              let str = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return str
    }

    private func parseJson(_ string: String) -> [String: Any]? {
        guard let data = string.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private func escapeJsString(_ str: String) -> String {
        return str
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
    }

    private func cleanOutputLocally(_ raw: String, providerId: String) -> String {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("```") && text.hasSuffix("```") {
            let lines = text.components(separatedBy: "\n")
            if lines.count >= 2 {
                text = lines.dropFirst().dropLast().joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return text
    }

    private func originAllowed(_ url: URL, in origins: [String]) -> Bool {
        origins.contains { candidate in
            guard let allowed = URL(string: candidate) else { return false }
            return url.scheme?.lowercased() == allowed.scheme?.lowercased()
                && url.host?.lowercased() == allowed.host?.lowercased()
                && url.port == allowed.port
        }
    }
}

// MARK: - WKNavigationDelegate Security & Origin Handling

extension AIBISession: WKNavigationDelegate {
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        // Provider uploaders and auxiliary UI can navigate child frames to about:, blob:, or
        // provider CDN origins. Scripts are never injected there, so security-gate only the
        // main-frame navigation.
        if navigationAction.targetFrame?.isMainFrame == false {
            decisionHandler(.allow)
            return
        }
        guard let url = navigationAction.request.url,
              let config = activeConfig else {
            decisionHandler(.allow)
            return
        }

        // Validate origin: allow provider script origins and visible auth origins
        let isScriptOrigin = originAllowed(url, in: config.allowedScriptOrigins)
        let isAuthOrigin = originAllowed(url, in: config.allowedAuthOrigins)

        if isScriptOrigin || isAuthOrigin {
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
            if !isVisibleBrowserPresented {
                escalateToVisible(reason: .navigationDisallowed)
            } else {
                failWithError("This sign-in page is not in the allowed origin list.")
            }
        }
    }

    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Stage only; the URL itself is never recorded.
        log("browser_loaded")
        guard let url = webView.url,
              let config = activeConfig,
              originAllowed(url, in: config.allowedScriptOrigins) else { return }
        Task { @MainActor in
            await ensureRuntimeInjected(webView: webView)
        }
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        handleNavError(error)
    }

    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        handleNavError(error)
    }

    private func handleNavError(_ error: Error) {
        let nsError = error as NSError
        // Ignore NSURLErrorCancelled (-999) from normal redirect handoffs
        if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
            return
        }
        log("browser_load_failed")
        let sanitized = "Network error: \(nsError.localizedDescription)"
        failWithError(sanitized)
    }

    /// WebKit silently kills the WebContent process without calling didFail/didFailProvisionalNavigation, so this must invalidate the run the same way other terminal failures do.
    public func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        guard webView === hiddenWebView || webView === visibleWebView else { return }
        log("browser_load_failed")
        failWithError("The browser was interrupted. Please try again.")
    }
}

// MARK: - Public WebKit File Panel Bridge

extension AIBISession: WKUIDelegate {
    @available(iOS 18.4, *)
    public func webView(
        _ webView: WKWebView,
        runOpenPanelWith parameters: WKOpenPanelParameters,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping @MainActor @Sendable ([URL]?) -> Void
    ) {
        let urls = pendingAttachmentURLs
        guard !urls.isEmpty, urls.count == 1 || parameters.allowsMultipleSelection else {
            if !urls.isEmpty {
                log("attachment_input_missing", ["expected_count": urls.count, "input_count": 1])
            }
            completionHandler(nil)
            return
        }
        nativeAttachmentPanelHandled = true
        log("attachment_dispatched", ["attached_count": urls.count])
        completionHandler(urls)
    }
}
