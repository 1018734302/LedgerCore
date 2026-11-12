# 衡账 LedgerCore — 电商商户结算与分账系统 · 技术规格说明书

| 项 | 内容 |
|---|---|
| 版本 | v0.1（待评审） |
| 日期 | 2026-11-12 |
| 技术栈 | Java / Spring Boot / MySQL |
| 状态 | **待用户确认**，确认后进入实现 |
| 目标读者 | 项目所有者（求职简历第三条项目） |

> 本文档只定义"做什么、怎么做、做到什么程度"，不含实现代码。第 13 节列出需要你拍板的决策点。

---

## 1. 项目定位与目标

### 1.1 一句话定位

模拟电商/支付机构的**收单 → 记账 → 分账 → 结算 → 对账 → 差错闭环**全链路，核心目标不是功能数量，而是**在任何异常下资金账目都不出错、且错了能定位到具体分录**。

- 内核名：`LedgerCore`（账务内核）
- 业务壳：**电商商户结算与分账系统**（避免空泛的"平台"表述，让面试官能追问到具体角色）
- 简历定位：**金融核心链路的领域建模与资金一致性**
  - 与现有两条项目形成递进：交付视角（CloudExplorer）→ 产品视角（UniHub）→ **领域深度与资金安全**

### 1.2 业务角色

| 角色 | 说明 |
|---|---|
| 买家 | 发起支付、发起退款 |
| 商户 | 应收主体，T+1 结算收款 |
| 平台 | 按佣金率抽佣，持有佣金账户 |
| 渠道 | 微信 / 支付宝 **Mock**，产出扣款流水与日终对账文件 |
| 运营 | 触发日切、结算、查看差异、处理差错工单 |

### 1.3 端到端链路

```
下单 100.00 → 渠道扣款 → 回调通知 → 幂等校验 → 生成记账凭证
→ 分账（商户应收 99.00 / 平台佣金 1.00）→ 日切余额快照
→ 与渠道账单核对 → 生成结算批次 → T+1 轧差打款（Mock）
→ 次日退款 30.00 → 生成反向冲正凭证 → 冲减商户应收 → 差异进差错工单
```

### 1.4 明确的非目标（Out of Scope）

- 不接真实支付渠道、不碰真实资金与真实账户数据（全 Mock + 造数）
- 不实现完整银行核心（客户信息、存款产品、总账会计科目体系）
- 不做等保 / PCI-DSS 等合规认证
- 不引入微服务拆分、注册中心、分布式事务框架（单机单体，见 §4.3 权衡）
- 不做多币种、跨境结算

### 1.5 成功标准（可量化，写进简历的数据来源）

| 指标 | 目标 |
|---|---|
| 造数规模 | 100 万笔支付流水 + 对应分录 |
| 日终对账 | 全量核对差异 **0 笔**，耗时记录为可复现数据 |
| 幂等 | 同一幂等键重放 1000 次，余额不变、分录不增 |
| 并发 | 100 线程并发支付/退款，无超扣、无重复分录 |
| 借贷平衡 | 断言覆盖 100% 凭证 |
| 可追溯 | 任意金额可反查 订单 → 凭证 → 分录 → 结算/冲正 |
| 演示 | 5 分钟内在页面上跑通完整链路，并人为制造差异后闭环 |

---

## 2. 领域模型

### 2.1 核心概念

| 概念 | 说明 |
|---|---|
| `Account` 账户 | 渠道待清算户、商户应收款户、平台佣金户、买家户、备付金户 |
| `Voucher` 记账凭证 | 一次业务动作的记账单元，含多条分录，**提交前必须借贷平衡** |
| `AccountEntry` 分录 | 账本最小单位，**只追加（append-only）**，禁止 UPDATE / DELETE |
| `PaymentOrder` 支付订单 | 买家发起的一笔支付 |
| `ChannelTxn` 渠道流水 | Mock 渠道返回的扣款结果 |
| `SplitDetail` 分账明细 | 订单金额按佣金率拆给商户与平台 |
| `RefundOrder` 退款单 | 关联原订单，通过**冲正凭证**实现，不改历史分录 |
| `SettlementBatch/Item` 结算批次 | T+1 按商户轧差汇总 |
| `ChannelBill` 渠道账单 | Mock 生成的对账文件解析结果 |
| `ReconResult` / `DiffTicket` | 核对结果 / 差错工单 |
| `LocalMessage` 本地消息表 | 记账与后续异步动作的一致性保障 |

