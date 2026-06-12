import { describe, expect, it } from "vitest";
import { PARAGRAPH_INDEX_ATTR } from "../text/paragraph-index.js";
import { tagParagraphs } from "./tag-paragraphs.js";

describe("tagParagraphs", () => {
  it("tags matching DOM blocks with paragraph indices", () => {
    document.body.innerHTML = `
      <p id="first">吾輩は猫</p>
      <p id="second">である</p>
    `;

    tagParagraphs(["吾輩は猫", "である"]);

    expect(document.getElementById("first")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("0");
    expect(document.getElementById("second")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("1");
  });

  it("tags standalone image blocks by alt text", () => {
    document.body.innerHTML = `
      <p id="first">本文</p>
      <img id="cover" src="cover.png" alt="Cover"/>
      <p id="second">続き</p>
    `;

    tagParagraphs(["本文", "Cover", "続き"]);

    expect(document.getElementById("first")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("0");
    expect(document.getElementById("cover")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("1");
    expect(document.getElementById("second")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("2");
  });

  it("tags image-only paragraph blocks with empty expected text", () => {
    document.body.innerHTML = `
      <p id="first">本文</p>
      <p id="image"><img src="cover.png"/></p>
      <p id="second">続き</p>
    `;

    tagParagraphs(["本文", "", "続き"]);

    expect(document.getElementById("first")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("0");
    expect(document.getElementById("image")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("1");
    expect(document.getElementById("second")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("2");
  });

  it("tags leaf div blocks when no paragraph tags exist", () => {
    document.body.innerHTML = `<div id="block">カタカナ語</div>`;

    tagParagraphs(["カタカナ語"]);

    expect(document.getElementById("block")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("0");
  });
});
