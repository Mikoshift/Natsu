"use strict";
(() => {
  // reader/src/types.ts
  var WEB_MESSAGE_TYPE = "natsu-reader-call";
  var BRIDGE_NAME = "NatsuBridge";

  // reader/src/native-bridge.ts
  function bridge() {
    return window[BRIDGE_NAME];
  }
  function callBridge(method, ...args) {
    const target = bridge();
    if (target && typeof target[method] === "function") {
      target[method](...args);
    }
  }

  // reader/src/text/walker.ts
  function collectTextRoot() {
    return document.body || document.documentElement;
  }
  function isInsideTag(node, root, tagName) {
    let parent = node.parentNode;
    while (parent && parent !== root) {
      if (parent.nodeType === Node.ELEMENT_NODE && parent.nodeName.toLowerCase() === tagName) {
        return true;
      }
      parent = parent.parentNode;
    }
    return false;
  }

  // reader/src/text/dom-text-scanner.ts
  var SKIP_TAGS = /* @__PURE__ */ new Set(["RT", "RP", "SCRIPT", "STYLE", "HEAD"]);
  var DOMTextScanner = class {
    constructor(node, offset, stopAtWordBoundary = false) {
      this._content = "";
      const ruby = getParentRubyElement(node);
      const resetOffset = ruby !== null;
      if (resetOffset && ruby !== null) {
        node = ruby;
      }
      this._node = node;
      this._offset = offset;
      this._remainder = 0;
      this._resetOffset = resetOffset;
      this._stopAtWordBoundary = stopAtWordBoundary;
    }
    get node() {
      return this._node;
    }
    get offset() {
      return this._offset;
    }
    get remainder() {
      return this._remainder;
    }
    get content() {
      return this._content;
    }
    seek(length) {
      const forward = length >= 0;
      this._remainder = forward ? length : -length;
      if (length === 0) {
        return this;
      }
      let node = this._node;
      let lastNode = node;
      let resetOffset = this._resetOffset;
      while (node !== null && this._remainder > 0) {
        let enterable = false;
        if (node.nodeType === Node.TEXT_NODE) {
          lastNode = node;
          const keepGoing = forward ? this.seekTextForward(node, resetOffset) : this.seekTextBackward(node, resetOffset);
          if (!keepGoing) {
            break;
          }
        } else if (node.nodeType === Node.ELEMENT_NODE) {
          if (this._stopAtWordBoundary && !forward) {
            break;
          }
          lastNode = node;
          this._offset = 0;
          ({ enterable } = getElementSeekInfo(node));
        }
        const exitedNodes = [];
        node = getNextNode(node, forward, enterable, exitedNodes);
        resetOffset = true;
      }
      this._node = lastNode;
      this._resetOffset = resetOffset;
      return this;
    }
    seekTextForward(textNode, resetOffset) {
      const value = textNode.nodeValue || "";
      if (resetOffset) {
        this._offset = 0;
      }
      while (this._offset < value.length && this._remainder > 0) {
        const char = value[this._offset];
        this._offset += 1;
        if (isInvisible(char)) {
          continue;
        }
        this._content += char;
        this._remainder -= 1;
      }
      return this._remainder > 0;
    }
    seekTextBackward(textNode, resetOffset) {
      const value = textNode.nodeValue || "";
      if (resetOffset) {
        this._offset = value.length;
      }
      while (this._offset > 0 && this._remainder > 0) {
        const char = value[this._offset - 1];
        if (this._stopAtWordBoundary && isWordDelimiter(char)) {
          if (!isApostropheInWord(value, this._offset - 1)) {
            return false;
          }
        }
        this._offset -= 1;
        if (isInvisible(char)) {
          continue;
        }
        this._content = char + this._content;
        this._remainder -= 1;
      }
      return this._remainder > 0;
    }
  };
  function isWordDelimiter(character) {
    return /[^\p{L}\p{N}]/u.test(character);
  }
  function isWhitespace(text) {
    return text.trim().length === 0;
  }
  function pointInAnyRect(x, y, rects) {
    for (let i = 0; i < rects.length; i += 1) {
      const rect = rects[i];
      if (rect.width <= 0 || rect.height <= 0) {
        continue;
      }
      if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
        return true;
      }
    }
    return false;
  }
  function getParentRubyElement(node) {
    let parent = node.parentNode;
    if (parent !== null && parent.nodeName.toUpperCase() === "RT") {
      parent = parent.parentNode;
      if (parent !== null && parent.nodeName.toUpperCase() === "RUBY") {
        return parent;
      }
    }
    return null;
  }
  function getElementSeekInfo(element) {
    const tag = element.tagName.toUpperCase();
    if (SKIP_TAGS.has(tag)) {
      return { enterable: false, newlines: 0 };
    }
    if (tag === "RB") {
      return { enterable: true, newlines: 0 };
    }
    if (tag === "BR") {
      return { enterable: false, newlines: 1 };
    }
    return { enterable: true, newlines: 0 };
  }
  function getNextNode(node, forward, visitChildren, exitedNodes) {
    let next = visitChildren ? forward ? node.firstChild : node.lastChild : null;
    if (next !== null) {
      return next;
    }
    while (true) {
      exitedNodes.push(node);
      next = forward ? node.nextSibling : node.previousSibling;
      if (next !== null) {
        return next;
      }
      next = node.parentNode;
      if (next === null) {
        return null;
      }
      node = next;
    }
  }
  function isInvisible(char) {
    switch (char.charCodeAt(0)) {
      case 8203:
      case 8204:
      case 173:
        return true;
      default:
        return false;
    }
  }
  function isApostropheInWord(text, index) {
    const char = text[index];
    if (!isSingleQuote(char) || index <= 0) {
      return false;
    }
    return isWordDelimiter(text[index - 1]);
  }
  function isSingleQuote(character) {
    switch (character.charCodeAt(0)) {
      case 39:
      case 8217:
      case 8242:
      case 8245:
      case 700:
        return true;
      default:
        return false;
    }
  }

  // reader/src/text/layout-text.ts
  var SKIP_TAGS2 = /* @__PURE__ */ new Set(["RT", "RP", "SCRIPT", "STYLE", "HEAD"]);
  var INVISIBLE_CODE_POINTS = /* @__PURE__ */ new Set([8203, 8204, 173]);
  function extractLayoutText(root) {
    const parts = [];
    walkLayout(root, root, (chunk) => parts.push(chunk));
    return parts.join("");
  }
  function layoutOffsetAtRange(root, range) {
    const targetNode = range.startContainer;
    const targetOffset = range.startOffset;
    let offset = 0;
    let found = false;
    walkLayout(root, root, (chunk, meta) => {
      if (found) {
        return;
      }
      if ((meta == null ? void 0 : meta.kind) === "text" && meta.node === targetNode) {
        offset += visibleOffsetInTextNode(meta.node, targetOffset);
        found = true;
        return;
      }
      offset += chunk.length;
    });
    return found ? offset : null;
  }
  function layoutRangeAtOffset(root, targetOffset) {
    let current = 0;
    let result = null;
    walkLayout(root, root, (chunk, meta) => {
      if (result) {
        return;
      }
      const start = current;
      const end = current + chunk.length;
      if (targetOffset >= start && targetOffset < end) {
        if ((meta == null ? void 0 : meta.kind) === "text") {
          const range = document.createRange();
          const rawOffset = rawOffsetForVisibleIndex(meta.node, targetOffset - start);
          range.setStart(meta.node, rawOffset);
          range.collapse(true);
          result = range;
        }
      }
      current = end;
    });
    return result;
  }
  function layoutSegmentsForRange(root, start, end) {
    if (start >= end) {
      return [];
    }
    const segments = [];
    let current = 0;
    walkLayout(root, root, (chunk, meta) => {
      const chunkStart = current;
      const chunkEnd = current + chunk.length;
      if ((meta == null ? void 0 : meta.kind) === "text" && end > chunkStart && start < chunkEnd) {
        segments.push({
          node: meta.node,
          localStart: Math.max(0, start - chunkStart),
          localEnd: Math.min(chunk.length, end - chunkStart)
        });
      }
      current = chunkEnd;
    });
    return segments;
  }
  function layoutRangeForSpan(root, start, end) {
    const segments = layoutSegmentsForRange(root, start, end);
    if (segments.length === 0) {
      return null;
    }
    const first = segments[0];
    const last = segments[segments.length - 1];
    const range = document.createRange();
    range.setStart(first.node, rawOffsetForVisibleIndex(first.node, first.localStart));
    range.setEnd(last.node, rawOffsetForVisibleIndex(last.node, last.localEnd));
    return range;
  }
  function walkLayout(node, root, emit) {
    if (node.nodeType === Node.TEXT_NODE) {
      if (isInsideTag(node, root, "rt")) {
        return;
      }
      const filtered = filterInvisible(node.textContent || "");
      if (filtered.length > 0) {
        emit(filtered, { kind: "text", node });
      }
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return;
    }
    const tag = node.tagName.toUpperCase();
    if (SKIP_TAGS2.has(tag)) {
      return;
    }
    if (tag === "BR") {
      emit("\n", { kind: "newline" });
      return;
    }
    for (const child of node.childNodes) {
      walkLayout(child, root, emit);
    }
  }
  function filterInvisible(text) {
    let result = "";
    for (const char of text) {
      if (!INVISIBLE_CODE_POINTS.has(char.charCodeAt(0))) {
        result += char;
      }
    }
    return result;
  }
  function visibleOffsetInTextNode(node, rawOffset) {
    const text = node.textContent || "";
    const clamped = Math.max(0, Math.min(rawOffset, text.length));
    let visible = 0;
    for (let i = 0; i < clamped; i += 1) {
      if (!INVISIBLE_CODE_POINTS.has(text.charCodeAt(i))) {
        visible += 1;
      }
    }
    return visible;
  }
  function visibleToRawOffset(node, visibleOffset) {
    return rawOffsetForVisibleIndex(node, visibleOffset);
  }
  function rawOffsetForVisibleIndex(node, visibleIndex) {
    const text = node.textContent || "";
    let visible = 0;
    for (let raw = 0; raw <= text.length; raw += 1) {
      if (visible === visibleIndex) {
        return raw;
      }
      if (raw < text.length && !INVISIBLE_CODE_POINTS.has(text.charCodeAt(raw))) {
        visible += 1;
      }
    }
    return text.length;
  }

  // reader/src/text/snap-offset.ts
  var MAX_SNAP_DISTANCE = 3;
  function snapToContentOffset(text, charOffset) {
    if (text.length === 0) {
      return null;
    }
    const clamped = Math.max(0, Math.min(charOffset, text.length - 1));
    if (isContentChar(text[clamped])) {
      return clamped;
    }
    for (let delta = 1; delta <= MAX_SNAP_DISTANCE; delta += 1) {
      if (clamped - delta >= 0 && isContentChar(text[clamped - delta])) {
        return clamped - delta;
      }
      if (clamped + delta < text.length && isContentChar(text[clamped + delta])) {
        return clamped + delta;
      }
    }
    return null;
  }
  function isContentChar(character) {
    return /[\p{L}\p{N}]/u.test(character);
  }

  // reader/src/text/range-from-point.ts
  var BLOCK_TAGS = /^(P|H[1-6]|LI|TD|BLOCKQUOTE|DIV)$/i;
  function findParagraphElement(node) {
    let current = node;
    while (current && current !== document.body) {
      if (current.nodeType === Node.ELEMENT_NODE && BLOCK_TAGS.test(current.tagName)) {
        return current;
      }
      current = current.parentNode;
    }
    return null;
  }
  function caretRangeFromPoint(clientX, clientY) {
    const doc = document;
    if (typeof doc.caretPositionFromPoint === "function") {
      const position = doc.caretPositionFromPoint(clientX, clientY);
      if (position == null ? void 0 : position.offsetNode) {
        const range = document.createRange();
        range.setStart(position.offsetNode, position.offset);
        range.collapse(true);
        return range;
      }
    }
    if (typeof doc.caretRangeFromPoint === "function") {
      return doc.caretRangeFromPoint(clientX, clientY);
    }
    return null;
  }
  function isPointInRange(clientX, clientY, range) {
    if (range.startContainer.nodeType !== Node.TEXT_NODE) {
      return false;
    }
    const startContainer = range.startContainer;
    const startOffset = range.startOffset;
    const endContainer = range.endContainer;
    const endOffset = range.endOffset;
    try {
      const forward = new DOMTextScanner(startContainer, startOffset).seek(1);
      range.setEnd(forward.node, forward.offset);
      if (!isWhitespace(forward.content) && pointInAnyRect(clientX, clientY, range.getClientRects())) {
        return true;
      }
    } finally {
      range.setStart(startContainer, startOffset);
      range.setEnd(endContainer, endOffset);
      range.collapse(true);
    }
    const backward = new DOMTextScanner(startContainer, startOffset, false).seek(-1);
    range.setStart(backward.node, backward.offset);
    range.setEnd(backward.node, backward.offset);
    const matched = !isWhitespace(backward.content) && pointInAnyRect(clientX, clientY, range.getClientRects());
    range.setStart(startContainer, startOffset);
    range.collapse(true);
    return matched;
  }
  function rangeFromPoint(clientX, clientY) {
    const range = caretRangeFromPoint(clientX, clientY);
    if (!range) {
      return null;
    }
    if (range.startContainer.nodeType !== Node.TEXT_NODE) {
      return null;
    }
    if (isPointInRange(clientX, clientY, range)) {
      return range;
    }
    return null;
  }
  function getTapContext(clientX, clientY) {
    const range = rangeFromPoint(clientX, clientY);
    if (!range) {
      return null;
    }
    const paragraph = findParagraphElement(range.startContainer);
    if (!paragraph) {
      return null;
    }
    const text = extractLayoutText(paragraph);
    if (!text.trim()) {
      return null;
    }
    const charOffset = layoutOffsetAtRange(paragraph, range);
    if (charOffset === null) {
      return null;
    }
    const snappedOffset = snapToContentOffset(text, charOffset);
    if (snappedOffset === null) {
      return null;
    }
    return {
      text,
      charOffset: snappedOffset
    };
  }
  function canTapAtPoint(clientX, clientY) {
    return getTapContext(clientX, clientY) !== null;
  }

  // reader/src/events.ts
  var SCROLL_THROTTLE_MS = 400;
  var TAP_MOVE_THRESHOLD_PX = 10;
  var TAP_CLICK_SUPPRESS_MS = 400;
  var lastScrollNotify = 0;
  var touchStartX = 0;
  var touchStartY = 0;
  var lastTouchTapAt = 0;
  function notifyScrollProgress() {
    const now = Date.now();
    if (now - lastScrollNotify < SCROLL_THROTTLE_MS) {
      return;
    }
    lastScrollNotify = now;
    const doc = document.documentElement;
    const scrollHeight = doc.scrollHeight - doc.clientHeight;
    const ratio = scrollHeight > 0 ? doc.scrollTop / scrollHeight : 0;
    callBridge("onScrollProgress", ratio);
  }
  function handleWordTap(clientX, clientY) {
    const result = getTapContext(clientX, clientY);
    if (!result) {
      return;
    }
    callBridge("onWordTap", result.text, result.charOffset);
  }
  function installEventListeners() {
    document.addEventListener(
      "touchstart",
      (event) => {
        if (event.touches.length !== 1) {
          return;
        }
        touchStartX = event.touches[0].clientX;
        touchStartY = event.touches[0].clientY;
      },
      true
    );
    document.addEventListener(
      "touchend",
      (event) => {
        if (event.changedTouches.length !== 1) {
          return;
        }
        const touch = event.changedTouches[0];
        const dx = touch.clientX - touchStartX;
        const dy = touch.clientY - touchStartY;
        if (dx * dx + dy * dy > TAP_MOVE_THRESHOLD_PX * TAP_MOVE_THRESHOLD_PX) {
          return;
        }
        if (!canTapAtPoint(touch.clientX, touch.clientY)) {
          return;
        }
        event.preventDefault();
        lastTouchTapAt = Date.now();
        handleWordTap(touch.clientX, touch.clientY);
      },
      { capture: true, passive: false }
    );
    document.addEventListener(
      "click",
      (event) => {
        if (Date.now() - lastTouchTapAt < TAP_CLICK_SUPPRESS_MS) {
          return;
        }
        handleWordTap(event.clientX, event.clientY);
      },
      true
    );
    window.addEventListener("scroll", notifyScrollProgress, { passive: true });
  }

  // reader/src/features/highlight.ts
  function clearHighlights() {
    document.querySelectorAll("mark.natsu-search-highlight").forEach((mark) => {
      const parent = mark.parentNode;
      if (!parent) {
        return;
      }
      while (mark.firstChild) {
        parent.insertBefore(mark.firstChild, mark);
      }
      parent.removeChild(mark);
      parent.normalize();
    });
  }
  function highlightRange(root, start, end) {
    var _a;
    const segments = layoutSegmentsForRange(root, start, end);
    for (let i = segments.length - 1; i >= 0; i -= 1) {
      const seg = segments[i];
      const localLength = seg.localEnd - seg.localStart;
      if (localLength <= 0) {
        continue;
      }
      const textNode = seg.node;
      const rawEnd = visibleToRawOffset(textNode, seg.localEnd);
      const rawStart = visibleToRawOffset(textNode, seg.localStart);
      if (rawEnd < textNode.length) {
        textNode.splitText(rawEnd);
      }
      let highlightNode = textNode;
      if (rawStart > 0) {
        highlightNode = textNode.splitText(rawStart);
      }
      const mark = document.createElement("mark");
      mark.className = "natsu-search-highlight";
      (_a = highlightNode.parentNode) == null ? void 0 : _a.replaceChild(mark, highlightNode);
      mark.appendChild(highlightNode);
    }
  }
  function highlightSearch(ranges) {
    clearHighlights();
    if (!(ranges == null ? void 0 : ranges.length)) {
      return;
    }
    const root = collectTextRoot();
    ranges.forEach((range) => {
      if (range.start == null || range.end == null) {
        return;
      }
      highlightRange(root, range.start, range.end);
    });
  }

  // reader/src/features/ruby.ts
  function injectRuby(tokens) {
    if (!(tokens == null ? void 0 : tokens.length)) {
      return;
    }
    const root = collectTextRoot();
    const sorted = [...tokens].sort((left, right) => right.start - left.start);
    sorted.forEach((token) => {
      injectRubyAtLayoutSpan(root, token);
    });
  }
  function injectRubyAtLayoutSpan(root, token) {
    if (!token.surface || !token.reading || token.start >= token.end) {
      return;
    }
    if (document.querySelector('ruby[data-natsu-layout-start="'.concat(token.start, '"]'))) {
      return;
    }
    const range = layoutRangeForSpan(root, token.start, token.end);
    if (!range || isInsideExistingRuby(range.startContainer, root)) {
      return;
    }
    const ruby = document.createElement("ruby");
    ruby.setAttribute("data-natsu-layout-start", String(token.start));
    ruby.setAttribute("data-natsu-surface", token.surface);
    const rb = document.createElement("rb");
    const rt = document.createElement("rt");
    rt.textContent = token.reading;
    try {
      const contents = range.extractContents();
      rb.appendChild(contents);
      ruby.appendChild(rb);
      ruby.appendChild(rt);
      range.insertNode(ruby);
    } catch (e) {
    }
  }
  function isInsideExistingRuby(node, root) {
    let current = node;
    while (current && current !== root) {
      if (current.nodeType === Node.ELEMENT_NODE && current.tagName.toUpperCase() === "RUBY") {
        return true;
      }
      current = current.parentNode;
    }
    return false;
  }

  // reader/src/text/offset.ts
  function charOffsetToPoint(root, targetOffset) {
    return layoutRangeAtOffset(root, targetOffset);
  }

  // reader/src/features/scroll.ts
  function scrollToOffset(charOffset) {
    const root = collectTextRoot();
    const range = charOffsetToPoint(root, charOffset);
    if (!range) {
      return;
    }
    const rect = range.getBoundingClientRect();
    const targetTop = window.scrollY + rect.top - window.innerHeight * 0.3;
    window.scrollTo({ top: Math.max(0, targetTop), behavior: "auto" });
  }

  // reader/src/messages.ts
  function installMessageListener(api) {
    window.addEventListener("message", (event) => {
      let payload = event.data;
      if (typeof payload === "string") {
        try {
          payload = JSON.parse(payload);
        } catch (e) {
          return;
        }
      }
      if (!payload || typeof payload !== "object" || !("type" in payload) || payload.type !== WEB_MESSAGE_TYPE || !("method" in payload) || typeof payload.method !== "string") {
        return;
      }
      const method = payload.method;
      if (typeof api[method] !== "function") {
        return;
      }
      const args = "args" in payload && Array.isArray(payload.args) ? payload.args : [];
      api[method].call(api, ...args);
    });
  }
  function scheduleReaderInit(init) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", init);
    } else {
      init();
    }
  }

  // reader/src/theme.ts
  function applyTheme(vars) {
    if (!vars) {
      return;
    }
    const root = document.documentElement;
    const body = document.body;
    if (vars.fontSizePx != null) {
      root.style.setProperty("--natsu-font-size", vars.fontSizePx + "px");
    }
    if (vars.lineHeight != null) {
      root.style.setProperty("--natsu-line-height", String(vars.lineHeight));
    }
    if (vars.backgroundColor != null) {
      root.style.setProperty("--natsu-bg", vars.backgroundColor);
      root.style.backgroundColor = vars.backgroundColor;
      if (body) {
        body.style.backgroundColor = vars.backgroundColor;
      }
    }
    if (vars.textColor != null) {
      root.style.setProperty("--natsu-text", vars.textColor);
      if (body) {
        body.style.color = vars.textColor;
      }
    }
    if (body) {
      body.classList.add("natsu-chapter");
    }
  }

  // reader/src/index.ts
  (function() {
    "use strict";
    const reader = {
      init() {
        if (window.__natsuReaderInitialized) {
          return;
        }
        window.__natsuReaderInitialized = true;
        installEventListeners();
        callBridge("onBridgeReady");
        requestAnimationFrame(() => {
          callBridge("onChapterReady");
        });
      },
      applyTheme,
      highlightSearch,
      injectRuby,
      scrollToOffset
    };
    window.NatsuReader = reader;
    installMessageListener(reader);
    scheduleReaderInit(() => reader.init());
  })();
})();
