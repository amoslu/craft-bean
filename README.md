# craft-bean 咖啡烘焙工坊管理系统

M1 后台内勤管理（生豆/烘焙/熟豆进销存）。技术栈：Spring Boot 3 + MySQL 8 + Vue3。
- 需求：`docs/requirements/`
- 技术设计：`docs/superpowers/specs/`
- 目录：`server/`(后端) · `admin-web/`(管理后台) · `deploy/`(部署编排) · `docs/`(文档)

## 本地开发（S0）
1. 数据库：`cd deploy && docker compose up -d mysql`（宿主机端口 3307）
2. 后端：`cd server && mvn spring-boot:run`（默认连 localhost:3307/craftbean）
3. 前端：`cd admin-web && npm install && npm run dev` → http://localhost:5173
   登录：`admin / admin123`
全量检查：`./scripts/check.sh`

> 本机环境注意：Maven 构建需 JDK 21（脚本已内置 `JAVA_HOME` 指向 Zulu 21）；宿主机 3306 已被占用故 MySQL 映射 3307。
