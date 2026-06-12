(function () {
  "use strict";

  var BRIDGE_NAME = "NatsuBridge";
  var SCROLL_THROTTLE_MS = 400;
  var lastScrollNotify = 0;

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

  function expandToWord(range) {
    if (!range || range.collapsed === false) {
      var text = range.toString();
      if (text.trim().length > 0) {
        return { text: text, start: range.startOffset, end: range.endOffset };
      }
    }
    if (!range) {
      return null;
    }
    var node = range.startContainer;
    if (node.nodeType !== Node.TEXT_NODE) {
      return null;
    }
    var textContent = node.textContent || "";
    var index = range.startOffset;
    if (index < 0 || index > textContent.length) {
      return null;
    }

    var start = index;
    var end = index;
    while (start > 0 && !/\s/.test(textContent.charAt(start - 1))) {
      start -= 1;
    }
    while (end < textContent.length && !/\s/.test(textContent.charAt(end))) {
      end += 1;
    }
    var word = textContent.slice(start, end).trim();
    if (!word) {
      return null;
    }
    return { text: word, start: start, end: end };
  }

  function getWordAtPoint(clientX, clientY) {
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
    return expandToWord(range);
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
      document.addEventListener(
        "click",
        function (event) {
          var result = getWordAtPoint(event.clientX, event.clientY);
          if (!result) {
            return;
          }
          var target = bridge();
          if (target && target.onWordTap) {
            target.onWordTap(result.text, result.start, result.end);
          }
        },
        true,
      );

      window.addEventListener("scroll", notifyScrollProgress, { passive: true });

      var target = bridge();
      if (target && target.onChapterReady) {
        target.onChapterReady();
      }
    },

    applyTheme: function (vars) {
      var root = document.documentElement;
      if (!vars) {
        return;
      }
      if (vars.fontSizePx != null) {
        root.style.setProperty("--natsu-font-size", vars.fontSizePx + "px");
      }
      if (vars.lineHeight != null) {
        root.style.setProperty("--natsu-line-height", String(vars.lineHeight));
      }
      if (vars.backgroundColor != null) {
        root.style.setProperty("--natsu-bg", vars.backgroundColor);
      }
      if (vars.textColor != null) {
        root.style.setProperty("--natsu-text", vars.textColor);
      }
      document.body.classList.add("natsu-chapter");
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
