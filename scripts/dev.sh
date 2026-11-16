#!/usr/bin/env bash
# 本地开发环境启动 / 停止 / 重启 / 状态脚本。
# 顺序：MySQL -> 后端(8080) -> 前端(5173)。幂等：已在运行的组件不会重复启动。
set -euo pipefail

# shellcheck disable=SC1091
. "$(cd "$(dirname "$0")" && pwd)/lib/dev-db-env.sh"

STATE_DIR="$ROOT_DIR/.dev"
RUN_DIR="$STATE_DIR/run"
LOG_DIR="$STATE_DIR/logs"
BACKEND_PORT=8080
WEB_PORT=5173
BACKEND_PID_FILE="$RUN_DIR/server.pid"
WEB_PID_FILE="$RUN_DIR/web.pid"
BACKEND_LOG="$LOG_DIR/server.log"
WEB_LOG="$LOG_DIR/web.log"

usage() {
  cat <<'EOF'
用法: scripts/dev.sh <命令> [组件]

命令:
  start    [db|server|web|all]   启动（默认 all，已运行则跳过）
  stop     [server|web|all]      停止（默认 all；MySQL 需显式传 db）
  restart  [db|server|web|all]   先停止再启动（默认 all）
  status                         查看 MySQL / 后端 / 前端状态

示例:
  scripts/dev.sh start             # 首次启动：MySQL + 后端 + 前端
  scripts/dev.sh restart server    # 只重启后端（改 Java 代码后常用）
  scripts/dev.sh restart web       # 只重启前端
  scripts/dev.sh stop              # 停后端和前端，保留 MySQL

日志: .dev/logs/server.log, .dev/logs/web.log
EOF
}

usage_error() {
  printf '错误: %s\n' "$*" >&2
  printf '使用 scripts/dev.sh --help 查看用法\n' >&2
  exit 2
}

# ---------- 进程工具 ----------

port_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -n1 || true
}

# 先杀子进程再杀自己，确保 mvn/npm 派生的 java/node 一并退出
kill_tree() {
  local pid="$1" sig="${2:-TERM}" child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    kill_tree "$child" "$sig"
  done
  kill -"$sig" "$pid" 2>/dev/null || true
}

wait_port_free() {
  local port="$1" i
  for i in $(seq 1 30); do
    [ -z "$(port_pid "$port")" ] && return 0
    sleep 1
  done
  return 1
}

stop_by_pidfile() {
  local name="$1" pid_file="$2" port="$3" pid
  pid=""
  [ -f "$pid_file" ] && pid="$(cat "$pid_file" 2>/dev/null || true)"

  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    info "停止${name} (PID $pid)"
    kill_tree "$pid" TERM
  fi

  # 兜底：pid 文件缺失或进程树未覆盖时，按端口清理
  local listener
  listener="$(port_pid "$port")"
  if [ -n "$listener" ]; then
    local cmd
    cmd="$(ps -o command= -p "$listener" 2>/dev/null | cut -c1-100 || true)"
    info "${name}端口 ${port} 仍被占用 (PID $listener: ${cmd:-unknown})，一并停止"
    kill_tree "$listener" TERM
  fi

  if ! wait_port_free "$port"; then
    info "${name}未在 30 秒内退出，强制结束"
    [ -n "$pid" ] && kill_tree "$pid" KILL
    listener="$(port_pid "$port")"
    [ -n "$listener" ] && kill_tree "$listener" KILL
    wait_port_free "$port" || fail "${name}无法停止（端口 ${port} 仍被占用）"
  fi

  rm -f "$pid_file"
}

# ---------- Docker / MySQL ----------

ensure_docker() {
  docker info >/dev/null 2>&1 && return 0
  command -v docker >/dev/null 2>&1 || fail "未找到 docker，请先安装 Docker Desktop"
  info "Docker 未运行，正在启动 Docker Desktop..."
  open -a Docker 2>/dev/null || fail "无法启动 Docker Desktop，请手动启动后重试"
  for _ in $(seq 1 90); do
    docker info >/dev/null 2>&1 && return 0
    sleep 2
  done
  fail "Docker 未在 180 秒内就绪"
}

start_db() {
  ensure_docker
  ensure_mysql
  info "MySQL 已就绪 (localhost:${MYSQL_PORT}/${MYSQL_DATABASE})"
}

stop_db() {
  require_docker
  info "停止 MySQL 容器"
  (cd "$ROOT_DIR/deploy" && docker compose stop mysql >/dev/null)
}

# ---------- 后端 ----------

start_server() {
  local pid
  pid="$(port_pid "$BACKEND_PORT")"
  if [ -n "$pid" ]; then
    info "后端已在 ${BACKEND_PORT} 运行 (PID $pid)，跳过启动"
  else
    require_jdk
    mkdir -p "$RUN_DIR" "$LOG_DIR"
    : >"$BACKEND_LOG"
    info "启动后端 (JDK: $JAVA_HOME)"
    (
      cd "$ROOT_DIR/server"
      nohup mvn spring-boot:run >>"$BACKEND_LOG" 2>&1 &
      echo $! >"$BACKEND_PID_FILE"
    )
    wait_log_or_die "$BACKEND_LOG" "Started CraftBeanApplication" 240 "后端" \
      "tail -n 40 $BACKEND_LOG"
  fi

  if login_ok; then
    info "后端登录冒烟通过 (admin / Admin@123456 / 0000)"
  else
    printf '警告: 后端已启动但登录冒烟失败，请检查 %s\n' "$BACKEND_LOG" >&2
  fi
}

