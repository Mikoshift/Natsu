import { describe, expect, it } from "vitest";
import { charOffsetToPoint } from "./offset.js";
import { extractLayoutText } from "./layout-text.js";

describe("charOffsetToPoint", () => {
  it("maps visible offsets skipping rt text", () => {
    document.body.innerHTML =
      '<div id="root">' + "あ<ruby><rb>い</rb><rt>イ</rt></ruby>う" + "</div>";
    const root = document.getElementById("root")!;

    const atSecondVisible = charOffsetToPoint(root, 1);
    expect(atSecondVisible).not.toBeNull();
    expect(atSecondVisible!.startContainer.textContent).toBe("い");
    expect(atSecondVisible!.startOffset).toBe(0);

    const atThirdVisible = charOffsetToPoint(root, 2);
    expect(atThirdVisible).not.toBeNull();
    expect(atThirdVisible!.startOffset).toBe(0);
    expect(extractLayoutText(root)).toBe("あいう");
  });
});
