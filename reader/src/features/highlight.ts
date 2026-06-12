import type { SearchRange } from "../types.js";
import { layoutSegmentsForRange, visibleToRawOffset } from "../text/layout-text.js";
import { collectTextRoot } from "../text/walker.js";

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
