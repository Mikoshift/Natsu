const MAX_SNAP_DISTANCE = 3;

/** Moves a tap off punctuation/whitespace onto the nearest readable character. */
export function snapToContentOffset(text: string, charOffset: number): number | null {
  if (text.length === 0) {
    return null;
  }

  const clamped = Math.max(0, Math.min(charOffset, text.length - 1));
  if (isContentChar(text[clamped])) {
    return clamped;
  }

  for (let delta = 1; delta <= MAX_SNAP_DISTANCE; delta += 1) {
    if (clamped - delta >= 0 && isContentChar(text[clamped - delta])) {
      return clamped - delta;
    }
    if (clamped + delta < text.length && isContentChar(text[clamped + delta])) {
      return clamped + delta;
    }
  }

  return null;
}

function isContentChar(character: string): boolean {
  return /[\p{L}\p{N}]/u.test(character);
}
