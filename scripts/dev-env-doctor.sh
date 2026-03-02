#!/usr/bin/env bash
set -euo pipefail

MODE="local"
DB_KIND="auto"
INSTALL_MISSING=0
CHECK_ONLY=0

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
    --install-missing)
      INSTALL_MISSING=1
      shift
      ;;
    --check-only)
      CHECK_ONLY=1
      shift
      ;;
    -h|--help)
      cat <<'EOF'
Usage: bash ./scripts/dev-env-doctor.sh [--mode local|public] [--db auto|h2|mysql|postgres] [--install-missing] [--check-only]
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
if [[ "$CHECK_ONLY" -eq 1 ]]; then INSTALL_MISSING=0; fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APT_UPDATED=0
MAVEN_WRAPPER="$REPO_ROOT/mvnw"
GRADLE_WRAPPER="$REPO_ROOT/gradlew"

title() { printf '== %s ==\n' "$1"; }
ok() { printf '[OK] %s\n' "$1"; }
warn() { printf '[WARN] %s\n' "$1"; }
info() { printf '[INFO] %s\n' "$1"; }
err() { printf '[ERR] %s\n' "$1"; }

cmd_exists() { command -v "$1" >/dev/null 2>&1; }

platform_name() {
  case "$(uname -s 2>/dev/null || echo unknown)" in
    Darwin) echo macos ;;
    Linux) echo linux ;;
    MINGW*|MSYS*|CYGWIN*) echo windows ;;
    *) echo unknown ;;
  esac
}

pkg_manager() {
  local p
  p="$(platform_name)"
  if [[ "$p" == "macos" ]]; then
    cmd_exists brew && echo brew && return 0
    return 1
  fi
  if [[ "$p" == "linux" ]]; then
    for pm in apt-get dnf yum pacman zypper; do
      if cmd_exists "$pm"; then
        echo "$pm"
        return 0
      fi
    done
    return 1
  fi
  return 1
}

java_major() {
  cmd_exists java || return 1
  local line
  line="$(java -version 2>&1 | head -n1 || true)"
  if [[ "$line" =~ \"([0-9]+) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

maven_version() {
  if cmd_exists mvn; then
    mvn -v 2>&1 | awk '/Apache Maven/ {print $3; exit}'
    return 0
  fi
  [[ -f "$MAVEN_WRAPPER" ]] || return 1
  bash "$MAVEN_WRAPPER" -v 2>&1 | awk '/Apache Maven/ {print $3; exit}'
}

gradle_version() {
  if cmd_exists gradle; then
    gradle -v 2>&1 | awk '/^Gradle / {print $2; exit}'
    return 0
  fi
  [[ -f "$GRADLE_WRAPPER" ]] || return 1
  bash "$GRADLE_WRAPPER" -v 2>&1 | awk '/^Gradle / {print $2; exit}'
}

node_version() {
  cmd_exists node || return 1
  node -v 2>/dev/null | sed 's/^v//'
}

version_ge() {
  local actual="${1:-0}"
  local min="${2:-0}"
  awk -v A="$actual" -v B="$min" '
    BEGIN {
      split(A, a, ".")
      split(B, b, ".")
      for (i = 1; i <= 4; i++) {
        gsub(/[^0-9].*$/, "", a[i])
        gsub(/[^0-9].*$/, "", b[i])
        if (a[i] == "") a[i] = 0
        if (b[i] == "") b[i] = 0
        if (a[i] > b[i]) exit 0
        if (a[i] < b[i]) exit 1
      }
      exit 0
    }
  '
}

run_install() {
  if [[ $# -lt 1 ]]; then return 1; fi
  info "RUN: $*"
  if [[ "$(id -u 2>/dev/null || echo 1)" -ne 0 ]] && cmd_exists sudo; then
    sudo "$@"
  else
    "$@"
  fi
}

ensure_apt_update() {
  if [[ "$APT_UPDATED" -eq 1 ]]; then return 0; fi
  if ! cmd_exists apt-get; then return 0; fi
  run_install apt-get update
  APT_UPDATED=1
}

try_install_tool() {
  local key="$1"
  local pm="${2:-}"
  [[ -n "$pm" ]] || return 1

  case "$pm" in
    brew)
      case "$key" in
        java21) run_install brew install openjdk@21 ;;
        maven) run_install brew install maven ;;
        gradle) run_install brew install gradle ;;
        git) run_install brew install git ;;
        node) run_install brew install node ;;
        cloudflared) run_install brew install cloudflared ;;
        *) return 1 ;;
      esac
      ;;
    apt-get)
      ensure_apt_update
      case "$key" in
        java21) run_install apt-get install -y openjdk-21-jdk ;;
        maven) run_install apt-get install -y maven ;;
        gradle) run_install apt-get install -y gradle ;;
        git) run_install apt-get install -y git ;;
        node) run_install apt-get install -y nodejs npm ;;
        cloudflared) run_install apt-get install -y cloudflared ;;
        *) return 1 ;;
      esac
      ;;
    dnf)
      case "$key" in
        java21) run_install dnf install -y java-21-openjdk-devel ;;
        maven) run_install dnf install -y maven ;;
        gradle) run_install dnf install -y gradle ;;
        git) run_install dnf install -y git ;;
        node) run_install dnf install -y nodejs ;;
        cloudflared) run_install dnf install -y cloudflared ;;
        *) return 1 ;;
      esac
      ;;
    yum)
      case "$key" in
        java21) run_install yum install -y java-21-openjdk-devel ;;
        maven) run_install yum install -y maven ;;
        gradle) run_install yum install -y gradle ;;
        git) run_install yum install -y git ;;
        node) run_install yum install -y nodejs npm ;;
        cloudflared) run_install yum install -y cloudflared ;;
        *) return 1 ;;
      esac
      ;;
    pacman)
      case "$key" in
        java21) run_install pacman -S --noconfirm jdk21-openjdk ;;
        maven) run_install pacman -S --noconfirm maven ;;
        gradle) run_install pacman -S --noconfirm gradle ;;
        git) run_install pacman -S --noconfirm git ;;
        node) run_install pacman -S --noconfirm nodejs npm ;;
        cloudflared) run_install pacman -S --noconfirm cloudflared ;;
        *) return 1 ;;
      esac
      ;;
    zypper)
      case "$key" in
        java21) run_install zypper --non-interactive install java-21-openjdk-devel ;;
        maven) run_install zypper --non-interactive install maven ;;
        gradle) run_install zypper --non-interactive install gradle ;;
        git) run_install zypper --non-interactive install git ;;
        node) run_install zypper --non-interactive install nodejs ;;
        cloudflared) run_install zypper --non-interactive install cloudflared ;;
        *) return 1 ;;
      esac
      ;;
    *)
      return 1
      ;;
  esac
}

