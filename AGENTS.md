# AGENTS.md

## 项目概览

这是一个 Java + Spring Boot + MyBatis Plus + MySQL 8 的后台管理系统，前端是 Vue 3 + Vite + TypeScript，代码分为两个主要子项目：

- `server/`：后端服务，Spring Boot 应用
- `admin-web/`：Vue 管理前端
- `deploy/`：Docker Compose / 部署配置
- `docs/`：需求、技术设计和项目文档
- `.superpowers/`：超能力/计划与 SDD 记录

## 关键约定

- 优先遵循现有目录和文档结构，不要自行重构仓库布局。
- 先查找 `docs/requirements/` 和 `docs/superpowers/specs/` 中的需求与设计，再开始实现。
- 仅在必要时修改代码；保持改动最小、可验证。
- 不要手动编辑或提交 `server/target/`、`admin-web/node_modules/`、构建产物或本地缓存。
- 若需要启动本地环境，优先使用仓库文档中的最小启动顺序。

## 本地开发命令

### 1) 启动数据库

```bash
cd deploy && docker compose up -d mysql
```

- 宿主机端口：`3307`
- 默认连接：`localhost:3307/craftbean`

### 2) 启动后端

```bash
cd server && mvn spring-boot:run
```

### 3) 启动前端

```bash
cd admin-web && npm install && npm run dev
```

- 前端地址：`http://localhost:5173`
- 默认登录：`admin / admin123`

### 4) 全量检查

```bash
./scripts/check.sh
```

## 重要环境说明

- README 中明确要求使用 JDK 21 进行 Maven 构建；库中脚本已内置对应 `JAVA_HOME`。
- MySQL 宿主机端口被占用，因此使用 `3307` 映射。
- 使用 `docs/superpowers/plans/` 里的里程碑计划时，按计划步骤执行，而不是直接跳过。

## 参考资料

- [README.md](README.md)
- [docs/requirements/mvp-requirements.md](docs/requirements/mvp-requirements.md)
- [docs/requirements/m1-backoffice-detail.md](docs/requirements/m1-backoffice-detail.md)
- [docs/superpowers/specs/2026-09-02-m1-backoffice-design.md](docs/superpowers/specs/2026-09-02-m1-backoffice-design.md)
- [docs/superpowers/plans/2026-09-02-s0-scaffold.md](docs/superpowers/plans/2026-09-02-s0-scaffold.md)

## 适合 AI 代理的工作方式

- 先阅读需求/docs，再判断是否需补充设计或实现细节。
- 优先利用现有脚本和文档，而不是发明新的流程。
- 每次都先确认目标文件与相关测试，再修改实现。
- 若任务涉及多阶段工作，可参考 `.superpowers/sdd/` 中的计划与任务记录。



## 节省上下文
- 读整个文件之前先 grep，只读需要的部分。
- 要了解整个项目，就派一个子代理去看，只把摘要带回来。
- 给我看 diff，不要给整个文件。
- 本次会话里已经定下的事，不要再重复。
- 切换任务时，建议用 /compact 或开新会话。
- 默认回答保持简短。