(() => {
  // reader-js/src/native-bridge.js
  var BRIDGE_NAME = "NatsuBridge";
  function bridge() {
    return window[BRIDGE_NAME];
  }
  function callBridge(method, ...args) {
    const target = bridge();
    if (target && typeof target[method] === "function") {
      target[method](...args);
    }
  }

  // reader-js/src/text/walker.js
  function collectTextRoot() {
    return document.body || document.documentElement;
  }
  function isInsideTag(node, root, tagName) {
    let parent = node.parentNode;
    while (parent && parent !== root) {
      if (parent.nodeType === Node.ELEMENT_NODE && parent.tagName.toLowerCase() === tagName) {
        return true;
      }
      parent = parent.parentNode;
    }
    return false;
  }
  function createTextWalker(root, acceptNode) {
    return document.createTreeWalker(root, NodeFilter.SHOW_TEXT, { acceptNode });
  }
  function createVisibleTextWalker(root) {
    return createTextWalker(root, (node) => {
      if (isInsideTag(node, root, "rt")) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    });
  }
  function createInjectableTextWalker(root) {
    return createTextWalker(root, (node) => {
      if (isInsideTag(node, root, "rt")) {
        return NodeFilter.FILTER_REJECT;
      }
      if (isInsideTag(node, root, "ruby")) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    });
  }

  // reader-js/src/text/offset.js
  function charOffsetInParagraph(paragraph, range) {
    const pre = document.createRange();
    pre.selectNodeContents(paragraph);
    pre.setEnd(range.startContainer, range.startOffset);
    return pre.toString().length;
  }
  function innerTextOffsetInParagraph(paragraph, range) {
    const targetNode = range.startContainer;
    const targetOffset = range.startOffset;
    let offset = 0;
    let found = false;
    const walker = createVisibleTextWalker(paragraph);
    let node = walker.nextNode();
    while (node) {
      if (node === targetNode) {
        offset += targetOffset;
        found = true;
        break;
      }
      offset += (node.textContent || "").length;
      node = walker.nextNode();
    }
    if (!found) {
      return charOffsetInParagraph(paragraph, range);
    }
    return offset;
  }
  function charOffsetToPoint(root, targetOffset) {
    let current = 0;
    const walker = createVisibleTextWalker(root);
    let node = walker.nextNode();
    while (node) {
      const length = (node.textContent || "").length;
      if (current + length >= targetOffset) {
        const range = document.createRange();
        const localOffset = Math.max(0, targetOffset - current);
        range.setStart(node, Math.min(localOffset, length));
        range.collapse(true);
        return range;
      }
      current += length;
      node = walker.nextNode();
    }
    return null;
  }

  // reader-js/src/text/range-from-point.js
  function findTextNodeInElement(element) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null);
    return walker.nextNode();
  }
  function findParagraphElement(node) {
    let current = node;
    while (current && current !== document.body) {
      if (current.nodeType === Node.ELEMENT_NODE && /^(P|H[1-6]|LI|TD|BLOCKQUOTE|DIV)$/i.test(current.tagName)) {
        return current;
      }
      current = current.parentNode;
    }
    return document.body;
  }
  function rangeFromPoint(clientX, clientY) {
    const doc = document;
    let range = null;
    if (doc.caretRangeFromPoint) {
      range = doc.caretRangeFromPoint(clientX, clientY);
    } else if (doc.caretPositionFromPoint) {
      const position = doc.caretPositionFromPoint(clientX, clientY);
      if (position) {
        range = doc.createRange();
        range.setStart(position.offsetNode, position.offset);
        range.collapse(true);
      }
    }
    if (range && range.startContainer.nodeType === Node.TEXT_NODE) {
      return range;
    }
    const element = doc.elementFromPoint(clientX, clientY);
    if (!element) {
      return null;
    }
    const textNode = findTextNodeInElement(element);
    if (!textNode) {
      return null;
    }
    range = doc.createRange();
    range.setStart(textNode, 0);
    range.collapse(true);
    return range;
  }
  function getTapContext(clientX, clientY) {
    const range = rangeFromPoint(clientX, clientY);
    if (!range) {
      return null;
    }
    const paragraph = findParagraphElement(range.startContainer);
    const text = paragraph.innerText || paragraph.textContent || "";
    if (!text.trim()) {
      return null;
    }
    return {
      text,
      charOffset: innerTextOffsetInParagraph(paragraph, range)
    };
  }

  // reader-js/src/events.js
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
        if (!getTapContext(touch.clientX, touch.clientY)) {
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

  // reader-js/src/features/highlight.js
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
    const segments = [];
    let current = 0;
    const walker = createVisibleTextWalker(root);
    let node = walker.nextNode();
    while (node) {
      const length = (node.textContent || "").length;
      const nodeStart = current;
      const nodeEnd = current + length;
      if (end > nodeStart && start < nodeEnd) {
        segments.push({
          node,
          localStart: Math.max(0, start - nodeStart),
          localEnd: Math.min(length, end - nodeStart)
        });
      }
      current = nodeEnd;
      node = walker.nextNode();
    }
    for (let i = segments.length - 1; i >= 0; i--) {
      const seg = segments[i];
      const localLength = seg.localEnd - seg.localStart;
      if (localLength <= 0) {
        continue;
      }
      let textNode = seg.node;
      if (seg.localEnd < textNode.length) {
        textNode.splitText(seg.localEnd);
      }
      let highlightNode = textNode;
      if (seg.localStart > 0) {
        highlightNode = textNode.splitText(seg.localStart);
      }
      const mark = document.createElement("mark");
      mark.className = "natsu-search-highlight";
      highlightNode.parentNode.replaceChild(mark, highlightNode);
      mark.appendChild(highlightNode);
    }
  }
  function highlightSearch(ranges) {
    clearHighlights();
    if (!ranges || !ranges.length) {
      return;
    }
    const root = collectTextRoot();
    ranges.forEach((range) => {
      if (range == null || range.start == null || range.end == null) {
        return;
      }
      highlightRange(root, range.start, range.end);
    });
  }

  // reader-js/src/features/ruby.js
  function injectRuby(tokens) {
    if (!tokens || !tokens.length) {
      return;
    }
    const root = collectTextRoot();
    tokens.forEach((token) => {
      if (!token || !token.surface || !token.reading) {
        return;
      }
      const walker = createInjectableTextWalker(root);
      let node = walker.nextNode();
      while (node) {
        const parent = node.parentNode;
        if (!parent) {
          node = walker.nextNode();
          continue;
        }
        const text = node.textContent || "";
        const index = text.indexOf(token.surface);
        if (index < 0) {
          node = walker.nextNode();
          continue;
        }
        const before = text.slice(0, index);
        const after = text.slice(index + token.surface.length);
        const ruby = document.createElement("ruby");
        ruby.setAttribute("data-natsu-surface", token.surface);
        const rb = document.createElement("rb");
        rb.textContent = token.surface;
        const rt = document.createElement("rt");
        rt.textContent = token.reading;
        ruby.appendChild(rb);
        ruby.appendChild(rt);
        const fragment = document.createDocumentFragment();
        if (before) {
          fragment.appendChild(document.createTextNode(before));
        }
        fragment.appendChild(ruby);
        if (after) {
          fragment.appendChild(document.createTextNode(after));
        }
        parent.replaceChild(fragment, node);
        break;
      }
    });
  }

  // reader-js/src/features/scroll.js
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

  // reader-js/src/messages.js
  var WEB_MESSAGE_TYPE = "natsu-reader-call";
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
      if (!payload || payload.type !== WEB_MESSAGE_TYPE) {
        return;
      }
      const fn = api[payload.method];
      if (typeof fn !== "function") {
        return;
      }
      fn.apply(api, payload.args || []);
    });
  }
  function scheduleReaderInit(init) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", init);
    } else {
      init();
    }
  }

  // reader-js/src/theme.js
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

  // reader-js/src/index.js
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
