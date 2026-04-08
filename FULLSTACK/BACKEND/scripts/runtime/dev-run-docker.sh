#!/usr/bin/env bash
set -euo pipefail

FOREGROUND=0
if [[ "${1:-}" == "--foreground" ]]; then
  FOREGROUND=1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

compose_exec() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose "$@"
    return
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
    return
  fi
  echo "[ERR] Khong tim thay Docker Compose (docker compose hoac docker-compose)." >&2
  exit 1
}

cd "$REPO_ROOT"

if ! compose_exec pull; then
  echo "[WARN] Khong pull duoc image Docker moi tu registry. Se thu chay bang image local neu da ton tai." >&2
fi

if [[ "$FOREGROUND" -eq 1 ]]; then
  compose_exec up
else
  compose_exec up -d
  echo "[OK] Docker app da chay: http://127.0.0.1:8080/Game"
  echo "[INFO] Dung app: ./scripts/manual-start.cmd stop"
fi
