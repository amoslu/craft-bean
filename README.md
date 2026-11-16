# craft-bean 咖啡烘焙工坊管理系统

M1 后台内勤管理（生豆/烘焙/熟豆进销存）。技术栈：Spring Boot 3 + MySQL 8 + Vue3。

## 功能模块

### 生豆管理
- 生豆批次管理
- 采购入库
- 库存流转

### 档案管理
- 生豆档案
- 供应商档案
- 冲煮方式档案
- 生豆与冲煮方式关联

### 系统管理
- 用户管理（含角色、权限、手机号、强密码策略）
- 审计日志
- 系统配置

## 技术栈

**后端：**
- Spring Boot 3.3.5
- Java 17
- MyBatis Plus 3.5.7
- Flyway 数据库迁移
- MySQL 8

**前端：**
- Vue 3.5.12
- Vite
- TypeScript
- Element Plus 2.8.4
- Pinia
- Vue Router
- Axios

## 目录结构

- `server/` - Spring Boot 后端
- `admin-web/` - Vue3 管理后台
- `deploy/` - 部署编排（Docker Compose）
- `docs/` - 项目文档

## 本地开发（S0）

1. 数据库：`cd deploy && docker compose up -d mysql`（宿主机端口 3307）
2. 后端：`cd server && mvn spring-boot:run`（默认连 localhost:3307/craftbean）
3. 前端：`cd admin-web && npm install && npm run dev` → http://localhost:5173
   登录：`admin / admin123`

全量检查：`./scripts/check.sh`

> 本机环境注意：Maven 构建需 JDK 21（脚本已内置 `JAVA_HOME` 指向 Zulu 21）；宿主机 3306 已被占用故 MySQL 映射 3307。
