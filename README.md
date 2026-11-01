# AssistiveManagementTool

**Excel-Style Data Analytics & AI Dashboard** — a JavaFX desktop application that turns admissions spreadsheets into a queryable SQLite database and puts a fully **offline AI assistant** on top of them.

Built for university admissions operations: import any Excel workbook, analyse applicant data by country/region, clean messy columns, and auto-generate weekly PowerPoint reports.

## Features

- **Excel → SQLite pipeline** — multi-sheet import (Apache POI), automatic table creation, schema tracking
- **Offline AI chat agent** — Qwen2.5-1.5B-Instruct running fully in-process via llama.cpp (no internet, no API keys). Ask questions in plain English; the agent writes and runs the SQL for you
- **Human-in-the-loop safety** — SELECT queries run freely; any UPDATE/INSERT/DELETE is detected by the application (never trusted to the model) and gated behind an explicit confirmation with a preview of the affected rows
- **AI data cleaning** — batch-clean columns (names, countries, phone numbers, NRIC, addresses, emails) with change logs and stop control
- **Country / Region analytics** — analysers, drill-downs, and bulk region assignment tools
- **Chart dashboard + PowerPoint export** — pivot-driven charts exported straight into .pptx reports

## Download

[![Download](https://img.shields.io/badge/%E2%AC%87%20Download-Setup_v1.0.0-2ea44f?style=for-the-badge)](https://github.com/ttc-099/AssistiveManagementTool/releases/download/v1.0.0/AssistiveManagementTool-Setup-v1.0.0.zip)

Click the button — that's the whole install. Then:

1. **Unzip** the folder (Windows: right-click → Extract All)
2. **Windows:** double-click `START-WINDOWS.bat` · **macOS/Linux:** run `bash start-mac-linux.sh`
3. The first launch automatically downloads the AI model (**~1 GB, one time only**) with a progress bar, then starts the app

Only requirement: **Java 21+** ([Adoptium Temurin](https://adoptium.net), free). The app itself is ~60 MB; the model streams from Hugging Face's CDN so you never handle it manually.

### Offline / manual install

Prefer to download everything yourself, or installing on a machine without internet at setup time? Grab both files from the [latest release](https://github.com/ttc-099/AssistiveManagementTool/releases/latest):

- `AssistiveManagementTool-v1.0.0.jar` (the app, ~65 MB, self-contained for Windows / macOS / Linux)
- `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` (the AI model, ~1.0 GB)

Put the model in an `app_models/` folder next to the jar:

```
your-folder/
├── AssistiveManagementTool-v1.0.0.jar
└── app_models/
    └── Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
```

Then run:

```
java -jar AssistiveManagementTool-v1.0.0.jar
```

The model loads in the background on first launch — the AI tab shows **Online** when it's ready.

## Build from source

```
mvn package
java -cp target/dynamic-pptx-exporter-1.0-SNAPSHOT.jar com.example.app.MainApp
```

Requires JDK 21. The build produces a single shaded jar bundling JavaFX natives for Windows, macOS and Linux.

## Verifying the AI pipeline

A headless smoke test loads the real model, imports a synthetic workbook, runs a natural-language query, checks the update-confirmation gate, and cleans a messy address column end-to-end:

```
mvn test-compile
java -cp target/dynamic-pptx-exporter-1.0-SNAPSHOT.jar:target/test-classes com.example.app.LlmSmokeTest
```

## Tech stack

Java 21 · JavaFX 21 · Apache POI · SQLite · llama.cpp (de.kherud/llama) · LangChain4j · Jackson
