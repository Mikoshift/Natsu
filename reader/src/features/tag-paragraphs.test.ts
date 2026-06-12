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

  it("tags leaf div blocks when no paragraph tags exist", () => {
    document.body.innerHTML = `<div id="block">カタカナ語</div>`;

    tagParagraphs(["カタカナ語"]);

    expect(document.getElementById("block")?.getAttribute(PARAGRAPH_INDEX_ATTR)).toBe("0");
  });
});
