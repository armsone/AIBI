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
  mediaCapabilities: { supportsImages: true, maxImagesPerTask: 8, requiresMultipleInputForBatch: true },
  selectors: { attachmentInput: ['input.image'], attachmentTrigger: [], attachmentPreview: ['.preview'] },
};
const images = Array.from({ length: 8 }, (_, index) => ({
  dataUrl: 'data:image/jpeg;base64,AA==',
  mimeType: 'image/jpeg',
  filename: `aibi-${String(index + 1).padStart(2, '0')}.jpg`,
}));

const attached = JSON.parse(runtime.attachImages(config, images));
assert.strictEqual(attached.success, true);
assert.strictEqual(attached.data.acceptedCount, 8);
const state = JSON.parse(runtime.getAttachmentState(config));
assert.strictEqual(state.data.previewCount, 8);
assert.deepStrictEqual(input.files.map((file) => file.name), images.map((image) => image.filename));

input.multiple = false;
const rejected = JSON.parse(runtime.attachImages(config, images));
assert.strictEqual(rejected.success, false);
assert.strictEqual(rejected.code, 'MULTIPLE_SELECTION_UNSUPPORTED');
