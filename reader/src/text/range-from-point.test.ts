import { describe, expect, it } from "vitest";
import { paragraphIndexFromElement } from "./range-from-point.js";

describe("paragraphIndexFromElement", () => {
  it("reads paragraph index from data attribute", () => {
    document.body.innerHTML = '<p data-natsu-paragraph-index="2">猫</p>';
    const paragraph = document.body.firstElementChild as HTMLElement;
    expect(paragraphIndexFromElement(paragraph)).toBe(2);
  });

  it("returns -1 when attribute is missing", () => {
    document.body.innerHTML = "<p>猫</p>";
    const paragraph = document.body.firstElementChild as HTMLElement;
    expect(paragraphIndexFromElement(paragraph)).toBe(-1);
  });
});
