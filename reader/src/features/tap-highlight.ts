import { layoutSegmentsForRange, visibleToRawOffset } from "../text/layout-text.js";
import { PARAGRAPH_INDEX_ATTR } from "../text/paragraph-index.js";

const FLASH_MS = 300;

let activeFlashTimeout: number | null = null;

export function highlightTapToken(paragraphIndex: number, start: number, end: number): void {
  if (paragraphIndex < 0 || start >= end) {
    return;
  }
  const paragraph = findParagraphByIndex(paragraphIndex);
  if (!paragraph) {
    return;
  }
  flashTapRange(paragraph, start, end);
}

export function flashTapRange(paragraph: HTMLElement, start: number, end: number): void {
  clearTapFlash();
  if (activeFlashTimeout !== null) {
    window.clearTimeout(activeFlashTimeout);
    activeFlashTimeout = null;
  }

  const segments = layoutSegmentsForRange(paragraph, start, end);
  if (segments.length === 0) {
    return;
  }

  const textSegments = segments.filter((segment) => segment.kind === "text");
  for (let index = textSegments.length - 1; index >= 0; index -= 1) {
    const segment = textSegments[index];
    const localLength = segment.localEnd - segment.localStart;
    if (localLength <= 0) {
      continue;
    }
    const textNode = segment.node;
    const rawEnd = visibleToRawOffset(textNode, segment.localEnd);
    const rawStart = visibleToRawOffset(textNode, segment.localStart);
    if (rawEnd < textNode.length) {
      textNode.splitText(rawEnd);
    }
    let flashNode = textNode;
    if (rawStart > 0) {
      flashNode = textNode.splitText(rawStart);
    }
    const mark = document.createElement("span");
    mark.className = "natsu-tap-flash";
    flashNode.parentNode?.replaceChild(mark, flashNode);
    mark.appendChild(flashNode);
  }

  activeFlashTimeout = window.setTimeout(() => {
    clearTapFlash();
    activeFlashTimeout = null;
  }, FLASH_MS);
}

export function clearTapFlash(): void {
  document.querySelectorAll("span.natsu-tap-flash").forEach((mark) => {
    const parent = mark.parentNode;
    if (!parent) {
      return;
    }
    while (mark.firstChild) {
      parent.insertBefore(mark.firstChild, mark);
    }
    parent.removeChild(mark);
    if (parent instanceof HTMLElement) {
      parent.normalize();
    }
  });
}

function findParagraphByIndex(paragraphIndex: number): HTMLElement | null {
  return document.querySelector(`[${PARAGRAPH_INDEX_ATTR}="${paragraphIndex}"]`);
}
