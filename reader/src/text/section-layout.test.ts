import { describe, expect, it } from "vitest";
import { charOffsetToPoint } from "./offset.js";
import {
  extractSectionCanonicalText,
  layoutRangeAtSectionOffset,
} from "./section-layout.js";

describe("extractSectionCanonicalText", () => {
  it("joins layout blocks with newlines and includes image alt", () => {
    document.body.innerHTML = `
      <h1 id="title">Title</h1>
      <p id="body">Body</p>
      <img id="cover" src="cover.png" alt="Cover"/>
    `;

    expect(extractSectionCanonicalText(document.body)).toBe("Title\nBody\nCover");
  });
});

describe("layoutRangeAtSectionOffset", () => {
  it("maps section offsets across block boundaries", () => {
    document.body.innerHTML = `
      <p id="first">あ</p>
      <p id="second">い</p>
    `;

    const range = layoutRangeAtSectionOffset(document.body, 2);
    expect(range).not.toBeNull();
    expect(range!.startContainer.textContent).toBe("い");
  });

  it("maps section offset to image block", () => {
    document.body.innerHTML = `
      <p id="body">Body</p>
      <img id="cover" src="cover.png" alt="Cover"/>
    `;

    const range = layoutRangeAtSectionOffset(document.body, 5);
    expect(range).not.toBeNull();
    expect(range!.startContainer).toBe(document.getElementById("cover"));
  });
});

describe("charOffsetToPoint", () => {
  it("uses section-local canonical coordinates", () => {
    document.body.innerHTML = `
      <p id="first">あ</p>
      <p id="second">い</p>
    `;

    const range = charOffsetToPoint(document.body, 2);
    expect(range).not.toBeNull();
    expect(range!.startContainer.textContent).toBe("い");
  });
});