### 2.2 记账规则（内核最重要约束）

1. 每个 `Voucher` 满足 `Σ借方金额 == Σ贷方金额`，不平衡直接拒绝提交。
2. 金额统一以**分为单位的 `long`** 存储；佣金率、利率等比率计算用 `BigDecimal`，**全项目禁止 `double`**。
3. 采用简化的"账户视角"方向约定：`balanceDelta = (direction == DR) ? +amount : -amount`。
   > 说明：真实会计需按科目性质（资产/负债/权益）区分借贷方向；本项目在 `Account.type` 上固定方向系数，并在文档与代码注释中显式标注这是简化模型，避免口径歧义。
4. 分录**只追加**。任何更正（退款、撤销、差错调整）一律生成**反向凭证**，并用 `original_voucher_no` 关联。
5. 分录冗余记录 `before_balance_cent` / `after_balance_cent`，用于逐笔回溯与总分核对。
6. 幂等由数据库唯一索引兜底：`UNIQUE(biz_no, idempotency_key)`，不依赖应用层判断。

### 2.3 状态机（只允许单向流转）

```
PaymentOrder : INIT → PAYING → SUCCESS | FAILED | CLOSED
RefundOrder  : INIT → PROCESSING → SUCCESS | FAILED
Settlement   : PENDING → PROCESSING → SETTLED | FAILED
DiffTicket   : OPEN → PROCESSING → RESOLVED | REJECTED
```

> 约束：禁止 `SUCCESS` 回退到 `INIT`；迟到的失败回调不得覆盖成功状态，只能落审计日志并触发人工核查。

---

## 3. 数据库设计（MySQL 8.0 / InnoDB）

金额字段一律 `BIGINT`（单位：分）；事务隔离级别 `REPEATABLE READ`；所有表带 `created_at` / `updated_at`。

| 表 | 关键字段 | 关键约束 / 索引 |
|---|---|---|
| `account` | `id`, `account_no`, `type`, `balance_cent`, `frozen_cent`, `currency`, `status`, `version` | `uk_account_no`；`balance_cent >= 0` 校验 |
| `voucher` | `voucher_no`, `biz_type`, `biz_no`, `total_cent`, `status`, `original_voucher_no` | `uk_voucher_no`；`idx_biz_no` |
| `account_entry` | `id`, `voucher_no`, `account_no`, `direction(DR/CR)`, `amount_cent`, `biz_no`, `idempotency_key`, `before_balance_cent`, `after_balance_cent` | **`uk_biz_idem(biz_no, idempotency_key)`**；`idx_account_time`；无更新/删除权限 |
| `payment_order` | `order_no`, `buyer_no`, `merchant_no`, `amount_cent`, `channel`, `status`, `channel_txn_no`, `idempotency_key`, `version` | `uk_order_no`, `uk_channel_txn_no`, `uk_idempotency_key` |
| `channel_txn` | `channel_txn_no`, `channel`, `amount_cent`, `status`, `notify_at` | `uk_channel_txn_no` |
| `split_detail` | `order_no`, `account_no`, `amount_cent`, `fee_rate` | `idx_order_no` |
| `refund_order` | `refund_no`, `original_order_no`, `amount_cent`, `reversal_voucher_no`, `status` | `uk_refund_no`；`amount <= 原单可退金额` |
| `settlement_batch` / `settlement_item` | `batch_no`, `merchant_no`, `settle_date`, `gross_cent`, `refund_cent`, `fee_cent`, `net_cent`, `status` | `uk_batch_merchant_date` |
| `channel_bill` | `bill_date`, `channel_txn_no`, `amount_cent`, `status`, `file_name` | `uk_bill_date_txn` |
| `recon_result` / `diff_ticket` | `recon_no`, `diff_type`, `biz_no`, `expected_cent`, `actual_cent`, `status`, `handler` | `idx_diff_type_status` |
| `local_message` | `msg_no`, `biz_no`, `topic`, `payload`, `retry_count`, `next_retry_at`, `status` | `idx_status_retry` |
| `audit_log` | `trace_no`, `actor`, `action`, `target`, `payload`, `created_at` | `idx_trace_no` |

**差异分类枚举**：`LONG`（渠道有内部无，长款）/ `SHORT`（内部有渠道无，短款）/ `AMOUNT_MISMATCH` / `STATUS_MISMATCH` / `DUPLICATE`。

---

