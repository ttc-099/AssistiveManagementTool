@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
  echo Java 21+ not found. Install it free from https://adoptium.net then run this file again.
  pause
  exit /b 1
)

set "MODEL=app_models\Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
if not exist "%MODEL%" (
  echo.
  echo Downloading the AI model ~1 GB. This happens ONE TIME only.
  curl -L --progress-bar -o "%MODEL%.part" "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
  if errorlevel 1 (
    echo Model download failed. Check your internet connection and run this file again.
    pause
    exit /b 1
  )
  move /y "%MODEL%.part" "%MODEL%" >nul
)

echo Starting AssistiveManagementTool...
java -jar AssistiveManagementTool-v1.0.0.jar
pause