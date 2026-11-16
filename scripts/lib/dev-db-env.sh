# shellcheck shell=bash
# 本地开发数据库脚本的公共环境与工具函数（被 dev-db-init.sh / dev-db-reset.sh source）

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# 读取 deploy/.env 中某个键的值。
# 注意：不直接 source .env，避免覆盖调用方显式传入的环境变量。
env_file_value() {
  local key="$1" file="$ROOT_DIR/deploy/.env" line value
  [ -f "$file" ] || return 1
  line="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "$file" | tail -n1 || true)"
  [ -n "$line" ] || return 1
  value="${line#*=}"
  # 去掉首尾空白
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  # 去掉成对引号
  case "$value" in
    \"*\") value="${value#\"}" ; value="${value%\"}" ;;
    \'*\') value="${value#\'}" ; value="${value%\'}" ;;
  esac
  printf '%s' "$value"
}

# 优先级：环境变量 > deploy/.env > 内置默认值
MYSQL_CONTAINER="${MYSQL_CONTAINER:-$(env_file_value MYSQL_CONTAINER || true)}"
MYSQL_PORT="${MYSQL_PORT:-$(env_file_value MYSQL_PORT || true)}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-$(env_file_value MYSQL_ROOT_PASSWORD || true)}"
MYSQL_DATABASE="${MYSQL_DATABASE:-$(env_file_value MYSQL_DATABASE || true)}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-craftbean-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123}"
MYSQL_DATABASE="${MYSQL_DATABASE:-craftbean}"

# 后端默认读 DB_*，未显式设置时从 MySQL 配置推导，确保两边连的是同一个库
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-$MYSQL_PORT}"
export DB_NAME="${DB_NAME:-$MYSQL_DATABASE}"
export DB_USER="${DB_USER:-$(env_file_value MYSQL_USER || true)}"
export DB_PASSWORD="${DB_PASSWORD:-$(env_file_value MYSQL_PASSWORD || true)}"
export DB_USER="${DB_USER:-craftbean}"
export DB_PASSWORD="${DB_PASSWORD:-craftbean123}"

# 本机 Maven 需要 JDK 21（Homebrew 默认 JDK 25 与 Lombok 不兼容）
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home}"
export JAVA_HOME

info() { printf '==> %s\n' "$*"; }

fail() {
  printf '错误: %s\n' "$*" >&2
  exit 1
}

require_docker() {
  command -v docker >/dev/null 2>&1 || fail "未找到 docker，请先安装并启动 Docker Desktop"
  docker info >/dev/null 2>&1 || fail "Docker 守护进程未运行：请先启动 Docker Desktop（open -a Docker）"
}

require_jdk() {
  [ -x "$JAVA_HOME/bin/java" ] || fail "未找到 JDK 21: ${JAVA_HOME}（可设置 JAVA_HOME 覆盖）"
}

validate_db_name() {
  [[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || fail "非法的数据库名: $MYSQL_DATABASE"
}

# 确保 MySQL 容器已启动且健康
ensure_mysql() {
  require_docker
  validate_db_name
  (cd "$ROOT_DIR/deploy" && docker compose up -d mysql >/dev/null 2>&1) \
    || fail "启动 MySQL 容器失败（cd deploy && docker compose up -d mysql 查看详情）"

  local status=""
  for _ in $(seq 1 60); do
    status="$(docker inspect -f '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null || true)"
    [ "$status" = "healthy" ] && return 0
    sleep 1
  done
  fail "MySQL 容器未在 60 秒内就绪（当前状态: ${status:-unknown}）"
}

# 以 root 执行 SQL：mysql_root -e "select 1"
mysql_root() {
  docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" mysql -uroot "$@"
}

# 以 root 执行标准输入中的 SQL（用于批量 TRUNCATE 等）
mysql_root_stdin() {
  docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" mysql -uroot "$@"
}

# 8080 后端监听进程 PID（无则输出空）
backend_pid() {
  lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null | head -n1 || true
}
