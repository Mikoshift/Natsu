import type { RubyToken } from "../types.js";
import { layoutRangeForSectionSpan } from "../text/section-layout.js";
import { collectTextRoot } from "../text/walker.js";

export function injectRuby(tokens: RubyToken[] | null | undefined): void {
  if (!tokens?.length) {
    return;
  }
  const root = collectTextRoot();
  const sorted = [...tokens].sort((left, right) => right.start - left.start);
  sorted.forEach((token) => {
    injectRubyAtLayoutSpan(root, token);
  });
}

function injectRubyAtLayoutSpan(root: Node, token: RubyToken): void {
  if (!token.surface || !token.reading || token.start >= token.end) {
    return;
  }
  if (document.querySelector(`ruby[data-natsu-layout-start="${token.start}"]`)) {
    return;
  }

  const range = layoutRangeForSectionSpan(root, token.start, token.end);
  if (!range || isInsideExistingRuby(range.startContainer, root)) {
    return;
  }

  const ruby = document.createElement("ruby");
  ruby.setAttribute("data-natsu-layout-start", String(token.start));
  ruby.setAttribute("data-natsu-surface", token.surface);
  const rb = document.createElement("rb");
  const rt = document.createElement("rt");
  rt.textContent = token.reading;

  try {
    const contents = range.extractContents();
    rb.appendChild(contents);
    ruby.appendChild(rb);
    ruby.appendChild(rt);
    range.insertNode(ruby);
  } catch {
    // Range may cross injected ruby from a later token when offsets drift — skip safely.
  }
}

function isInsideExistingRuby(node: Node, root: Node): boolean {
  let current: Node | null = node;
  while (current && current !== root) {
    if (current.nodeType === Node.ELEMENT_NODE && (current as Element).tagName.toUpperCase() === "RUBY") {
      return true;
    }
    current = current.parentNode;
  }
  return false;
}
