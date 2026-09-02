# S1 用户管理 + RBAC + 审计日志 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: 内联执行（executing-plans 风格，控制器在本会话直接实现）。

**Goal:** 在 S0 骨架上实现系统用户管理（列表/新增/编辑/停启用/重置密码/软删除，含防呆守卫）、登录失败锁定、操作审计日志（记录 + 查询）。

**Architecture:** 沿用 Spring Boot 3 + MyBatis-Plus + MySQL + JWT/RBAC。用户管理与审计接口仅 ADMIN 可写；审计查询 ADMIN 看全部、非 ADMIN 只看自己。登录锁定通过在 `sys_user` 加 `failed_attempts`/`locked_until` 两列实现。

**Tech Stack:** 同 S0（Java 17 / Spring Boot 3.3.5 / MyBatis-Plus 3.5.7 / Flyway / Vue3 + Element Plus）。

**Spec:** `docs/superpowers/specs/2026-09-02-m1-backoffice-design.md`（实现其「里程碑 S1」）；需求 `docs/requirements/m1-backoffice-detail.md` §1、§7。

## Global Constraints

- 代码位于 `/Users/lujun/projects/craft-bean`，分支 `s1-user-audit`。
- 本机构建需 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home`；集成测试直连本地 MySQL(localhost:3307/craftbean)；测试用 httpclient5 规避 POST+401 缺陷。以上均已固化在 `scripts/check.sh`。
- 统一响应 `{code,message,data}`；接口前缀 `/api/v1`；JWT Bearer；RBAC 用 `@PreAuthorize("hasRole('ADMIN')")`。
- 用户角色枚举 `ADMIN/STAFF/READONLY`；状态 `ACTIVE/DISABLED`。
- 审计查询：ADMIN 返回全部；STAFF/READONLY 仅返回 operator_id 为本人。
- 每个 Task 末尾 git commit，前缀 `feat|fix|chore|test`。

## 决策（相对 Spec 的澄清）

- 登录锁定字段未列入 Spec 数据模型，但需求要求「连续 5 次失败锁 15 分钟」→ Flyway V2 为 `sys_user` 增加 `failed_attempts INT NOT NULL DEFAULT 0` 与 `locked_until DATETIME NULL`。
- 审计查询权限：Spec §7 写「日志查询仅 ADMIN」，但需求 §7 写「管理员看全部、员工看自己」→ 采纳更细的需求：查询接口对已登录用户开放，ADMIN 看全部、非 ADMIN 看自己。

---

### Task 1: Flyway V2 迁移 + AuditLog 实体/Mapper

**Files:**
- Create: `server/src/main/resources/db/migration/V2__lockout_and_audit_log.sql`
- Create: `server/src/main/java/com/craftbean/audit/AuditLog.java`
- Create: `server/src/main/java/com/craftbean/audit/AuditLogMapper.java`
- Modify: `server/src/main/java/com/craftbean/system/SysUser.java`（加 failedAttempts、lockedUntil）

**Interfaces:**
- Produces: `audit_log` 表（id, operator_id, action, target_no, detail, created_at）；`sys_user.failed_attempts`/`locked_until`。
- Produces: `AuditLogMapper extends BaseMapper<AuditLog>`；`AuditLog` 实体字段 `id, operatorId, action, targetNo, detail, createdAt`。

- [ ] Step 1: 写 V2 迁移
- [ ] Step 2: SysUser 加字段
- [ ] Step 3: AuditLog 实体 + Mapper
- [ ] Step 4: 跑 `mvn test`（现有 7 测试仍绿，验证迁移不破坏）
- [ ] Step 5: commit

---

### Task 2: 用户管理后端

**Files:**
- Create: `server/src/main/java/com/craftbean/system/UserController.java`
- Create: `server/src/main/java/com/craftbean/system/UserService.java`
- Create: `server/src/main/java/com/craftbean/system/dto/CreateUserRequest.java`
- Create: `server/src/main/java/com/craftbean/system/dto/UpdateUserRequest.java`
- Create: `server/src/main/java/com/craftbean/system/dto/ResetPasswordRequest.java`
- Create: `server/src/main/java/com/craftbean/system/dto/UserResponse.java`
- Test: `server/src/test/java/com/craftbean/system/UserControllerIT.java`

**Interfaces:**
- Produces（均需 ADMIN）:
  - `GET /api/v1/system/users?keyword=&role=&status=&page=&size=` → 分页 `Result<PageResult<UserResponse>>`
  - `POST /api/v1/system/users` body `{username,name,password,role}` → 创建
  - `PUT /api/v1/system/users/{id}` body `{name,role,status}` → 更新
  - `POST /api/v1/system/users/{id}/reset-password` body `{newPassword}` → 重置密码
  - `DELETE /api/v1/system/users/{id}` → 软删除
- 守卫：不能改/删/停用自己；不能停用或删除最后一个 ACTIVE 的 ADMIN；用户名唯一。

- [ ] Step 1: 写 DTO
- [ ] Step 2: 写 UserService（含守卫逻辑）
- [ ] Step 3: 写 UserController（@PreAuthorize ADMIN）
- [ ] Step 4: 写 UserControllerIT（含守卫用例）
- [ ] Step 5: commit

---

### Task 3: 登录锁定 + AuditService + 记录

**Files:**
- Create: `server/src/main/java/com/craftbean/audit/AuditService.java`
- Modify: `server/src/main/java/com/craftbean/auth/AuthController.java`（锁定判断 + 失败计数 + 记录登录审计）
- Modify: `server/src/main/java/com/craftbean/system/UserService.java`（增删改/重置密码写审计）
- Test: `server/src/test/java/com/craftbean/auth/LockoutIT.java`

**Interfaces:**
- Produces: `AuditService.record(Long operatorId, String action, String targetNo, String detail)`。
- 登录规则：locked_until > now → 拒绝「账号已锁定，请 15 分钟后再试」；密码错 → failed_attempts+1，达 5 次置 locked_until=now+15min 并清零计数；成功 → 清零 + 更新 last_login_at。

- [ ] Step 1: 写 AuditService
- [ ] Step 2: 改 AuthController（锁定 + 记录登录审计）
- [ ] Step 3: 改 UserService 写审计
- [ ] Step 4: 写 LockoutIT
- [ ] Step 5: commit

---

### Task 4: 审计日志查询接口

**Files:**
- Create: `server/src/main/java/com/craftbean/audit/AuditController.java`
- Create: `server/src/main/java/com/craftbean/audit/dto/AuditLogResponse.java`
- Test: `server/src/test/java/com/craftbean/audit/AuditLogIT.java`

**Interfaces:**
- Produces: `GET /api/v1/audit/logs?operatorId=&page=&size=` → 分页；ADMIN 看全部，非 ADMIN 强制只看自己（忽略 operatorId 参数或校验为本人）。

- [ ] Step 1: 写响应 DTO
- [ ] Step 2: 写 AuditController
- [ ] Step 3: 写 AuditLogIT
- [ ] Step 4: commit

---

### Task 5: 前端用户管理页 + 审计日志页

**Files:**
- Create: `admin-web/src/api/users.ts`
- Create: `admin-web/src/api/audit.ts`
- Create: `admin-web/src/views/system/UserManageView.vue`
- Create: `admin-web/src/views/system/AuditLogView.vue`
- Modify: `admin-web/src/router/index.ts`（加 `/system/audit` 路由，指向 AuditLogView；`/system/users` 指向 UserManageView）

**Interfaces:**
- 用户管理页：表格（用户名/姓名/角色/状态/最近登录）、搜索（关键字/角色/状态）、新增/编辑弹窗、重置密码、启用/停用、删除（确认）。
- 审计日志页：表格（操作人/动作/对象/详情/时间）。

- [ ] Step 1: 写 api/users.ts、api/audit.ts
- [ ] Step 2: 写 UserManageView
- [ ] Step 3: 写 AuditLogView
- [ ] Step 4: 改路由
- [ ] Step 5: `npm run build` 通过 + commit

---

### Task 6: 全量检查 + 端到端冒烟

- [ ] Step 1: `./scripts/check.sh` 全绿
- [ ] Step 2: 启动后端，curl 验证：登录 → 建用户 → 停用 → 登录被拒 → 审计可查
- [ ] Step 3: 记录结果（无代码变更可不提交）

---

## Self-Review 记录

- Spec 覆盖：S1 对应 Spec §6.5(审计)/§7(RBAC)/§8(审计) 与需求 §1/§7。
- 占位符扫描：无 TBD。
- 类型一致性：`Result.ok/fail`、`SysUser` 字段沿用 S0；`UserResponse` 与前端类型对应。
