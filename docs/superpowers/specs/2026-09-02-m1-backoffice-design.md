# craft-bean M1 后台内勤管理系统 · 技术设计

- 日期：2026-09-02
- 状态：待评审
- 上游需求：[mvp-requirements.md](../../requirements/mvp-requirements.md) · [m1-backoffice-detail.md](../../requirements/m1-backoffice-detail.md)

---

## 1. 设计目标与范围

M1 把工坊内勤数字化：系统用户、供货商、生豆采购入库、烘焙批次、熟豆进销存、客户档案、操作日志。
范围、角色、字段级需求以两份需求文档为准；本文件只定**技术实现方案**。

**成功标准**：库存账实一致、批次全程可追溯、任何出库不可能出现负库存、关键操作留痕。

## 2. 技术栈决策

| 层 | 选型 | 理由 |
|---|---|---|
| 后端 | Java 17 · Spring Boot 3 · Maven | 用户选定；生态成熟、易招人 |
| 持久层 | MyBatis-Plus + MySQL 8 | 业务 SQL 可控，便于库存 `FOR UPDATE` 行锁 |
| 认证鉴权 | Spring Security + JWT + RBAC | 三角色（管理员/员工/只读）够用且标准 |
| 迁移 | Flyway | 表结构变更版本化，账务表改结构安全 |
| 管理后台 | Vue 3 + Vite + Element Plus + Pinia + Axios | 用户选定；国内后台主流 |
| 部署 | Docker Compose · Nginx · HTTPS（国内云） | 单机起步省成本；国内才可 ICP 备案接小程序 |

**数据库约定**：字符集 `utf8mb4`；重量 `kg` 用 `DECIMAL(10,3)`；金额用 `DECIMAL(10,2)`；时间统一存 `DATETIME`（北京时间）；所有表含 `id BIGINT AUTO_INCREMENT`、`created_at`、`updated_at`；软删除字段 `deleted`。

## 3. 总体架构与部署

```
员工浏览器 ─HTTPS─▶ Nginx ─/──▶ admin-web 静态资源(Vue)
                          └/api──▶ Spring Boot(:8080)
                                        │
                                    MySQL 8 (:3306)
（M2 小程序：同域 HTTPS 调 /api，走同一套 JWT/接口）
```

- 一台腾讯云/阿里云轻量服务器，Docker Compose 编排：`nginx` + `server` + `mysql`。
- 数据备份：宿主机 cron 定时 `mysqldump`，保留最近 N 天。
- 上线前置（M1 上线时）：域名 + ICP 备案 + HTTPS 证书；本地开发不受此限制。

## 4. 单仓库结构

```
craft-bean/
├── docs/requirements/          # 需求文档（已提交）
├── docs/superpowers/specs/     # 技术设计（本文件）
├── server/                     # Spring Boot 3
│   └── src/main/java/com/craftbean/{config,common,auth,system,archive,greenbean,roast,stock,audit}
├── admin-web/                  # Vue3 + Element Plus
├── miniprogram/                # (M2) 预留
└── deploy/                     # docker-compose.yml、nginx.conf、backup.sh、.env.example
```

后端按业务分包：`auth`（登录）、`system`（用户）、`archive`（供货商/客户/品种/商品档案）、`greenbean`（采购入库）、`roast`（烘焙）、`stock`（熟豆出入库/看板）、`audit`（操作日志）。包内统一 Controller / Service / Mapper / DTO。

## 5. 数据模型（核心表）

> 库存双写原则：**批次剩余量字段 + 不可变流水**。同一事务内完成。

| 表 | 关键字段 | 说明 |
|---|---|---|
| `sys_user` | username(唯一) · password_hash · name · role(ADMIN/STAFF/READONLY) · status · last_login_at | 系统用户 |
| `supplier` | name · contact_person · phone · address · status | 供货商 |
| `customer` | name · type(WHOLESALE/RETAIL) · contact · phone · address · status | 客户档案 |
| `green_bean` | name · origin · process · variety · default_supplier_id · status | 生豆品种档案 |
| `green_lot` | lot_no(唯一) · green_bean_id · supplier_id · purchase_date · **total_kg** · **remaining_kg** · cost_price · amount · status | 生豆采购批=生豆库存批 |
| `roasted_product` | name · green_bean_id · default_roast_level · price · alert_threshold_kg · status | 熟豆商品 |
| `roast_batch` | batch_no(唯一) · roast_date · roaster_id · equipment · product_id · roast_level · total_in_kg · output_kg · **remaining_kg** · first_crack_at · out_temp_c · duration_min · status(草稿/待质检/可售/已作废) · remark | 烘焙批次=熟豆库存批 |
| `roast_batch_ingredient` | roast_batch_id · green_lot_id · qty_kg | 烘焙用料明细（驱动生豆扣减） |
| `stock_doc` | doc_no(唯一) · doc_type(IN/OUT_SALE/OUT_SAMPLE/OUT_WASTE/ADJUST) · customer_id · operator_id · total_qty · remark · status | 出入库单头 |
| `stock_doc_line` | stock_doc_id · batch_kind(GREEN/ROASTED) · batch_id · qty_kg | 单明细（出库=FIFO 分配结果，可人工改） |
| `inventory_movement` | biz_type · batch_kind · batch_id · green_bean_id/roasted_product_id · **change_kg(入库+ / 出库−)** · ref_doc_no · operator_id · customer_id · remark · created_at | **库存流水，只增不改** |
| `audit_log` | operator_id · action · target_no · detail · created_at | 操作日志 |

**金额/成本字段**：`green_lot.cost_price` 为采购单价；M1 不计算毛利，仅存档供日后核算。

