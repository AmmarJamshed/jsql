# How to download and run JSQL (simple steps)

JSQL is a **desktop app** for your own computer. This guide is written so you do **not** need to be a developer.

---

## What you need on your Windows PC

1. **Windows 10 or 11** (64-bit).
2. **Internet** — the first time you run the app, it may download build tools (Maven) and Python libraries.
3. **Java 17 or newer** — often called a **JDK**. If you do not have it:
   - Install **Temurin 17+** or another JDK from [Adoptium](https://adoptium.net/) or your vendor of choice.
   - During or after install, make sure `java` works in a new Command Prompt (optional: set `JAVA_HOME`).

4. **Python 3.11 or 3.12** — from [python.org](https://www.python.org/downloads/).  
   During setup, turn on **“Add python.exe to PATH”** (or use the **“py”** launcher that the installer adds).

5. **Ollama (only if you want AI features)** — SQL and CSV still work without it.  
   - We **cannot** put Ollama inside our zip (license and size). You install it yourself:
   - Download **Ollama for Windows** from **[https://ollama.com/download](https://ollama.com/download)**.
   - Open the Ollama app so it runs in the background.
   - In a terminal run: `ollama pull llama3.2` (or another model you prefer).

---

## Step A — Get the project as a zip

**Option 1 — GitHub Releases (recommended)**  
1. Open the JSQL repository on GitHub.  
2. Click **Releases** (usually on the right side of the repo page).  
3. Download the file named like **`jsql-v0.1.0.zip`** (the exact version number may differ).  
4. **Extract** the zip to a folder you keep, for example `C:\Users\YourName\jsql` or `D:\jsql`.  
   After extraction you should see folders such as **`frontend-java`**, **`backend-python`**, and **`scripts`**.

**Option 2 — Clone with Git**  
If you use Git: clone the repository, then open the folder. You get the same layout as the zip.

---

## Step B — Install Python dependencies (one time per PC)

1. Open the extracted **`jsql`** folder.
2. Double-click **`scripts\Windows-1-Install-Python-Deps.bat`**.  
3. Wait until it finishes. It creates **`backend-python\.venv`** and installs packages listed in **`requirements.txt`**.  
4. If it says Python was not found, install Python from python.org and run the batch file again.

This step downloads **PyTorch**, **sentence-transformers**, and other libraries — it can take several minutes and needs disk space.

---

## Step C — Start the app

1. Double-click **`scripts\Windows-2-Start-JSQL.bat`**  
   **or** double-click **`Start-JSQL.bat`** in the main **`jsql`** folder.

2. The **first** run may take a while: Maven may download itself and Java libraries. Later starts are faster.

3. The **JSQL window** should open. The app tries to start the **backend** in the background if nothing is already listening on port **8765**.

---

## Desktop shortcut (optional)

The first time the app opens on Windows, it may ask: **add a Desktop icon?**  
If you click **Yes**, it creates **`JSQL.lnk`** on your Desktop. That shortcut runs the same launcher script as the batch files.

You can always create your own shortcut: point it to **`scripts\Open-JSQL-Desktop.ps1`** (run with PowerShell) or to **`Start-JSQL.bat`**.

---

## What is **not** inside our zip

| Item | Why |
|------|-----|
| **Ollama** | Official installer only — [ollama.com/download](https://ollama.com/download). |
| **JDK** | Too large; install separately. |
| **Python venv** | Created on **your** machine by the install batch file (correct for your PC). |
| **A single “one-click .exe” installer** | This release is a **portable source + scripts** layout. A full installer can be added later (e.g. `jpackage`). |

---

## If something fails

- Read **`README.md`** in the same folder for developers’ details, environment variables, and API notes.
- **Backend problems** — ensure **`Windows-1-Install-Python-Deps.bat`** completed without errors and that **`backend-python\.venv`** exists.
- **AI errors** — start **Ollama** and pull a model (`ollama pull llama3.2`).
- **“Maven not found”** — you should **not** need global Maven if **`frontend-java\mvnw.cmd`** is present; use the provided batch files.

---

## Sharing and license

The project is open source under the **MIT License** — see **`LICENSE`**. You may download, use, and share it according to that file.
