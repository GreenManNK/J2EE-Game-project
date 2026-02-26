#!/usr/bin/env bash
set -euo pipefail

PROFILE="prod"
PORT="8080"
SKIP_DOCTOR=0
NO_H2_FALLBACK=0
FORCE_BOOTSTRAP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="${2:-}"
      shift 2
      ;;
    --port)
      PORT="${2:-}"
      shift 2
      ;;
    --skip-doctor)
      SKIP_DOCTOR=1
      shift
      ;;
    --force-bootstrap)
      FORCE_BOOTSTRAP=1
      shift
      ;;
    --no-h2-fallback)
      NO_H2_FALLBACK=1
      shift
      ;;
    -h|--help)
      cat <<'EOF'
Usage: bash ./scripts/dev-run-local.sh [--profile prod] [--port 8080] [--skip-doctor] [--force-bootstrap] [--no-h2-fallback]
EOF
      exit 0
      ;;
    *)
      echo "[ERR] Unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ "$SKIP_DOCTOR" -eq 0 ]]; then
  if [[ "$FORCE_BOOTSTRAP" -eq 1 ]]; then
    bash "$SCRIPT_DIR/dev-env-bootstrap.sh" --mode local --db auto --force
  else
    bash "$SCRIPT_DIR/dev-env-bootstrap.sh" --mode local --db auto
  fi
fi

mkdir -p "$REPO_ROOT/.data"
export APP_EMAIL_MODE="${APP_EMAIL_MODE:-log}"
if [[ "$NO_H2_FALLBACK" -eq 0 ]]; then
  export APP_DATASOURCE_ALLOW_H2_FALLBACK="${APP_DATASOURCE_ALLOW_H2_FALLBACK:-true}"
  export APP_DATASOURCE_H2_FILE="${APP_DATASOURCE_H2_FILE:-.data/game-local}"
fi

cd "$REPO_ROOT"
echo "[INFO] Starting Game Hub locally at http://127.0.0.1:${PORT}/Game"
echo "[INFO] Profile=${PROFILE} | H2Fallback=$([[ "$NO_H2_FALLBACK" -eq 0 ]] && echo true || echo false)"
exec mvn "-Dspring-boot.run.profiles=${PROFILE}" "-Dspring-boot.run.arguments=--server.port=${PORT}" spring-boot:run
