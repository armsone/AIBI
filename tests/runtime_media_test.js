'use strict';

const assert = require('assert');
const path = require('path');

class FakeFile {
  constructor(parts, name, options) {
    this.parts = parts;
    this.name = name;
    this.type = options.type;
  }
}

class FakeDataTransfer {
  constructor() {
    const files = [];
    this.files = files;
    this.items = { add(file) { files.push(file); } };
  }
}

let previews = [];
const input = {
  disabled: false,
  multiple: true,
  files: [],
  getAttribute(name) { return name === 'accept' ? 'image/*' : null; },
  dispatchEvent(event) {
    if (event.type === 'change') {
      previews = Array.from(this.files, () => visibleElement());
    }
    return true;
  },
};

function visibleElement() {
  return {
    offsetWidth: 20,
    offsetHeight: 20,
    getClientRects() { return [{}]; },
    getBoundingClientRect() { return { width: 20, height: 20 }; },
  };
}

global.File = FakeFile;
global.DataTransfer = FakeDataTransfer;
global.window = {
  getComputedStyle() { return { display: 'block', visibility: 'visible', opacity: '1' }; },
};
global.document = {
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; },
  querySelectorAll(selector) {
    if (selector === 'input.image') return [input];
    if (selector === '.preview') return previews;
    return [];
  },
};

require(path.resolve(__dirname, '..', 'packages', 'runtime', 'aibi-browser-runtime.js'));
const runtime = window.__AIBI_RUNTIME__;
const config = {
  mediaCapabilities: { supportsImages: true, maxImagesPerTask: 20, requiresMultipleInputForBatch: true },
  selectors: { attachmentInput: ['input.image'], attachmentTrigger: [], attachmentPreview: ['.preview'] },
};
const images = Array.from({ length: 20 }, (_, index) => ({
  dataUrl: 'data:image/jpeg;base64,AA==',
  mimeType: 'image/jpeg',
  filename: `aibi-${String(index + 1).padStart(2, '0')}.jpg`,
}));

const attached = JSON.parse(runtime.attachImages(config, images));
assert.strictEqual(attached.success, true);
assert.strictEqual(attached.data.acceptedCount, 20);
const state = JSON.parse(runtime.getAttachmentState(config));
assert.strictEqual(state.data.previewCount, 20);
assert.deepStrictEqual(input.files.map((file) => file.name), images.map((image) => image.filename));

input.multiple = false;
const rejected = JSON.parse(runtime.attachImages(config, images));
assert.strictEqual(rejected.success, false);
assert.strictEqual(rejected.code, 'MULTIPLE_SELECTION_UNSUPPORTED');

input.multiple = true;
previews = [];
assert.strictEqual(JSON.parse(runtime.beginAttachmentBatch(config, 20)).success, true);
images.forEach((image, index) => {
  const staged = JSON.parse(runtime.stageAttachment(image, index));
  assert.strictEqual(staged.success, true);
  assert.strictEqual(staged.data.stagedCount, index + 1);
});
const committed = JSON.parse(runtime.commitAttachmentBatch(config));
assert.strictEqual(committed.success, true);
assert.strictEqual(committed.data.acceptedCount, 20);
assert.strictEqual(JSON.parse(runtime.getAttachmentState(config)).data.previewCount, 20);

assert.strictEqual(JSON.parse(runtime.beginAttachmentBatch(config, 20)).success, true);
const outOfOrder = JSON.parse(runtime.stageAttachment(images[1], 1));
assert.strictEqual(outOfOrder.success, false);
assert.strictEqual(outOfOrder.code, 'ATTACHMENT_ORDER_MISMATCH');
assert.strictEqual(JSON.parse(runtime.beginAttachmentBatch(config, 21)).code, 'ATTACHMENT_LIMIT_EXCEEDED');

