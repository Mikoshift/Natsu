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

/** Kotlin -> JS: furigana token with section-local layout offsets. */
export interface RubyToken {
  surface: string;
  reading: string;
  start: number;
  end: number;
}

/** Public API exposed as `window.NatsuReader`. */
export interface ReaderApi {
  init(): void;
  applyTheme(vars: ThemeVars): void;
  tagParagraphs(texts: string[]): void;
  highlightTapToken(paragraphIndex: number, start: number, end: number): void;
  highlightSearch(ranges: SearchRange[]): void;
  injectRuby(tokens: RubyToken[]): void;
  scrollToOffset(charOffset: number): void;
}

/** JS -> Kotlin via `@JavascriptInterface` ([ReaderJsBridge]). */
export interface NativeBridge {
  onWordTap(paragraphIndex: number, charOffset: number, paragraphText: string): void;
  onScrollProgress(ratio: number): void;
  onBridgeReady(): void;
  onChapterReady(): void;
}

export interface WebMessageEnvelope {
  type: typeof WEB_MESSAGE_TYPE;
  method: keyof ReaderApi;
  args?: unknown[];
}

/** Tap payload from [getTapContext]. */
export interface TapContext {
  paragraphIndex: number;
  /** Paragraph-local layout text (fallback for Kotlin when index is missing). */
  text: string;
  /** Tap position within [text], snapped off punctuation when needed. */
  charOffset: number;
  paragraph: HTMLElement;
}

declare global {
  interface Window {
    NatsuBridge?: NativeBridge;
    NatsuReader?: ReaderApi;
    __natsuReaderInitialized?: boolean;
  }
}
