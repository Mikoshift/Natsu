import { describe, expect, it } from "vitest";
import { charOffsetToPoint, innerTextOffsetInParagraph } from "./offset.js";

describe("innerTextOffsetInParagraph", () => {
  it("counts visible text and skips rt readings", () => {
    document.body.innerHTML = '<p id="p">' + "漢<ruby><rb>字</rb><rt>じ</rt></ruby>テスト" + "</p>";
    const paragraph = document.getElementById("p");
    const rbNode = paragraph.querySelector("rb").firstChild;
    const range = document.createRange();
    range.setStart(rbNode, 0);
    range.collapse(true);

    expect(innerTextOffsetInParagraph(paragraph, range)).toBe(1);
  });
});

describe("charOffsetToPoint", () => {
  it("maps visible offsets skipping rt text", () => {
    document.body.innerHTML =
      '<div id="root">' + "あ<ruby><rb>い</rb><rt>イ</rt></ruby>う" + "</div>";
    const root = document.getElementById("root");

    const atSecondVisible = charOffsetToPoint(root, 1);
    expect(atSecondVisible).not.toBeNull();
    expect(atSecondVisible.startContainer.textContent).toBe("あ");
    expect(atSecondVisible.startOffset).toBe(1);

    const atThirdVisible = charOffsetToPoint(root, 2);
    expect(atThirdVisible).not.toBeNull();
    expect(atThirdVisible.startOffset).toBe(1);
  });
});
