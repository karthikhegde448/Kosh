![[Adobe Express - file.png]]
  <h1>Kosh — PDF Reader</h1>

  <p>
    A powerful, privacy-focused PDF reader and toolkit for Android.<br/>
    Read, annotate, and transform PDFs with 14 built-in tools — fully offline, zero ads, zero tracking.
  </p>

[![Android](https://img.shields.io/badge/Platform-Android-green.svg?style=flat)](https://www.android.com/)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-blue.svg)](https://developer.android.com/jetpack/compose)

</div>

---

## Download

[![GitHub Release](https://img.shields.io/github/v/release/karthikhegde448/Kosh)](https://github.com/karthikhegde448/Kosh/releases/latest)

<a href="https://github.com/karthikhegde448/Kosh/releases/download/v2.1.3/Kosh.apk">
  <img src="https://raw.githubusercontent.com/ahmmedrejowan/PdfReaderPro/master/files/get.png" width="224px"/>
</a>

Check out the [releases](https://github.com/karthikhegde448/Kosh/releases) section for all versions.

---

## Features

### 📖 PDF Reader
- Smooth PDF rendering with pinch-to-zoom, scroll, and page navigation
- Vertical and horizontal scroll modes
- Bookmarks — save and jump to any page
- Table of Contents — navigate using document outlines
- Full-text search within documents
- Page scrubber slider for fast navigation
- Auto-scroll with adjustable speed for hands-free reading
- Brightness control within the reader
- Annotations and highlights

### 🗂️ File Management
- Browse all PDFs on your device
- Folder browser — navigate by directory
- Recent files for quick access
- Favorites — star your most-used PDFs
- Sort by name, date, size
- Rename, delete, share, and get file info

### 🛠️ 14 PDF Tools

| # | Tool | What it does |
|---|---|---|
| 1 | **Merge PDF** | Combine multiple PDFs into one document |
| 2 | **Split PDF** | Split a PDF into multiple files by page range |
| 3 | **Compress PDF** | Reduce PDF file size while preserving quality |
| 4 | **Rotate PDF** | Rotate individual or all pages by 90° increments |
| 5 | **Reorder Pages** | Drag and drop to reorder pages in any order |
| 6 | **Remove Pages** | Delete selected pages from a PDF |
| 7 | **Add Page Numbers** | Stamp page numbers with custom position and style |
| 8 | **Watermark** | Add text or image watermarks to every page |
| 9 | **Lock PDF** | Password-protect a PDF |
| 10 | **Unlock PDF** | Remove password protection from a PDF |
| 11 | **PDF to Images** | Export PDF pages as PNG or JPEG images |
| 12 | **PDF to Text** | Extract text content with optional OCR support |
| 13 | **Images to PDF** | Convert images to PDF with full image editor |
| 14 | **Text to PDF** | Convert plain text to PDF with custom styling |
| 15 | **Multiple Pages per Sheet** | Place multiple PDF pages onto a single sheet |

### 🖼️ Image to PDF Editor
Full-featured image editor when converting images to PDF:
- **14 Filters** — Photo, Mono, Lomoish, Poster, Process, Vignette, Negative, Sepia, Grain, Document, Lighten, B&W, Grayscale, Whiteboard
- **Perspective Crop** — 4 corner handles + draggable mid-edge bars
- **Pen Annotation** — Draw on images with 7 colors, adjustable size, undo/redo, two-finger zoom
- **Rotate** — 90° rotation
- **Reorder** — Drag and drop to reorder pages
- **Apply filter to all pages** at once

### ✏️ Text to PDF
- Custom font, size, line spacing, and margins
- **Notebook mode** — text sits on ruled lines like a real notebook page
- Custom output filename

### 🔒 Privacy
- 100% offline — no internet required for any core feature
- No ads, no analytics, no data collection
- No account required

---

## Architecture

Kosh follows **Clean Architecture** with **MVVM** pattern:

```
app/
├── data/                      # Data layer
│   ├── local/
│   │   ├── database/          # Room database (recents, favorites, bookmarks, annotations)
│   │   └── preferences/       # DataStore preferences
│   └── repository/            # Repository implementations
│
├── domain/                    # Domain layer
│   ├── model/                 # Domain models
│   └── repository/            # Repository interfaces
│
├── presentation/              # Presentation layer (UI)
│   ├── components/            # Reusable Compose components
│   ├── navigation/            # Navigation graph
│   ├── screens/
│   │   ├── home/              # Home with Recent, Favorites, All Files tabs
│   │   ├── reader/            # PDF reader with scrubber, bookmarks, annotations
│   │   ├── tools/             # 14 PDF tools
│   │   ├── folders/           # Folder browser
│   │   └── settings/          # Settings & about
│   └── theme/                 # Material 3 theming
│
├── di/                        # Koin dependency injection
└── util/                      # Utilities
```

### Tech Stack

| Component | Technology |
|---|---|
| UI Framework | Jetpack Compose (100%) |
| Language | Kotlin (100%) |
| Architecture | MVVM + Clean Architecture |
| Dependency Injection | Koin |
| Database | Room |
| Async | Kotlin Coroutines + StateFlow |
| Navigation | Jetpack Navigation Compose |
| PDF Rendering | Custom PDF.js WebView + Android PdfRenderer |
| PDF Processing | iText 7 + Bouncy Castle |
| Image Loading | Coil |
| Data Storage | DataStore Preferences |
| OCR | Tesseract4Android |

---

## Requirements

- **Minimum SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK**: API 36 (Android 16)
- **Compile SDK**: API 36
- **Gradle**: 9.4.0
- **AGP**: 9.1.0
- **Kotlin**: 2.3.10
- **Java**: 21

### Permissions

- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_DOCUMENTS` — Access PDF files
- `WRITE_EXTERNAL_STORAGE` — Save processed PDFs (Android 9 and below)
- `CAMERA` — Optional, capture images in Image to PDF tool
- `INTERNET` — Optional, for update checks only

---

## Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/karthikhegde448/Kosh.git
   ```
2. Open the `PdfReaderPro-patched/` folder in Android Studio.
3. Sync the project with Gradle files.
4. Connect a device or emulator (API 24+).
5. Click **Run**.

```bash
# Or build from command line
cd PdfReaderPro-patched
./gradlew assembleDebug
```

---

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/MyFeature`)
3. Commit your changes (`git commit -m 'Add MyFeature'`)
4. Push to the branch (`git push origin feature/MyFeature`)
5. Open a Pull Request

---

## License

```
Copyright (C) 2025-2026 Kosh Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

> Kosh is based on [PDF Reader Pro](https://github.com/ahmmedrejowan/PdfReaderPro) by K M Rejowan Ahmmed, licensed under GPL v3. Any derivative work must also be released under GPL v3.

---

## Acknowledgments

- [PDF Reader Pro](https://github.com/ahmmedrejowan/PdfReaderPro) — Original open-source base by K M Rejowan Ahmmed
- [PDF.js](https://mozilla.github.io/pdf.js/) — Mozilla PDF rendering library
- [iText 7](https://itextpdf.com/) — PDF processing library
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI toolkit
- [Material Design 3](https://m3.material.io/) — Design system
- [Koin](https://insert-koin.io/) — Dependency injection
- [Coil](https://coil-kt.github.io/coil/) — Image loading
- [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) — OCR engine
- [Timber](https://github.com/JakeWharton/timber) — Logging
- [Reorderable](https://github.com/Calvin-LL/Reorderable) — Drag-and-drop reordering

---

## Changelog

### v1.0.0 (2026)
- Initial public release
- 14 PDF tools
- Full PDF reader with bookmarks, search, auto-scroll
- Image to PDF with 14 MS Word-style filters, crop, pen annotation, two-finger zoom
- Text to PDF with notebook mode
- Material 3 UI, dark mode, 100% offline

---

<div align="center">
  <p>Made with ❤️ for Android</p>
  <a href="https://github.com/karthikhegde448/Kosh/issues">Report Bug</a> ·
  <a href="https://github.com/karthikhegde448/Kosh/issues">Request Feature</a> ·
  <a href="https://github.com/karthikhegde448/Kosh/releases">Download</a>
</div>
