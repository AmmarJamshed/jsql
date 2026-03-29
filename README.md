# JSQL (JamshedSQL)

Local-first **SQL + AI data analysis** desktop app: **JavaFX** frontend + **FastAPI** backend on **DuckDB**, **FAISS**, and **Ollama**.

## Download for end users (public)

**One-click zip (JSQL + official Ollama installer for Windows):**

[https://github.com/AmmarJamshed/jsql/releases/download/continuous/JSQL-Windows-with-Ollama.zip](https://github.com/AmmarJamshed/jsql/releases/download/continuous/JSQL-Windows-with-Ollama.zip)

This uses the rolling **`continuous`** release that **GitHub Actions updates on every push to `main`**, so the link works even before you create a versioned release. If you see **Not Found**, open the **[Actions](https://github.com/AmmarJamshed/jsql/actions)** tab and wait until **Release zip** finishes (usually a few minutes), then try again.

The zip contains the full app **and** **`bundled\OllamaSetup.exe`** (official installer from [ollama/ollama releases](https://github.com/ollama/ollama/releases) at build time — MIT license, see **`bundled\THIRD-PARTY-Ollama.txt`**). If you publish a **stable** (non‑prerelease) version tag, you can also use `releases/latest/download/...` for that asset name.

- **Plain-language steps**: [docs/HOW-TO-DOWNLOAD.md](docs/HOW-TO-DOWNLOAD.md) — extract the zip → run **`scripts\Install-Bundled-Ollama.bat`** once if Ollama is not installed yet → **`scripts\Windows-1-Install-Python-Deps.bat`** once → **`Start-JSQL.bat`**.
- **Repo “About” description** (GitHub website): paste text from [docs/GITHUB-ABOUT-DESCRIPTION.txt](docs/GITHUB-ABOUT-DESCRIPTION.txt).
- Maintainers: [docs/GITHUB-PUBLISH.md](docs/GITHUB-PUBLISH.md). Workflow: [`.github/workflows/release.yml`](.github/workflows/release.yml) produces **`JSQL-Windows-with-Ollama.zip`** (stable filename for the link above).

---

All project files may live under **`D:\jsql\`** on the original dev machine (or any folder after you extract the zip). To keep Hugging Face / PyTorch caches on D: as well, set for example:

`$env:TRANSFORMERS_CACHE='D:\jsql\.cache\transformers'` and `$env:HF_HOME='D:\jsql\.cache\huggingface'` before `pip install` / first run (PowerShell).

## Prerequisites

On this machine, **Python** and **Maven** were installed under **`D:\tools\`** and prepended to your **user** `Path`. Open a **new** terminal after setup so `python` and `mvn` resolve.

- **JDK 17+** (or newer) on `PATH` — e.g. `D:\Java\jdk-21` for Maven/JavaFX
- **Python 3.12** at `D:\tools\Py312` (venv for the backend: `D:\jsql\backend-python\.venv`)
- **Maven 3.9.14** at `D:\tools\apache-maven-3.9.14`
- **Ollama — must be installed and running on this PC** (local only; JSQL does not call a cloud LLM for NL→SQL / insights):
  1. **Release zip users:** run **`scripts\Install-Bundled-Ollama.bat`** (runs **`bundled\OllamaSetup.exe`**) or double-click that installer yourself. **Git clone users:** install from [ollama.com/download](https://ollama.com/download) or copy **`OllamaSetup.exe`** from a Release zip’s **`bundled`** folder.
  2. Start the **Ollama app** (it listens on **`http://127.0.0.1:11434`**). Keep it running while you use AI features.
  3. Pull at least one model, e.g. **`ollama pull llama3.2`** (or pick a name from **`ollama list`** and set **`OLLAMA_MODEL`** if needed).
  - SQL, CSV, and DuckDB work **without** Ollama; **AI Assistant**, **Insights Gym**, and **NL→SQL** need local Ollama.
- **RAM**: semantic search loads **SentenceTransformers** `all-MiniLM-L6-v2` (first run downloads weights). With **under 8 GB** RAM, use smaller workloads; the API reports suggested limits via `/health`.

## Project layout

```
D:\jsql\
├── frontend-java\          # JavaFX client
├── backend-python\         # FastAPI + DuckDB + FAISS
└── README.md
```

## 1. Start the backend

```powershell
cd D:\jsql\backend-python
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn main:app --host 127.0.0.1 --port 8765
```

Or double-check the app is up:

```http
GET http://127.0.0.1:8765/health
```

**Environment (optional)**

| Variable        | Meaning                          | Default              |
|----------------|-----------------------------------|----------------------|
| `OLLAMA_HOST`  | **Local** Ollama base URL (same machine) | `http://localhost:11434` |
| `OLLAMA_MODEL` | Model for `/api/generate`         | `llama3.2`           |
| `OLLAMA_MODEL_FALLBACKS` | Extra names to try if 404 (comma-separated) | *(built-in list)* |

## 2. Start the frontend

```powershell
cd D:\jsql\frontend-java
.\mvnw.cmd javafx:run
```

If you have Maven installed globally, `mvn javafx:run` also works. **`mvnw.cmd`** is included so downloaders do not need a separate Maven install.

### No separate backend console

When you launch **JSQL**, it tries to start **Uvicorn in the background** if nothing is listening on **:8765**:

- On **Windows** it prefers **`.venv\Scripts\pythonw.exe`** (no black console window).
- Server stdout/stderr go to a temp file: **`%TEMP%\jsql-backend-*.log`** (only if you need raw Uvicorn output).
- **What users watch in the app:** open the **Activity** tab — it polls **`/activity_log`** and shows requests, SQL, AI, PDF conversion, etc.

**Turn off auto-start** (you run the API yourself in a terminal):

`-Djsql.skipBackend=true`

**If the backend folder is not detected**, set either:

- JVM property **`jsql.backend.home`** = full path to `backend-python` (folder that contains `main.py`), or  
- Environment variable **`JSQL_BACKEND_HOME`**

**Maven’s own window** during `mvn javafx:run` is only for the build; that is separate from the API. For a shipped **`.exe`**, use **`jpackage`** (or similar) with **`--win-console false`** so double-clicking the app does not open a host console — see [Packaging JavaFX](https://openjfx.io/openjfx-docs/#modular). Ship **`backend-python`** (with `.venv` and `pip install -r requirements.txt` done) next to the install, or set **`JSQL_BACKEND_HOME`** in the installer.

**Custom API URL** — property **`jsql.api`** (default `http://127.0.0.1:8765`).

### Desktop shortcut (first open, Windows)

The first time you open JSQL on **Windows**, you may be asked: **add a Desktop icon?** Nothing is created unless you click **Yes** (explicit permission). The shortcut runs **`scripts/Open-JSQL-Desktop.ps1`** (Maven + JavaFX). To see the prompt again when developing, clear the preference or call `FirstLaunchShortcut.resetPromptFlagForDevelopment()` from a debugger.

A Cursor rule for this behavior lives in **`.cursor/rules/jsql-first-run-desktop.mdc`**.

**Desktop launcher** — **`scripts\Open-JSQL-Desktop.ps1`** prefers **`mvnw.cmd`** in `frontend-java`, then falls back to **`mvn`** on `PATH`.

## REST API (MVP)

| Method | Path               | Description |
|--------|--------------------|-------------|
| `GET`  | `/health`          | Status, RAM/CPU hints, Ollama reachability |
| `GET`  | `/activity_log`    | `{ "lines": [...], "count": N }` for the desktop **Activity** tab |
| `POST` | `/upload_csv`      | Multipart `file` → DuckDB table + preview |
| `POST` | `/query_sql`       | JSON `{ "sql": "..." }` → rows |
| `POST` | `/nl_to_sql`       | JSON `{ "prompt": "..." }` → generated SQL + rows |
| `POST` | `/upload_text`     | Multipart `file` (`.txt`, `.md`, `.pdf`) → chunk, embed, FAISS |
| `POST` | `/convert_pdf_csv` | Multipart `file` (`.pdf`) → **CSV download** (tables via pdfplumber, else text lines) |
| `POST` | `/semantic_search` | JSON `{ "query": "...", "top_k": 8 }` |

**Bonus (used by the UI)**

| Method | Path                      | Description |
|--------|---------------------------|-------------|
| `POST` | `/ai/explain_dataset`     | Ollama summary of loaded schema |
| `POST` | `/ai/summarize_results`   | JSON `{ "columns": [], "rows": [] }` |
| `POST` | `/ai/mode`                | JSON `{ "enabled": true/false }` |

Vector index files are stored under `D:\jsql\backend-python\data\vectors\`.

## Ollama model files on **D:** (next to JSQL)

Ollama normally stores models under your user profile. To keep blobs on **D:** in a folder beside the project:

1. Run **`D:\jsql\scripts\setup-ollama-models-on-d.ps1`** once (sets user env var **`OLLAMA_MODELS=D:\jsql\ollama-models`**).
2. **Fully quit** Ollama (tray → Exit) and start it again so it picks up the variable.
3. Run **`ollama pull llama3.2`** (or any model); files download into **`D:\jsql\ollama-models`**.

JSQL does **not** read that folder directly — **Ollama** does. The backend asks Ollama which models exist and tries **exact names** from `ollama list` (e.g. **`qwen2.5:3b`**) so tags like `:3b` match.

## Troubleshooting

- **Backend offline in the app** — Start Uvicorn on port **8765** and check the firewall.
- **Ollama 404 / “model not found”** — Use the **exact** name from `ollama list` (including tags, e.g. `qwen2.5:3b`). The API now tries your **installed** names automatically after `OLLAMA_MODEL`. You can still set `OLLAMA_MODEL=qwen2.5:3b` when starting the backend.
- **Ollama offline / AI errors** — Ollama must be **installed on this computer** and **running** (system tray / Start menu → Ollama). JSQL talks only to **`localhost:11434`** — there is no remote API key for this path.
- **Heavy first import** — `sentence-transformers` / PyTorch may take time and disk on first `pip install` / first embedding run.

## License

See [LICENSE](LICENSE) (MIT).
