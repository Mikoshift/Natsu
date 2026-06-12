export const WEB_MESSAGE_TYPE = "natsu-reader-call";
export const BRIDGE_NAME = "NatsuBridge";

/** Kotlin -> JS: theme CSS variables from [ReaderWebViewController.applyTheme]. */
export interface ThemeVars {
  fontSizePx?: number;
  lineHeight?: number;
  backgroundColor?: string;
  textColor?: string;
}

/** Kotlin -> JS: search highlight range (section-local char offsets). */
export interface SearchRange {
  start: number;
  end: number;
}

/** Kotlin -> JS: furigana token from Kuromoji. */
export interface RubyToken {
  surface: string;
  reading: string;
}

/** Public API exposed as `window.NatsuReader`. */
export interface ReaderApi {
  init(): void;
  applyTheme(vars: ThemeVars): void;
  highlightSearch(ranges: SearchRange[]): void;
  injectRuby(tokens: RubyToken[]): void;
  scrollToOffset(charOffset: number): void;
}

/** JS -> Kotlin via `@JavascriptInterface` ([ReaderJsBridge]). */
export interface NativeBridge {
  onWordTap(text: string, charOffset: number): void;
  onScrollProgress(ratio: number): void;
  onBridgeReady(): void;
  onChapterReady(): void;
}

export interface WebMessageEnvelope {
  type: typeof WEB_MESSAGE_TYPE;
  method: keyof ReaderApi;
  args?: unknown[];
}

/** JS -> Kotlin: tap payload from [getTapContext]. */
export interface TapContext {
  text: string;
  charOffset: number;
}

declare global {
  interface Window {
    NatsuBridge?: NativeBridge;
    NatsuReader?: ReaderApi;
    __natsuReaderInitialized?: boolean;
  }
}