## 4. Java 技术栈

### 4.1 选型清单

| 层 | 选型 | 理由 |
|---|---|---|
| JDK | **17 LTS**（可平滑升 21） | LTS、生态稳定 |
| 框架 | **Spring Boot 3.3.x** | 主流基线，版本可在实现时按当时 LTS 微调 |
| 构建 | Maven 多模块 | 领域层可独立测试 |
| 持久层 | **MyBatis-Plus 3.5.x** | SQL 可控，便于写 `SELECT ... FOR UPDATE` 与批量分录 |
| 数据库 | **MySQL 8.0 / InnoDB** | 行锁 + RR + 唯一索引实现幂等 |
| 连接池 | HikariCP | Spring Boot 默认 |
| 事务 | Spring `@Transactional` + 声明式事务边界 | 记账与分录同事务 |
| 异步一致性 | **本地消息表 + `@Scheduled` 补偿任务** | 见 §4.3 |
| 并发控制 | DB 行锁 + `version` 乐观锁（CAS） | 不引入分布式锁 |
| 定时任务 | Spring Scheduler（可选 ShedLock 防重） | 日切、对账、结算 |
| 规则计算 | 策略模式 + 自实现表达式解析（可选 Aviator） | 风控规则热更新 |
| 金额 | `long`（分）+ `BigDecimal`（比率） | 禁止浮点 |
| API | REST + springdoc-openapi（Swagger UI） | 接口自描述 |
| 校验 | Jakarta Validation | 入参约束 |
| 测试 | **JUnit 5 + Testcontainers(MySQL) + MockMvc**，`CountDownLatch` 并发用例 | 可复现的一致性验证 |
| 可观测 | Actuator + Micrometer + MDC（`traceNo` / `bizNo` / `idempotencyKey` 全链路贯穿） | 追溯与排障 |
| 演示页 | **Thymeleaf + Bootstrap + 原生 JS**（默认，零 Node 依赖） | 一条命令启动即可演示 |
| 部署 | Docker Compose（app + MySQL） + 造数脚本 | 面试环境可复现 |

### 4.2 Maven 模块划分

```
ledgercore/
├── common/          统一响应、错误码、金额工具、幂等、MDC、异常体系
├── domain/          领域模型 / 状态机 / 借贷平衡规则（纯 Java，不依赖 Spring）
├── infrastructure/  Mapper、事务、本地消息表、渠道 Mock、定时任务、账单解析
├── application/     用例编排：支付、退款、记账、结算、对账、差错闭环
├── interfaces/      REST Controller、演示页、OpenAPI 配置
└── bootstrap/       启动类、配置、DDL、造数与压测脚本
```

> **架构要点**：`domain` 为纯 POJO、零框架依赖，借贷平衡与状态机规则可脱离数据库做单元测试。依赖方向单向向内，`domain` 不依赖任何外层模块。

### 4.3 刻意不做的技术选型（架构权衡，面试重点）

| 没上 | 原因 | 扩展路径 |
|---|---|---|
| MQ（RocketMQ/Kafka） | MVP 单机下本地消息表已能保证"记账成功必有后续动作"，引入 MQ 反而新增一个不一致源 | 交易消息改 RocketMQ 事务消息 |
| Redis / 分布式锁 | 单实例部署，DB 行锁 + 乐观锁足够，且幂等最终由唯一索引兜底 | 多实例后用 Redisson，但唯一索引仍是最后防线 |
| 微服务 / 注册中心 | 拆分会把"一个事务内写完分录"变成分布式事务，与本项目要证明的资金一致性主题相悖 | 按读写分离/账务独立部署演进 |
| Drools | 规则量小，策略模式更可控、更好讲 | 规则规模上来后换 Aviator/Drools |

---

## 5. 核心流程设计

### 5.1 支付回调入账（主链路）

```
1. 渠道 Mock 回调 POST /api/channels/{channel}/notify
2. 校验签名（Mock 实现 HMAC）+ 解析幂等键
3. 唯一索引预检：uk_biz_idem 命中 → 直接返回原结果（幂等）
4. 开启事务：
   a. CAS 更新 payment_order 状态（version 条件），非 INIT/PAYING 直接拒绝
   b. 构造 Voucher，校验 ΣDR == ΣCR
   c. 批量 INSERT 分录（含 before/after 余额快照）
   d. SELECT ... FOR UPDATE 锁定相关账户，更新 balance_cent
   e. 写 split_detail（商户应收 / 平台佣金）
   f. 写 local_message（待触发分账通知）
   g. 写 audit_log（traceNo 贯穿）
5. 提交 → 异步任务消费 local_message
```

