# 本地测试环境启动说明

本文档说明如何在 macOS 上从零启动 craft-bean 的本地开发/测试环境，以及如何验证、停止和排查。

## 1. 端口与组件总览

| 组件 | 地址 | 说明 |
|---|---|---|
| MySQL 8 | `localhost:3307` | Docker 容器 `craftbean-mysql`；库名 `craftbean` |
| 后端 | `http://localhost:8080` | Spring Boot；启动时自动执行 Flyway 迁移 |
| 前端 | `http://localhost:5173` | Vite 开发服务器，`/api` 代理到 8080 |

宿主机 `3306` 已被占用，因此 MySQL 映射到 `3307`，后端默认也用 `3307`。

## 2. 前置条件

- **Docker Desktop**（需先启动守护进程）
- **JDK 21**：本机路径为 `/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home`
  - 注意：Homebrew 默认的 JDK 25 与 Lombok 1.18.34 不兼容，Maven 构建必须用 JDK 21
- **Maven 3.9+**
- **Node.js 18+ / npm**（本机实测 Node 20.11、npm 10.2 可用）

## 3. 启动步骤

### 3.0 一键启动 / 重启（推荐）

```bash
# 首次或日常启动：MySQL -> 后端 -> 前端（幂等，已运行则跳过）
./scripts/dev.sh start

# 改完 Java 代码：只重启后端
./scripts/dev.sh restart server

# 只重启前端
./scripts/dev.sh restart web

# 全部重启
./scripts/dev.sh restart

# 停止后端和前端（保留 MySQL）
./scripts/dev.sh stop

# 查看各组件状态
./scripts/dev.sh status
```

- 脚本会自动拉起 Docker Desktop、等待 MySQL healthy、用 JDK 21 启动后端并做登录冒烟
- 日志：`.dev/logs/server.log`、`.dev/logs/web.log`；PID：`.dev/run/`
- 下面 3.1–3.3 是手动分步启动，排查问题时可用

### 3.1 启动 Docker 与数据库

```bash
# 若 Docker Desktop 未运行，先启动并等待守护进程就绪
open -a Docker
until docker info >/dev/null 2>&1; do sleep 2; done

# 首次使用需创建环境文件（已存在则跳过）
cd deploy && cp .env.example .env

# 启动 MySQL（后续重复执行是幂等的）
docker compose up -d mysql

# 确认状态为 healthy
docker compose ps
```

数据库默认账号（见 `deploy/.env.example`）：

- 地址：`localhost:3307`
- 库名：`craftbean`
- 用户：`craftbean` / `craftbean123`（root：`root123`）

### 3.2 启动后端

```bash
cd server
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
mvn spring-boot:run
```

看到 `Started CraftBeanApplication` 即启动成功。Flyway 会自动执行 `server/src/main/resources/db/migration/` 下的迁移，无需手动建表。

后端数据源可用环境变量覆盖（默认值已指向本地 3307）：

| 变量 | 默认值 |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3307` |
| `DB_NAME` | `craftbean` |
| `DB_USER` | `craftbean` |
| `DB_PASSWORD` | `craftbean123` |

### 3.3 启动前端

```bash
cd admin-web
npm install   # 首次或依赖变更时执行
npm run dev
```

打开 `http://localhost:5173`。

## 4. 登录

登录现为**三要素**：用户名 + 密码 + 手机号后四位。

| 字段 | 默认值 |
|---|---|
| 用户名 | `admin` |
| 密码 | `Admin@123456` |
| 手机号后四位 | `0000` |

说明：

- 账号未填写手机号时，尾号按 `0000` 兜底（`admin` 当前即为此情况）。
- 连续失败 5 次会锁定账号 15 分钟。
- README 中的 `admin / admin123` 已过时，且旧登录方式缺少手机号尾号字段，以本文档为准。

## 5. 验证环境

```bash
# 前端页面
curl -s -o /dev/null -w "frontend: %{http_code}\n" http://localhost:5173/

# 后端登录（成功返回 code=0 与 token）
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123456","phoneTail":"0000"}'

# 数据库连通性
docker exec craftbean-mysql mysqladmin ping -uroot -proot123
```

后端根路径与 `/actuator/health` 返回 `401` 属正常现象（需要鉴权）。

## 6. 初始化 / 清理测试库

仓库提供两个脚本，均从仓库根目录执行。

### 6.1 幂等初始化（不删数据）

```bash
./scripts/dev-db-init.sh
```

- 确保 MySQL 容器已启动且处于 healthy 状态
- 启动一个临时后端实例（随机端口），执行尚未应用的 Flyway 迁移
- 若 `admin` 账号不存在则自动创建：`admin / Admin@123456`（尾号 `0000`）
- 初始化完成后临时实例自动退出，不会影响已在 8080 运行的后端

适用于新机器首次启动、或拉取到新的迁移脚本之后。

### 6.2 清空数据并重新初始化（破坏性）

```bash
./scripts/dev-db-reset.sh
```

- 清空 `craftbean` 库中所有表的数据（业务数据、用户、审计日志），并重置自增 ID
- **保留**数据库本身、表结构和 `flyway_schema_history`，不会删库重建，也不会重跑历史迁移
- 随后自动调用 `dev-db-init.sh` 重新创建默认管理员：`admin / Admin@123456`（尾号 `0000`）
- 执行前必须先停止后端（占用 8080 的进程），否则脚本会拒绝执行
- 默认需要输入 `yes` 二次确认；脚本化场景可加 `--yes`
- 若只想清空数据、暂不初始化，可加 `--ignore-backend`
- 数据库尚未建表时，请先运行 `./scripts/dev-db-init.sh`

## 7. 停止环境

```bash
# 后端 / 前端：在各自终端按 Ctrl+C

# 停止数据库（保留数据卷，下次可继续使用）
cd deploy && docker compose stop mysql

# 如需连同数据一起清除
cd deploy && docker compose down -v
```

重启时按「3. 启动步骤」重新执行即可。

## 8. 常见问题

| 现象 | 处理 |
|---|---|
| `failed to connect to the docker API` | Docker Desktop 未启动：`open -a Docker`，等待 `docker info` 成功 |
| MySQL 端口被占用 | 确认宿主机的 3306 是别的服务在用；本项目固定走 3307 |
| Maven 编译报 Lombok/注解处理错误 | 未使用 JDK 21。按 3.2 设置 `JAVA_HOME` |
| 满屏 context 加载失败 / 类找不到 | 多为 IDE 用 JDK 25 编译污染了 `server/target/`，执行 `mvn clean` 后重试 |
| 登录返回 401「用户名或密码错误」 | 检查三要素是否齐全；手机号尾号无手机号时为 `0000`；确认未被锁定（15 分钟） |
| 前端请求 404 | 确认后端已在 8080 启动；Vite 的 `/api` 代理指向 8080 |
| 集成测试断言某表总行数失败 | 集成测试直连开发库且不清理数据，断言不要依赖总行数 |

## 9. 全量检查

提交前可运行：

```bash
./scripts/check.sh
```

该脚本会用 JDK 21 执行后端测试，并构建前端。
