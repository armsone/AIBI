//
//  AIBIEngine.swift
//  AIBI — AI Browser Interface (Apple Platform Reference)
//
//  Platform: iOS/iPadOS 15.0+ and Mac Catalyst 15.0+
//  Framework: SwiftUI & WebKit
//  Description: Complete, copy-ready reference implementation of AIBI core orchestrator,
//               lifecycle state machine, and WKWebView adapter.
//  Date: 2026-08-28
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
    public var attachmentPreview: [String]? = nil
}

public struct AIBIMediaCapabilities: Codable, Equatable {
    public let supportsImages: Bool
    public let maxImagesPerTask: Int
    public let requiresMultipleInputForBatch: Bool
}

public struct AIBIProviderConfig: Identifiable, Codable, Equatable {
    public let id: String
    public let displayName: String
    public let initialUrl: String
    public let allowedScriptOrigins: [String]
    public let allowedAuthOrigins: [String]
    public let selectors: AIBIProviderSelectors
    public var mediaCapabilities: AIBIMediaCapabilities? = nil
}

// MARK: - Session Configuration & Timers Profile

public struct AIBITimingProfile {
    public var readinessTimeout: TimeInterval = 35.0
    public var readinessCadence: TimeInterval = 0.7
    public var maxReadinessMisses: Int = 12 // ~8.4s of consecutive misses
    public var attachmentTimeout: TimeInterval = 30.0
    public var attachmentCadence: TimeInterval = 0.35
    public var submitTimeout: TimeInterval = 15.0
    public var submitCadence: TimeInterval = 0.5
    public var submitVerificationDelay: TimeInterval = 0.7
    public var visibleAutoFillTimeout: TimeInterval = 45.0
    public var observationCadence: TimeInterval = 0.7
    public var stabilityRequiredTicks: Int = 2 // 3 matching consecutive observations (~1.4s)

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

    // Runtime JS Cache
    private var runtimeJavaScript: String = ""

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

