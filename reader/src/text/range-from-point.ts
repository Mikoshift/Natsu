import type { TapContext } from "../types.js";
import { DOMTextScanner, isWhitespace, pointInAnyRect } from "./dom-text-scanner.js";
import { extractLayoutText, layoutOffsetAtRange } from "./layout-text.js";
import { snapToContentOffset } from "./snap-offset.js";
import { PARAGRAPH_INDEX_ATTR } from "../text/paragraph-index.js";

const PREFERRED_BLOCK_TAGS = new Set(["P", "LI", "TD", "BLOCKQUOTE", "H1", "H2", "H3", "H4", "H5", "H6"]);

export function findParagraphElement(node: Node): HTMLElement | null {
  let current: Node | null = node;
  let divFallback: HTMLElement | null = null;
  while (current && current !== document.body) {
    if (current.nodeType === Node.ELEMENT_NODE) {
      const element = current as HTMLElement;
      const tag = element.tagName.toUpperCase();
      if (PREFERRED_BLOCK_TAGS.has(tag)) {
        return element;
      }
      if (tag === "DIV" && divFallback === null) {
        divFallback = element;
      }
    }
    current = current.parentNode;
  }
  return divFallback;
}

export function paragraphIndexFromElement(paragraph: HTMLElement): number {
  const raw = paragraph.getAttribute(PARAGRAPH_INDEX_ATTR);
  if (raw === null) {
    return -1;
  }
  const parsed = Number.parseInt(raw, 10);
  return Number.isNaN(parsed) ? -1 : parsed;
}

function caretRangeFromPoint(clientX: number, clientY: number): Range | null {
  const doc = document as Document & {
    caretRangeFromPoint?(x: number, y: number): Range | null;
    caretPositionFromPoint?(
      x: number,
      y: number,
      options?: { shadowRoots?: ShadowRoot[] },
    ): { offsetNode: Node; offset: number } | null;
  };

  if (typeof doc.caretPositionFromPoint === "function") {
    const position = doc.caretPositionFromPoint(clientX, clientY);
    if (position?.offsetNode) {
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

function isPointInRange(clientX: number, clientY: number, range: Range): boolean {
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
  const matched = !isWhitespace(backward.content) &&
    pointInAnyRect(clientX, clientY, range.getClientRects());
  range.setStart(startContainer, startOffset);
  range.collapse(true);
  return matched;
}

function rangeFromPoint(clientX: number, clientY: number): Range | null {
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
  // Android WebView often reports unreliable client rects; keep the caret hit.
  return range;
}

export function getTapContext(clientX: number, clientY: number): TapContext | null {
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
    paragraphIndex: paragraphIndexFromElement(paragraph),
    text,
    charOffset: snappedOffset,
    paragraph,
  };
}

/** Used by tests and pre-tap validation in touch handlers. */
export function canTapAtPoint(clientX: number, clientY: number): boolean {
  return getTapContext(clientX, clientY) !== null;
}
