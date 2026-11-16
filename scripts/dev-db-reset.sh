#!/usr/bin/env bash
# 清空本地开发数据库的全部数据（不删除库、不改表结构），然后重新初始化默认管理员。
# 破坏性操作：会清空所有业务数据、用户与审计日志，执行前必须确认。
set -euo pipefail

# shellcheck disable=SC1091
. "$(cd "$(dirname "$0")" && pwd)/lib/dev-db-env.sh"

ASSUME_YES=0
IGNORE_BACKEND=0

usage() {
  cat <<'EOF'
用法: scripts/dev-db-reset.sh [选项]

清空本地开发数据库中所有表的数据：
  - 保留数据库本身、表结构与 Flyway 迁移记录（flyway_schema_history）
  - 清空业务数据、用户、审计日志，并重置自增 ID
  - 随后重新创建默认管理员 admin / Admin@123456（手机号尾四位 0000）

选项:
  -y, --yes             跳过交互确认（脚本化场景使用）
      --ignore-backend  检测到后端仍在运行时仍继续，只清数据、不执行初始化
  -h, --help            显示帮助

执行前请先停止后端（占用 8080 的进程），避免清库期间请求读到空数据。
若数据库尚未建表，请先运行 scripts/dev-db-init.sh。
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -y | --yes)
      ASSUME_YES=1
      ;;
    --ignore-backend)
      IGNORE_BACKEND=1
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      fail "未知参数: $1（使用 --help 查看用法）"
      ;;
  esac
  shift
done

validate_db_name

# 1) 后端必须停止：TRUNCATE 需要表级元数据锁，且清空期间请求会读到空数据
pid="$(backend_pid)"
if [ -n "$pid" ]; then
  if [ "$IGNORE_BACKEND" -eq 0 ]; then
    echo "检测到后端仍在运行（PID ${pid}，端口 8080）:" >&2
    ps -p "$pid" -o command= >&2 || true
    fail "请先停止后端后重试；如确认要强制清数据，可加 --ignore-backend"
  fi
  printf '警告: 后端仍在运行（PID %s），清空期间接口可能读到空数据。\n' "$pid" >&2
fi

# 2) 连接数据库并找出所有需要清空的表（保留 Flyway 迁移记录）
ensure_mysql
tables="$(mysql_root -N -e "
  select table_name
  from information_schema.tables
  where table_schema = '$MYSQL_DATABASE'
    and table_type = 'BASE TABLE'
    and table_name <> 'flyway_schema_history'
  order by table_name
")"

if [ -z "$tables" ]; then
  fail "数据库 ${MYSQL_DATABASE} 中没有任何表，请先运行 scripts/dev-db-init.sh 完成建表"
fi

if printf '%s\n' "$tables" | grep -qvE '^[A-Za-z0-9_]+$'; then
  fail "检测到异常表名，已中止: $(printf '%s\n' "$tables" | grep -vE '^[A-Za-z0-9_]+$' | tr '\n' ' ')"
fi

# 3) 明确展示目标并确认
echo "即将清空数据库中的数据:"
echo "    容器:   ${MYSQL_CONTAINER}"
echo "    地址:   localhost:${MYSQL_PORT}"
echo "    数据库: ${MYSQL_DATABASE}"
echo "    表数量: $(printf '%s\n' "$tables" | wc -l | tr -d ' ')（保留表结构与 flyway_schema_history）"
echo
printf '%s\n' "$tables" | while IFS= read -r table; do
  [ -n "$table" ] && echo "    - $table"
done
echo
echo "所有数据将被清空，且无法恢复。"

if [ "$ASSUME_YES" -ne 1 ]; then
  read -r -p "输入 yes 确认继续: " answer
  if [ "$answer" != "yes" ] && [ "$answer" != "YES" ]; then
    echo "已取消。"
    exit 0
  fi
fi

# 4) 清空数据：临时关闭外键检查，逐表 TRUNCATE（会重置自增 ID）
{
  echo "USE \`$MYSQL_DATABASE\`;"
  echo "SET FOREIGN_KEY_CHECKS = 0;"
  printf '%s\n' "$tables" | sed -e 's/^/TRUNCATE TABLE `/' -e 's/$/`;/'
  echo "SET FOREIGN_KEY_CHECKS = 1;"
} | mysql_root_stdin

info "已清空 $(printf '%s\n' "$tables" | wc -l | tr -d ' ') 张表的数据: ${MYSQL_DATABASE}"

if [ "$IGNORE_BACKEND" -eq 1 ]; then
  info "已按 --ignore-backend 跳过初始化"
  info "停止后端后运行 scripts/dev-db-init.sh 重新创建默认管理员"
  exit 0
fi

# 5) 迁移 + 初始化（复用 dev-db-init.sh；Flyway 记录保留，不会重跑历史迁移）
"$ROOT_DIR/scripts/dev-db-init.sh"
