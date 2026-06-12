import type { ReaderApi } from "./types.js";
import { WEB_MESSAGE_TYPE } from "./types.js";

export { WEB_MESSAGE_TYPE };

export function installMessageListener(api: ReaderApi): void {
  window.addEventListener("message", (event) => {
    let payload: unknown = event.data;
    if (typeof payload === "string") {
      try {
        payload = JSON.parse(payload);
      } catch {
        return;
      }
    }
    if (
      !payload ||
      typeof payload !== "object" ||
      !("type" in payload) ||
      payload.type !== WEB_MESSAGE_TYPE ||
      !("method" in payload) ||
      typeof payload.method !== "string"
    ) {
      return;
    }
    const method = payload.method as keyof ReaderApi;
    if (typeof api[method] !== "function") {
      return;
    }
    const args = "args" in payload && Array.isArray(payload.args) ? payload.args : [];
    (api[method] as (...args: unknown[]) => void).call(api, ...args);
  });
}

export function scheduleReaderInit(init: () => void): void {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
}
