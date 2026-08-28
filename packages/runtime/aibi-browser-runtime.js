/**
 * AIBI — AI Browser Interface JavaScript Runtime
 * Platform-neutral in-browser automation, observation, and sanitization engine.
 *
 * Evaluated within WKWebView (Apple) and WebView (Android).
 * All public methods return JSON strings with a structured payload:
 * { success: boolean, data?: any, error?: string, code?: string }
 *
 * Date: 2026-08-28
 */

(function () {
  'use strict';

  if (window.__AIBI_RUNTIME__) {
    return;
  }

  const RUNTIME = {};

  /**
   * Helper to query first matching element from a selector list.
   */
  function queryFirst(selectors, root = document) {
    if (!selectors) return null;
    const list = Array.isArray(selectors) ? selectors : [selectors];
    for (const selector of list) {
      try {
        const el = root.querySelector(selector);
        if (el) return el;
      } catch (_) {
        // Ignore invalid selector syntax in fallback chain
      }
    }
    return null;
  }

  /**
   * Helper to query all matching elements from a selector list.
   */
  function queryAll(selectors, root = document) {
    if (!selectors) return [];
    const list = Array.isArray(selectors) ? selectors : [selectors];
    const results = [];
    for (const selector of list) {
      try {
        const els = root.querySelectorAll(selector);
        if (els && els.length > 0) {
          results.push(...Array.from(els));
        }
      } catch (_) {
        // Ignore invalid selector syntax in fallback chain
      }
    }
    return results;
  }

  /**
   * Checks if an element is visible and rendered.
   */
  function isVisible(el) {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
      return false;
    }
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  /**
   * 1. Baseline State Discovery
   * Records assistant message count and security/login states before prompt injection.
   */
  RUNTIME.getBaselineState = function (config) {
    try {
      const assistantEls = queryAll(config.selectors.assistantMessage);
      const isLoginVisible = isVisible(queryFirst(config.selectors.loginIndicator));
      const isChallengeVisible = isVisible(queryFirst(config.selectors.challengeIndicator));

      return JSON.stringify({
        success: true,
        data: {
          assistantCount: assistantEls.length,
          isLoggedIn: !isLoginVisible,
          hasChallenge: isChallengeVisible,
          currentUrl: window.location.href,
        },
      });
    } catch (err) {
      return JSON.stringify({
        success: false,
        error: String(err && err.message ? err.message : err),
      });
    }
  };

  /**
   * 2. Readiness Probe
   * Checks whether the page is ready to accept prompt injection.
   */
  RUNTIME.checkReadiness = function (config) {
    try {
      const isLoginVisible = isVisible(queryFirst(config.selectors.loginIndicator));
      if (isLoginVisible) {
        return JSON.stringify({
          success: true,
          data: {
            isReady: false,
            isLoggedIn: false,
            hasChallenge: false,
            reason: 'AUTH_REQUIRED',
          },
        });
      }

      const isChallengeVisible = isVisible(queryFirst(config.selectors.challengeIndicator));
      if (isChallengeVisible) {
        return JSON.stringify({
          success: true,
          data: {
            isReady: false,
            isLoggedIn: true,
            hasChallenge: true,
            reason: 'SECURITY_CHALLENGE_PRESENTED',
          },
        });
      }

      const inputEl = queryFirst(config.selectors.promptInput);
      if (!inputEl) {
        return JSON.stringify({
          success: true,
          data: {
            isReady: false,
            isLoggedIn: true,
            hasChallenge: false,
            reason: 'INPUT_NOT_FOUND',
          },
        });
      }

      const isContentEditable =
        inputEl.isContentEditable || inputEl.getAttribute('contenteditable') === 'true';
      const existingText = isContentEditable ? (inputEl.innerText || '').trim() : (inputEl.value || '').trim();

      return JSON.stringify({
        success: true,
        data: {
          isReady: true,
          isLoggedIn: true,
          hasChallenge: false,
          isContentEditable: isContentEditable,
          hasExistingText: existingText.length > 0,
          existingTextLength: existingText.length,
        },
      });
    } catch (err) {
      return JSON.stringify({
        success: false,
        error: String(err && err.message ? err.message : err),
      });
    }
  };

  /**
   * 3. Prompt Injection
   * Dispatches synthetic events and handles native prototype setters without overwriting
   * unprompted user text unless forced.
   */
  RUNTIME.injectPrompt = function (config, promptText, force) {
    try {
      const inputEl = queryFirst(config.selectors.promptInput);
      if (!inputEl) {
        return JSON.stringify({
          success: false,
          code: 'INPUT_NOT_FOUND',
          error: 'Target prompt input element was not found.',
        });
      }

      const isContentEditable =
        inputEl.isContentEditable || inputEl.getAttribute('contenteditable') === 'true';
      const currentText = isContentEditable ? (inputEl.innerText || '').trim() : (inputEl.value || '').trim();

      // Avoid clobbering user text unless explicit force retry is requested
      if (currentText.length > 0 && currentText !== promptText.trim() && !force) {
        return JSON.stringify({
          success: false,
          code: 'EXISTING_TEXT_PRESERVED',
          error: 'Input area already contains different text. Manual force required to overwrite.',
        });
      }

      inputEl.focus();

      if (isContentEditable) {
        // ContentEditable (e.g. Claude ProseMirror, Gemini Quill, ChatGPT rich input)
        // Select all existing content
        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(inputEl);
        selection.removeAllRanges();
        selection.addRange(range);

        // Try document.execCommand for native undo-stack & framework integration
        let inserted = false;
        try {
          inserted = document.execCommand('insertText', false, promptText);
        } catch (_) {
          inserted = false;
        }

        if (!inserted) {
          inputEl.innerText = promptText;
        }

        // Dispatch synthetic InputEvents
        inputEl.dispatchEvent(
          new InputEvent('input', {
            bubbles: true,
            cancelable: true,
            composed: true,
            inputType: 'insertText',
            data: promptText,
          })
        );
        inputEl.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
      } else {
        // HTMLInputElement / HTMLTextAreaElement
        const proto =
          inputEl instanceof HTMLTextAreaElement
            ? window.HTMLTextAreaElement.prototype
            : window.HTMLInputElement.prototype;
        const descriptor = Object.getOwnPropertyDescriptor(proto, 'value');

        if (descriptor && descriptor.set) {
          descriptor.set.call(inputEl, promptText);
        } else {
          inputEl.value = promptText;
        }

        inputEl.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
        inputEl.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
      }

      return JSON.stringify({
        success: true,
        data: {
          injectedLength: promptText.length,
          isContentEditable: isContentEditable,
        },
      });
    } catch (err) {
      return JSON.stringify({
        success: false,
        error: String(err && err.message ? err.message : err),
      });
    }
  };

  /**
   * 4. Submission Escalation
   * Escalates across multiple interaction modalities:
   * Attempt 1: Button click
   * Attempt 2: Pointer & Touch event sequence + Mouse click
   * Attempt 3: Form requestSubmit / submit
   * Attempt 4+: Enter keydown/keypress/keyup
   */
  RUNTIME.submitPrompt = function (config, attemptNumber) {
    try {
      const submitBtn = queryFirst(config.selectors.submitButton);
      const inputEl = queryFirst(config.selectors.promptInput);
      const attempt = attemptNumber || 1;

      if (attempt === 1) {
        if (submitBtn && isVisible(submitBtn) && !submitBtn.disabled) {
          submitBtn.click();
          return JSON.stringify({ success: true, data: { modality: 'BUTTON_CLICK', attempt: 1 } });
        }
      }

      if (attempt === 2) {
        if (submitBtn && isVisible(submitBtn)) {
          const rect = submitBtn.getBoundingClientRect();
          const clientX = rect.left + rect.width / 2;
          const clientY = rect.top + rect.height / 2;
          const opts = { bubbles: true, cancelable: true, clientX: clientX, clientY: clientY };

          submitBtn.dispatchEvent(new PointerEvent('pointerdown', opts));
          submitBtn.dispatchEvent(new MouseEvent('mousedown', opts));
          submitBtn.dispatchEvent(new PointerEvent('pointerup', opts));
          submitBtn.dispatchEvent(new MouseEvent('mouseup', opts));
          submitBtn.click();
          return JSON.stringify({ success: true, data: { modality: 'POINTER_TOUCH_CLICK', attempt: 2 } });
        }
      }

      if (attempt === 3) {
        const formEl = (inputEl && inputEl.closest('form')) || queryFirst('form');
        if (formEl) {
          if (typeof formEl.requestSubmit === 'function') {
            formEl.requestSubmit(submitBtn || undefined);
          } else {
            formEl.submit();
          }
          return JSON.stringify({ success: true, data: { modality: 'FORM_REQUEST_SUBMIT', attempt: 3 } });
        }
      }

      // Attempt 4+: Enter key dispatch on prompt input
      if (inputEl) {
        inputEl.focus();
        const keyOpts = {
          key: 'Enter',
          code: 'Enter',
          keyCode: 13,
          which: 13,
          bubbles: true,
          cancelable: true,
          composed: true,
        };
        inputEl.dispatchEvent(new KeyboardEvent('keydown', keyOpts));
        inputEl.dispatchEvent(new KeyboardEvent('keypress', keyOpts));
        inputEl.dispatchEvent(new KeyboardEvent('keyup', keyOpts));
        return JSON.stringify({ success: true, data: { modality: 'ENTER_KEY_EVENT', attempt: attempt } });
      }

      return JSON.stringify({
        success: false,
        error: 'No valid submission target found for escalation.',
      });
    } catch (err) {
      return JSON.stringify({
        success: false,
        error: String(err && err.message ? err.message : err),
      });
    }
  };

  /**
   * 5. Submission Verification
   * Verifies that the prompt was received: input cleared, assistant count incremented, or generating active.
   */
  RUNTIME.verifySubmission = function (config, baselineAssistantCount) {
    try {
      const inputEl = queryFirst(config.selectors.promptInput);
      const assistantEls = queryAll(config.selectors.assistantMessage);
      const stopBtn = queryFirst(config.selectors.stopButton);

      let inputCleared = false;
      if (inputEl) {
        const isContentEditable =
          inputEl.isContentEditable || inputEl.getAttribute('contenteditable') === 'true';
        const currentText = isContentEditable ? (inputEl.innerText || '').trim() : (inputEl.value || '').trim();
        inputCleared = currentText.length === 0;
      }

      const countIncreased = assistantEls.length > (baselineAssistantCount || 0);
      const isGeneratingVisible = isVisible(stopBtn);

      const isSubmitted = inputCleared || countIncreased || isGeneratingVisible;

      return JSON.stringify({
        success: true,
        data: {
          submitted: isSubmitted,
          inputCleared: inputCleared,
          countIncreased: countIncreased,
          isGeneratingVisible: isGeneratingVisible,
          currentAssistantCount: assistantEls.length,
        },
      });
    } catch (err) {
      return JSON.stringify({
        success: false,
        error: String(err && err.message ? err.message : err),
      });
    }
  };

  /**
   * 6. Generation Observation
   * Polls latest assistant message, checks generating indicator, errors, and challenges.
   */
  RUNTIME.observeGeneration = function (config, baselineAssistantCount) {
    try {
      // 1. Check for Challenge
      const isChallengeVisible = isVisible(queryFirst(config.selectors.challengeIndicator));
      if (isChallengeVisible) {
        return JSON.stringify({
          success: true,
          data: {
            phase: 'FALLBACK_REQUIRED',
            fallbackReason: 'SECURITY_CHALLENGE_PRESENTED',
            isLoggedIn: true,
            hasChallenge: true,
            isGenerating: false,
            rawText: '',
            errorMessage: null,
          },
        });
      }

      // 2. Check for In-Page Provider Error
      const errorEl = queryFirst(config.selectors.errorBanner);
      if (errorEl && isVisible(errorEl)) {
        const rawError = (errorEl.innerText || errorEl.textContent || '').trim();
        if (rawError.length > 0) {
          return JSON.stringify({
            success: true,
            data: {
              phase: 'FAILED',
              fallbackReason: 'PROVIDER_ERROR_DETECTED',
              isLoggedIn: true,
              hasChallenge: false,
              isGenerating: false,
              rawText: '',
              errorMessage: RUNTIME.sanitizeError(rawError),
            },
          });
        }
      }

      // 3. Check for Generating State Indicator
      const stopBtn = queryFirst(config.selectors.stopButton);
      const isGenerating = isVisible(stopBtn);

      // 4. Extract Assistant Response
      const assistantEls = queryAll(config.selectors.assistantMessage);
      const baseline = baselineAssistantCount || 0;
      let rawText = '';
      let hasNewAnswer = false;

      if (assistantEls.length > baseline) {
        hasNewAnswer = true;
        const latestAssistantEl = assistantEls[assistantEls.length - 1];

        // Prefer <pre><code> code blocks if present
        const preCodeEl = queryFirst(config.selectors.preCode || 'pre code', latestAssistantEl);
        if (preCodeEl && isVisible(preCodeEl)) {
          rawText = preCodeEl.innerText || preCodeEl.textContent || '';
        } else {
          rawText = latestAssistantEl.innerText || latestAssistantEl.textContent || '';
        }
      }

      return JSON.stringify({
        success: true,
        data: {
          phase: isGenerating ? 'GENERATING' : (hasNewAnswer && rawText.trim().length > 0 ? 'STABILIZING' : 'WAITING'),
          isGenerating: isGenerating,
          hasNewAnswer: hasNewAnswer,
          assistantCount: assistantEls.length,
          rawText: rawText,
          errorMessage: null,
          isLoggedIn: true,
          hasChallenge: false,
        },
      });
    } catch (err) {
      return JSON.stringify({
        success: false,
        error: String(err && err.message ? err.message : err),
      });
    }
  };

  /**
   * 7. Output Text Cleanup
   * Strips outer markdown code fences, simple headers, and provider-specific boilerplate.
   */
  RUNTIME.cleanOutput = function (rawText, providerId) {
    if (!rawText || typeof rawText !== 'string') {
      return JSON.stringify({ success: true, data: { cleanedText: '' } });
    }

    let text = rawText.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();

    // 1. Remove outer markdown code fences
    // e.g. ```markdown ... ``` or ```text ... ``` or ``` ... ```
    const fenceMatch = text.match(/^```[a-zA-Z0-9_-]*\n([\s\S]*?)\n```$/);
    if (fenceMatch) {
      text = fenceMatch[1].trim();
    } else {
      text = text.replace(/^```[a-zA-Z0-9_-]*\n?/, '').replace(/\n?```$/, '').trim();
    }

    // 2. Remove simple leading result headers
    // e.g. "글:", "본문:", "답변:", "결과:", "text:", "plaintext:", "markdown:"
    const headerRegex = /^(글|본문|답변|결과|포스팅|초안|인스타그램|text|plaintext|markdown|result|output|response)\s*[:：]\s*/i;
    text = text.replace(headerRegex, '').trim();

    // 3. Provider-specific cleanup
    if (providerId === 'grok') {
      // Clean Grok thinking duration headers if present
      text = text.replace(/^(Thought for \d+ seconds?|Thinking Process:?|Thinking:?)\s*\n*/i, '').trim();
    }

    return JSON.stringify({
      success: true,
      data: {
        cleanedText: text,
      },
    });
  };

  /**
   * 8. Error Sanitizer
   * Strips HTML tags, stack traces, URLs, and secrets; caps length near 80 characters.
   */
  RUNTIME.sanitizeError = function (rawError) {
    if (!rawError || typeof rawError !== 'string') return 'Unknown provider error';

    let clean = rawError
      .replace(/<[^>]+>/g, ' ') // Strip HTML tags
      .replace(/https?:\/\/[^\s]+/g, '[URL]') // Mask URLs
      .replace(/\bat\s+[^\n]+/g, '') // Strip stack trace lines
      .replace(/[\r\n\t]+/g, ' ') // Normalize whitespace
      .replace(/\s{2,}/g, ' ')
      .trim();

    if (clean.length > 80) {
      clean = clean.substring(0, 77).trim() + '...';
    }

    return clean || 'Provider error detected';
  };

  window.__AIBI_RUNTIME__ = RUNTIME;
})();
