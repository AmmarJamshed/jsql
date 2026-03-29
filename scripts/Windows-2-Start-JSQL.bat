@echo off
setlocal
cd /d "%~dp0..\frontend-java"

if not exist "mvnw.cmd" (
  echo ERROR: mvnw.cmd not found in frontend-java.
  echo Use the full zip from Releases or clone the repository.
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java ^(JDK 17+^) not on PATH.
  echo Install a JDK from https://adoptium.net/ and open a new terminal.
  pause
  exit /b 1
)

echo.
echo Starting JSQL...
echo First run may download Maven and libraries ^(needs Internet^).
echo.
call mvnw.cmd javafx:run
if errorlevel 1 (
  echo.
  echo Build or run failed. Install Python deps first: scripts\Windows-1-Install-Python-Deps.bat
  pause
)
