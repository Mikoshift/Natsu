export const BRIDGE_NAME = "NatsuBridge";

export function bridge() {
  return window[BRIDGE_NAME];
}

export function callBridge(method, ...args) {
  const target = bridge();
  if (target && typeof target[method] === "function") {
    target[method](...args);
  }
}
