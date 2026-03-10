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
Usage: bash ./scripts/runtime/dev-run-local.sh [--profile prod] [--port 8080] [--skip-doctor] [--force-bootstrap] [--no-h2-fallback]
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
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

resolve_run_tool() {
  if [[ -f "$REPO_ROOT/mvnw" ]]; then
    echo "maven:$REPO_ROOT/mvnw"
    return 0
  fi
  if command -v mvn >/dev/null 2>&1; then
    echo "maven:mvn"
    return 0
  fi
  if [[ -f "$REPO_ROOT/gradlew" ]]; then
    echo "gradle:$REPO_ROOT/gradlew"
    return 0
  fi
  if command -v gradle >/dev/null 2>&1; then
    echo "gradle:gradle"
    return 0
  fi
  return 1
}

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
tool_entry="$(resolve_run_tool || true)"
if [[ -z "$tool_entry" ]]; then
  echo "[ERR] Khong tim thay Maven/Gradle. Hay dung mvnw/mvn hoac gradlew/gradle." >&2
  exit 2
fi
tool_kind="${tool_entry%%:*}"
tool_cmd="${tool_entry#*:}"
tool_exec=("$tool_cmd")
if [[ "$tool_cmd" == */mvnw || "$tool_cmd" == */gradlew ]]; then
  tool_exec=(bash "$tool_cmd")
fi

echo "[INFO] Starting Game Hub locally at http://127.0.0.1:${PORT}/Game"
echo "[INFO] Profile=${PROFILE} | H2Fallback=$([[ "$NO_H2_FALLBACK" -eq 0 ]] && echo true || echo false) | BuildTool=${tool_kind}"
if [[ "$tool_kind" == "maven" ]]; then
  exec "${tool_exec[@]}" "-Dspring-boot.run.profiles=${PROFILE}" "-Dspring-boot.run.arguments=--server.port=${PORT}" spring-boot:run
fi
exec "${tool_exec[@]}" --no-daemon bootRun "-PspringProfile=${PROFILE}" "-PserverPort=${PORT}"
