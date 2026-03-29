# Launch JSQL desktop (JavaFX). Desktop shortcut points to this file (with permission).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root "frontend-java"
if (-not (Test-Path $frontend)) {
    Write-Error "Could not find frontend-java at: $frontend"
    exit 1
}
Set-Location $frontend
$mvnw = Join-Path $frontend "mvnw.cmd"
if (Test-Path $mvnw) {
    & $mvnw javafx:run
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn javafx:run
} else {
    Write-Error "Neither mvnw.cmd nor Maven (mvn) found. Use the GitHub zip (includes Maven Wrapper) or install Maven."
    exit 1
}
