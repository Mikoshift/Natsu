import { callBridge } from "./native-bridge.js";
import { canTapAtPoint, getTapContext } from "./text/range-from-point.js";

const SCROLL_THROTTLE_MS = 400;
const TAP_MOVE_THRESHOLD_PX = 10;
const TAP_CLICK_SUPPRESS_MS = 400;

let lastScrollNotify = 0;
let touchStartX = 0;
let touchStartY = 0;
let lastTouchTapAt = 0;

function notifyScrollProgress(): void {
  const now = Date.now();
  if (now - lastScrollNotify < SCROLL_THROTTLE_MS) {
    return;
  }
  lastScrollNotify = now;
  const doc = document.documentElement;
  const scrollHeight = doc.scrollHeight - doc.clientHeight;
  const ratio = scrollHeight > 0 ? doc.scrollTop / scrollHeight : 0;
  callBridge("onScrollProgress", ratio);
}

function clearNativeSelection(): void {
  const selection = window.getSelection();
  if (selection && selection.rangeCount > 0) {
    selection.removeAllRanges();
  }
}

function handleWordTap(clientX: number, clientY: number): void {
  const result = getTapContext(clientX, clientY);
  clearNativeSelection();
  if (!result) {
    return;
  }
  callBridge("onWordTap", result.paragraphIndex, result.charOffset, result.text);
}

export function installEventListeners(): void {
  document.addEventListener(
    "selectstart",
    (event) => {
      event.preventDefault();
    },
    true,
  );

  document.addEventListener(
    "contextmenu",
    (event) => {
      event.preventDefault();
    },
    true,
  );

  document.addEventListener(
    "touchstart",
    (event) => {
      if (event.touches.length !== 1) {
        return;
      }
      touchStartX = event.touches[0].clientX;
      touchStartY = event.touches[0].clientY;
    },
    true,
  );

  document.addEventListener(
    "touchend",
    (event) => {
      if (event.changedTouches.length !== 1) {
        return;
      }
      const touch = event.changedTouches[0];
      const dx = touch.clientX - touchStartX;
      const dy = touch.clientY - touchStartY;
      if (dx * dx + dy * dy > TAP_MOVE_THRESHOLD_PX * TAP_MOVE_THRESHOLD_PX) {
        return;
      }
      clearNativeSelection();
      if (!canTapAtPoint(touch.clientX, touch.clientY)) {
        return;
      }
      event.preventDefault();
      lastTouchTapAt = Date.now();
      handleWordTap(touch.clientX, touch.clientY);
    },
    { capture: true, passive: false },
  );

  document.addEventListener(
    "click",
    (event) => {
      if (Date.now() - lastTouchTapAt < TAP_CLICK_SUPPRESS_MS) {
        event.preventDefault();
        return;
      }
      clearNativeSelection();
      handleWordTap(event.clientX, event.clientY);
    },
    true,
  );

  window.addEventListener("scroll", notifyScrollProgress, { passive: true });
}
