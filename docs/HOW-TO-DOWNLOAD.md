# How to download and run JSQL (simple steps)

JSQL is a **desktop app** for your own computer. This guide is written so you do **not** need to be a developer.

---

## Easiest download (one zip, includes Ollama installer)

1. Open this link in your browser (**always points at the newest release**):

   **[https://github.com/AmmarJamshed/jsql/releases/latest/download/JSQL-Windows-with-Ollama.zip](https://github.com/AmmarJamshed/jsql/releases/latest/download/JSQL-Windows-with-Ollama.zip)**

2. If the download does not start, go to **[Releases](https://github.com/AmmarJamshed/jsql/releases)** and click **`JSQL-Windows-with-Ollama.zip`** under **Assets**.

3. **Extract** the zip anywhere (for example `D:\jsql` or `Desktop\jsql`). You should see a **`jsql`** folder containing **`bundled`**, **`scripts`**, **`frontend-java`**, **`backend-python`**, etc.

Inside **`jsql\bundled`** you will find **`OllamaSetup.exe`** — that is the **official** Ollama Windows installer (MIT license, from the Ollama project). You do **not** need to visit ollama.com first.

---

## What you still need on your PC

| You need | Why |
|----------|-----|
| **Windows 10 or 11** (64-bit) | The bundled installer and scripts target Windows. |
| **Internet (first time)** | Python packages and Maven may download on first run. |
| **Java 17+ (JDK)** | Not inside the zip — install from [Adoptium](https://adoptium.net/) or similar. |
| **Python 3.11 or 3.12** | Not inside the zip — install from [python.org](https://www.python.org/downloads/) and enable **Add to PATH**. |

---

## Step-by-step after extracting

### Step 1 — Install Ollama (from the zip, no extra download)

1. Open **`jsql\scripts`**.
2. Double-click **`Install-Bundled-Ollama.bat`**.  
   It starts **`OllamaSetup.exe`** from **`jsql\bundled`**.  
3. Finish the installer, then open **Ollama** from the Start menu so it runs in the background.
4. Open **Command Prompt** or **PowerShell** once and run: **`ollama pull llama3.2`** (downloads the AI model; needs Internet).

*(SQL and CSV features work without Ollama; AI features need Ollama running.)*

### Step 2 — Install Python packages for JSQL (one time)

1. Double-click **`scripts\Windows-1-Install-Python-Deps.bat`**.  
2. Wait until it finishes (can take several minutes).

### Step 3 — Start JSQL

1. Double-click **`Start-JSQL.bat`** in the **`jsql`** folder  
   **or** **`scripts\Windows-2-Start-JSQL.bat`**.

The first launch may take extra time while Maven downloads tools. Later starts are faster.

### Optional — Desktop icon

The first time the app opens, you may be asked to add a **Desktop** shortcut. Choose **Yes** if you want **`JSQL.lnk`**.

---

## If you used `git clone` instead of the zip

The repository on GitHub does **not** store **`OllamaSetup.exe`** (too large). Either:

- Download **`JSQL-Windows-with-Ollama.zip`** from **Releases** and copy **`bundled\OllamaSetup.exe`** into your clone’s **`bundled`** folder, **or**
- Install Ollama from [https://ollama.com/download](https://ollama.com/download).

---

## What is not inside the zip

| Item | Why |
|------|-----|
| **JDK** | Install separately (see above). |
| **Python venv** | Created on your PC by **`Windows-1-Install-Python-Deps.bat`**. |
| **Ollama AI models** | Pulled after install with **`ollama pull ...`** (large downloads). |
| **One single combined “JSQL.exe”** | This release is a **script + source** layout; a full native installer can be added later. |

---

## If something fails

- **`Install-Bundled-Ollama.bat` says Ollama is missing** — You probably cloned Git without the Release zip; use the zip link at the top or install from ollama.com.
- **Backend / API errors** — Run **`Windows-1-Install-Python-Deps.bat`** again and check that **`backend-python\.venv`** exists.
- **AI errors** — Ensure Ollama is running (system tray) and you ran **`ollama pull`** for at least one model.
- **More detail** — See **`README.md`**.

---

## Licenses

JSQL is **MIT** — see **`LICENSE`**. The bundled **`OllamaSetup.exe`** belongs to the **Ollama** project; see **`bundled\THIRD-PARTY-Ollama.txt`**.
