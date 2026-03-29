# Publishing JSQL to your GitHub account

Use this checklist once on your machine. Replace **`YOUR_USER`** and **`YOUR_REPO`** with your GitHub username and repository name.

## 1. Create an empty repository on GitHub

1. Log in at [https://github.com](https://github.com).  
2. **New repository** → name it (e.g. `jsql`) → **Public** → **do not** add README (this project already has one).  
3. Create the repo and copy the **HTTPS** URL, e.g. `https://github.com/YOUR_USER/jsql.git`.

## 2. Commit the project from your computer

Open **PowerShell** in the **`jsql`** folder (the one that contains `frontend-java`, `backend-python`, `README.md`).

```powershell
cd D:\jsql
git init
git add .
git status
git commit -m "Initial commit: JSQL desktop app (JavaFX + FastAPI)"
git branch -M main
git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
git push -u origin main
```

If `git` asks you to sign in, use a **Personal Access Token** (GitHub → Settings → Developer settings) when prompted for a password, or use **GitHub CLI** (`gh auth login`).

## 3. Attach a zip to a Release (for easy public download)

1. On GitHub: **Releases** → **Create a new release**.  
2. **Tag**: e.g. `v0.1.0`.  
3. **Title**: e.g. `JSQL v0.1.0`.  
4. Paste a short description (you can copy bullets from **`docs/HOW-TO-DOWNLOAD.md`**).  
5. **Publish release**.

If **[`.github/workflows/release.yml`](../.github/workflows/release.yml)** is enabled, GitHub Actions will build a **`jsql-<tag>.zip`** from the tagged commit and upload it to that release automatically. You can also upload a zip you built locally:

```powershell
cd D:\jsql
git archive --format=zip --prefix=jsql/ -o jsql-v0.1.0.zip HEAD
```

Then attach **`jsql-v0.1.0.zip`** manually to the release.

## 4. Point readers to the download

In the repo **README**, link to:

- **Latest release**: `https://github.com/YOUR_USER/YOUR_REPO/releases/latest`  
- **How to install**: `docs/HOW-TO-DOWNLOAD.md` (or the same text in the release notes).

That way anyone can download the zip without using Git.