wait_log_or_die() {
  local log="$1" marker="$2" timeout="$3" name="$4" hint="$5" i pid
  pid=""
  [ -f "$BACKEND_PID_FILE" ] && pid="$(cat "$BACKEND_PID_FILE" 2>/dev/null || true)"
  for i in $(seq 1 "$timeout"); do
    grep -q "$marker" "$log" 2>/dev/null && return 0
    # 进程已退出则立即失败，避免干等
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  printf '错误: %s 未在 %s 秒内就绪。最近日志:\n' "$name" "$timeout" >&2
  tail -n 40 "$log" >&2 || true
  printf '排查: %s\n' "$hint" >&2
  exit 1
}

login_ok() {
  local body
  body="$(curl -s --max-time 5 -X POST "http://localhost:${BACKEND_PORT}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"Admin@123456","phoneTail":"0000"}' 2>/dev/null || true)"
  printf '%s' "$body" | grep -q '"code":0'
}

# ---------- 前端 ----------

start_web() {
  local pid
  pid="$(port_pid "$WEB_PORT")"
  if [ -n "$pid" ]; then
    info "前端已在 ${WEB_PORT} 运行 (PID $pid)，跳过启动"
    return 0
  fi
  command -v npm >/dev/null 2>&1 || fail "未找到 npm，请先安装 Node.js"
  [ -d "$ROOT_DIR/admin-web/node_modules" ] || {
    info "安装前端依赖"
    (cd "$ROOT_DIR/admin-web" && npm install)
  }

  mkdir -p "$RUN_DIR" "$LOG_DIR"
  : >"$WEB_LOG"
  info "启动前端"
  (
    cd "$ROOT_DIR/admin-web"
    nohup npm run dev >>"$WEB_LOG" 2>&1 &
    echo $! >"$WEB_PID_FILE"
  )
  wait_http "http://localhost:${WEB_PORT}/" 90 "前端" "$WEB_LOG"
}

wait_http() {
  local url="$1" timeout="$2" name="$3" log="$4" i
  for i in $(seq 1 "$timeout"); do
    curl -fsS -o /dev/null --max-time 2 "$url" 2>/dev/null && return 0
    sleep 1
  done
  printf '错误: %s 未在 %s 秒内就绪。最近日志:\n' "$name" "$timeout" >&2
  tail -n 40 "$log" >&2 || true
  exit 1
}

# ---------- 命令分发 ----------

status() {
  local pid
  if docker info >/dev/null 2>&1; then
    local health
    health="$(docker inspect -f '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null || echo "未运行")"
    printf 'MySQL   : %s (localhost:%s/%s)\n' "$health" "$MYSQL_PORT" "$MYSQL_DATABASE"
  else
    printf 'MySQL   : Docker 未运行\n'
  fi

  pid="$(port_pid "$BACKEND_PORT")"
  if [ -n "$pid" ]; then
    login_ok && printf '后端    : 运行中 PID %s, 登录正常 (http://localhost:%s)\n' "$pid" "$BACKEND_PORT" \
      || printf '后端    : 运行中 PID %s, 登录异常 (http://localhost:%s)\n' "$pid" "$BACKEND_PORT"
  else
    printf '后端    : 未运行\n'
  fi

  pid="$(port_pid "$WEB_PORT")"
  if [ -n "$pid" ]; then
    printf '前端    : 运行中 PID %s (http://localhost:%s)\n' "$pid" "$WEB_PORT"
  else
    printf '前端    : 未运行\n'
  fi
}

cmd="${1:-}"
target="${2:-all}"

case "$cmd" in
  -h | --help | help)
    usage
    ;;
  start)
    case "$target" in
      all) start_db; start_server; start_web ;;
      db) start_db ;;
      server) start_server ;;
      web) start_web ;;
      *) usage_error "未知组件: $target" ;;
    esac
    ;;
  stop)
    case "$target" in
      all) stop_by_pidfile "后端" "$BACKEND_PID_FILE" "$BACKEND_PORT"; stop_by_pidfile "前端" "$WEB_PID_FILE" "$WEB_PORT" ;;
      db) stop_db ;;
      server) stop_by_pidfile "后端" "$BACKEND_PID_FILE" "$BACKEND_PORT" ;;
      web) stop_by_pidfile "前端" "$WEB_PID_FILE" "$WEB_PORT" ;;
      *) usage_error "未知组件: $target" ;;
    esac
    ;;
  restart)
    case "$target" in
      all) stop_by_pidfile "后端" "$BACKEND_PID_FILE" "$BACKEND_PORT"; stop_by_pidfile "前端" "$WEB_PID_FILE" "$WEB_PORT"; start_db; start_server; start_web ;;
      db) stop_db; start_db ;;
      server) stop_by_pidfile "后端" "$BACKEND_PID_FILE" "$BACKEND_PORT"; start_server ;;
      web) stop_by_pidfile "前端" "$WEB_PID_FILE" "$WEB_PORT"; start_web ;;
      *) usage_error "未知组件: $target" ;;
    esac
    ;;
  status)
    status
    ;;
  "")
    usage
    exit 2
    ;;
  *)
    usage_error "未知命令: $cmd"
    ;;
esac
