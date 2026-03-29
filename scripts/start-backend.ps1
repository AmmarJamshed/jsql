# JSQL backend — uses venv on D: (Python from D:\tools\Py312)
Set-Location D:\jsql\backend-python
if (-not (Test-Path .\.venv\Scripts\python.exe)) {
    & D:\tools\Py312\python.exe -m venv .venv
    & .\.venv\Scripts\pip.exe install -r requirements.txt
}
& .\.venv\Scripts\Activate.ps1
python -m uvicorn main:app --host 127.0.0.1 --port 8765
