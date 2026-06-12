import { collectLayoutBlocks } from "../features/tag-paragraphs.js";
import {
  extractLayoutText,
  layoutRangeAtOffset,
  layoutSegmentsForRange,
  visibleToRawOffset,
  type LayoutSegment,
} from "./layout-text.js";

interface SectionChunk {
  block: HTMLElement;
  start: number;
  end: number;
}

/** Canonical section text: layout blocks joined by `\n`, matching Kotlin [SectionLayout.canonicalText]. */
export function extractSectionCanonicalText(root: Node): string {
  return collectLayoutBlocks(root).map((block) => extractLayoutText(block)).join("\n");
}

function sectionChunks(root: Node): SectionChunk[] {
  const blocks = collectLayoutBlocks(root);
  const chunks: SectionChunk[] = [];
  let current = 0;

  blocks.forEach((block, index) => {
    const text = extractLayoutText(block);
    chunks.push({ block, start: current, end: current + text.length });
    current += text.length;
    if (index < blocks.length - 1) {
      current += 1;
    }
  });

  return chunks;
}

/** Maps a section-local canonical offset to a collapsed DOM range. */
export function layoutRangeAtSectionOffset(root: Node, targetOffset: number): Range | null {
  const chunks = sectionChunks(root);

  for (let index = 0; index < chunks.length; index += 1) {
    const { block, start, end } = chunks[index];
    if (targetOffset >= start && targetOffset <= end) {
      const localOffset = Math.min(Math.max(0, targetOffset - start), Math.max(0, end - start));
      const range = layoutRangeAtOffset(block, localOffset);
      if (range) {
        return range;
      }
    }

    if (index < chunks.length - 1 && targetOffset === end + 1) {
      const nextRange = layoutRangeAtOffset(chunks[index + 1].block, 0);
      if (nextRange) {
        return nextRange;
      }
    }
  }

  return null;
}

/** Layout segments overlapping `[start, end)` in section-local canonical coordinates. */
export function layoutSegmentsForSectionRange(root: Node, start: number, end: number): LayoutSegment[] {
  if (start >= end) {
    return [];
  }

  const segments: LayoutSegment[] = [];
  sectionChunks(root).forEach(({ block, start: blockStart, end: blockEnd }) => {
    const overlapStart = Math.max(start, blockStart);
    const overlapEnd = Math.min(end, blockEnd);
    if (overlapStart >= overlapEnd) {
      return;
    }
    const localStart = overlapStart - blockStart;
    const localEnd = overlapEnd - blockStart;
    segments.push(...layoutSegmentsForRange(block, localStart, localEnd));
  });

  return segments;
}

/** DOM range for a section-local canonical span `[start, end)`. */
export function layoutRangeForSectionSpan(root: Node, start: number, end: number): Range | null {
  const segments = layoutSegmentsForSectionRange(root, start, end);
  const textSegments = segments.filter(
    (segment): segment is Extract<LayoutSegment, { kind: "text" }> => segment.kind === "text",
  );
  if (textSegments.length === 0) {
    return null;
  }

  const first = textSegments[0];
  const last = textSegments[textSegments.length - 1];
  const range = document.createRange();
  range.setStart(first.node, visibleToRawOffset(first.node, first.localStart));
  range.setEnd(last.node, visibleToRawOffset(last.node, last.localEnd));
  return range;
}
