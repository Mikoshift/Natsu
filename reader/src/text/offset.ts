import { layoutRangeAtOffset } from "./layout-text.js";

/** @deprecated Use layoutRangeAtOffset — kept for scroll/highlight call sites. */
export function charOffsetInParagraph(paragraph: Node, range: Range): number {
  const pre = document.createRange();
  pre.selectNodeContents(paragraph);
  pre.setEnd(range.startContainer, range.startOffset);
  return pre.toString().length;
}

/** Maps a section-local layout-text offset to a collapsed DOM range. */
export function charOffsetToPoint(root: Node, targetOffset: number): Range | null {
  return layoutRangeAtOffset(root, targetOffset);
}

export { extractLayoutText, layoutOffsetAtRange, layoutRangeAtOffset } from "./layout-text.js";
