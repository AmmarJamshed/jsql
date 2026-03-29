@echo off
setlocal
cd /d "%~dp0..\bundled"
if not exist "OllamaSetup.exe" (
  echo.
  echo OllamaSetup.exe was not found in the bundled folder.
  echo Download the full zip from this project^'s GitHub Releases page ^(see README^).
  echo Or install Ollama from https://ollama.com/download
  echo.
  pause
  exit /b 1
)
echo Starting the official Ollama installer...
start "" "%CD%\OllamaSetup.exe"