事务边界：**订单状态 + 凭证 + 分录 + 余额 + 本地消息必须在同一个本地事务内**，这是全系统最关键的一条设计。

### 5.2 退款与跨日切冲正

退款不修改原分录：生成 `original_voucher_no` 指向原凭证的反向凭证（借贷互换、金额相等），冲减商户应收与平台佣金；若跨日切，冲正计入**发生日**，并在结算批次中以 `refund_cent` 轧差，不追溯改动历史日期的报表。

### 5.3 日切、总分核对与渠道对账

```
日切冻结 → 生成账户余额快照 + checksum
  ├─ 单账户核对：balance_cent == 期初 + Σ(该账户分录 delta)
  ├─ 全局核对：Σ全库借方 == Σ全库贷方（逐凭证 + 汇总双校验）
  └─ 渠道核对：channel_txn ⟷ channel_bill 按 channel_txn_no 双向全外连接
        → 差异落 recon_result 并按 §3 枚举分类
        → 自动生成 diff_ticket（OPEN）→ 运营处理 → RESOLVED
```

### 5.4 T+1 结算

按商户汇总当日 `SUCCESS` 分账 → 轧差（减退款、减手续费）→ 生成 `settlement_batch/item` → 打款 Mock → 生成结算凭证 → 状态 `SETTLED`。重复触发由 `uk_batch_merchant_date` 兜底。

### 5.5 补偿机制

`local_message` 定时重投，指数退避、最大重试次数；超限置 `DEAD`，自动生成人工差错工单。所有重试带同一 `biz_no`，消费端幂等。

### 5.6 失败矩阵（面试直接可讲）

| 异常场景 | 兜底手段 |
|---|---|
| 渠道回调重复 | `uk_biz_idem` 唯一索引 + 返回原结果 |
| 回调乱序（失败通知晚到） | 状态机单向流转，晚到失败不覆盖 SUCCESS |
| 回调丢失 | 主动查单补偿任务 + 日终渠道对账捞回 |
| 事务中途失败 | 单事务原子回滚，不留半条分录 |
| 并发扣同一账户 | 行锁 + `version` CAS，无超扣 |
| 日切过程中有在途交易 | 日切按 `created_at` 划界，在途单进次日批次并标记 |
| 渠道账单缺失/延迟 | 对账任务标记 `BILL_MISSING`，不生成错误结论，转人工工单 |
| 金额算错/精度丢失 | 全链路 `long` 分，比率用 `BigDecimal` 且统一舍入策略 |

---

## 6. 风控规则模块（M3 亮点，可选）

单笔限额、单日累计限额、短时频次、买家/商户黑名单、佣金率合法性校验；规则配置存库、支持运行时热更新；命中即拦截并写 `audit_log`（含规则版本快照）。实现方式为策略模式 + 责任链，规则表达式可选 Aviator。

> 这一块是让"业务抽象 / 能力沉淀"的产品视角在金融项目里也有落点，避免项目被讲成纯后端CRUD。

---

## 7. REST API 清单

