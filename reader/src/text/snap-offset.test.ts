import { describe, expect, it } from "vitest";
import { snapToContentOffset } from "./snap-offset.js";

describe("snapToContentOffset", () => {
  it("keeps offset on content characters", () => {
    expect(snapToContentOffset("猫である", 0)).toBe(0);
  });

  it("snaps from punctuation to the nearest content", () => {
    expect(snapToContentOffset("吾輩は、猫", 3)).toBe(2);
  });

  it("returns null when only punctuation is nearby", () => {
    expect(snapToContentOffset("、、、", 1)).toBeNull();
  });
});
