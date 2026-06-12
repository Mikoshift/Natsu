import { isInsideTag } from "./walker.js";

const SKIP_TAGS = new Set(["RT", "RP", "SCRIPT", "STYLE", "HEAD"]);
const INVISIBLE_CODE_POINTS = new Set([0x200b, 0x200c, 0x00ad]);

/** Layout-visible text: skips ruby readings, preserves `<br>` as `\n`, drops zero-width chars. */
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
      }
    }
    current = end;
  });

  return result;
}

type LayoutChunkMeta =
  | { kind: "text"; node: Text }
  | { kind: "newline" };

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

  const tag = (node as Element).tagName.toUpperCase();
  if (SKIP_TAGS.has(tag)) {
    return;
  }
  if (tag === "BR") {
    emit("\n", { kind: "newline" });
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
