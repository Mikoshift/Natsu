import { callBridge } from "./native-bridge.js";
import { installEventListeners } from "./events.js";
import { highlightSearch } from "./features/highlight.js";
import { injectRuby } from "./features/ruby.js";
import { scrollToOffset } from "./features/scroll.js";
import { installMessageListener, scheduleReaderInit } from "./messages.js";
import { applyTheme } from "./theme.js";

(function () {
  "use strict";

  const reader = {
    init() {
      if (window.__natsuReaderInitialized) {
        return;
      }
      window.__natsuReaderInitialized = true;

      installEventListeners();

      callBridge("onBridgeReady");

      requestAnimationFrame(() => {
        callBridge("onChapterReady");
      });
    },

    applyTheme,
    highlightSearch,
    injectRuby,
    scrollToOffset,
  };

  window.NatsuReader = reader;
  installMessageListener(reader);
  scheduleReaderInit(() => reader.init());
})();
