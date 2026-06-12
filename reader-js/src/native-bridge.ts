import type { NativeBridge } from "./types.js";
import { BRIDGE_NAME } from "./types.js";

export { BRIDGE_NAME };

export function bridge(): NativeBridge | undefined {
  return window[BRIDGE_NAME];
}

export function callBridge(method: "onWordTap", text: string, charOffset: number): void;
export function callBridge(method: "onScrollProgress", ratio: number): void;
export function callBridge(method: "onBridgeReady" | "onChapterReady"): void;
export function callBridge(method: keyof NativeBridge, ...args: unknown[]): void {
  const target = bridge();
  if (target && typeof target[method] === "function") {
    (target[method] as (...a: unknown[]) => void)(...args);
  }
}
