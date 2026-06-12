import { charOffsetToPoint } from "../text/offset.js";
import { collectTextRoot } from "../text/walker.js";

export function scrollToOffset(charOffset: number): void {
  const root = collectTextRoot();
  const range = charOffsetToPoint(root, charOffset);
  if (!range) {
    return;
  }
  const rect = range.getBoundingClientRect();
  const targetTop = window.scrollY + rect.top - window.innerHeight * 0.3;
  window.scrollTo({ top: Math.max(0, targetTop), behavior: "auto" });
}
