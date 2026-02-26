#!/usr/bin/env bash
set -euo pipefail

MODE="local"
DB_KIND="auto"
FORCE=0
SCHEMA_VERSION="1"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --db)
      DB_KIND="${2:-}"
      shift 2
      ;;
    --force)
      FORCE=1
      shift
      ;;
    -h|--help)
      cat <<'EOF'
Usage: bash ./scripts/dev-env-bootstrap.sh [--mode local|public] [--db auto|h2|mysql|postgres] [--force]
EOF
      exit 0
      ;;
    *)
      echo "[ERR] Unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

case "$MODE" in local|public) ;; *) echo "[ERR] Invalid --mode: $MODE" >&2; exit 2;; esac
case "$DB_KIND" in auto|h2|mysql|postgres) ;; *) echo "[ERR] Invalid --db: $DB_KIND" >&2; exit 2;; esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$REPO_ROOT/.data/dev-env"
STATE_FILE="$STATE_DIR/bootstrap-${MODE}-${DB_KIND}.state"

ok() { printf '[OK] %s\n' "$1"; }
info() { printf '[INFO] %s\n' "$1"; }

state_valid() {
  [[ -f "$STATE_FILE" ]] || return 1
  tr -d '\r' < "$STATE_FILE" | grep -q "^schema_version=${SCHEMA_VERSION}$"
}

if [[ "$FORCE" -eq 0 ]] && state_valid; then
  ok "Bootstrap da hoan tat truoc do. Bo qua buoc chuan doan/cai dat."
  info "Neu muon chay lai, dung --force."
  exit 0
fi

mkdir -p "$STATE_DIR"
info "Chay bootstrap moi truong (lan dau hoac bi ep chay lai)..."
bash "$SCRIPT_DIR/dev-env-setup.sh" --mode "$MODE" --db "$DB_KIND"

cat > "$STATE_FILE" <<EOF
schema_version=${SCHEMA_VERSION}
mode=${MODE}
db=${DB_KIND}
completed_at_utc=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || true)
host=$(hostname 2>/dev/null || true)
EOF

ok "Bootstrap moi truong hoan tat. Cac lan chay sau se bo qua buoc nay."
info "State file: $STATE_FILE"
exit 0
