@echo off
setlocal
cd /d "%~dp0..\backend-python"
echo.
echo ========================================
echo   JSQL - Install Python dependencies
echo ========================================
echo.
echo This creates backend-python\.venv and runs pip install.
echo First time may take several minutes (PyTorch, etc.).
echo.

where py >nul 2>&1
if %errorlevel%==0 (
  echo Using: py -3
  py -3 -m venv .venv
  if errorlevel 1 (
    echo py -3 failed; trying py without version...
    py -m venv .venv
  )
) else (
  where python >nul 2>&1
  if %errorlevel%==0 (
    echo Using: python
    python -m venv .venv
  ) else (
    echo ERROR: Python not found.
    echo Install Python 3.11+ from https://www.python.org/downloads/
    echo Enable "Add python.exe to PATH" during setup, then run this again.
    pause
    exit /b 1
  )
)

if not exist ".venv\Scripts\activate.bat" (
  echo ERROR: Could not create .venv under backend-python
  pause
  exit /b 1
)

call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt
if errorlevel 1 (
  echo.
  echo pip install failed. See messages above.
  pause
  exit /b 1
)

echo.
echo Done. Next: run Start-JSQL.bat or scripts\Windows-2-Start-JSQL.bat
echo.
pause
