import { createVisibleTextWalker, collectTextRoot } from "../text/walker.js";

export function clearHighlights() {
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
        localEnd: Math.min(length, end - nodeStart),
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

export function highlightSearch(ranges) {
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
