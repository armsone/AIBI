package com.armsone.starmanager.ui.externalai

import com.armsone.starmanager.service.DirectAIProvider
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * AIBI in-browser DOM 자동화, 관찰, 오류 감지, 미디어 첨부 스크립트 킷.
 */
object ExternalAIScripts {

    private val gson = Gson()

    /** 프롬프트 문자열을 JavaScript 안전한 문자열 리터럴로 인코딩 */
    fun jsStringLiteral(value: String): String = gson.toJson(value)

    /** 첨부 입력 또는 제공사별 중첩 메뉴를 한 단계씩 연다. 준비될 때까지 호출자가 반복한다. */
    fun prepareAttachmentInputScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        val requiresNestedFileAction = provider == DirectAIProvider.GEMINI ||
            provider == DirectAIProvider.OPEN_AI
        return """
            (function() {
                try {
                    var fileInputSelectors = [
                        'input[type="file"][accept*="image"]',
                        'input[type="file"][accept*=".jpg"]',
                        'input[type="file"][accept*=".jpeg"]',
                        'input[type="file"][accept*=".png"]',
                        'input[type="file"]'
                    ];
                    var triggerSelectors = ${config.attachTrigger};
                    var menuActionSelectors = ${config.attachmentMenuAction};
                    var menuActionTexts = ${config.attachmentMenuActionText};

                    function queryFirst(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var el = document.querySelector(selectors[i]);
                                if (el) return el;
                            } catch (e) {}
                        }
                        return null;
                    }

                    function preferredInput() {
                        var specific = queryFirst(fileInputSelectors.slice(0, 4));
                        if (specific) return specific;
                        if (!$requiresNestedFileAction || window.__sm_attachment_file_action_selected === true) {
                            return queryFirst([fileInputSelectors[4]]);
                        }
                        return null;
                    }

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        return style.display !== 'none' && style.visibility !== 'hidden' &&
                            style.opacity !== '0' && (el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0);
                    }

                    function visibleMenuAction() {
                        var direct = queryFirst(menuActionSelectors);
                        if (isVisible(direct)) return direct.closest('button,[role="menuitem"],[role="option"],li,[mat-menu-item],[data-test-id]') || direct;
                        var candidates = document.querySelectorAll('button,[role="menuitem"],[role="option"],li,[mat-menu-item],[data-test-id],div,span');
                        for (var i = 0; i < candidates.length; i++) {
                            if (!isVisible(candidates[i])) continue;
                            var text = (candidates[i].innerText || candidates[i].textContent || '').trim().toLowerCase();
                            for (var j = 0; j < menuActionTexts.length; j++) {
                                if (text === String(menuActionTexts[j]).trim().toLowerCase()) {
                                    return candidates[i].closest('button,[role="menuitem"],[role="option"],li,[mat-menu-item],[data-test-id]') || candidates[i];
                                }
                            }
                        }
                        return null;
                    }

                    var input = preferredInput();
                    if (input) {
                        return JSON.stringify({ success: true, inputFound: true, allowsMultiple: !!input.multiple, action: 'none' });
                    }
                    var menuAction = visibleMenuAction();
                    if (menuAction) {
                        window.__sm_attachment_file_action_selected = true;
                        menuAction.click();
                        return JSON.stringify({ success: true, inputFound: false, action: 'menu-action' });
                    }
                    var trigger = queryFirst(triggerSelectors);
                    if (isVisible(trigger)) {
                        trigger.click();
                        return JSON.stringify({ success: true, inputFound: false, action: 'trigger' });
                    }
                    return JSON.stringify({ success: false, inputFound: false, error: 'FILE_INPUT_NOT_FOUND' });
                } catch (e) {
                    return JSON.stringify({ success: false, inputFound: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 발견된 제공사 파일 입력을 클릭해 Android WebChromeClient의 공개 파일 패널 콜백을 연다. */
    fun openAttachmentPanelScript(provider: DirectAIProvider): String {
        val requiresNestedFileAction = provider == DirectAIProvider.GEMINI ||
            provider == DirectAIProvider.OPEN_AI
        return """
            (function() {
                try {
                    var selectors = [
                        'input[type="file"][accept*="image"]',
                        'input[type="file"][accept*=".jpg"]',
                        'input[type="file"][accept*=".jpeg"]',
                        'input[type="file"][accept*=".png"]',
                        'input[type="file"]'
                    ];
                    var limit = (!$requiresNestedFileAction || window.__sm_attachment_file_action_selected === true)
                        ? selectors.length : selectors.length - 1;
                    var input = null;
                    for (var i = 0; i < limit && !input; i++) {
                        try { input = document.querySelector(selectors[i]); } catch (_) {}
                    }
                    if (!input) return JSON.stringify({ success: false, inputFound: false, error: 'FILE_INPUT_NOT_FOUND' });
                    input.click();
                    return JSON.stringify({ success: true, inputFound: true, action: 'native-file-panel' });
                } catch (e) {
                    return JSON.stringify({ success: false, inputFound: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** Returns the viewport coordinate of the provider's hydrated image input. */
    fun attachmentImageInputTargetScript(provider: DirectAIProvider): String {
        providerSelectors(provider)
        return """
            (function() {
                var selectors = [
                    'input[type="file"][accept*="image"]',
                    'input[type="file"][accept*=".jpg"]',
                    'input[type="file"][accept*=".jpeg"]',
                    'input[type="file"][accept*=".png"]'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var elements = document.querySelectorAll(selectors[i]);
                    for (var j = 0; j < elements.length; j++) {
                        var el = elements[j];
                        var r = el.getBoundingClientRect();
                        var s = window.getComputedStyle(el);
                        if (r.width > 0 && r.height > 0 && s.display !== 'none' &&
                            s.visibility !== 'hidden' && !el.disabled) {
                            return JSON.stringify({found:true, x:(r.left+r.width/2)/window.innerWidth, y:(r.top+r.height/2)/window.innerHeight});
                        }
                    }
                }
                return JSON.stringify({found:false});
            })();
        """.trimIndent()
    }

    /** 제공사 첨부 트리거의 화면 비율 좌표. Android 네이티브 터치에만 사용한다. */
    fun attachmentTriggerTargetScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return visibleTargetScript(config.attachTrigger)
    }

    /** Opens the provider's top-level attachment trigger through its public DOM control. */
    fun openAttachmentTriggerScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                var selectors = ${config.attachTrigger};
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var elements = document.querySelectorAll(selectors[i]);
                        for (var j = 0; j < elements.length; j++) {
                            var el = elements[j];
                            var r = el.getBoundingClientRect();
                            var s = window.getComputedStyle(el);
                            if (r.width > 0 && r.height > 0 && s.display !== 'none' &&
                                s.visibility !== 'hidden' && !el.disabled && el.getAttribute('aria-disabled') !== 'true') {
                                el.click();
                                return JSON.stringify({opened:true});
                            }
                        }
                    } catch (_) {}
                }
                return JSON.stringify({opened:false});
            })();
        """.trimIndent()
    }

    /** Gemini 등의 중첩 메뉴에서 실제 파일 버튼의 화면 비율 좌표를 찾는다. */
    fun attachmentMenuActionTargetScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                var selectors = [
                    "[data-test-id='uploader-images-files-button-advanced'] button",
                    "images-files-uploader[data-test-id='uploader-images-files-button-advanced'] button"
                ].concat(${config.attachmentMenuAction});
                var allowed = ${config.attachmentMenuActionText};
                function usable(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    var s = window.getComputedStyle(el);
                    return r.width > 0 && r.height > 0 && s.display !== 'none' &&
                        s.visibility !== 'hidden' && !el.disabled && el.getAttribute('aria-disabled') !== 'true';
                }
                function result(el) {
                    var r = el.getBoundingClientRect();
                    return JSON.stringify({found:true, x:(r.left+r.width/2)/window.innerWidth, y:(r.top+r.height/2)/window.innerHeight});
                }
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var direct = document.querySelector(selectors[i]);
                        if (usable(direct)) return result(direct);
                    } catch (_) {}
                }
                var buttons = document.querySelectorAll('button,[role="menuitem"],[role="option"]');
                for (var b = 0; b < buttons.length; b++) {
                    if (!usable(buttons[b])) continue;
                    var text = (buttons[b].innerText || buttons[b].textContent || '').trim().toLowerCase();
                    for (var t = 0; t < allowed.length; t++) {
                        if (text === String(allowed[t]).trim().toLowerCase()) return result(buttons[b]);
                    }
                }
                return JSON.stringify({found:false});
            })();
        """.trimIndent()
    }

    private fun visibleTargetScript(selectorsJson: String): String = """
        (function() {
            var selectors = $selectorsJson;
            for (var i = 0; i < selectors.length; i++) {
                try {
                    var elements = document.querySelectorAll(selectors[i]);
                    for (var j = 0; j < elements.length; j++) {
                        var el = elements[j];
                        var r = el.getBoundingClientRect();
                        var s = window.getComputedStyle(el);
                        if (r.width > 0 && r.height > 0 && s.display !== 'none' &&
                            s.visibility !== 'hidden' && !el.disabled && el.getAttribute('aria-disabled') !== 'true') {
                            return JSON.stringify({found:true, x:(r.left+r.width/2)/window.innerWidth, y:(r.top+r.height/2)/window.innerHeight});
                        }
                    }
                } catch (_) {}
            }
            return JSON.stringify({found:false});
        })();
    """.trimIndent()

    fun beginAttachmentBatchScript(expectedCount: Int): String = """
        (function() {
            var count = $expectedCount;
            if (!Number.isInteger(count) || count < 1 || count > 8) {
                return JSON.stringify({ success: false, error: 'ATTACHMENT_LIMIT_EXCEEDED' });
            }
            window.__sm_attachment_batch = { expectedCount: count, files: [] };
            return JSON.stringify({ success: true, acceptedCount: 0 });
        })();
    """.trimIndent()

    /** 정규화된 사진 한 장을 브리지 크기를 제한하며 메모리 배치에 추가한다. */
    fun appendAttachmentToBatchScript(attachment: ExternalAIAttachment): String {
        val dataUrlLiteral = jsStringLiteral(attachment.dataUrl)
        val mimeLiteral = jsStringLiteral(attachment.mimeType)
        val filenameLiteral = jsStringLiteral(attachment.filename)
        return """
            (function() {
                try {
                    var batch = window.__sm_attachment_batch;
                    if (!batch || batch.files.length >= batch.expectedCount) {
                        return JSON.stringify({ success: false, error: 'ATTACHMENT_BATCH_NOT_READY' });
                    }
                    var dataUrl = $dataUrlLiteral;
                    var comma = dataUrl.indexOf(',');
                    if (comma < 0) throw new Error('INVALID_DATA_URL');
                    var header = dataUrl.slice(0, comma);
                    var payload = dataUrl.slice(comma + 1);
                    var binary = /;base64/i.test(header) ? atob(payload) : decodeURIComponent(payload);
                    var bytes = new Uint8Array(binary.length);
                    for (var b = 0; b < binary.length; b++) bytes[b] = binary.charCodeAt(b);
                    batch.files.push(new File(
                        [new Blob([bytes], { type: $mimeLiteral })],
                        $filenameLiteral,
                        { type: $mimeLiteral, lastModified: Date.now() }
                    ));
                    return JSON.stringify({ success: true, acceptedCount: batch.files.length });
                } catch (e) {
                    return JSON.stringify({ success: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 준비된 전체 배치를 input.files에 한 번만 할당해 부분 첨부를 방지한다. */
    fun commitAttachmentBatchScript(provider: DirectAIProvider): String {
        providerSelectors(provider) // 제공사 설정 존재 여부를 조기에 검증한다.
        val requiresNestedFileAction = provider == DirectAIProvider.GEMINI
        return """
            (function() {
                try {
                    var batch = window.__sm_attachment_batch;
                    if (!batch || batch.files.length !== batch.expectedCount) {
                        return JSON.stringify({ success: false, error: 'ATTACHMENT_BATCH_INCOMPLETE' });
                    }
                    var selectors = [
                        'input[type="file"][accept*="image"]',
                        'input[type="file"][accept*=".jpg"]',
                        'input[type="file"][accept*=".jpeg"]',
                        'input[type="file"][accept*=".png"]',
                        'input[type="file"]'
                    ];
                    var input = null;
                    var selectorLimit = (!$requiresNestedFileAction || window.__sm_attachment_file_action_selected === true)
                        ? selectors.length : selectors.length - 1;
                    for (var i = 0; i < selectorLimit && !input; i++) {
                        try { input = document.querySelector(selectors[i]); } catch (_) {}
                    }
                    if (!input) return JSON.stringify({ success: false, error: 'FILE_INPUT_NOT_FOUND' });
                    if (batch.expectedCount > 1 && !input.multiple) {
                        return JSON.stringify({ success: false, error: 'MULTIPLE_SELECTION_UNSUPPORTED' });
                    }
                    var transfer = new DataTransfer();
                    batch.files.forEach(function(file) { transfer.items.add(file); });
                    input.files = transfer.files;
                    input.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                    input.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                    var acceptedCount = transfer.files.length;
                    window.__sm_attachment_batch = null;
                    return JSON.stringify({ success: acceptedCount === batch.expectedCount, acceptedCount: acceptedCount });
                } catch (e) {
                    window.__sm_attachment_batch = null;
                    return JSON.stringify({ success: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 첨부 완료 여부를 한 선택자 계열의 정확한 미리보기 수로 확인한다. */
    fun checkAttachmentConfirmedScript(provider: DirectAIProvider, expectedCount: Int = 1): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var selectors = ${config.attachmentConfirmed};
                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }
                    var maximumVisibleCount = 0;
                    for (var i = 0; i < selectors.length; i++) {
                        try {
                            var els = document.querySelectorAll(selectors[i]);
                            var visibleCount = 0;
                            for (var j = 0; j < els.length; j++) {
                                if (isVisible(els[j])) visibleCount += 1;
                            }
                            maximumVisibleCount = Math.max(maximumVisibleCount, visibleCount);
                        } catch (_) {}
                    }
                    return JSON.stringify({ confirmed: maximumVisibleCount === $expectedCount, previewCount: maximumVisibleCount });
                } catch (e) {
                    return JSON.stringify({ confirmed: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 현재 어시스턴트 메시지 기준선(baseline)을 페이지 JS 전역 상태에 기록하는 스크립트 */
    fun recordBaselineScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var assistantSelectors = ${config.assistant};
                    var loginSelectors = ${config.login};
                    var challengeSelectors = ${config.challenge};

                    function queryAll(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var list = document.querySelectorAll(selectors[i]);
                                if (list && list.length > 0) return Array.from(list);
                            } catch (e) {}
                        }
                        return [];
                    }

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }

                    var assistantEls = queryAll(assistantSelectors);
                    var count = assistantEls.length;
                    var lastText = '';
                    var lastId = '';
                    if (count > 0) {
                        var lastEl = assistantEls[count - 1];
                        lastText = (lastEl.innerText || lastEl.textContent || '').trim();
                        lastId = lastEl.getAttribute('data-message-id') || lastEl.getAttribute('data-testid') || lastEl.id || '';
                    }

                    var isLogin = false;
                    for (var l = 0; l < loginSelectors.length; l++) {
                        var logEl = document.querySelector(loginSelectors[l]);
                        if (isVisible(logEl)) { isLogin = true; break; }
                    }

                    var hasChallenge = false;
                    for (var c = 0; c < challengeSelectors.length; c++) {
                        var chEl = document.querySelector(challengeSelectors[c]);
                        if (isVisible(chEl)) { hasChallenge = true; break; }
                    }

                    window.__sm_ai_baseline = {
                        count: count,
                        lastText: lastText,
                        lastId: lastId,
                        isLoggedIn: !isLogin,
                        hasChallenge: hasChallenge,
                        recordedAt: Date.now()
                    };
                    return JSON.stringify({
                        success: true,
                        data: window.__sm_ai_baseline
                    });
                } catch (e) {
                    window.__sm_ai_baseline = { count: 0, lastText: '', lastId: '', isLoggedIn: true, hasChallenge: false, recordedAt: Date.now() };
                    return JSON.stringify({ success: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 페이지 준비 상태 및 로그인/보안 챌린지 검사 스크립트 */
    fun checkReadinessScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var inputSelectors = ${config.input};
                    var loginSelectors = ${config.login};
                    var challengeSelectors = ${config.challenge};

                    function queryFirst(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var el = document.querySelector(selectors[i]);
                                if (el) return el;
                            } catch (e) {}
                        }
                        return null;
                    }

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }

                    for (var l = 0; l < loginSelectors.length; l++) {
                        var logEl = document.querySelector(loginSelectors[l]);
                        if (isVisible(logEl)) {
                            return JSON.stringify({
                                success: true,
                                data: { isReady: false, isLoggedIn: false, hasChallenge: false, reason: 'AUTH_REQUIRED' }
                            });
                        }
                    }

                    for (var c = 0; c < challengeSelectors.length; c++) {
                        var chEl = document.querySelector(challengeSelectors[c]);
                        if (isVisible(chEl)) {
                            return JSON.stringify({
                                success: true,
                                data: { isReady: false, isLoggedIn: true, hasChallenge: true, reason: 'SECURITY_CHALLENGE_PRESENTED' }
                            });
                        }
                    }

                    var inputEl = queryFirst(inputSelectors);
                    if (!inputEl) {
                        return JSON.stringify({
                            success: true,
                            data: { isReady: false, isLoggedIn: true, hasChallenge: false, reason: 'INPUT_NOT_FOUND' }
                        });
                    }

                    var isTextField = inputEl.tagName === 'TEXTAREA' || inputEl.tagName === 'INPUT';
                    var current = (isTextField ? inputEl.value : (inputEl.innerText || inputEl.textContent || '')).trim();

                    return JSON.stringify({
                        success: true,
                        data: {
                            isReady: true,
                            isLoggedIn: true,
                            hasChallenge: false,
                            hasExistingText: current.length > 0,
                            existingLength: current.length
                        }
                    });
                } catch (e) {
                    return JSON.stringify({ success: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 프롬프트 입력 스크립트 (기존 사용자 텍스트 보존, ContentEditable & 네이티브 세터 지원) */
    fun injectPromptScript(provider: DirectAIProvider, prompt: String, force: Boolean = false): String {
        val encodedPrompt = jsStringLiteral(prompt)
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var inputSelectors = ${config.input};
                    var text = $encodedPrompt;
                    var force = $force;

                    function queryFirst(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var el = document.querySelector(selectors[i]);
                                if (el) return el;
                            } catch (e) {}
                        }
                        return null;
                    }

                    var el = queryFirst(inputSelectors);
                    if (!el) {
                        return JSON.stringify({
                            success: false,
                            inputFound: false,
                            submitted: false,
                            error: 'INPUT_NOT_FOUND'
                        });
                    }

                    var isTextField = el.tagName === 'TEXTAREA' || el.tagName === 'INPUT';
                    var current = (isTextField ? el.value : (el.innerText || el.textContent || '')).trim();
                    var isPreviousAIBIDraft = current.indexOf('[내가 입력한 내용]') === 0 ||
                        current.indexOf('[상황]') === 0;

                    // 이전 시도에서 같은 문구가 남아 있으면 성공으로 인정
                    if (current === text.trim()) {
                        window.__sm_last_filled = text;
                        return JSON.stringify({ success: true, inputFound: true, submitted: false });
                    }

                    // force가 아닌 경우 사용자 직접 입력 텍스트 보존
                    if (!force && current.length > 0 && current !== window.__sm_last_filled && !isPreviousAIBIDraft) {
                        return JSON.stringify({
                            success: false,
                            inputFound: true,
                            submitted: false,
                            error: 'EXISTING_TEXT_PRESERVED'
                        });
                    }

                    el.focus();

                    if (isTextField) {
                        var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                        var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(el, text);
                        } else {
                            el.value = text;
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                    } else if (el.isContentEditable || el.getAttribute('contenteditable') === 'true') {
                        var selection = window.getSelection();
                        var range = document.createRange();
                        range.selectNodeContents(el);
                        selection.removeAllRanges();
                        selection.addRange(range);

                        var inserted = false;
                        try {
                            inserted = document.execCommand('insertText', false, text);
                        } catch (_) {
                            inserted = false;
                        }

                        if (!inserted) {
                            el.innerText = text;
                        }

                        el.dispatchEvent(new InputEvent('input', {
                            bubbles: true,
                            cancelable: true,
                            composed: true,
                            inputType: 'insertText',
                            data: text
                        }));
                        el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                    }

                    window.__sm_last_filled = text;
                    return JSON.stringify({
                        success: true,
                        inputFound: true,
                        submitted: false
                    });
                } catch (e) {
                    return JSON.stringify({
                        success: false,
                        inputFound: false,
                        submitted: false,
                        error: e.message || String(e)
                    });
                }
            })();
        """.trimIndent()
    }

    /** 단계별 전송 에스컬레이션 스크립트 */
    fun submitPromptScript(provider: DirectAIProvider, attemptNumber: Int): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var inputSelectors = ${config.input};
                    var sendSelectors = ${config.send};
                    var attempt = $attemptNumber;

                    function queryFirst(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var el = document.querySelector(selectors[i]);
                                if (el) return el;
                            } catch (e) {}
                        }
                        return null;
                    }

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }

                    var input = queryFirst(inputSelectors);
                    var btn = queryFirst(sendSelectors);

                    if (input) input.focus();

                    // 1단계: 버튼 직접 클릭
                    if (attempt === 1) {
                        if (btn && isVisible(btn) && !btn.disabled && btn.getAttribute('aria-disabled') !== 'true') {
                            btn.focus();
                            btn.click();
                            return JSON.stringify({ success: true, modality: 'BUTTON_CLICK', attempt: 1 });
                        }
                    }

                    // 2단계: 터치/포인터 및 마우스 이벤트 시퀀스
                    if (attempt === 2) {
                        if (btn && isVisible(btn)) {
                            var rect = btn.getBoundingClientRect();
                            var clientX = rect.left + rect.width / 2;
                            var clientY = rect.top + rect.height / 2;
                            var opts = { bubbles: true, cancelable: true, clientX: clientX, clientY: clientY, view: window };

                            try { btn.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (_) {}
                            try { btn.dispatchEvent(new MouseEvent('mousedown', opts)); } catch (_) {}
                            try { btn.dispatchEvent(new PointerEvent('pointerup', opts)); } catch (_) {}
                            try { btn.dispatchEvent(new MouseEvent('mouseup', opts)); } catch (_) {}
                            btn.click();
                            return JSON.stringify({ success: true, modality: 'POINTER_TOUCH_CLICK', attempt: 2 });
                        }
                    }

                    // 3단계: 폼 requestSubmit / submit
                    if (attempt === 3) {
                        var form = (input && input.closest('form')) || (btn && btn.closest('form')) || document.querySelector('form');
                        if (form) {
                            if (typeof form.requestSubmit === 'function') {
                                form.requestSubmit(btn || undefined);
                            } else {
                                form.submit();
                            }
                            return JSON.stringify({ success: true, modality: 'FORM_REQUEST_SUBMIT', attempt: 3 });
                        }
                    }

                    // 4단계+: 엔터 키 이벤트 디스패치
                    if (input) {
                        input.focus();
                        ['keydown', 'keypress', 'keyup'].forEach(function(type) {
                            input.dispatchEvent(new KeyboardEvent(type, {
                                key: 'Enter',
                                code: 'Enter',
                                keyCode: 13,
                                which: 13,
                                bubbles: true,
                                cancelable: true,
                                composed: true
                            }));
                        });
                        return JSON.stringify({ success: true, modality: 'ENTER_KEY_EVENT', attempt: attempt });
                    }

                    return JSON.stringify({ success: false, error: 'NO_SUBMIT_TARGET' });
                } catch (e) {
                    return JSON.stringify({ success: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** Android WebView에서 신뢰된 네이티브 터치를 만들기 위한 전송 버튼 중심 좌표 */
    fun submitTargetScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                var selectors = ${config.send};
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var candidates = document.querySelectorAll(selectors[i]);
                        for (var j = 0; j < candidates.length; j++) {
                            var el = candidates[j];
                            var r = el.getBoundingClientRect();
                            var s = window.getComputedStyle(el);
                            if (r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden' && !el.disabled && el.getAttribute('aria-disabled') !== 'true') {
                                return JSON.stringify({found:true, x:(r.left+r.width/2)/window.innerWidth, y:(r.top+r.height/2)/window.innerHeight});
                            }
                        }
                    } catch (_) {}
                }
                return JSON.stringify({found:false});
            })();
        """.trimIndent()
    }

    fun focusInputScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                var selectors = ${config.input};
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var el = document.querySelector(selectors[i]);
                        if (el) { el.focus(); return true; }
                    } catch (_) {}
                }
                return false;
            })();
        """.trimIndent()
    }

    /** 전송 완료 여부 확인 스크립트 */
    fun verifySubmissionScript(provider: DirectAIProvider, baselineCount: Int): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var inputSelectors = ${config.input};
                    var assistantSelectors = ${config.assistant};
                    var generatingSelectors = ${config.generating};
                    var baseline = $baselineCount;

                    function queryFirst(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var el = document.querySelector(selectors[i]);
                                if (el) return el;
                            } catch (e) {}
                        }
                        return null;
                    }

                    function queryAll(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var list = document.querySelectorAll(selectors[i]);
                                if (list && list.length > 0) return Array.from(list);
                            } catch (e) {}
                        }
                        return [];
                    }

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }

                    var inputEl = queryFirst(inputSelectors);
                    var isTextField = inputEl && (inputEl.tagName === 'TEXTAREA' || inputEl.tagName === 'INPUT');
                    var current = inputEl ? (isTextField ? inputEl.value : (inputEl.innerText || inputEl.textContent || '')).trim() : '';
                    var inputCleared = current.length === 0;

                    var assistantEls = queryAll(assistantSelectors);
                    var countIncreased = assistantEls.length > baseline;

                    var isGenerating = queryAll(generatingSelectors).some(isVisible);

                    var submitted = inputCleared || countIncreased || isGenerating;

                    return JSON.stringify({
                        success: true,
                        data: {
                            submitted: submitted,
                            inputCleared: inputCleared,
                            countIncreased: countIncreased,
                            isGeneratingVisible: isGenerating,
                            currentCount: assistantEls.length
                        }
                    });
                } catch (e) {
                    return JSON.stringify({ success: false, error: e.message || String(e) });
                }
            })();
        """.trimIndent()
    }

    /** 생성 관찰 및 답변 추출 스크립트 */
    fun extractAnswerScript(provider: DirectAIProvider): String {
        val config = providerSelectors(provider)
        return """
            (function() {
                try {
                    var baseline = window.__sm_ai_baseline || { count: 0, lastText: '', lastId: '' };
                    var assistantSelectors = ${config.assistant};
                    var generatingSelectors = ${config.generating};
                    var preCodeSelectors = ${config.preCode};
                    var errorSelectors = ${config.error};
                    var challengeSelectors = ${config.challenge};

                    function queryAll(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var list = document.querySelectorAll(selectors[i]);
                                if (list && list.length > 0) return Array.from(list);
                            } catch (e) {}
                        }
                        return [];
                    }

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }

                    // 1. 보안 챌린지 검사
                    for (var c = 0; c < challengeSelectors.length; c++) {
                        var chEl = document.querySelector(challengeSelectors[c]);
                        if (isVisible(chEl)) {
                            return JSON.stringify({
                                newAnswer: false,
                                generating: false,
                                text: '',
                                hasChallenge: true
                            });
                        }
                    }

                    // 2. 오류 배너 검사
                    for (var e = 0; e < errorSelectors.length; e++) {
                        var errEls = document.querySelectorAll(errorSelectors[e]);
                        for (var k = 0; k < errEls.length; k++) {
                            var el = errEls[k];
                            if (isVisible(el)) {
                                var t = (el.innerText || el.textContent || '').trim();
                                if (t && t.length >= 2) {
                                    var lower = t.toLowerCase();
                                    if (lower.includes('error') || lower.includes('failed') || lower.includes('unavailable') ||
                                        lower.includes('rate limit') || lower.includes('too many') || lower.includes('limit reached') ||
                                        lower.includes('try again') || lower.includes('오류') || lower.includes('실패') ||
                                        lower.includes('한도') || lower.includes('잠시 후') || lower.includes('문제') ||
                                        el.getAttribute('role') === 'alert') {
                                        return JSON.stringify({
                                            newAnswer: false,
                                            generating: false,
                                            text: '',
                                            hasError: true,
                                            error: t
                                        });
                                    }
                                }
                            }
                        }
                    }

                    // 3. 생성/스트리밍 인디케이터 검사
                    var isGenerating = queryAll(generatingSelectors).some(isVisible);

                    // 4. 어시스턴트 메시지 추출
                    var assistantEls = queryAll(assistantSelectors);
                    var currentCount = assistantEls.length;
                    if (currentCount === 0) {
                        return JSON.stringify({
                            newAnswer: false,
                            generating: isGenerating,
                            text: ''
                        });
                    }

                    var latestEl = assistantEls[currentCount - 1];

                    // pre code 블록 우선 추출
                    var rawText = '';
                    var codeEls = [];
                    for (var p = 0; p < preCodeSelectors.length; p++) {
                        try {
                            var found = latestEl.querySelectorAll(preCodeSelectors[p]);
                            if (found && found.length > 0) {
                                codeEls = Array.from(found);
                                break;
                            }
                        } catch (_) {}
                    }

                    if (codeEls.length > 0) {
                        rawText = codeEls.map(function(c) { return (c.innerText || c.textContent || '').trim(); })
                            .filter(Boolean)
                            .join('\n\n');
                    } else {
                        rawText = (latestEl.innerText || latestEl.textContent || '').trim();
                    }

                    var hasNewAnswer = rawText.length > 0 &&
                        (currentCount > baseline.count || rawText !== (baseline.lastText || ''));

                    return JSON.stringify({
                        newAnswer: hasNewAnswer,
                        generating: isGenerating,
                        text: hasNewAnswer ? rawText : ''
                    });
                } catch (err) {
                    return JSON.stringify({
                        newAnswer: false,
                        generating: false,
                        text: '',
                        error: err.message || String(err)
                    });
                }
            })();
        """.trimIndent()
    }

    /** 오류 감지 스크립트 */
    fun extractErrorScript(): String {
        return """
            (function() {
                try {
                    var errorSelectors = [
                        "[role='alert']",
                        "[data-testid*='error']",
                        "[data-testid*='toast-error']",
                        "div[class*='error-message']",
                        "div[class*='errorMessage']",
                        "div[class*='alert-danger']",
                        ".snack-bar",
                        "simple-snack-bar",
                        "div.model-response-error",
                        ".text-red-500"
                    ];
                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }
                    for (var i = 0; i < errorSelectors.length; i++) {
                        var els = document.querySelectorAll(errorSelectors[i]);
                        for (var j = 0; j < els.length; j++) {
                            var el = els[j];
                            if (isVisible(el)) {
                                var text = (el.innerText || el.textContent || '').trim();
                                if (text.length >= 2) {
                                    var lower = text.toLowerCase();
                                    if (lower.includes('error') || lower.includes('failed') || lower.includes('unavailable') ||
                                        lower.includes('rate limit') || lower.includes('too many') || lower.includes('limit reached') ||
                                        lower.includes('try again') || lower.includes('오류') || lower.includes('실패') ||
                                        lower.includes('한도') || lower.includes('잠시 후') || lower.includes('문제') ||
                                        el.getAttribute('role') === 'alert') {
                                        return JSON.stringify({ hasError: true, error: text.slice(0, 150) });
                                    }
                                }
                            }
                        }
                    }
                    return JSON.stringify({ hasError: false, error: null });
                } catch (e) {
                    return JSON.stringify({ hasError: false, error: null });
                }
            })();
        """.trimIndent()
    }

    /** 챌린지/로그인 감지 스크립트 */
    fun checkChallengeScript(): String {
        return """
            (function() {
                try {
                    var challengeSelectors = [
                        '#cf-challenge-running',
                        '#challenge-stage',
                        'div.cf-turnstile',
                        'iframe[src*="cloudflare"]',
                        'iframe[src*="turnstile"]',
                        'iframe[src*="recaptcha"]',
                        'div.g-recaptcha',
                        '#challenge-form'
                    ];
                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }
                    for (var i = 0; i < challengeSelectors.length; i++) {
                        var el = document.querySelector(challengeSelectors[i]);
                        if (isVisible(el)) {
                            return JSON.stringify({ hasChallenge: true });
                        }
                    }
                    return JSON.stringify({ hasChallenge: false });
                } catch (e) {
                    return JSON.stringify({ hasChallenge: false });
                }
            })();
        """.trimIndent()
    }

    /** 긍정적 인증 완료 여부 및 로그인/챌린지 상태 검사 스크립트 */
    fun checkAuthStatusScript(
        provider: DirectAIProvider,
        requireVisible: Boolean = true
    ): String {
        val config = providerSelectors(provider)
        val visibilityLiteral = if (requireVisible) "true" else "false"
        val strongAuthMarker = when (provider) {
            DirectAIProvider.GEMINI -> "a[href*='accounts.google.com/SignOutOptions']"
            DirectAIProvider.OPEN_AI -> "[data-testid='accounts-profile-button']"
            else -> ""
        }
        return """
            (function() {
                try {
                    var inputSelectors = ${config.input};
                    var loginSelectors = ${config.login};
                    var challengeSelectors = ${config.challenge};
                    var authMarkerSelectors = ${config.authMarkers};
                    var strongAuthMarkerSelector = ${gson.toJson(strongAuthMarker)};
                    var requireVisible = $visibilityLiteral;

                    function isVisible(el) {
                        if (!el) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                        return el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0;
                    }

                    function queryFirstVisible(selectors) {
                        for (var i = 0; i < selectors.length; i++) {
                            try {
                                var els = document.querySelectorAll(selectors[i]);
                                for (var j = 0; j < els.length; j++) {
                                    if (!requireVisible || isVisible(els[j])) return els[j];
                                }
                            } catch (e) {}
                        }
                        return null;
                    }

                    // 1. 보안 챌린지 검사
                    var challengeEl = queryFirstVisible(challengeSelectors);
                    if (challengeEl) {
                        return JSON.stringify({
                            success: true,
                            authenticated: false,
                            hasInput: false,
                            hasLogin: false,
                            hasChallenge: true,
                            reason: 'SECURITY_CHALLENGE_PRESENTED'
                        });
                    }

                    // 2. 로그인 화면/버튼 검사 (로그인 필요 상태)
                    var loginEl = queryFirstVisible(loginSelectors);
                    if (loginEl) {
                        return JSON.stringify({
                            success: true,
                            authenticated: false,
                            hasInput: false,
                            hasLogin: true,
                            hasChallenge: false,
                            reason: 'LOGIN_REQUIRED'
                        });
                    }

                    // 3. 긍정적 인증 증거 (제공사별 계정/프로필 마커) 검사
                    var authMarkerEl = queryFirstVisible(authMarkerSelectors);
                    var strongAuthMarkerEl = strongAuthMarkerSelector
                        ? document.querySelector(strongAuthMarkerSelector)
                        : null;
                    var inputEl = queryFirstVisible(inputSelectors);
                    var hasPositiveEvidence = (authMarkerEl !== null || strongAuthMarkerEl !== null);

                    if (hasPositiveEvidence) {
                        return JSON.stringify({
                            success: true,
                            authenticated: true,
                            hasInput: inputEl !== null,
                            hasLogin: false,
                            hasChallenge: false,
                            reason: 'AUTHENTICATED'
                        });
                    }

                    // 4. 아직 렌더링되지 않음 (하이드레이션 대기)
                    return JSON.stringify({
                        success: true,
                        authenticated: false,
                        hasInput: inputEl !== null,
                        hasLogin: false,
                        hasChallenge: false,
                        reason: 'NO_POSITIVE_EVIDENCE'
                    });
                } catch (e) {
                    return JSON.stringify({
                        success: false,
                        authenticated: false,
                        hasInput: false,
                        hasLogin: false,
                        hasChallenge: false,
                        error: e.message || String(e)
                    });
                }
            })();
        """.trimIndent()
    }

    // MARK: - 파싱 유틸리티

    fun parseAttachmentResult(rawResult: String?): ExternalAIAttachmentResult {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return ExternalAIAttachmentResult(success = false, error = "NO_RESPONSE")
        }
        return try {
            val element = parseJsonElement(rawResult)
                ?: return ExternalAIAttachmentResult(success = false, error = "INVALID_RESPONSE")
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                ExternalAIAttachmentResult(
                    success = obj.optBoolean("success", false),
                    inputFound = obj.optBoolean("inputFound", false),
                    acceptedCount = obj.optInt("acceptedCount", 0),
                    error = obj.optNullableString("error")
                )
            } else {
                ExternalAIAttachmentResult(success = false, error = null)
            }
        } catch (e: Exception) {
            ExternalAIAttachmentResult(success = false, error = e.message)
        }
    }

    fun parseAttachmentConfirmed(rawResult: String?): Boolean {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) return false
        return try {
            val element = parseJsonElement(rawResult) ?: return false
            if (element.isJsonObject) {
                element.asJsonObject.optBoolean("confirmed", false)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parseAttachmentPreviewCount(rawResult: String?): Int {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) return 0
        return try {
            val element = parseJsonElement(rawResult) ?: return 0
            if (element.isJsonObject) element.asJsonObject.optInt("previewCount", 0) else 0
        } catch (_: Exception) {
            0
        }
    }

    fun parseAuthCheckResult(rawResult: String?): ExternalAIAuthCheckResult {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return ExternalAIAuthCheckResult(
                success = false,
                authenticated = false,
                hasInput = false,
                hasLogin = false,
                hasChallenge = false,
                reason = "NO_RESPONSE"
            )
        }
        return try {
            val element = parseJsonElement(rawResult)
                ?: return ExternalAIAuthCheckResult(
                    success = false,
                    authenticated = false,
                    hasInput = false,
                    hasLogin = false,
                    hasChallenge = false,
                    reason = "INVALID_RESPONSE"
                )

            if (element.isJsonObject) {
                val obj = element.asJsonObject
                ExternalAIAuthCheckResult(
                    success = obj.optBoolean("success", false),
                    authenticated = obj.optBoolean("authenticated", false),
                    hasInput = obj.optBoolean("hasInput", false),
                    hasLogin = obj.optBoolean("hasLogin", false),
                    hasChallenge = obj.optBoolean("hasChallenge", false),
                    reason = obj.optNullableString("reason") ?: obj.optNullableString("error")
                )
            } else {
                ExternalAIAuthCheckResult(
                    success = false,
                    authenticated = false,
                    hasInput = false,
                    hasLogin = false,
                    hasChallenge = false,
                    reason = null
                )
            }
        } catch (e: Exception) {
            ExternalAIAuthCheckResult(
                success = false,
                authenticated = false,
                hasInput = false,
                hasLogin = false,
                hasChallenge = false,
                reason = e.message
            )
        }
    }

    fun parseInjectionResult(rawResult: String?): ExternalAIInjectionResult {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return ExternalAIInjectionResult(
                success = false,
                inputFound = false,
                submitted = false,
                error = "NO_RESPONSE"
            )
        }
        return try {
            val element = parseJsonElement(rawResult)
                ?: return ExternalAIInjectionResult(
                    success = false,
                    inputFound = false,
                    submitted = false,
                    error = "INVALID_RESPONSE"
                )

            when {
                element.isJsonObject -> {
                    val jsonObject = element.asJsonObject
                    ExternalAIInjectionResult(
                        success = jsonObject.optBoolean("success", false),
                        inputFound = jsonObject.optBoolean("inputFound", false),
                        submitted = jsonObject.optBoolean("submitted", false),
                        error = jsonObject.optNullableString("error")
                    )
                }
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    when (val text = element.asString) {
                        "INPUT_SUCCESS" -> ExternalAIInjectionResult(
                            success = true,
                            inputFound = true,
                            submitted = true
                        )
                        "INPUT_NOT_FOUND" -> ExternalAIInjectionResult(
                            success = false,
                            inputFound = false,
                            submitted = false,
                            error = "INPUT_NOT_FOUND"
                        )
                        else -> ExternalAIInjectionResult(
                            success = false,
                            inputFound = false,
                            submitted = false,
                            error = text.ifBlank { null }
                        )
                    }
                }
                else -> ExternalAIInjectionResult(
                    success = false,
                    inputFound = false,
                    submitted = false,
                    error = null
                )
            }
        } catch (e: Exception) {
            ExternalAIInjectionResult(
                success = false,
                inputFound = false,
                submitted = false,
                error = e.message
            )
        }
    }

    fun parseReadinessResult(rawResult: String?): ExternalAIReadinessResult {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return ExternalAIReadinessResult(isReady = false)
        }
        return runCatching {
            val element = parseJsonElement(rawResult)
                ?: return ExternalAIReadinessResult(isReady = false)
            val data = element.asJsonObject.getAsJsonObject("data")
                ?: return ExternalAIReadinessResult(isReady = false)
            ExternalAIReadinessResult(
                isReady = data.optBoolean("isReady", false),
                reason = data.optNullableString("reason")
            )
        }.getOrElse { ExternalAIReadinessResult(isReady = false) }
    }

    fun parseSubmissionVerified(rawResult: String?): Boolean {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) return false
        return runCatching {
            val element = parseJsonElement(rawResult) ?: return false
            element.isJsonObject &&
                element.asJsonObject.getAsJsonObject("data")?.optBoolean("submitted", false) == true
        }.getOrDefault(false)
    }

    fun parseBaselineCount(rawResult: String?): Int {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) return 0
        return runCatching {
            val element = parseJsonElement(rawResult) ?: return 0
            element.asJsonObject.getAsJsonObject("data")?.get("count")?.asInt ?: 0
        }.getOrDefault(0)
    }

    fun parseSubmitPoint(rawResult: String?): Pair<Float, Float>? {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) return null
        return runCatching {
            val element = parseJsonElement(rawResult) ?: return null
            if (!element.isJsonObject) return null
            val obj = element.asJsonObject
            if (!obj.optBoolean("found", false)) return null
            val x = obj.get("x")?.asFloat ?: return null
            val y = obj.get("y")?.asFloat ?: return null
            if (x !in 0f..1f || y !in 0f..1f) return null
            x to y
        }.getOrNull()
    }

    fun parsePollResult(rawResult: String?): ExternalAIPollResult {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return ExternalAIPollResult(newAnswer = false, generating = false, text = "")
        }
        return try {
            val element = parseJsonElement(rawResult)
                ?: return ExternalAIPollResult(newAnswer = false, generating = false, text = "")

            if (element.isJsonObject) {
                val jsonObject = element.asJsonObject
                ExternalAIPollResult(
                    newAnswer = jsonObject.optBoolean("newAnswer", false),
                    generating = jsonObject.optBoolean("generating", false),
                    text = jsonObject.optString("text", "")
                )
            } else {
                ExternalAIPollResult(newAnswer = false, generating = false, text = "")
            }
        } catch (_: Exception) {
            ExternalAIPollResult(newAnswer = false, generating = false, text = "")
        }
    }

    fun parseErrorResult(rawResult: String?): ExternalAIDomErrorResult {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return ExternalAIDomErrorResult(hasError = false, error = null)
        }
        return try {
            val element = parseJsonElement(rawResult)
                ?: return ExternalAIDomErrorResult(hasError = false, error = null)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                ExternalAIDomErrorResult(
                    hasError = obj.optBoolean("hasError", false),
                    error = obj.optNullableString("error")
                )
            } else {
                ExternalAIDomErrorResult(hasError = false, error = null)
            }
        } catch (_: Exception) {
            ExternalAIDomErrorResult(hasError = false, error = null)
        }
    }

    fun parseChallengeResult(rawResult: String?): Boolean {
        if (rawResult == null || rawResult == "null" || rawResult.isBlank()) {
            return false
        }
        return try {
            val element = parseJsonElement(rawResult) ?: return false
            if (element.isJsonObject) {
                element.asJsonObject.optBoolean("hasChallenge", false)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    // MARK: - 선택자 구성

    private data class ProviderSelectors(
        val input: String,
        val assistant: String,
        val generating: String,
        val send: String,
        val preCode: String,
        val error: String,
        val login: String,
        val challenge: String,
        val authMarkers: String,
        val attachTrigger: String,
        val attachmentMenuAction: String,
        val attachmentMenuActionText: String,
        val attachmentConfirmed: String
    )

    private fun providerSelectors(provider: DirectAIProvider): ProviderSelectors {
        return when (provider) {
            DirectAIProvider.GEMINI -> ProviderSelectors(
                input = toJsonArray(
                    listOf(
                        "div.ql-editor[contenteditable='true']",
                        "rich-textarea div[contenteditable='true']",
                        "rich-textarea p",
                        "div[aria-label*='프롬프트']",
                        "div[aria-label*='prompt' i]",
                        "div[contenteditable='true']",
                        "div[role='textbox']",
                        "textarea"
                    )
                ),
                assistant = toJsonArray(
                    listOf(
                        "model-response .markdown",
                        "message-content .markdown",
                        "div.model-response-text",
                        "div[data-test-id='model-response']",
                        "message-content",
                        "model-response",
                        ".response-container-content"
                    )
                ),
                generating = toJsonArray(
                    listOf(
                        "button[aria-label*='Stop' i]",
                        "button[aria-label*='중지' i]",
                        "button.stop-generating-button"
                    )
                ),
                send = toJsonArray(
                    listOf(
                        "button.send-button",
                        "button[aria-label*='Send' i]",
                        "button[aria-label*='보내기' i]",
                        "button[mat-icon-button][aria-label*='send' i]",
                        "button[type='submit']"
                    )
                ),
                preCode = toJsonArray(listOf("pre code", "code-block pre")),
                error = toJsonArray(
                    listOf(
                        ".error-message",
                        "[data-test-id='error-card']",
                        ".sparkle-error-container",
                        "div.model-response-error",
                        "[role='alert']"
                    )
                ),
                login = toJsonArray(
                    listOf(
                        "a[href*='accounts.google.com/ServiceLogin']",
                        "a[href*='accounts.google.com/InteractiveLogin']",
                        "a[aria-label*='Sign in' i]",
                        "button[aria-label*='Sign in' i]",
                        "button[aria-label*='로그인' i]",
                        "a[aria-label*='로그인' i]",
                        "a[href*='/signin']"
                    )
                ),
                challenge = toJsonArray(
                    listOf(
                        "iframe[src*='recaptcha']",
                        "div.g-recaptcha",
                        "#challenge-stage"
                    )
                ),
                authMarkers = toJsonArray(
                    listOf(
                        "a[aria-label*='Google 계정' i]",
                        "a[aria-label*='Google Account' i]",
                        "button[aria-label*='Google 계정' i]",
                        "button[aria-label*='Google Account' i]",
                        "gem-user-menu",
                        "a[href*='accounts.google.com/SignOutOptions']",
                        "a[href*='myaccount.google.com']",
                        "button[data-test-id='user-menu-button']"
                    )
                ),
                attachTrigger = toJsonArray(
                    listOf(
                        "button[aria-label*='Add files']",
                        "button[aria-label*='Upload']",
                        "button[aria-label*='업로드 및 도구']",
                        "gem-icon-button[arialabel*='업로드']",
                        "button[aria-label*='이미지']",
                        "button[aria-label*='사진']",
                        "button[aria-label*='파일 추가']"
                    )
                ),
                attachmentMenuAction = toJsonArray(
                    listOf(
                        "images-files-uploader[data-test-id='uploader-images-files-button-advanced'] button",
                        "[data-test-id='uploader-images-files-button-advanced'] button",
                        "button[aria-label='파일']",
                        "[role='menuitem'][aria-label='파일']",
                        "button[aria-label='Files']",
                        "[role='menuitem'][aria-label='Files']"
                    )
                ),
                attachmentMenuActionText = toJsonArray(
                    listOf("파일", "Files", "Upload files", "Upload from device")
                ),
                attachmentConfirmed = toJsonArray(
                    listOf(
                        "button[aria-label='첨부파일 닫기']",
                        "button[aria-label='Remove attachment']",
                        "button[aria-label='Remove file']",
                        "div[data-test-id*='file-preview']",
                        "div.file-preview-container",
                        "div[class*='uploader-file']"
                    )
                )
            )
            DirectAIProvider.OPEN_AI -> ProviderSelectors(
                input = toJsonArray(
                    listOf(
                        "#prompt-textarea",
                        "textarea[data-id='root']",
                        "div[contenteditable='true']#prompt-textarea",
                        "div#prompt-textarea",
                        "form div[contenteditable='true']",
                        "div[role='textbox']",
                        "textarea[data-testid]"
                    )
                ),
                assistant = toJsonArray(
                    listOf(
                        "[data-message-author-role='assistant']",
                        "div.agent-turn",
                        "div[data-testid*='conversation-turn'] .markdown"
                    )
                ),
                generating = toJsonArray(
                    listOf(
                        "button[data-testid='stop-button']",
                        "button[aria-label*='Stop' i]",
                        "button[aria-label*='중지' i]"
                    )
                ),
                send = toJsonArray(
                    listOf(
                        "#composer-submit-button",
                        "button[data-testid='send-button']",
                        "form button[type='submit']",
                        "button[aria-label*='프롬프트 보내기']",
                        "button[aria-label*='Send' i]",
                        "button[aria-label*='보내기' i]",
                        "button:has(svg[data-icon='arrow-up'])"
                    )
                ),
                preCode = toJsonArray(listOf("pre code", "div.code-block pre")),
                error = toJsonArray(
                    listOf(
                        ".text-red-500",
                        "[data-testid*='error-notification']",
                        "div.border-red-500",
                        "[data-testid*='error']",
                        "div.error-message"
                    )
                ),
                login = toJsonArray(
                    listOf(
                        "button[data-testid='login-button']",
                        "a[href*='/auth/login']",
                        "a[href*='/login']",
                        "button[data-testid='signup-button']",
                        "a[href*='/signup']",
                        "button[data-testid='welcome-login-button']",
                        "button[data-testid*='login']",
                        "a[data-testid*='login']"
                    )
                ),
                challenge = toJsonArray(
                    listOf(
                        "#cf-challenge-running",
                        "iframe[src*='challenges.cloudflare.com']",
                        "#challenge-form",
                        ".cf-turnstile",
                        "iframe[src*='turnstile']"
                    )
                ),
                authMarkers = toJsonArray(
                    listOf(
                        "button[data-testid='profile-button']",
                        "button[data-testid='user-menu-button']",
                        "button[data-testid='user-menu']",
                        "[data-testid='accounts-profile-button']",
                        "button[aria-label*='profile menu' i]",
                        "button[aria-label*='프로필 메뉴' i]"
                    )
                ),
                attachTrigger = toJsonArray(
                    listOf(
                        "button[aria-label*='Attach']",
                        "button[aria-label*='첨부']",
                        "button[data-testid='composer-plus-btn']",
                        "button[aria-label*='Add photos']",
                        "button[aria-label*='사진']"
                    )
                ),
                attachmentMenuAction = toJsonArray(emptyList()),
                attachmentMenuActionText = toJsonArray(
                    listOf("사진", "Photos", "Upload photos")
                ),
                attachmentConfirmed = toJsonArray(
                    listOf(
                        "div[data-testid*='attachment']",
                        "img[alt='Uploaded image']",
                        "button[aria-label*='uploaded image' i]",
                        "button[aria-label*='업로드한 이미지']",
                        "img[src*='/backend-api/estuary/content']",
                        "div[class*='attachment-tile']"
                    )
                )
            )
            DirectAIProvider.CLAUDE -> ProviderSelectors(
                input = toJsonArray(
                    listOf(
                        "div.ProseMirror[contenteditable='true']",
                        "div[contenteditable='true'][data-placeholder]",
                        "div[contenteditable='true'][role='textbox']",
                        "fieldset div[contenteditable='true']",
                        "div[contenteditable='true']"
                    )
                ),
                assistant = toJsonArray(
                    listOf(
                        ".font-claude-response .standard-markdown",
                        "[data-testid='transcript-row'] [data-is-streaming] .standard-markdown",
                        "[data-is-streaming] .font-claude-response-body",
                        ".font-claude-response-body",
                        "div.font-claude-message",
                        "div[data-testid*='assistant']",
                        "div[data-testid='assistant-message']",
                        ".standard-grid .font-user-message + div"
                    )
                ),
                generating = toJsonArray(
                    listOf(
                        "button[aria-label*='Stop' i]",
                        "button[aria-label*='중단' i]",
                        "button[aria-label*='중지' i]",
                        "button[aria-label*='Stop generating' i]",
                        "div[data-is-streaming='true']"
                    )
                ),
                send = toJsonArray(
                    listOf(
                        "button[aria-label='Send Message']",
                        "button[aria-label*='Send' i]",
                        "button[aria-label*='전송' i]",
                        "button[aria-label*='보내기' i]",
                        "button:has(svg[data-icon='paper-plane'])",
                        "fieldset button[type='button']:not([disabled])",
                        "button[type='submit']"
                    )
                ),
                preCode = toJsonArray(listOf("pre code", "div.code-block code")),
                error = toJsonArray(
                    listOf(
                        "div[data-testid*='error']",
                        ".bg-danger-100",
                        "div.text-danger"
                    )
                ),
                login = toJsonArray(
                    listOf(
                        "input[type='email'][name='email']",
                        "input[type='email']",
                        "a[href*='/login']",
                        "button[data-testid*='login']",
                        "button[data-testid='login-button']",
                        "a[href*='/signup']",
                        "button[data-testid*='signup']",
                        "button[data-testid='signup-button']"
                    )
                ),
                challenge = toJsonArray(
                    listOf(
                        "iframe[src*='cloudflare']",
                        "div#challenge-stage",
                        "iframe[src*='turnstile']"
                    )
                ),
                authMarkers = toJsonArray(
                    listOf(
                        "button[data-testid='user-menu-button']",
                        "button[data-testid='user-menu']"
                    )
                ),
                attachTrigger = toJsonArray(
                    listOf(
                        "button[aria-label*='Attach']",
                        "button[aria-label*='파일']",
                        "button[aria-label*='업로드']"
                    )
                ),
                attachmentMenuAction = toJsonArray(emptyList()),
                attachmentMenuActionText = toJsonArray(emptyList()),
                attachmentConfirmed = toJsonArray(
                    listOf(
                        "div[data-testid='file-thumbnail']",
                        "div[data-testid*='attachment']"
                    )
                )
            )
            DirectAIProvider.GROK -> ProviderSelectors(
                input = toJsonArray(listOf("textarea", "div[contenteditable='true']", "div[role='textbox']")),
                assistant = toJsonArray(listOf("[data-testid='grok-response']", "div.response-body", "div.message-bubble")),
                generating = toJsonArray(listOf("button[aria-label*='Stop' i]", "button[data-testid='stop-button']")),
                send = toJsonArray(listOf("button[aria-label*='Send' i]", "button[aria-label*='보내기' i]", "button[type='submit']")),
                preCode = toJsonArray(listOf("pre code")),
                error = toJsonArray(listOf(".text-destructive", "div.error-container")),
                login = toJsonArray(listOf("a[href*='/login']", "button[data-testid*='login']", "a[href*='/signin']")),
                challenge = toJsonArray(listOf("iframe[src*='challenges']")),
                authMarkers = toJsonArray(
                    listOf(
                        "button[data-testid='user-menu-button']",
                        "button[aria-label*='Account' i]"
                    )
                ),
                attachTrigger = toJsonArray(listOf("button[aria-label*='Attach']")),
                attachmentMenuAction = toJsonArray(emptyList()),
                attachmentMenuActionText = toJsonArray(emptyList()),
                attachmentConfirmed = toJsonArray(listOf("div[data-testid*='attachment']"))
            )
        }
    }

    private fun toJsonArray(items: List<String>): String {
        return gson.toJson(items)
    }

    private fun parseJsonElement(rawResult: String): JsonElement? {
        val trimmed = rawResult.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        return try {
            val parsed = JsonParser.parseString(trimmed)
            if (parsed.isJsonNull) {
                null
            } else if (parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString) {
                val innerString = parsed.asString.trim()
                if (innerString.isEmpty() || innerString == "null") {
                    parsed
                } else if (innerString.startsWith("{") || innerString.startsWith("[")) {
                    try {
                        val innerParsed = JsonParser.parseString(innerString)
                        if (innerParsed.isJsonObject || innerParsed.isJsonArray) innerParsed else parsed
                    } catch (_: Exception) {
                        parsed
                    }
                } else {
                    parsed
                }
            } else {
                parsed
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.optBoolean(key: String, defaultValue: Boolean): Boolean {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return try {
            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isBoolean) {
                    prim.asBoolean
                } else if (prim.isString) {
                    prim.asString.toBooleanStrictOrNull() ?: defaultValue
                } else {
                    defaultValue
                }
            } else {
                defaultValue
            }
        } catch (_: Exception) {
            defaultValue
        }
    }

    private fun JsonObject.optInt(key: String, defaultValue: Int): Int {
        val value = get(key) ?: return defaultValue
        if (value.isJsonNull || !value.isJsonPrimitive) return defaultValue
        return runCatching { value.asInt }.getOrDefault(defaultValue)
    }

    private fun JsonObject.optString(key: String, defaultValue: String = ""): String {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return try {
            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isString) prim.asString else prim.toString()
            } else {
                element.toString()
            }
        } catch (_: Exception) {
            defaultValue
        }
    }

    private fun JsonObject.optNullableString(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return try {
            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isString) prim.asString else prim.toString()
            } else {
                element.toString()
            }
        } catch (_: Exception) {
            null
        }
    }
}
