# Publishing JSQL to your GitHub account

Use this checklist once on your machine. Replace **`YOUR_USER`** and **`YOUR_REPO`** with your GitHub username and repository name.

## 1. Create an empty repository on GitHub

1. Log in at [https://github.com](https://github.com).  
2. **New repository** → name it (e.g. `jsql`) → **Public** → **do not** add README (this project already has one).  
3. Create the repo and copy the **HTTPS** URL, e.g. `https://github.com/YOUR_USER/jsql.git`.

## 2. Push from your computer

If you **already** ran `git init` and have a commit (this repo does), you only need a remote and a push.

### Option A — automated script (token in env)

1. Create a **Personal Access Token**: GitHub → **Settings** → **Developer settings** → **Personal access tokens**. Give it **`repo`** scope (classic) or fine-grained access to **Contents: Read and write** on the new repository.
2. In PowerShell:

```powershell
cd D:\jsql
$env:GITHUB_PAT = "paste_your_token_here"
.\scripts\Publish-To-GitHub.ps1 -Owner "YOUR_GITHUB_USERNAME" -Repo "jsql"
```

The script creates **`YOUR_GITHUB_USERNAME/jsql`** if it does not exist, then pushes **`main`**.

### Option B — manual

```powershell
cd D:\jsql
git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
git push -u origin main
```

If `git` asks you to sign in, use a **Personal Access Token** as the password (GitHub no longer accepts account passwords for Git HTTPS), or sign in with **GitHub CLI** (`gh auth login`).

## 3. Publish a Release (zip with JSQL + Ollama installer)

1. On GitHub: **Releases** → **Create a new release**.  
2. **Tag**: e.g. `v0.1.1`.  
3. **Title**: e.g. `JSQL v0.1.1 (Windows bundle)`.  
4. Paste a short description (copy bullets from **`docs/HOW-TO-DOWNLOAD.md`**).  
5. **Publish release**.

If **[`.github/workflows/release.yml`](../.github/workflows/release.yml)** is enabled, Actions will build **`JSQL-Windows-with-Ollama.zip`** (app + official **`OllamaSetup.exe`** in **`jsql/bundled/`**) and attach it to the release.

**Stable download link for README** (replace owner/repo if needed — works after first successful run on `main`):

`https://github.com/YOUR_USER/YOUR_REPO/releases/download/continuous/JSQL-Windows-with-Ollama.zip`

(`releases/latest/download/...` only works after you publish a **non‑prerelease** versioned release; the **`continuous`** prerelease is updated automatically on every push to `main`.)

## 4. Point readers to the download

In the repo **README**, use the **latest/download** link above and link **`docs/HOW-TO-DOWNLOAD.md`**.

Set the GitHub **About** description using **[`GITHUB-ABOUT-DESCRIPTION.txt`](GITHUB-ABOUT-DESCRIPTION.txt)** (copy from that file into the repo **About** box on GitHub).
