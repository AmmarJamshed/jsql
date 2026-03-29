# Creates the GitHub repo (if needed) and pushes D:\jsql to github.com/<Owner>/<Repo>.
# Usage:
#   $env:GITHUB_PAT = "github_pat_...."   # or classic ghp_....
#   .\scripts\Publish-To-GitHub.ps1 -Owner "your-github-username" -Repo "jsql"
#
# Create a PAT: GitHub -> Settings -> Developer settings -> Personal access tokens.
# Scopes: "repo" (full control of private repositories) for a classic token, or for fine-grained: Contents read/write on this repo.

param(
    [Parameter(Mandatory = $true)]
    [string] $Owner,

    [Parameter(Mandatory = $false)]
    [string] $Repo = "jsql",

    [Parameter(Mandatory = $false)]
    [string] $Pat = $env:GITHUB_PAT
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Pat)) {
    Write-Error "Set environment variable GITHUB_PAT to a personal access token, or pass -Pat."
}

$headers = @{
    Authorization  = "Bearer $Pat"
    Accept         = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}

Write-Host "Checking if repo ${Owner}/${Repo} exists..."
try {
    Invoke-RestMethod -Uri "https://api.github.com/repos/$Owner/$Repo" -Headers $headers -Method Get | Out-Null
    Write-Host "Repository already exists."
}
catch {
    if ($_.Exception.Response.StatusCode -ne 404) { throw }
    Write-Host "Creating public repository ${Owner}/${Repo}..."
    $body = @{
        name        = $Repo
        description = "JSQL (JamshedSQL) — local-first SQL + AI desktop app"
        private     = $false
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "https://api.github.com/user/repos" -Headers $headers -Method Post -Body $body -ContentType "application/json" | Out-Null
    Write-Host "Created."
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$publicUrl = "https://github.com/$Owner/$Repo.git"

git remote remove origin 2>$null
git remote add origin $publicUrl
# One-shot push with token (avoids storing PAT in .git/config long-term)
git -c http.extraHeader="AUTHORIZATION: Bearer $Pat" push -u origin main
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push failed. Check token scopes (repo) and that branch is main."
}

Write-Host ""
Write-Host "Done. Open: https://github.com/$Owner/$Repo"
Write-Host "Next: GitHub -> Releases -> create a tag (e.g. v0.1.0) to build the zip via Actions."
