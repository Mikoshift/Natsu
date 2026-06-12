(function () {
  "use strict";

  var BRIDGE_NAME = "NatsuBridge";
  var SCROLL_THROTTLE_MS = 400;
  var TAP_MOVE_THRESHOLD_PX = 10;
  var lastScrollNotify = 0;
  var touchStartX = 0;
  var touchStartY = 0;

  function bridge() {
    return window[BRIDGE_NAME];
  }

  function notifyScrollProgress() {
    var now = Date.now();
    if (now - lastScrollNotify < SCROLL_THROTTLE_MS) {
      return;
    }
    lastScrollNotify = now;
    var doc = document.documentElement;
    var scrollHeight = doc.scrollHeight - doc.clientHeight;
    var ratio = scrollHeight > 0 ? doc.scrollTop / scrollHeight : 0;
    var target = bridge();
    if (target && target.onScrollProgress) {
      target.onScrollProgress(ratio);
    }
  }

  function findParagraphElement(node) {
    var current = node;
    while (current && current !== document.body) {
      if (
        current.nodeType === Node.ELEMENT_NODE &&
        /^(P|H[1-6]|LI|TD|BLOCKQUOTE|DIV)$/i.test(current.tagName)
      ) {
        return current;
      }
      current = current.parentNode;
    }
    return document.body;
  }

  function charOffsetInParagraph(paragraph, range) {
    var pre = document.createRange();
    pre.selectNodeContents(paragraph);
    pre.setEnd(range.startContainer, range.startOffset);
    return pre.toString().length;
  }

  // Returns the char offset of `range` within `paragraph.innerText`.
  // innerText normalises whitespace and strips <rt> (furigana) content, so we
  // walk the visible text nodes the same way innerText does: skip <rt> nodes.
  function innerTextOffsetInParagraph(paragraph, range) {
    var targetNode = range.startContainer;
    var targetOffset = range.startOffset;
    var offset = 0;
    var found = false;
    var walker = document.createTreeWalker(
      paragraph,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode: function (node) {
          // Skip text inside <rt> tags (furigana readings injected by injectRuby)
          var parent = node.parentNode;
          while (parent && parent !== paragraph) {
            if (parent.tagName && parent.tagName.toLowerCase() === "rt") {
              return NodeFilter.FILTER_REJECT;
            }
            parent = parent.parentNode;
          }
          return NodeFilter.FILTER_ACCEPT;
        },
      },
    );
    var node = walker.nextNode();
    while (node) {
      if (node === targetNode) {
        offset += targetOffset;
        found = true;
        break;
      }
      offset += (node.textContent || "").length;
      node = walker.nextNode();
    }
    // If the exact node wasn't found (e.g. target is inside <rt>), fall back to
    // the textContent-based offset which is still better than nothing.
    if (!found) {
      return charOffsetInParagraph(paragraph, range);
    }
    return offset;
  }

  function findTextNodeInElement(element) {
    var walker = document.createTreeWalker(
      element,
      NodeFilter.SHOW_TEXT,
      null,
    );
    return walker.nextNode();
  }

  function rangeFromPoint(clientX, clientY) {
    var doc = document;
    var range = null;
    if (doc.caretRangeFromPoint) {
      range = doc.caretRangeFromPoint(clientX, clientY);
    } else if (doc.caretPositionFromPoint) {
      var position = doc.caretPositionFromPoint(clientX, clientY);
      if (position) {
        range = doc.createRange();
        range.setStart(position.offsetNode, position.offset);
        range.collapse(true);
      }
    }
    if (range && range.startContainer.nodeType === Node.TEXT_NODE) {
      return range;
    }
    var element = doc.elementFromPoint(clientX, clientY);
    if (!element) {
      return null;
    }
    var textNode = findTextNodeInElement(element);
    if (!textNode) {
      return null;
    }
    range = doc.createRange();
    range.setStart(textNode, 0);
    range.collapse(true);
    return range;
  }

  function getTapContext(clientX, clientY) {
    var range = rangeFromPoint(clientX, clientY);
    if (!range) {
      return null;
    }
    var paragraph = findParagraphElement(range.startContainer);
    // innerText gives the visible text without furigana readings (<rt> content),
    // which matches what Kotlin tokenizes. Use innerTextOffsetInParagraph so the
    // charOffset is consistent with that same string.
    var text = paragraph.innerText || paragraph.textContent || "";
    if (!text.trim()) {
      return null;
    }
    return {
      text: text,
      charOffset: innerTextOffsetInParagraph(paragraph, range),
    };
  }

  function handleWordTap(clientX, clientY) {
    var result = getTapContext(clientX, clientY);
    if (!result) {
      return;
    }
    var target = bridge();
    if (target && target.onWordTap) {
      target.onWordTap(result.text, result.charOffset);
    }
  }

  function clearHighlights() {
    document.querySelectorAll("mark.natsu-search-highlight").forEach(function (mark) {
      var parent = mark.parentNode;
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
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var current = 0;
    var node = walker.nextNode();
    while (node) {
      var length = node.textContent.length;
      var nodeStart = current;
      var nodeEnd = current + length;
      if (end > nodeStart && start < nodeEnd) {
        var localStart = Math.max(0, start - nodeStart);
        var localEnd = Math.min(length, end - nodeStart);
        var range = document.createRange();
        range.setStart(node, localStart);
        range.setEnd(node, localEnd);
        var mark = document.createElement("mark");
        mark.className = "natsu-search-highlight";
        range.surroundContents(mark);
        return mark;
      }
      current = nodeEnd;
      node = walker.nextNode();
    }
    return null;
  }

  function collectTextRoot() {
    return document.body || document.documentElement;
  }

  function charOffsetToPoint(root, targetOffset) {
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var current = 0;
    var node = walker.nextNode();
    while (node) {
      var length = node.textContent.length;
      if (current + length >= targetOffset) {
        var range = document.createRange();
        var localOffset = Math.max(0, targetOffset - current);
        range.setStart(node, Math.min(localOffset, length));
        range.collapse(true);
        return range;
      }
      current += length;
      node = walker.nextNode();
    }
    return null;
  }

  window.NatsuReader = {
    init: function () {
      if (window.__natsuReaderInitialized) {
        return;
      }
      window.__natsuReaderInitialized = true;

      document.addEventListener(
        "touchstart",
        function (event) {
          if (event.touches.length !== 1) {
            return;
          }
          touchStartX = event.touches[0].clientX;
          touchStartY = event.touches[0].clientY;
        },
        true,
      );

      document.addEventListener(
        "touchend",
        function (event) {
          if (event.changedTouches.length !== 1) {
            return;
          }
          var touch = event.changedTouches[0];
          var dx = touch.clientX - touchStartX;
          var dy = touch.clientY - touchStartY;
          if (dx * dx + dy * dy > TAP_MOVE_THRESHOLD_PX * TAP_MOVE_THRESHOLD_PX) {
            return;
          }
          var result = getTapContext(touch.clientX, touch.clientY);
          if (!result) {
            return;
          }
          event.preventDefault();
          handleWordTap(touch.clientX, touch.clientY);
        },
        { capture: true, passive: false },
      );

      document.addEventListener(
        "click",
        function (event) {
          handleWordTap(event.clientX, event.clientY);
        },
        true,
      );

      window.addEventListener("scroll", notifyScrollProgress, { passive: true });

      var target = bridge();
      if (target && target.onBridgeReady) {
        target.onBridgeReady();
      }
      if (target && target.onChapterReady) {
        target.onChapterReady();
      }
    },

    applyTheme: function (vars) {
      if (!vars) {
        return;
      }
      var root = document.documentElement;
      var body = document.body;
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
    },

    highlightSearch: function (ranges) {
      clearHighlights();
      if (!ranges || !ranges.length) {
        return;
      }
      var root = collectTextRoot();
      ranges.forEach(function (range) {
        if (range == null || range.start == null || range.end == null) {
          return;
        }
        highlightRange(root, range.start, range.end);
      });
    },

    injectRuby: function (tokens) {
      if (!tokens || !tokens.length) {
        return;
      }
      var root = collectTextRoot();
      tokens.forEach(function (token) {
        if (!token || !token.surface || !token.reading) {
          return;
        }
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var node = walker.nextNode();
        while (node) {
          var parent = node.parentNode;
          if (!parent || (parent.closest && parent.closest("ruby"))) {
            node = walker.nextNode();
            continue;
          }
          var text = node.textContent || "";
          var index = text.indexOf(token.surface);
          if (index < 0) {
            node = walker.nextNode();
            continue;
          }
          var before = text.slice(0, index);
          var after = text.slice(index + token.surface.length);
          var ruby = document.createElement("ruby");
          ruby.setAttribute("data-natsu-surface", token.surface);
          var rb = document.createElement("rb");
          rb.textContent = token.surface;
          var rt = document.createElement("rt");
          rt.textContent = token.reading;
          ruby.appendChild(rb);
          ruby.appendChild(rt);
          var fragment = document.createDocumentFragment();
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
    },

    scrollToOffset: function (charOffset) {
      var root = collectTextRoot();
      var range = charOffsetToPoint(root, charOffset);
      if (!range) {
        return;
      }
      var rect = range.getBoundingClientRect();
      var targetTop = window.scrollY + rect.top - window.innerHeight * 0.3;
      window.scrollTo({ top: Math.max(0, targetTop), behavior: "auto" });
    },
  };
})();
