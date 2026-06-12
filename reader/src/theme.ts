import type { ThemeVars } from "./types.js";

export function applyTheme(vars: ThemeVars | null | undefined): void {
  if (!vars) {
    return;
  }
  const root = document.documentElement;
  const body = document.body;
  if (vars.fontSizePx != null) {
    root.style.setProperty("--natsu-font-size", vars.fontSizePx + "px");
  }
  if (vars.lineHeight != null) {
    root.style.setProperty("--natsu-line-height", String(vars.lineHeight));
  }
  if (vars.backgroundColor != null) {
    root.style.setProperty("--natsu-bg", vars.backgroundColor);
    root.style.backgroundColor = vars.backgroundColor;
    if (body) {
      body.style.backgroundColor = vars.backgroundColor;
    }
  }
  if (vars.textColor != null) {
    root.style.setProperty("--natsu-text", vars.textColor);
    if (body) {
      body.style.color = vars.textColor;
    }
  }
  if (body) {
    body.classList.add("natsu-chapter");
  }
}