统一响应体 `{code, message, data, traceNo}`；幂等键通过请求头 `Idempotency-Key` 传递。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/payments` | 创建支付单 |
| POST | `/api/channels/{channel}/notify` | 渠道回调（Mock） |
| GET | `/api/payments/{orderNo}` | 订单详情与状态 |
| POST | `/api/refunds` | 发起退款（冲正） |
| GET | `/api/accounts/{accountNo}` | 余额与分录流水 |
| GET | `/api/vouchers/{voucherNo}` | 凭证追溯（含关联冲正） |
| POST | `/api/settlements/batches` | 触发 T+1 结算 |
| POST | `/api/recon/run` | 触发日终对账（可指定日期） |
| GET | `/api/recon/diffs` | 差异列表（按类型筛选） |
| POST | `/api/diff-tickets/{id}/resolve` | 差错工单闭环 |
| POST | `/api/admin/seed` | 造数（可指定笔数） |
| GET | `/` | 演示页（链路、流水、差异、规则配置） |

演示页至少 4 个视图：**全链路操作台**（一键下单/回调/退款）、**账户流水**、**对账差异与工单**、**规则配置**，并支持"一键注入异常"（重复回调 / 制造金额差异）用于现场演示。

---

## 8. 非功能需求

- **一致性**：任何时刻 `ΣDR == ΣCR`；账户余额可由分录完整重算
- **可追溯**：MDC 贯穿 `traceNo / bizNo / idempotencyKey`，日志与 `audit_log` 双向可查
- **幂等**：所有写接口必须支持幂等键，数据库唯一索引为最终防线
- **安全**：不存任何真实数据；Mock 渠道回调做 HMAC 签名验签；演示页仅本地访问
- **可复现**：`docker compose up` + 一键造数脚本，全流程可在干净环境重跑
- **测试**：`domain` 模块行覆盖率 ≥ 85%；一致性用例必须包含并发、重放、乱序、部分失败

---

## 9. 迭代计划（约 3 周）

| 里程碑 | 交付内容 | 验收 |
|---|---|---|
| **M1 记账内核** | 多模块骨架、DDL、账户/凭证/分录、借贷平衡、幂等、行锁+CAS、并发单测 | 100 线程并发无超扣；重放 1000 次分录不增 |
| **M2 支付链路** | 支付订单、渠道 Mock、回调状态机、分账、退款冲正、本地消息表与补偿 | 失败矩阵 8 个场景全部有用例覆盖 |
| **M3 结算对账与演示** | 日切快照、总分核对、渠道账单核对、差错工单、结算批次、风控规则、演示页、造数/压测报告、README | 100 万笔对账差异 0；5 分钟现场演示闭环 |

每个里程碑结束产出一份可复现的验证数据，直接作为简历与面试素材。

---

## 10. 面试叙事映射

| 讲点 | 对应实现 | 一句话讲法 |
|---|---|---|
| 资金不错账 | 同事务写凭证+分录+余额、借贷平衡、append-only | "我把'钱不能错'拆成三条硬约束：平衡、幂等、只追加" |
| 回调不可靠 | 状态机单向 + 唯一索引 + 补偿 + 对账捞回 | "网络不可靠是前提，不是异常，所以每层的兜底都不依赖上一层成功" |
| 可追溯与闭环 | 余额快照、traceNo 贯穿、差异工单 | "错了不怕，怕的是查不到和没人闭环" |
| 架构权衡 | §4.3 刻意不上的组件 | "我没上 MQ/Redis，不是不会，是单机 MVP 下它们会引入新的一致性问题" |
| 领域抽象 | 纯 Java `domain` 层 | "记账规则脱离框架可测，业务规则不被技术细节污染" |

---

## 11. 风险与边界

- 简化借贷方向模型需显式标注，避免被追问会计口径时失分
- 单机性能数据必须如实标注环境（机器配置、数据量），禁止写"百万 QPS"
- 渠道对账文件为 Mock 生成，需在 README 说明格式与真实渠道账单的差异
- 演示页若选 Vue3 会引入 Node 构建链，与"一条命令启动"目标冲突（默认 Thymeleaf）

---

## 12. 交付物清单

1. 可运行的多模块 Maven 工程（含 Docker Compose、DDL、造数脚本）
2. Swagger UI + 演示页
3. 测试报告（一致性/并发/幂等用例）
4. 100 万笔造数的对账性能报告
5. `README.md`：启动方式、业务链路、失败矩阵、面试讲法、扩展路径

---

## 13. 待确认决策点（请拍板）

| # | 决策项 | 默认建议 | 备选 |
|---|---|---|---|
| 1 | 业务壳 | 电商商户结算与分账 | 支付机构日终对账 / 信贷还款计息 |
| 2 | 演示页 | Thymeleaf 单页（零 Node 依赖） | Vue3 + Vite 独立前端 |
| 3 | 中间件 | 不引入 Redis / MQ / Drools，仅保留扩展说明 | 引入 Redis + RocketMQ 增强亮点 |
| 4 | 数据库 | MySQL 8.0（Testcontainers） | 追加 H2 profile 便于零依赖启动 |
| 5 | 造数规模 | 100 万笔流水 | 10 万笔（更省时间） |
| 6 | 交付节奏 | 按 M1 → M2 → M3 分批，每批可评审 | 一次性交付完整实现 |
| 7 | 简历数据口径 | 如实记录本机实测结果 | 不写性能数字，只讲机制 |

> 确认后我会在 `c:/Users/xiezhenyu/CodeBuddy/jinrong` 初始化工程，先交付 M1（记账内核 + 一致性测试）。
