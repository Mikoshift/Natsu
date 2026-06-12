#!/usr/bin/env python3
"""Collect EPUB structure metrics for docs/epub-spike-findings.md."""

from __future__ import annotations

import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SAMPLE_DIRS = [
    ROOT / "app/src/androidTest/assets/spike",
    ROOT / "spike-samples",
]

NS = {
    "opf": "http://www.idpf.org/2007/opf",
    "dc": "http://purl.org/dc/elements/1.1/",
    "xhtml": "http://www.w3.org/1999/xhtml",
    "epub": "http://www.idpf.org/2007/ops",
}


def analyze_epub(path: Path) -> dict[str, object]:
    with zipfile.ZipFile(path) as epub:
        opf_path = find_opf_path(epub)
        opf = ET.fromstring(epub.read(opf_path))
        title = text_or_empty(opf.find("opf:metadata/dc:title", NS))
        spine_ids = [
            itemref.attrib["idref"]
            for itemref in opf.findall("opf:spine/opf:itemref", NS)
        ]
        manifest = {
            item.attrib["id"]: item.attrib["href"]
            for item in opf.findall("opf:manifest/opf:item", NS)
        }
        spine_hrefs = [manifest[item_id] for item_id in spine_ids if item_id in manifest]

        xhtml_files = [
            name
            for name in epub.namelist()
            if name.lower().endswith((".xhtml", ".html", ".htm"))
            and "nav" not in name.lower()
        ]
        paragraph_count = 0
        ruby_paragraph_count = 0
        ruby_count = 0
        aside_count = 0
        image_count = 0
        canonical_length = 0

        for name in xhtml_files:
            xml = epub.read(name).decode("utf-8", errors="replace")
            aside_count += len(re.findall(r"<aside\b", xml, flags=re.IGNORECASE))
            image_count += len(re.findall(r"<img\b", xml, flags=re.IGNORECASE))
            ruby_count += len(re.findall(r"<ruby\b", xml, flags=re.IGNORECASE))
            paragraphs = re.findall(r"<p\b[^>]*>.*?</p>", xml, flags=re.IGNORECASE | re.DOTALL)
            paragraph_count += len(paragraphs)
            for paragraph in paragraphs:
                plain = strip_tags(paragraph)
                canonical_length += len(plain)
                if "<ruby" in paragraph.lower():
                    ruby_paragraph_count += 1

        nav_depth = 0
        nav_entries = 0
        nav_file = next((name for name in epub.namelist() if name.endswith("nav.xhtml")), None)
        if nav_file:
            nav_xml = ET.fromstring(epub.read(nav_file))
            nav_entries, nav_depth = walk_nav(nav_xml)

        return {
            "file": path.name,
            "path": str(path),
            "size_bytes": path.stat().st_size,
            "title": title,
            "spine_size": len(spine_hrefs),
            "spine_hrefs": spine_hrefs,
            "toc_entries": nav_entries,
            "toc_depth": nav_depth,
            "xhtml_files": len(xhtml_files),
            "paragraph_count": paragraph_count,
            "ruby_paragraph_count": ruby_paragraph_count,
            "ruby_count": ruby_count,
            "aside_count": aside_count,
            "image_count": image_count,
            "canonical_text_length_estimate": canonical_length,
        }


def find_opf_path(epub: zipfile.ZipFile) -> str:
    container = ET.fromstring(epub.read("META-INF/container.xml"))
    return container.find(".//{urn:oasis:names:tc:opendocument:xmlns:container}rootfile").attrib["full-path"]


def text_or_empty(node: ET.Element | None) -> str:
    return (node.text or "").strip() if node is not None else ""


def strip_tags(fragment: str) -> str:
    text = re.sub(r"<rt[^>]*>.*?</rt>", "", fragment, flags=re.IGNORECASE | re.DOTALL)
    text = re.sub(r"<rp[^>]*>.*?</rp>", "", text, flags=re.IGNORECASE | re.DOTALL)
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s+", " ", text).strip()


def walk_nav(node: ET.Element, depth: int = 0) -> tuple[int, int]:
    count = 0
    max_depth = depth
    for child in node:
        tag = child.tag.rsplit("}", 1)[-1]
        if tag == "a":
            count += 1
            max_depth = max(max_depth, depth + 1)
        child_count, child_depth = walk_nav(child, depth + 1 if tag in {"ol", "ul", "li", "nav"} else depth)
        count += child_count
        max_depth = max(max_depth, child_depth)
    return count, max_depth


def main() -> None:
    reports: list[dict[str, object]] = []
    for directory in SAMPLE_DIRS:
        if not directory.exists():
            continue
        for epub_path in sorted(directory.glob("*.epub")):
            reports.append(analyze_epub(epub_path))

    for report in reports:
        print(f"## {report['file']}")
        for key, value in report.items():
            if key == "file":
                continue
            print(f"- {key}: {value}")
        print()


if __name__ == "__main__":
    main()
