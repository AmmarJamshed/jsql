# Store Ollama model files on D: next to JSQL (same drive as the project).
# Run once in PowerShell, then fully QUIT and restart the Ollama app (tray icon).

$dir = "D:\jsql\ollama-models"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# Ollama reads this to decide where blobs live (see https://github.com/ollama/ollama/blob/main/docs/faq.md)
[Environment]::SetEnvironmentVariable("OLLAMA_MODELS", $dir, "User")

Write-Host ""
Write-Host "Set user environment variable:"
Write-Host "  OLLAMA_MODELS = $dir"
Write-Host ""
Write-Host "NEXT STEPS (required):"
Write-Host "  1. Quit Ollama completely (system tray -> Exit, or Task Manager)."
Write-Host "  2. Start Ollama again from the Start menu."
Write-Host "  3. Pull a model (downloads into the folder above):"
Write-Host "       ollama pull llama3.2"
Write-Host "     or keep using an existing model name from: ollama list"
Write-Host ""
Write-Host "JSQL will auto-detect installed model names (e.g. qwen2.5:3b) after you restart the Python API."
Write-Host ""
