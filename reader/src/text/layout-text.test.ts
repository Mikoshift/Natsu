import { describe, expect, it } from "vitest";
import { extractLayoutText, layoutOffsetAtRange } from "./layout-text.js";

describe("extractLayoutText", () => {
  it("skips rt readings and preserves br newlines", () => {
    document.body.innerHTML =
      '<p id="p">上<ruby><rb>手</rb><rt>じょうず</rt></ruby>に<br>なった</p>';
    const paragraph = document.getElementById("p")!;

    expect(extractLayoutText(paragraph)).toBe("上手に\nなった");
  });
});

describe("layoutOffsetAtRange", () => {
  it("maps a range inside rb to layout-text offset", () => {
    document.body.innerHTML = '<p id="p">' + "漢<ruby><rb>字</rb><rt>じ</rt></ruby>テスト" + "</p>";
    const paragraph = document.getElementById("p")!;
    const rbNode = paragraph.querySelector("rb")!.firstChild as Text;
    const range = document.createRange();
    range.setStart(rbNode, 0);
    range.collapse(true);

    expect(layoutOffsetAtRange(paragraph, range)).toBe(1);
  });

  it("counts br as one layout character", () => {
    document.body.innerHTML = '<p id="p">あ<br>い</p>';
    const paragraph = document.getElementById("p")!;
    const textNodes = [...paragraph.childNodes].filter((node) => node.nodeType === Node.TEXT_NODE);
    const secondLine = textNodes[1] as Text;
    const range = document.createRange();
    range.setStart(secondLine, 0);
    range.collapse(true);

    expect(layoutOffsetAtRange(paragraph, range)).toBe(2);
  });
});
