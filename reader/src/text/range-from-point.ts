import type { TapContext } from "../types.js";
import { innerTextOffsetInParagraph } from "./offset.js";

function findTextNodeInElement(element: Element): Text | null {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null);
  const node = walker.nextNode();
  return node?.nodeType === Node.TEXT_NODE ? (node as Text) : null;
}

export function findParagraphElement(node: Node): HTMLElement {
  let current: Node | null = node;
  while (current && current !== document.body) {
    if (
      current.nodeType === Node.ELEMENT_NODE &&
      /^(P|H[1-6]|LI|TD|BLOCKQUOTE|DIV)$/i.test((current as Element).tagName)
    ) {
      return current as HTMLElement;
    }
    current = current.parentNode;
  }
  return document.body;
}

export function rangeFromPoint(clientX: number, clientY: number): Range | null {
  const doc = document as Document & {
    caretRangeFromPoint?(x: number, y: number): Range | null;
  };
  let range: Range | null = null;
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

export function getTapContext(clientX: number, clientY: number): TapContext | null {
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
    charOffset: innerTextOffsetInParagraph(paragraph, range),
  };
}
