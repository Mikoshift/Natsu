#!/usr/bin/env python3
"""Build synthetic EPUB fixtures for spike analysis."""

from __future__ import annotations

import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT / "app/src/androidTest/assets/spike"
SAMPLES_DIR = ROOT / "spike-samples"

MIMETYPE = b"application/epub+zip"
CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
""".strip()

COVER_PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000a49444154789c6360000000020001e221bc330000000049454e44ae426082"
)


def write_epub(output_path: Path, *, opf: str, files: dict[str, str | bytes]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_path, "w") as epub:
        epub.writestr("mimetype", MIMETYPE, compress_type=zipfile.ZIP_STORED)
        epub.writestr("META-INF/container.xml", CONTAINER_XML)
        epub.writestr("OEBPS/content.opf", opf)
        for relative_path, payload in files.items():
            if isinstance(payload, str):
                epub.writestr(f"OEBPS/{relative_path}", payload)
            else:
                epub.writestr(f"OEBPS/{relative_path}", payload)


def build_minimal_ruby() -> None:
    opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">spike-minimal-ruby</dc:identifier>
    <dc:title>Spike Ruby Chapter</dc:title>
    <dc:language>ja</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover" href="images/cover.png" media-type="image/png"/>
  </manifest>
  <spine>
    <itemref idref="chapter"/>
  </spine>
</package>
""".strip()
    chapter = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head><title>第一章</title></head>
  <body>
    <h1>第一章</h1>
    <p><ruby>漢字<rt>かんじ</rt></ruby>のテストです。</p>
    <p>吾輩は猫である。</p>
    <img src="images/cover.png" alt="Cover"/>
    <aside epub:type="footnote"><p>脚注テキスト</p></aside>
  </body>
</html>
""".strip()
    nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <body>
    <nav epub:type="toc"><ol><li><a href="chapter.xhtml">第一章</a></li></ol></nav>
  </body>
</html>
""".strip()
    write_epub(
        ASSETS_DIR / "minimal-ruby.epub",
        opf=opf,
        files={
            "chapter.xhtml": chapter,
            "nav.xhtml": nav,
            "images/cover.png": COVER_PNG,
        },
    )


def build_plain_japanese() -> None:
    opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">spike-plain-japanese</dc:identifier>
    <dc:title>吾輩は猫である（抜粋）</dc:title>
    <dc:language>ja</dc:language>
  </metadata>
  <manifest>
    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter"/>
  </spine>
</package>
""".strip()
    chapter = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>吾輩は猫である</title></head>
  <body>
    <h1>吾輩は猫である</h1>
    <p>吾輩は猫である。名前はまだ無い。</p>
    <p>どこで生れたかとんと見当がつかぬ。</p>
  </body>
</html>
""".strip()
    write_epub(
        SAMPLES_DIR / "plain-japanese.epub",
        opf=opf,
        files={"chapter.xhtml": chapter},
    )


def build_multi_chapter() -> None:
    opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">spike-multi-chapter</dc:identifier>
    <dc:title>Multi Chapter Spike</dc:title>
    <dc:language>ja</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
  </spine>
</package>
""".strip()
    chapter1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <body><h1>第一章</h1><p><ruby>猫<rt>ねこ</rt></ruby>の話。</p></body>
</html>
""".strip()
    chapter2 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
  <body><h1>第二章</h1><p>続きの本文。</p></body>
</html>
""".strip()
    nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <body>
    <nav epub:type="toc">
      <ol>
        <li><a href="chapter1.xhtml">第一章</a>
          <ol><li><a href="chapter1.xhtml#section-a">猫</a></li></ol>
        </li>
        <li><a href="chapter2.xhtml">第二章</a></li>
      </ol>
    </nav>
  </body>
</html>
""".strip()
    write_epub(
        SAMPLES_DIR / "multi-chapter-ruby.epub",
        opf=opf,
        files={
            "chapter1.xhtml": chapter1,
            "chapter2.xhtml": chapter2,
            "nav.xhtml": nav,
        },
    )


if __name__ == "__main__":
    build_minimal_ruby()
    build_plain_japanese()
    build_multi_chapter()
    print(f"Wrote fixtures to {ASSETS_DIR} and {SAMPLES_DIR}")
