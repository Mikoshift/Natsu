import { extractLayoutText } from "../text/layout-text.js";
import { PARAGRAPH_INDEX_ATTR } from "../text/paragraph-index.js";
import { collectTextRoot } from "../text/walker.js";

const PREFERRED_BLOCK_TAGS = new Set(["P", "LI", "TD", "BLOCKQUOTE", "H1", "H2", "H3", "H4", "H5", "H6"]);
const NESTED_BLOCK_SELECTOR = "p, li, h1, h2, h3, h4, h5, h6, td, blockquote, div, img";

export function tagParagraphs(expectedTexts: string[] | null | undefined): void {
  if (!expectedTexts?.length) {
    return;
  }
  const root = collectTextRoot();
  const blocks = collectLayoutBlocks(root);
  let cursor = 0;

  blocks.forEach((block) => {
    if (block.hasAttribute(PARAGRAPH_INDEX_ATTR)) {
      return;
    }
    const text = extractLayoutText(block);
    const matchedIndex = matchParagraphIndex(text, expectedTexts, cursor);
    if (matchedIndex >= 0) {
      block.setAttribute(PARAGRAPH_INDEX_ATTR, String(matchedIndex));
      cursor = matchedIndex + 1;
    }
  });
}

function matchParagraphIndex(text: string, expectedTexts: string[], cursor: number): number {
  if (cursor < expectedTexts.length && textsMatch(text, expectedTexts[cursor])) {
    return cursor;
  }
  return expectedTexts.findIndex((expected, index) => index >= cursor && textsMatch(text, expected));
}

export function collectLayoutBlocks(root: Node): HTMLElement[] {
  const blocks: HTMLElement[] = [];
  collectBlocksFrom(root, blocks);
  return blocks;
}

function collectBlocksFrom(container: Node, blocks: HTMLElement[]): void {
  container.childNodes.forEach((child) => {
    if (child.nodeType !== Node.ELEMENT_NODE) {
      return;
    }
    const element = child as HTMLElement;
    const tag = element.tagName.toUpperCase();
    if (tag === "IMG") {
      blocks.push(element);
      return;
    }
    if (PREFERRED_BLOCK_TAGS.has(tag)) {
      if (isLayoutBlock(element)) {
        blocks.push(element);
      }
      return;
    }
    if (tag === "DIV" || tag === "FIGURE") {
      if (hasNestedLayoutBlock(element)) {
        collectBlocksFrom(element, blocks);
      } else if (isLayoutBlock(element)) {
        blocks.push(element);
      }
      return;
    }
    if (hasNestedLayoutBlock(element)) {
      collectBlocksFrom(element, blocks);
    }
  });
}

function isLayoutBlock(element: HTMLElement): boolean {
  if (element.tagName.toUpperCase() === "IMG") {
    return true;
  }
  if (extractLayoutText(element).trim()) {
    return true;
  }
  return isImageOnlyBlock(element);
}

function isImageOnlyBlock(element: HTMLElement): boolean {
  return element.querySelector("img") !== null && !extractLayoutText(element).trim();
}

function hasNestedLayoutBlock(element: Element): boolean {
  return element.querySelector(NESTED_BLOCK_SELECTOR) !== null;
}

function textsMatch(actual: string, expected: string): boolean {
  if (actual === expected) {
    return true;
  }
  return normalizeLayoutText(actual) === normalizeLayoutText(expected);
}

function normalizeLayoutText(text: string): string {
  return text.replace(/\s+/g, " ").trim();
}
