import { isInsideTag } from "./walker.js";

const SKIP_TAGS = new Set(["RT", "RP", "SCRIPT", "STYLE", "HEAD"]);
const INVISIBLE_CODE_POINTS = new Set([0x200b, 0x200c, 0x00ad]);

/** Layout-visible text: skips ruby readings, preserves `<br>` as `\n`, includes `<img alt>`. */
export function extractLayoutText(root: Node): string {
  const parts: string[] = [];
  walkLayout(root, root, (chunk) => parts.push(chunk));
  return parts.join("");
}

/** Maps a collapsed range to an offset in [extractLayoutText] coordinates. */
export function layoutOffsetAtRange(root: Node, range: Range): number | null {
  const targetNode = range.startContainer;
  const targetOffset = range.startOffset;
  let offset = 0;
  let found = false;

  walkLayout(root, root, (chunk, meta) => {
    if (found) {
      return;
    }
    if (meta?.kind === "text" && meta.node === targetNode) {
      offset += visibleOffsetInTextNode(meta.node, targetOffset);
      found = true;
      return;
    }
    if (meta?.kind === "image" && targetNode === meta.element) {
      found = true;
      return;
    }
    offset += chunk.length;
  });

  return found ? offset : null;
}

/** Maps a layout-text offset back to a collapsed DOM range. */
export function layoutRangeAtOffset(root: Node, targetOffset: number): Range | null {
  let current = 0;
  let result: Range | null = null;

  walkLayout(root, root, (chunk, meta) => {
    if (result) {
      return;
    }
    const start = current;
    const end = current + chunk.length;
    if (targetOffset >= start && targetOffset < end) {
      if (meta?.kind === "text") {
        const range = document.createRange();
        const rawOffset = rawOffsetForVisibleIndex(meta.node, targetOffset - start);
        range.setStart(meta.node, rawOffset);
        range.collapse(true);
        result = range;
      } else if (meta?.kind === "image") {
        const range = document.createRange();
        range.setStart(meta.element, 0);
        range.collapse(true);
        result = range;
      }
    } else if (meta?.kind === "image" && chunk.length === 0 && targetOffset === start) {
      const range = document.createRange();
      range.setStart(meta.element, 0);
      range.collapse(true);
      result = range;
    }
    current = end;
  });

  return result;
}

export interface LayoutTextSegment {
  node: Text;
  localStart: number;
  localEnd: number;
}

export type LayoutSegment =
  | ({ kind: "text" } & LayoutTextSegment)
  | { kind: "image"; element: HTMLImageElement; localStart: number; localEnd: number };

/** Layout-text segments overlapping `[start, end)` within a single block root. */
export function layoutSegmentsForRange(root: Node, start: number, end: number): LayoutSegment[] {
  if (start >= end) {
    return [];
  }
  const segments: LayoutSegment[] = [];
  let current = 0;

  walkLayout(root, root, (chunk, meta) => {
    const chunkStart = current;
    const chunkEnd = current + chunk.length;
    if (end > chunkStart && start < chunkEnd) {
      if (meta?.kind === "text") {
        segments.push({
          kind: "text",
          node: meta.node,
          localStart: Math.max(0, start - chunkStart),
          localEnd: Math.min(chunk.length, end - chunkStart),
        });
      } else if (meta?.kind === "image") {
        segments.push({
          kind: "image",
          element: meta.element,
          localStart: Math.max(0, start - chunkStart),
          localEnd: Math.min(chunk.length, end - chunkStart),
        });
      }
    }
    current = chunkEnd;
  });

  return segments;
}

/** Collapsed DOM range for a layout-text span `[start, end)` within a single block root. */
export function layoutRangeForSpan(root: Node, start: number, end: number): Range | null {
  const segments = layoutSegmentsForRange(root, start, end);
  if (segments.length === 0) {
    return null;
  }
  const textSegments = segments.filter((segment): segment is { kind: "text" } & LayoutTextSegment => segment.kind === "text");
  if (textSegments.length === 0) {
    return null;
  }
  const first = textSegments[0];
  const last = textSegments[textSegments.length - 1];
  const range = document.createRange();
  range.setStart(first.node, rawOffsetForVisibleIndex(first.node, first.localStart));
  range.setEnd(last.node, rawOffsetForVisibleIndex(last.node, last.localEnd));
  return range;
}

type LayoutChunkMeta =
  | { kind: "text"; node: Text }
  | { kind: "newline" }
  | { kind: "image"; element: HTMLImageElement };

function walkLayout(
  node: Node,
  root: Node,
  emit: (chunk: string, meta?: LayoutChunkMeta) => void,
): void {
  if (node.nodeType === Node.TEXT_NODE) {
    if (isInsideTag(node, root, "rt")) {
      return;
    }
    const filtered = filterInvisible(node.textContent || "");
    if (filtered.length > 0) {
      emit(filtered, { kind: "text", node: node as Text });
    }
    return;
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return;
  }

  const element = node as Element;
  const tag = element.tagName.toUpperCase();
  if (SKIP_TAGS.has(tag)) {
    return;
  }
  if (tag === "BR") {
    emit("\n", { kind: "newline" });
    return;
  }
  if (tag === "IMG") {
    const alt = (element as HTMLImageElement).alt || "";
    emit(alt, { kind: "image", element: element as HTMLImageElement });
    return;
  }

  for (const child of node.childNodes) {
    walkLayout(child, root, emit);
  }
}

function filterInvisible(text: string): string {
  let result = "";
  for (const char of text) {
    if (!INVISIBLE_CODE_POINTS.has(char.charCodeAt(0))) {
      result += char;
    }
  }
  return result;
}

function visibleOffsetInTextNode(node: Text, rawOffset: number): number {
  const text = node.textContent || "";
  const clamped = Math.max(0, Math.min(rawOffset, text.length));
  let visible = 0;
  for (let i = 0; i < clamped; i += 1) {
    if (!INVISIBLE_CODE_POINTS.has(text.charCodeAt(i))) {
      visible += 1;
    }
  }
  return visible;
}

/** Maps a visible character index inside [node] to a raw DOM offset. */
export function visibleToRawOffset(node: Text, visibleOffset: number): number {
  return rawOffsetForVisibleIndex(node, visibleOffset);
}

function rawOffsetForVisibleIndex(node: Text, visibleIndex: number): number {
  const text = node.textContent || "";
  let visible = 0;
  for (let raw = 0; raw <= text.length; raw += 1) {
    if (visible === visibleIndex) {
      return raw;
    }
    if (raw < text.length && !INVISIBLE_CODE_POINTS.has(text.charCodeAt(raw))) {
      visible += 1;
    }
  }
  return text.length;
}
