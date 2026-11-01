#!/usr/bin/env bash
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "Java 21+ not found. Install it free from https://adoptium.net then run this again."
  exit 1
fi
MODEL="app_models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
if [ ! -f "$MODEL" ]; then
  echo "Downloading the AI model (~1 GB, one time only)..."
  mkdir -p app_models
  curl -L --progress-bar -o "$MODEL.part" "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf" || { echo "Download failed"; exit 1; }
  mv "$MODEL.part" "$MODEL"
fi
exec java -jar AssistiveManagementTool-v1.0.0.jar