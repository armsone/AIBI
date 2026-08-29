'use strict';

// Regression coverage for the ChatGPT lateDomReplacement quirk: injectPrompt succeeding with
// no JS exception must not be trusted on its own, and a different pre-existing user prompt
// must stay a terminal, non-retryable outcome. See
// fixtures/providers/chatgpt-late-dom-replacement-injection.json for the sanitized device trace
// this test encodes.

const assert = require('assert');
const path = require('path');

global.HTMLTextAreaElement = function () {};
global.HTMLTextAreaElement.prototype = {};

global.HTMLInputElement = function () {};
global.HTMLInputElement.prototype = {};
Object.defineProperty(HTMLInputElement.prototype, 'value', {
  configurable: true,
  get() { return this._v || ''; },
  set(v) { this._v = v; },
});

global.window = {
  getComputedStyle() { return { display: 'block', visibility: 'visible', opacity: '1' }; },
  getSelection() { return { removeAllRanges() {}, addRange() {} }; },
  HTMLTextAreaElement: global.HTMLTextAreaElement,
  HTMLInputElement: global.HTMLInputElement,
};

function makeComposer(initialValue) {
  const el = Object.create(HTMLInputElement.prototype);
  el._v = initialValue;
  el.focus = function () {};
  el.dispatchEvent = function () { return true; };
  el.getAttribute = function () { return null; };
  return el;
}

let composer = null;
global.document = {
  querySelector(selector) {
    return selector === 'textarea.composer' ? composer : null;
  },
  querySelectorAll() { return []; },
};

require(path.resolve(__dirname, '..', 'packages', 'runtime', 'aibi-browser-runtime.js'));
const runtime = window.__AIBI_RUNTIME__;
const config = { selectors: { promptInput: ['textarea.composer'] } };

// 1. Composer missing entirely: transient, and reported as a structured code, not a JS exception.
composer = null;
const missing = JSON.parse(runtime.injectPrompt(config, 'hello', false));
assert.strictEqual(missing.success, false);
assert.strictEqual(missing.code, 'INPUT_NOT_FOUND');
const missingVerify = JSON.parse(runtime.verifyPromptInjected(config, 'hello'));
assert.strictEqual(missingVerify.success, false);
assert.strictEqual(missingVerify.code, 'INPUT_NOT_FOUND');

// 2. Composer present and empty: injection succeeds and verification confirms the exact text.
composer = makeComposer('');
const injected = JSON.parse(runtime.injectPrompt(config, 'denim jacket detail request', false));
assert.strictEqual(injected.success, true);
const verified = JSON.parse(runtime.verifyPromptInjected(config, 'denim jacket detail request'));
assert.strictEqual(verified.success, true);
assert.strictEqual(verified.data.matches, true);

// 3. lateDomReplacement: injectPrompt reported success with no exception, but ChatGPT swaps the
// composer node right after (simulated here as the same node losing its value). verifyPromptInjected
// must catch the silently dropped text so the host retries instead of assuming success.
composer.value = '';
const verifiedAfterReplacement = JSON.parse(runtime.verifyPromptInjected(config, 'denim jacket detail request'));
assert.strictEqual(verifiedAfterReplacement.success, true);
assert.strictEqual(verifiedAfterReplacement.data.matches, false);

// 4. A different, non-empty user prompt is a terminal state without force...
composer.value = 'user is already typing something else';
const blocked = JSON.parse(runtime.injectPrompt(config, 'denim jacket detail request', false));
assert.strictEqual(blocked.success, false);
assert.strictEqual(blocked.code, 'EXISTING_TEXT_PRESERVED');
assert.strictEqual(composer.value, 'user is already typing something else');

// ...but force explicitly overwrites it.
const forced = JSON.parse(runtime.injectPrompt(config, 'denim jacket detail request', true));
assert.strictEqual(forced.success, true);
assert.strictEqual(composer.value, 'denim jacket detail request');
