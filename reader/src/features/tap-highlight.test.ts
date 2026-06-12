import { describe, expect, it, vi } from "vitest";
import { flashTapRange } from "./tap-highlight.js";

describe("flashTapRange", () => {
  it("wraps tapped range with flash class and removes it", () => {
    vi.useFakeTimers();
    document.body.innerHTML = "<p id=\"p\">吾輩は猫</p>";
    const paragraph = document.getElementById("p") as HTMLElement;

    flashTapRange(paragraph, 3, 4);

    const flash = paragraph.querySelector(".natsu-tap-flash");
    expect(flash).not.toBeNull();
    expect(flash?.textContent).toBe("猫");

    vi.advanceTimersByTime(300);
    expect(paragraph.querySelector(".natsu-tap-flash")).toBeNull();
    expect(paragraph.textContent).toBe("吾輩は猫");
    vi.useRealTimers();
  });

  it("highlights multi-character morpheme", () => {
    vi.useFakeTimers();
    document.body.innerHTML = "<p id=\"p\">吾輩は猫</p>";
    const paragraph = document.getElementById("p") as HTMLElement;

    flashTapRange(paragraph, 0, 3);

    const flash = paragraph.querySelector(".natsu-tap-flash");
    expect(flash).not.toBeNull();
    expect(flash?.textContent).toBe("吾輩は");
    vi.useRealTimers();
  });
});
