import { describe, expect, it } from "vitest";
import { injectRuby } from "../features/ruby.js";
import { extractLayoutText, layoutSegmentsForRange } from "../text/layout-text.js";

describe("injectRuby", () => {
  it("injects at layout offsets instead of first indexOf match", () => {
    document.body.innerHTML = "<p id=\"p\">猫は猫</p>";
    const root = document.getElementById("p")!;

    injectRuby([
      { surface: "猫", reading: "ねこ", start: 2, end: 3 },
    ]);

    const rubies = root.querySelectorAll("ruby");
    expect(rubies).toHaveLength(1);
    expect(rubies[0]?.querySelector("rb")?.textContent).toBe("猫");
    expect(extractLayoutText(root)).toBe("猫は猫");
  });
});

describe("layoutSegmentsForRange", () => {
  it("skips br newlines when mapping highlight spans", () => {
    document.body.innerHTML = "<div id=\"root\">あ<br>い</div>";
    const root = document.getElementById("root")!;

    const segments = layoutSegmentsForRange(root, 0, 3);
    expect(segments).toHaveLength(2);
    expect(segments[0]?.localStart).toBe(0);
    expect(segments[1]?.localStart).toBe(0);
  });
});
