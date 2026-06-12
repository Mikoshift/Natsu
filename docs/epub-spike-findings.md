# EPUB spike findings (этап 4)

Дата: 2026-06-12  
Ветка spike: `main` (изолированный код в `data/book/epub/spike/`, без DB/UI)

## 1. Образцы

| Файл | Источник | Размер | Ruby | Секции (spine) | Примечание |
|------|----------|--------|------|----------------|------------|
| `minimal-ruby.epub` | Синтетический fixture (`scripts/build_spike_epubs.py`) | 2.3 KB | Да (`<ruby>漢字<rt>かんじ</rt></ruby>`) | 1 | androidTest asset, footnote `aside`, image |
| `plain-japanese.epub` | Синтетический (стиль 吾輩は猫である, plain XHTML) | 1.6 KB | Нет | 1 | Публичный домен / Aozora-like plain text |
| `multi-chapter-ruby.epub` | Синтетический multi-chapter | 2.4 KB | Частично (только ch.1) | 2 | Nested TOC в `nav.xhtml` |

Публичные DRM-free JP EPUB из сети не скачивались: для spike достаточно синтетических образцов, повторяющих типичные паттерны Aozora/電書協 (ruby в одной главе, plain в другой, nested nav). Скрипт `scripts/analyze_epub_spike.py` можно прогнать на внешних `.epub` в `spike-samples/`.

## 2. Spine granularity

**Finding:** один HTML/XHTML resource в `readingOrder` = одна `ReadingSection` в IR.

- `minimal-ruby`: spine `chapter.xhtml` → 1 section, 4 blocks (h1, 2×p, img; `aside` пропущен)
- `multi-chapter-ruby`: `chapter1.xhtml` + `chapter2.xhtml` → 2 sections

**Рекомендация:** не дробить spine item по внутренним `<h1>` на этапе 6. Внутренние заголовки остаются `ReadingBlock.Heading` внутри секции. Section boundary = spine item (совпадает с этапом 5 lazy loading).

## 3. TOC

| Образец | TOC entries | Max depth | Совпадение с spine |
|---------|-------------|-----------|-------------------|
| minimal-ruby | 1 | 4 (nested `<ol>`) | 1:1 с единственным spine href |
| multi-chapter-ruby | 3 | 6 | 2 spine href + 1 nested anchor (`#section-a`) |
| plain-japanese | 0 | 0 | Нет `nav.xhtml` |

**Finding:** Readium `tableOfContents` может быть вложенным и содержать anchor-only записи, не совпадающие 1:1 со spine. `EpubSpineMapper` берёт заголовок секции по точному href spine item; nested TOC используется только для `title`, не для дробления секций.

## 4. Ruby coverage

| Образец | Paragraphs | С ruby | Ruby spans | Ratio |
|---------|------------|--------|------------|-------|
| minimal-ruby | 2 (в body, без aside) | 1 | 1 | 50% |
| multi-chapter-ruby | 2 | 1 | 1 | 50% |
| plain-japanese | 2 | 0 | 0 | 0% |

**Парсер spike (`XhtmlToReadingBlocks`):**

- `<ruby>…<rt>…</rt></ruby>` → `TextSpan(text, reading)` — работает
- `<ruby><rb>…</rb><rt>…</rt></ruby>` — работает
- Ruby без `<rt>` → plain `TextSpan`, Kuromoji fallback на этапе 6
- Групповое / nested ruby — не встречалось в образцах; отложить до реальных издательских EPUB

## 5. Footnotes / endnotes

- `minimal-ruby`: `<aside epub:type="footnote">` — **пропускается** (не попадает в `ReadingBook`)
- Поведение зафиксировано: study mode v1 не показывает сноски; book mode (WebView) — fallback

## 6. Images / CSS

- Локальные `<img src="images/…">` → `ReadingBlock.Image` с путём, разрешённым относительно spine href (`DraftEpubLoader.resolveImagePath`)
- Remote `http(s)://` — отфильтровываются
- CSS / `writing-mode: vertical-rl` — не парсятся (ожидаемо для study mode)

## 7. Рекомендации для этапа 5 (ReadingLocator, lazy sections)

```kotlin
// Draft schema после spike
data class ReadingLocator(
    val sectionId: String,      // = spine item id / manifest section id
    val blockIndex: Int,        // index in ReadingSection.blocks
    val charOffset: Int,        // offset inside block canonical text
)
```

- **Section** = spine item (не TOC node)
- Lazy loading: грузить `ReadingSection` по `sectionId`, не flatten всей книги
- Search index at import: `(sectionId, blockIndex, charOffset)` + snippet
- DB v4: хранить progress как `ReadingLocator`, не flat `charOffset` по всей книге

## 8. Рекомендации для этапа 6 (EPUB study mode)

**Book package layout (предложение):**

```
files/books/{id}/
  manifest.json          # format: epub, spine = readingOrder hrefs
  source.epub            # оригинал (опционально, для re-import)
  OEBPS/...              # extracted assets (или читать из epub via Readium)
```

**Importer:**

1. `EpubPublicationOpener` → `publication.readingOrder`
2. Записать manifest spine (id, title из TOC, path = href)
3. Не распаковывать в `content.txt` — assets per format (принцип 3.1)

**Loader:**

- Переименовать `DraftEpubLoader` → `EpubFormatLoader`
- Contract tests: minimal-ruby + multi-chapter fixtures
- MIME `application/epub+zip` в LibraryScreen

## 9. Технические заметки

- Readium **3.1.2** (`readium-shared`, `readium-streamer`), `pdfFactory = null`
- Требуется **core library desugaring** (minSdk 28)
- XHTML парсинг: **Jsoup** (`Parser.xmlParser()`)
- Full pipeline тестируется в `EpubSpikeInstrumentedTest` (требует устройство/эмулятор)
- JVM: `XhtmlToReadingBlocksTest`, `DraftEpubLoaderTest`
- Сборка androidTest: `./gradlew :app:assembleDebugAndroidTest` — OK

## 10. Открытые вопросы (для реальных издательских EPUB)

- [ ] Покрытие группового ruby (`<ruby>漢<rt>かん</rt>字<rt>じ</rt></ruby>`)
- [ ] `epub:type="footnote"` в popup vs inline aside
- [ ] Vertical writing hints в OPF/CSS
- [ ] DRM/LCP (вне scope v1)
