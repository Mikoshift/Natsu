import { isInsideTag } from "./walker.js";

const SKIP_TAGS = new Set(["RT", "RP", "SCRIPT", "STYLE", "HEAD"]);

export class DOMTextScanner {
  private _node: Node;
  private _offset: number;
  private _content = "";
  private _remainder: number;
  private _resetOffset: boolean;
  private _stopAtWordBoundary: boolean;

  constructor(
    node: Node,
    offset: number,
    stopAtWordBoundary = false,
  ) {
    const ruby = getParentRubyElement(node);
    const resetOffset = ruby !== null;
    if (resetOffset && ruby !== null) {
      node = ruby;
    }

    this._node = node;
    this._offset = offset;
    this._remainder = 0;
    this._resetOffset = resetOffset;
    this._stopAtWordBoundary = stopAtWordBoundary;
  }

  get node(): Node {
    return this._node;
  }

  get offset(): number {
    return this._offset;
  }

  get remainder(): number {
    return this._remainder;
  }

  get content(): string {
    return this._content;
  }

  seek(length: number): DOMTextScanner {
    const forward = length >= 0;
    this._remainder = forward ? length : -length;
    if (length === 0) {
      return this;
    }

    let node: Node | null = this._node;
    let lastNode: Node = node;
    let resetOffset = this._resetOffset;

    while (node !== null && this._remainder > 0) {
      let enterable = false;

      if (node.nodeType === Node.TEXT_NODE) {
        lastNode = node;
        const keepGoing = forward
          ? this.seekTextForward(node as Text, resetOffset)
          : this.seekTextBackward(node as Text, resetOffset);
        if (!keepGoing) {
          break;
        }
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        if (this._stopAtWordBoundary && !forward) {
          break;
        }
        lastNode = node;
        this._offset = 0;
        ({ enterable } = getElementSeekInfo(node as Element));
      }

      const exitedNodes: Node[] = [];
      node = getNextNode(node, forward, enterable, exitedNodes);
      resetOffset = true;
    }

    this._node = lastNode;
    this._resetOffset = resetOffset;
    return this;
  }

  private seekTextForward(textNode: Text, resetOffset: boolean): boolean {
    const value = textNode.nodeValue || "";
    if (resetOffset) {
      this._offset = 0;
    }

    while (this._offset < value.length && this._remainder > 0) {
      const char = value[this._offset];
      this._offset += 1;
      if (isInvisible(char)) {
        continue;
      }
      this._content += char;
      this._remainder -= 1;
    }

    return this._remainder > 0;
  }

  private seekTextBackward(textNode: Text, resetOffset: boolean): boolean {
    const value = textNode.nodeValue || "";
    if (resetOffset) {
      this._offset = value.length;
    }

    while (this._offset > 0 && this._remainder > 0) {
      const char = value[this._offset - 1];
      if (this._stopAtWordBoundary && isWordDelimiter(char)) {
        if (!isApostropheInWord(value, this._offset - 1)) {
          return false;
        }
      }
      this._offset -= 1;
      if (isInvisible(char)) {
        continue;
      }
      this._content = char + this._content;
      this._remainder -= 1;
    }

    return this._remainder > 0;
  }
}

export function isWordDelimiter(character: string): boolean {
  return /[^\p{L}\p{N}]/u.test(character);
}

export function isWhitespace(text: string): boolean {
  return text.trim().length === 0;
}

export function pointInAnyRect(x: number, y: number, rects: DOMRectList | DOMRect[]): boolean {
  for (let i = 0; i < rects.length; i += 1) {
    const rect = rects[i];
    if (rect.width <= 0 || rect.height <= 0) {
      continue;
    }
    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
      return true;
    }
  }
  return false;
}

function getParentRubyElement(node: Node): HTMLElement | null {
  let parent: Node | null = node.parentNode;
  if (parent !== null && parent.nodeName.toUpperCase() === "RT") {
    parent = parent.parentNode;
    if (parent !== null && parent.nodeName.toUpperCase() === "RUBY") {
      return parent as HTMLElement;
    }
  }
  return null;
}

function getElementSeekInfo(element: Element): { enterable: boolean; newlines: number } {
  const tag = element.tagName.toUpperCase();
  if (SKIP_TAGS.has(tag)) {
    return { enterable: false, newlines: 0 };
  }
  if (tag === "RB") {
    return { enterable: true, newlines: 0 };
  }
  if (tag === "BR") {
    return { enterable: false, newlines: 1 };
  }
  return { enterable: true, newlines: 0 };
}

function getNextNode(
  node: Node,
  forward: boolean,
  visitChildren: boolean,
  exitedNodes: Node[],
): Node | null {
  let next: Node | null = visitChildren ? (forward ? node.firstChild : node.lastChild) : null;
  if (next !== null) {
    return next;
  }

  while (true) {
    exitedNodes.push(node);
    next = forward ? node.nextSibling : node.previousSibling;
    if (next !== null) {
      return next;
    }
    next = node.parentNode;
    if (next === null) {
      return null;
    }
    node = next;
  }
}

function isInvisible(char: string): boolean {
  switch (char.charCodeAt(0)) {
    case 0x200b:
    case 0x200c:
    case 0x00ad:
      return true;
    default:
      return false;
  }
}

function isApostropheInWord(text: string, index: number): boolean {
  const char = text[index];
  if (!isSingleQuote(char) || index <= 0) {
    return false;
  }
  return isWordDelimiter(text[index - 1]);
}

function isSingleQuote(character: string): boolean {
  switch (character.charCodeAt(0)) {
    case 0x27:
    case 0x2019:
    case 0x2032:
    case 0x2035:
    case 0x02bc:
      return true;
    default:
      return false;
  }
}

/** Returns true when [node] is visible text under [root], not inside ruby readings. */
export function isScannableTextNode(node: Node, root: Node): boolean {
  return node.nodeType === Node.TEXT_NODE && !isInsideTag(node, root, "rt");
}