// Gemini mobile exposes a nested flow: upload/tools trigger -> visible Files action -> input.
let nestedMenuOpen = false;
let nestedInputReady = false;
const nestedTrigger = {
  innerText: '',
  textContent: '',
  click() { nestedMenuOpen = true; },
  getAttribute(name) { return name === 'aria-label' ? '업로드 및 도구' : null; },
  getBoundingClientRect() { return { width: 20, height: 20 }; },
};
const nestedFileAction = {
  innerText: '파일',
  textContent: '파일',
  click() { nestedInputReady = true; },
  getAttribute() { return null; },
  getBoundingClientRect() { return { width: 80, height: 40 }; },
};
const nestedInput = {
  disabled: false,
  multiple: true,
  getAttribute(name) { return name === 'accept' ? 'image/*' : null; },
};
document.querySelectorAll = function (selector) {
  if (selector === 'input.nested') return nestedInputReady ? [nestedInput] : [];
  if (selector === 'button.trigger') return [nestedTrigger];
  if (selector === 'button' || selector === "[role='menuitem']" || selector === "[role='option']" ||
      selector === '[mat-menu-item]' || selector === '[data-test-id]') {
    return nestedMenuOpen && !nestedInputReady ? [nestedFileAction] : [];
  }
  return [];
};
const nestedConfig = {
  selectors: {
    attachmentInput: ['input.nested'],
    attachmentTrigger: ['button.trigger'],
    attachmentMenuAction: [],
    attachmentMenuActionText: ['파일'],
    attachmentPreview: [],
  },
};
const firstPrepare = JSON.parse(runtime.prepareAttachmentInput(nestedConfig));
assert.strictEqual(firstPrepare.data.action, 'trigger');
assert.strictEqual(firstPrepare.data.inputFound, false);
const secondPrepare = JSON.parse(runtime.prepareAttachmentInput(nestedConfig));
assert.strictEqual(secondPrepare.data.action, 'menu-action');
const thirdPrepare = JSON.parse(runtime.prepareAttachmentInput(nestedConfig));
assert.strictEqual(thirdPrepare.data.inputFound, true);

// ChatGPT assistant selectors overlap: one answer node can match both the
// role selector and the generic markdown selector. It must count once, and
// the newest DOM node must remain last regardless of selector order.
const olderAnswer = visibleElement();
const latestAnswer = visibleElement();
olderAnswer.innerText = '{"valueRange":{"low":10000,"high":20000}}';
latestAnswer.innerText = '{"valueRange":{"low":30000,"high":40000}}';
olderAnswer.compareDocumentPosition = (other) => other === latestAnswer ? 4 : 0;
latestAnswer.compareDocumentPosition = (other) => other === olderAnswer ? 2 : 0;
document.querySelectorAll = function (selector) {
  if (selector === "[data-message-author-role='assistant']") return [olderAnswer, latestAnswer];
  if (selector === '.markdown') return [latestAnswer, olderAnswer];
  return [];
};
const overlappingConfig = {
  selectors: {
    assistantMessage: ["[data-message-author-role='assistant']", '.markdown'],
    stopButton: [],
    errorBanner: [],
    challengeIndicator: [],
    preCode: [],
  },
};
const observed = JSON.parse(runtime.observeGeneration(overlappingConfig, 1));
assert.strictEqual(observed.data.assistantCount, 2);
assert.strictEqual(observed.data.hasNewAnswer, true);
assert.strictEqual(observed.data.rawText, latestAnswer.innerText);

const validationTranscript = [
  'draft prose',
  '',
  'print("Length with spaces:", len(text))',
  'Length with spaces: 103',
  'Text:',
  '#final #caption',
  'validated prose',
].join('\n');
const cleanedValidation = JSON.parse(runtime.cleanOutput(validationTranscript, 'gemini'));
assert.strictEqual(cleanedValidation.data.cleanedText, '#final #caption\nvalidated prose');
const tripleQuotedValidation = [
  'def check_len(text):',
  '  print(len(text))',
  '  print("--- Text ---")',
  '  print(text)',
  '',
  'draft = """#final #caption',
  'validated prose',
  'final line"""',
  'check_len(draft)',
].join('\n');
const cleanedTripleQuotedValidation = JSON.parse(runtime.cleanOutput(tripleQuotedValidation, 'gemini'));
assert.strictEqual(cleanedTripleQuotedValidation.data.cleanedText, '#final #caption\nvalidated prose\nfinal line');
const ordinaryTextLabel = JSON.parse(runtime.cleanOutput('Text:\nordinary prose', 'gemini'));
assert.strictEqual(ordinaryTextLabel.data.cleanedText, 'ordinary prose');