## 6. 关键业务逻辑与事务边界

### 6.1 采购入库（greenbean）
1. 校验供货商/品种存在 → 生成 `green_lot`，`remaining_kg = total_kg` → 写 `inventory_movement`(+)。
   单一事务；失败整体回滚。
   （采购入库走**独立表单**直接建生豆批次，不经 `stock_doc`；`stock_doc` 用于熟豆手动出入库与盘点调整。）

### 6.2 烘焙出炉（roast）——最关键的联动
状态机：`草稿 → 待质检 → 可售`；任何状态下可 `作废`（仅 ADMIN，需填原因）。
出炉登记「合格」的事务内顺序：
1. 逐行读用料 `green_lot` **`FOR UPDATE`** 校验 `remaining_kg >= qty_kg`；
2. 扣减生豆批次剩余量，写生豆出库流水（−）；
3. 创建/更新 `roast_batch`（status=可售，`remaining_kg = output_kg`）；
4. 写熟豆入库流水（+）。
任一用料批不够 → **整单回滚**，不留半账。
「不合格」→ 熟豆按废料出库（不进入可售）。
`作废` 已入库批次 = 反向冲销：恢复/创建冲销流水并还原剩余量，ADMIN 二次确认。

### 6.3 熟豆出库（stock）——FIFO 与防超卖
1. `stock_doc` 出库单，类型=销售时 `customer_id` 必填；
2. 事务内：对该商品下所有 `remaining_kg>0` 的 `roast_batch` **`FOR UPDATE` 行锁**，按 `roast_date ASC, id ASC` 升序分配数量 → 生成 `stock_doc_line`（可保存前人工改为指定批次）；
3. 校验总数不超可扣总量，否则报 `STOCK_INSUFFICIENT`；
4. 逐批扣 `remaining_kg`，逐行写 `inventory_movement`(−)。
盘点调整走独立出入库类型，同样留流水。

### 6.4 并发正确性
- 库存相关所有写操作在同一 DB 事务内对批次行加 `FOR UPDATE`，MySQL InnoDB 保证串行化临界区，杜绝并发超卖。
- `doc_no` / `lot_no` / `batch_no` 数据库唯一键兜底；作废操作幂等（状态检查）。

### 6.5 追溯
任意熟豆批次 → 反向查用料 → 生豆批次 → 采购单/供货商；任意流水可按 `ref_doc_no` 回到单据。

## 7. 接口与安全

- REST 风格，统一前缀 `/api/v1`；统一响应 `{code, message, data}`；分页统一 `page/size`。
- 登录 `POST /auth/login` → 返回 JWT（access token）；`/auth/me`、`/auth/password`。
- Spring Security：无 token 一律 401；按 `ROLE_ADMIN/STAFF/READONLY` 用 `@PreAuthorize` 控制；用户管理、作废/冲销、日志查询仅 ADMIN。
- 业务错误码示例：`STOCK_INSUFFICIENT`、`BATCH_ALREADY_VOIDED`、`USER_DISABLED`；全局异常处理器统一转 JSON。
- 只读角色后端即拦截写接口（前端只隐藏按钮不够）。

## 8. 错误处理与审计

- 业务异常（非 500）给出可读 message；系统异常记日志并返回通用错误。
- `audit_log` 记录：登录、用户增改/重置密码、采购入库、烘焙登记/出炉/作废、出入库、盘点调整、冲销（人、时间、动作、单号、关键前后值）。
- 员工仅可查自己的操作日志；ADMIN 可查全部。

## 9. 测试策略

| 层 | 内容 |
|---|---|
| 单元 | FIFO 分配算法、失重率/金额计算、状态机流转 |
| 集成 | 采购入库、烘焙出炉全链路（含「用料不足整单回滚」）、出库防负、作废冲销；用 Testcontainers 跑 MySQL |
| 并发 | 两个并发出库单不超卖（关键验收场景） |
| 前端 | 核心流程组件测试；样式以人工验收为主 |
| 验收 | 对照 m1-backoffice-detail 的用户场景逐条过 |

## 10. 里程碑（实施顺序）

- **S0 工程脚手架**：monorepo 目录、Spring Boot + Flyway 骨架、MySQL compose、Vue3 壳、登录登出跑通、CI 本地脚本。
- **S1 用户与审计**：`sys_user` CRUD + RBAC + `audit_log` 骨架。
- **S2 基础档案**：供货商 / 客户 / 生豆品种 / 熟豆商品。
- **S3 生豆采购**：采购入库 + 生豆库存/流水/报废。
- **S4 烘焙批次**：草稿→出炉→质检→可售 + 用料联动 + 作废冲销（本里程碑验收「联动与回滚」）。
- **S5 熟豆库存**：库存看板 + FIFO 出库（可改批次）+ 预警 + 盘点调整 + 工作台待办。
- **S6 收尾上线**：操作日志查询完善、部署（备案/HTTPS/备份）、文档、冒烟。

每个里程碑独立可验收，S0–S6 顺序执行，S4 为技术风险最高点优先充分测试。

## 11. 明确不做（M1）

小程序/支付（M2）、批发自助（M3）、报表分析、温度曲线图表、多租户隔离实现（仅预留 `org_id` 设计位）、烘焙机对接。

## 12. 风险与开放项

- 备案周期（若上线国内需提前）；可由「先本地/测试环境联调，后备案上线」缓解。
- 烘焙记录是否接真实曲线数据暂缓，M1 结构化作缓冲。
- `org_id` 预留列 M1 是否落库？→ 建议 S0 建表即带 `org_id`（默认 1），避免后期迁移成本。
