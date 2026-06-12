import type { RubyToken } from "../types.js";
import { createInjectableTextWalker, collectTextRoot } from "../text/walker.js";

export function injectRuby(tokens: RubyToken[] | null | undefined): void {
  if (!tokens?.length) {
    return;
  }
  const root = collectTextRoot();
  tokens.forEach((token) => {
    if (!token.surface || !token.reading) {
      return;
    }
    const walker = createInjectableTextWalker(root);
    let node = walker.nextNode();
    while (node) {
      const parent = node.parentNode;
      if (!parent) {
        node = walker.nextNode();
        continue;
      }
      const text = node.textContent || "";
      const index = text.indexOf(token.surface);
      if (index < 0) {
        node = walker.nextNode();
        continue;
      }
      const before = text.slice(0, index);
      const after = text.slice(index + token.surface.length);
      const ruby = document.createElement("ruby");
      ruby.setAttribute("data-natsu-surface", token.surface);
      const rb = document.createElement("rb");
      rb.textContent = token.surface;
      const rt = document.createElement("rt");
      rt.textContent = token.reading;
      ruby.appendChild(rb);
      ruby.appendChild(rt);
      const fragment = document.createDocumentFragment();
      if (before) {
        fragment.appendChild(document.createTextNode(before));
      }
      fragment.appendChild(ruby);
      if (after) {
        fragment.appendChild(document.createTextNode(after));
      }
      parent.replaceChild(fragment, node);
      break;
    }
  });
}
