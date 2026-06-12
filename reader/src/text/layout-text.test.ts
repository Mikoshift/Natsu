import { describe, expect, it } from "vitest";
import {
  extractLayoutText,
  layoutOffsetAtRange,
  layoutRangeAtOffset,
  layoutRangeForSpan,
} from "./layout-text.js";

describe("extractLayoutText", () => {
  it("includes img alt text", () => {
    document.body.innerHTML = '<img id="cover" src="cover.png" alt="Cover"/>';
    const image = document.getElementById("cover")!;

    expect(extractLayoutText(image)).toBe("Cover");
  });

  it("returns empty string for img without alt", () => {
    document.body.innerHTML = '<img id="cover" src="cover.png"/>';
    const image = document.getElementById("cover")!;

    expect(extractLayoutText(image)).toBe("");
  });

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

describe("layoutRangeAtOffset", () => {
  it("maps offset inside img alt to the image element", () => {
    document.body.innerHTML = '<img id="cover" src="cover.png" alt="Cover"/>';
    const image = document.getElementById("cover")!;

    const range = layoutRangeAtOffset(image, 2);
    expect(range).not.toBeNull();
    expect(range!.startContainer).toBe(image);
  });
});

describe("layoutRangeForSpan", () => {
  it("maps a span across br boundaries to DOM range endpoints", () => {
    document.body.innerHTML = "<p id=\"p\">あ<br>い</p>";
    const paragraph = document.getElementById("p")!;

    const range = layoutRangeForSpan(paragraph, 0, 3);
    expect(range).not.toBeNull();
    expect(extractLayoutText(paragraph)).toBe("あ\nい");
  });
});
