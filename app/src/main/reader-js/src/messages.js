export const WEB_MESSAGE_TYPE = "natsu-reader-call";

export function installMessageListener(api) {
  window.addEventListener("message", (event) => {
    let payload = event.data;
    if (typeof payload === "string") {
      try {
        payload = JSON.parse(payload);
      } catch {
        return;
      }
    }
    if (!payload || payload.type !== WEB_MESSAGE_TYPE) {
      return;
    }
    const fn = api[payload.method];
    if (typeof fn !== "function") {
      return;
    }
    fn.apply(api, payload.args || []);
  });
}

export function scheduleReaderInit(init) {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
}
