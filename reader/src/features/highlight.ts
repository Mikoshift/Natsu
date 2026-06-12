import type { SearchRange } from "../types.js";
import { createVisibleTextWalker, collectTextRoot } from "../text/walker.js";

interface HighlightSegment {
  node: Text;
  localStart: number;
  localEnd: number;
}

export function clearHighlights(): void {
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

function highlightRange(root: Node, start: number, end: number): void {
  const segments: HighlightSegment[] = [];
  let current = 0;
  const walker = createVisibleTextWalker(root);
  let node = walker.nextNode();
  while (node) {
    if (node.nodeType !== Node.TEXT_NODE) {
      node = walker.nextNode();
      continue;
    }
    const textNode = node as Text;
    const length = (textNode.textContent || "").length;
    const nodeStart = current;
    const nodeEnd = current + length;
    if (end > nodeStart && start < nodeEnd) {
      segments.push({
        node: textNode,
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
    const textNode = seg.node;
    if (seg.localEnd < textNode.length) {
      textNode.splitText(seg.localEnd);
    }
    let highlightNode = textNode;
    if (seg.localStart > 0) {
      highlightNode = textNode.splitText(seg.localStart);
    }
    const mark = document.createElement("mark");
    mark.className = "natsu-search-highlight";
    highlightNode.parentNode?.replaceChild(mark, highlightNode);
    mark.appendChild(highlightNode);
  }
}

export function highlightSearch(ranges: SearchRange[] | null | undefined): void {
  clearHighlights();
  if (!ranges?.length) {
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