        let media = providerConfig.mediaCapabilities
        if task.attachments.count > 20 ||
            (!task.attachments.isEmpty &&
                (media?.supportsImages != true || task.attachments.count > (media?.maxImagesPerTask ?? 0))) {
            failWithError("Image attachments are not supported for this task.")
            return
        }

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
        if currentPhase != .idle {
            updatePhase(.cancelled, message: "Cancelled")
        }
        destroyHiddenBrowser()
        // Note: activeProviderId is preserved so manual paste remains available if visible view is open
    }

    public func fullReset() {
        stopAllTimers()
        generationId &+= 1
        activeTask = nil
        activeConfig = nil
        activeProviderId = nil
        pendingResult = nil
        lastErrorMessage = nil
        destroyHiddenBrowser()
        destroyVisibleBrowser()
        updatePhase(.idle, message: "Ready")
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
                await recordBaselineAndInject(generation: generation)
            } else if reason == "INPUT_NOT_FOUND" {
                consecutiveMisses += 1
                if consecutiveMisses >= timingProfile.maxReadinessMisses {
                    timer.invalidate()
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

        if !task.attachments.isEmpty {
            let attached = await attachImagesAtomically(
                task.attachments,
                config: config,
                webView: webView,
                generation: generation
            )
            guard attached else {
                if generationId == generation { escalateToVisible(reason: .attachmentFailed) }
                return
            }
        }

        // Inject prompt
        let escapedPrompt = escapeJsString(task.promptText)
        let injectScript = "window.__AIBI_RUNTIME__.injectPrompt(\(configJson(config)), '\(escapedPrompt)', \(task.forceFill))"

        do {
            let injectResult = try await evaluateScript(injectScript, on: webView)
            guard generationId == generation else { return }
            guard let json = parseJson(injectResult),
                  let success = json["success"] as? Bool, success else {
                escalateToVisible(reason: .inputMissing)
                return
            }

            // Begin submission loop
            startSubmissionLoop(generation: generation)
        } catch {
            escalateToVisible(reason: .inputMissing)
        }
    }

    private func attachImagesAtomically(
        _ attachments: [AIBIMediaAttachment],
        config: AIBIProviderConfig,
        webView: WKWebView,
        generation: UInt64
    ) async -> Bool {
        updatePhase(.attachingMedia, message: "Attaching \(attachments.count) photos...", isWaiting: true)
        let encodedConfig = configJson(config)
        let stateScript = "window.__AIBI_RUNTIME__.getAttachmentState(\(encodedConfig))"
        let baseline = (try? await evaluateScript(stateScript, on: webView)).flatMap(parseAttachmentPreviewCount) ?? 0

        _ = try? await evaluateScript("window.__AIBI_RUNTIME__.prepareAttachmentInput(\(encodedConfig))", on: webView)
        try? await Task.sleep(nanoseconds: 700_000_000)
        guard generationId == generation else { return false }

        let payload: [[String: String]] = attachments.sorted { $0.sourceIndex < $1.sourceIndex }.map {
            ["dataUrl": $0.dataURL, "mimeType": $0.mimeType, "filename": $0.filename]
        }
        guard let payloadData = try? JSONSerialization.data(withJSONObject: payload),
              let payloadJson = String(data: payloadData, encoding: .utf8),
              let attachResult = try? await evaluateScript(
                "window.__AIBI_RUNTIME__.attachImages(\(encodedConfig), \(payloadJson))",
                on: webView
              ),
              parseJson(attachResult)?["success"] as? Bool == true else {
            return false
        }

        let deadline = Date().addingTimeInterval(timingProfile.attachmentTimeout)
        while generationId == generation && Date() < deadline {
            if let state = try? await evaluateScript(stateScript, on: webView),
               let count = parseAttachmentPreviewCount(state),
               count >= baseline + attachments.count {
                return true
            }
            try? await Task.sleep(nanoseconds: UInt64(timingProfile.attachmentCadence * 1_000_000_000))
        }
        return false
    }

    private func parseAttachmentPreviewCount(_ string: String) -> Int? {
        guard let data = parseJson(string)?["data"] as? [String: Any] else { return nil }
        return data["previewCount"] as? Int
    }

    private func startSubmissionLoop(generation: UInt64) {
        stateTimer?.invalidate()
        submitAttemptCount = 1
        let startTime = Date()
        updatePhase(.submitting, message: "Sending prompt...")

        stateTimer = Timer.scheduledTimer(withTimeInterval: timingProfile.submitCadence + timingProfile.submitVerificationDelay, repeats: true) { [weak self] timer in
            Task { @MainActor [weak self] in
                guard let self = self, self.generationId == generation else {
                    timer.invalidate()
                    return
                }

                if Date().timeIntervalSince(startTime) > self.timingProfile.submitTimeout {
                    timer.invalidate()
                    self.escalateToVisible(reason: .inputMissing)
                    return
                }

                await self.performSubmitAttempt(generation: generation, timer: timer)
            }
        }
    }

    private func performSubmitAttempt(generation: UInt64, timer: Timer) async {
        guard let config = activeConfig, let webView = activeWebView else { return }

        let submitScript = "window.__AIBI_RUNTIME__.submitPrompt(\(configJson(config)), \(submitAttemptCount))"
        _ = try? await evaluateScript(submitScript, on: webView)
        guard generationId == generation else { return }

        // Wait verification delay before checking
        try? await Task.sleep(nanoseconds: UInt64(timingProfile.submitVerificationDelay * 1_000_000_000))
        guard generationId == generation else { return }

        let verifyScript = "window.__AIBI_RUNTIME__.verifySubmission(\(configJson(config)), \(baselineAssistantCount))"
        if let verifyResult = try? await evaluateScript(verifyScript, on: webView),
           let json = parseJson(verifyResult),
           let data = json["data"] as? [String: Any],
           let submitted = data["submitted"] as? Bool, submitted {
            timer.invalidate()
            startObservationLoop(generation: generation)
        } else {
            submitAttemptCount += 1
        }
    }

    // MARK: - Observation & Stability Phase

    private func startObservationLoop(generation: UInt64) {
        stateTimer?.invalidate()
        stabilityText = nil
        stabilityTickCount = 0
        updatePhase(.generating, message: "Waiting for answer...", isWaiting: true)

        stateTimer = Timer.scheduledTimer(withTimeInterval: timingProfile.observationCadence, repeats: true) { [weak self] timer in
            Task { @MainActor [weak self] in
                guard let self = self, self.generationId == generation else {
                    timer.invalidate()
                    return
                }

                await self.performObservationTick(generation: generation, timer: timer)
            }
        }
    }

    private func performObservationTick(generation: UInt64, timer: Timer) async {
        guard let config = activeConfig, let webView = activeWebView, let task = activeTask else { return }

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
                        let cleanScript = "window.__AIBI_RUNTIME__.cleanOutput('\(escapeJsString(rawText))', '\(task.providerId)')"
                        var cleaned = rawText
                        if let cleanResult = try? await evaluateScript(cleanScript, on: webView),
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
        self.pendingResult = result

        // 1. Copy to clipboard
        UIPasteboard.general.string = result.cleanedText

        // 2. Commit to host result sink before dismissal
        if let sink = resultSink {
            let commitOutcome = sink.commitResult(result)
            switch commitOutcome {
            case .success:
                updatePhase(.completed, message: "Import completed")
                dismissVisibleBrowser()
                destroyHiddenBrowser()
            case .failure(let err):
                updatePhase(.failed, message: "Host import validation failed: \(err.localizedDescription)")
            }
        } else {
            updatePhase(.completed, message: "Result ready")
            dismissVisibleBrowser()
            destroyHiddenBrowser()
        }
    }

    private func failWithError(_ message: String) {
        stopAllTimers()
        self.lastErrorMessage = message
        updatePhase(.failed, message: message)
        destroyHiddenBrowser()
    }

    private func escalateToVisible(reason: AIBIFallbackReason) {
        stopAllTimers()
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
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.alpha = 0.001
        webView.isUserInteractionEnabled = false
        webView.accessibilityElementsHidden = true
        webView.navigationDelegate = self
        parent.addSubview(webView)
        parent.sendSubviewToBack(webView)
        self.hiddenWebView = webView
    }

    private func destroyHiddenBrowser() {
        hiddenWebView?.stopLoading()
        hiddenWebView?.navigationDelegate = nil
        hiddenWebView?.removeFromSuperview()
        hiddenWebView = nil
    }

    private func presentVisibleBrowser() {
        if visibleWebView == nil {
            let webView = WKWebView(frame: .zero, configuration: webConfiguration)
            webView.navigationDelegate = self
            self.visibleWebView = webView
        }
        self.isVisibleBrowserPresented = true
    }

    private func dismissVisibleBrowser() {
        self.isVisibleBrowserPresented = false
    }

    private func destroyVisibleBrowser() {
        visibleWebView?.stopLoading()
        visibleWebView?.navigationDelegate = nil
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
        let sanitized = "Network error: \(nsError.localizedDescription)"
        failWithError(sanitized)
    }
}
