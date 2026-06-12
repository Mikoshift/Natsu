import { createVisibleTextWalker } from "./walker.js";

export function charOffsetInParagraph(paragraph: Node, range: Range): number {
  const pre = document.createRange();
  pre.selectNodeContents(paragraph);
  pre.setEnd(range.startContainer, range.startOffset);
  return pre.toString().length;
}

/**
 * Char offset of `range` within `paragraph.innerText`.
 * Skips `<rt>` so offsets match Kotlin tokenization and visible text.
 */
export function innerTextOffsetInParagraph(paragraph: Node, range: Range): number {
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

/** Map a section-local visible char offset to a collapsed DOM Range. */
export function charOffsetToPoint(root: Node, targetOffset: number): Range | null {
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
