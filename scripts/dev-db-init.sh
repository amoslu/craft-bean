#!/usr/bin/env bash
# 初始化本地开发数据库：确保 MySQL 已启动，执行 Flyway 迁移，并创建默认管理员。
# 幂等：可重复执行，不会删除已有业务数据。
set -euo pipefail

# shellcheck disable=SC1091
. "$(cd "$(dirname "$0")" && pwd)/lib/dev-db-env.sh"

usage() {
  cat <<'EOF'
用法: scripts/dev-db-init.sh

确保 MySQL 容器运行，执行尚未应用的 Flyway 迁移，并在没有 admin 账号时创建默认管理员。
本脚本不会删除任何已有数据。

默认管理员（仅在账号不存在时创建）:
  用户名: admin
  密码:   Admin@123456
  手机号后四位: 0000

可用环境变量（通常来自 deploy/.env 或后端配置）:
  MYSQL_CONTAINER / MYSQL_PORT / MYSQL_DATABASE / MYSQL_ROOT_PASSWORD
  DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
  JAVA_HOME
EOF
}

case "${1:-}" in
  -h | --help)
    usage
    exit 0
    ;;
  "")
    ;;
  *)
    fail "未知参数: $1（使用 --help 查看用法）"
    ;;
esac

command -v mvn >/dev/null 2>&1 || fail "未找到 mvn，请先安装 Maven"
require_jdk

LOG_FILE="$ROOT_DIR/server/target/dev-db-init.log"
BOOT_PID=""

stop_boot() {
  [ -n "$BOOT_PID" ] || return 0
  kill -TERM "$BOOT_PID" 2>/dev/null || true
  for _ in $(seq 1 10); do
    kill -0 "$BOOT_PID" 2>/dev/null || break
    sleep 1
  done
  kill -KILL "$BOOT_PID" 2>/dev/null || true
  wait "$BOOT_PID" 2>/dev/null || true
  BOOT_PID=""
}

cleanup_boot() {
  stop_boot
}
trap cleanup_boot EXIT INT TERM

ensure_mysql

# 数据库缺失时自动创建（与 MySQL 容器的默认字符集保持一致）
db_exists="$(mysql_root -N -e \
  "select count(*) from information_schema.schemata where schema_name = '$MYSQL_DATABASE'")"
if [ "$db_exists" = "0" ]; then
  mysql_root -e "CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  info "已创建数据库: ${MYSQL_DATABASE}"
fi

info "MySQL 已就绪: localhost:${DB_PORT}/${DB_NAME}（容器 ${MYSQL_CONTAINER}）"

mkdir -p "$(dirname "$LOG_FILE")"
info "启动临时后端实例执行迁移与初始化（随机端口，完成后自动退出）"
info "日志: $LOG_FILE"
(
  cd "$ROOT_DIR/server"
  exec mvn -q spring-boot:run \
    -Dspring-boot.run.fork=false \
    -Dspring-boot.run.arguments=--server.port=0
) >"$LOG_FILE" 2>&1 &
BOOT_PID=$!

started=0
for _ in $(seq 1 180); do
  if grep -q "Started CraftBeanApplication" "$LOG_FILE"; then
    started=1
    break
  fi
  if ! kill -0 "$BOOT_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [ "$started" -ne 1 ]; then
  echo "---- 启动日志末尾 ----" >&2
  tail -n 40 "$LOG_FILE" >&2 || true
  echo "----------------------" >&2
  fail "后端初始化实例未能启动（详见 ${LOG_FILE}）"
fi

# ApplicationRunner（BootstrapDataInitializer）在 Started 日志之后执行，等待 admin 落库
admin_count="0"
for _ in $(seq 1 30); do
  admin_count="$(mysql_root -N -e \
    "select count(*) from \`$MYSQL_DATABASE\`.sys_user where username = 'admin'" 2>/dev/null || echo 0)"
  [ "$admin_count" != "0" ] && break
  sleep 1
done

stop_boot

[ "$admin_count" != "0" ] || fail "初始化后仍未找到 admin 账号（详见 ${LOG_FILE}）"

table_count="$(mysql_root -N -e \
  "select count(*) from information_schema.tables where table_schema = '$MYSQL_DATABASE'")"
flyway_version="$(mysql_root -N -e \
  "select version from \`$MYSQL_DATABASE\`.flyway_schema_history order by installed_rank desc limit 1" 2>/dev/null || true)"

echo
info "初始化完成"
echo "    数据库:   ${DB_NAME} @ localhost:${DB_PORT}（容器 ${MYSQL_CONTAINER}）"
echo "    表数量:   ${table_count}"
echo "    迁移版本: ${flyway_version:-unknown}"

if grep -q "已创建默认管理员" "$LOG_FILE"; then
  echo "    管理员:   admin / Admin@123456（手机号尾四位 0000，首次启动新建）"
  echo "    请登录后尽快修改密码。"
else
  echo "    管理员:   admin 已存在，密码未改动"
fi