ensure_example_file() {
  local target="$REPO_ROOT/$1"
  local example="$REPO_ROOT/$2"
  if [[ -f "$target" ]]; then
    ok "$1 ton tai"
    return 0
  fi
  if [[ ! -f "$example" ]]; then
    warn "Khong tim thay file mau: $2"
    return 0
  fi
  cp "$example" "$target"
  ok "Da tao $1 tu $2"
}

ensure_tool() {
  local display="$1" cmd="$2" key="$3" required="$4" min="$5" version_type="$6"
  local pm="$7"
  local version=""
  local exists=0
  local version_ok=1

  if cmd_exists "$cmd"; then
    exists=1
  elif [[ "$cmd" == "mvn" && -f "$MAVEN_WRAPPER" ]]; then
    exists=1
  elif [[ "$cmd" == "gradle" && -f "$GRADLE_WRAPPER" ]]; then
    exists=1
  fi

  if [[ "$exists" -eq 1 ]]; then
    case "$version_type" in
      java) version="$(java_major || true)" ;;
      maven) version="$(maven_version || true)" ;;
      gradle) version="$(gradle_version || true)" ;;
      node) version="$(node_version || true)" ;;
      none) version="" ;;
    esac
  fi

  if [[ -n "$min" && "$exists" -eq 1 ]]; then
    if [[ "$version_type" == "java" ]]; then
      [[ -n "$version" && "$version" -ge "$min" ]] || version_ok=0
    else
      if ! version_ge "${version:-0}" "$min"; then
        version_ok=0
      fi
    fi
  fi

  if [[ "$exists" -eq 1 && "$version_ok" -eq 1 ]]; then
    if [[ -n "$version" ]]; then ok "$display OK ($version)"; else ok "$display OK"; fi
    return 0
  fi

  if [[ "$exists" -eq 1 && "$version_ok" -eq 0 ]]; then
    warn "$display co version chua dat (can >= $min, hien tai: ${version:-unknown})"
  else
    if [[ "$required" == "1" ]]; then
      warn "$display thieu (bat buoc)"
    else
      warn "$display thieu (khuyen nghi)"
    fi
  fi

  if [[ "$INSTALL_MISSING" -eq 1 ]]; then
    info "Dang thu cai tu dong $display ..."
    if ! try_install_tool "$key" "$pm"; then
      warn "Cai tu dong $display that bai. Hay cai thu cong va mo terminal moi."
    fi
  fi

  exists=0
  version=""
  version_ok=1
  if cmd_exists "$cmd"; then
    exists=1
  elif [[ "$cmd" == "mvn" && -f "$MAVEN_WRAPPER" ]]; then
    exists=1
  elif [[ "$cmd" == "gradle" && -f "$GRADLE_WRAPPER" ]]; then
    exists=1
  fi
  if [[ "$exists" -eq 1 ]]; then
    case "$version_type" in
      java) version="$(java_major || true)" ;;
      maven) version="$(maven_version || true)" ;;
      gradle) version="$(gradle_version || true)" ;;
      node) version="$(node_version || true)" ;;
      none) version="" ;;
    esac
  fi
  if [[ -n "$min" && "$exists" -eq 1 ]]; then
    if [[ "$version_type" == "java" ]]; then
      [[ -n "$version" && "$version" -ge "$min" ]] || version_ok=0
    else
      version_ge "${version:-0}" "$min" || version_ok=0
    fi
  fi

  if [[ "$required" == "1" ]]; then
    [[ "$exists" -eq 1 && "$version_ok" -eq 1 ]]
  else
    return 0
  fi
}

