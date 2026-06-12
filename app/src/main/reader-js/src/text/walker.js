export function collectTextRoot() {
  return document.body || document.documentElement;
}

export function isInsideTag(node, root, tagName) {
  let parent = node.parentNode;
  while (parent && parent !== root) {
    if (
      parent.nodeType === Node.ELEMENT_NODE &&
      parent.tagName.toLowerCase() === tagName
    ) {
      return true;
    }
    parent = parent.parentNode;
  }
  return false;
}

function createTextWalker(root, acceptNode) {
  return document.createTreeWalker(root, NodeFilter.SHOW_TEXT, { acceptNode });
}

/** Text nodes visible to the user — skips furigana readings inside `<rt>`. */
export function createVisibleTextWalker(root) {
  return createTextWalker(root, (node) => {
    if (isInsideTag(node, root, "rt")) {
      return NodeFilter.FILTER_REJECT;
    }
    return NodeFilter.FILTER_ACCEPT;
  });
}

/** Text nodes eligible for ruby injection — skips `<rt>` and existing `<ruby>`. */
export function createInjectableTextWalker(root) {
  return createTextWalker(root, (node) => {
    if (isInsideTag(node, root, "rt")) {
      return NodeFilter.FILTER_REJECT;
    }
    if (isInsideTag(node, root, "ruby")) {
      return NodeFilter.FILTER_REJECT;
    }
    return NodeFilter.FILTER_ACCEPT;
  });
}