required_failures=()

title "Game Hub Dev Environment Doctor"
info "Repo: $REPO_ROOT"
info "Mode: $MODE | DB: $DB_KIND | AutoInstall: $INSTALL_MISSING"
PM="$(pkg_manager || true)"
if [[ -n "$PM" ]]; then
  info "Package manager: $PM"
else
  warn "Khong phat hien package manager ho tro (chi check, khong cai tu dong)."
fi

if ! ensure_tool "Java (JDK)" "java" "java21" 1 "17" "java" "$PM"; then
  required_failures+=("Java 17+")
fi
if ! ensure_tool "Maven (hoac Maven Wrapper)" "mvn" "maven" 1 "3.8.6" "maven" "$PM"; then
  required_failures+=("Maven 3.8.6+ (hoac mvnw)")
fi
ensure_tool "Gradle (hoac Gradle Wrapper)" "gradle" "gradle" 0 "8.7.0" "gradle" "$PM" || true
ensure_tool "Git" "git" "git" 0 "" "none" "$PM" || true
ensure_tool "Node.js" "node" "node" 0 "18.0.0" "node" "$PM" || true

if [[ "$MODE" == "public" ]]; then
  if ! ensure_tool "cloudflared" "cloudflared" "cloudflared" 1 "" "none" "$PM"; then
    required_failures+=("cloudflared")
  fi
fi

title "Project Files And Local Data"
mkdir -p "$REPO_ROOT/.data"
ok "Thu muc .data da san sang"
ensure_example_file ".env.public.local" ".env.public.example"
if [[ "$DB_KIND" == "mysql" ]]; then
  ensure_example_file ".env.public.mysql.local" ".env.public.mysql.example"
  if cmd_exists mysql; then ok "MySQL CLI ton tai"; else warn "Khong tim thay lenh 'mysql' (chi de kiem tra/quan ly DB)."; fi
fi
if [[ "$DB_KIND" == "postgres" ]]; then
  ensure_example_file ".env.public.postgres.local" ".env.public.postgres.example"
  if cmd_exists psql; then ok "psql ton tai"; else warn "Khong tim thay lenh 'psql' (chi de kiem tra/quan ly DB)."; fi
fi

title "Quick Start"
echo "Windows (PowerShell): powershell -ExecutionPolicy Bypass -File .\\scripts\\dev-run-local.ps1"
echo "macOS/Linux (bash):  bash ./scripts/dev-run-local.sh"
echo "Maven wrapper: .\\mvnw.cmd spring-boot:run (Windows) | ./mvnw spring-boot:run (macOS/Linux)"
echo "Gradle wrapper: .\\gradlew.bat bootRun (Windows) | ./gradlew bootRun (macOS/Linux)"
if [[ "$MODE" == "public" ]]; then
  echo "Windows public helper: cmd /c scripts\\manual-start-public.cmd"
fi

if [[ ${#required_failures[@]} -gt 0 ]]; then
  title "Result"
  err "Moi truong chua dat. Thieu: ${required_failures[*]}"
  if [[ "$INSTALL_MISSING" -eq 0 ]]; then
    info "Chay lai voi --install-missing de thu cai tu dong."
  else
    info "Neu vua cai xong ma van bao thieu, hay mo terminal moi va chay lai script."
  fi
  exit 1
fi

title "Result"
ok "Moi truong dat yeu cau de chay du an."
if [[ "$INSTALL_MISSING" -eq 0 ]]; then
  info "Co the them --install-missing de script tu cai cong cu thieu tren may moi."
fi
exit 0
