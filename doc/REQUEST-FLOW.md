# 实时拦截请求链路与引擎内部处理逻辑

> **文档状态（2026-07-22）**：本文的原始流程图保留作演进说明。当前正式入口为 `POST /api/v1/risk/evaluations`；Decision 与 Outbox 同事务提交，Kafka 由 relay 有界重试发布 `transaction.v1` / `decision.v1`，不再使用静默失败的 `TransactionPublisher`。

> 本文记录**架构 B / 实时拦截**这条同步链路：银行侧请求如何进来、`fraud-gateway` 与 `fraud-engine` 内部如何一步步把一笔交易变成风险决策，以及决策返回之后的 Kafka 异步扇出。
> 相关文档：两套架构对比见 [ARCHITECTURE-COMPARISON.md](ARCHITECTURE-COMPARISON.md)，双轨决策与模型理论见 [RANDOM-FOREST-THEORY.md](RANDOM-FOREST-THEORY.md)，能力清单见 [CAPABILITIES.md](CAPABILITIES.md)。

## 0. 一句话回答：请求是直连接口，不走 Kafka

银行侧是**同步 HTTP 调用** `POST /risk/evaluate`，在一次请求-响应内拿到放行/拦截决策。**Kafka 不是交易入口**，它在决策返回之后才出场，是异步的数据闭环（回写特征 + 落库），不在关键路径上。

```
银行 ──HTTP同步──▶ fraud-gateway ──▶ 拉特征 ──▶ fraud-engine(规则+模型) ──▶ 同步返回决策给银行
                                                                            │
                                                            (返回之后) 异步发 Kafka txn-events
                                                                            ├─▶ profiling-realtime → 算窗口特征 → 回写 Redis feature:{账号}
                                                                            └─▶ Logstash → 脱敏 → 写 ES txn-YYYY.MM.dd
```

## 1. 入口：RiskController.evaluate（同步链路骨架）

`fraud-gateway/.../RiskController.java`，端口 8082。一次请求按固定 4 步走，引擎评估前把所有 IO（拉特征）做完，引擎内零 IO：

| 步骤 | 动作 | 代码 | 说明 |
|------|------|------|------|
| 1 | 预取特征 | `featureClient.fetch(accountNo)` | 按账号一次取齐 Redis 特征；Redis 不可用降级为**空快照**，链路继续 |
| 2 | 组装交易事件 | `new TransactionEvent()` | 把 `RiskRequest` 字段拷进引擎入参 Fact |
| 3 | 规则引擎评估 | `engine.evaluate(txn, features)` | 进 Drools，详见 §3 |
| 4 | **先返回**，再异步发 Kafka | `publisher.publishAsync(...)` | Kafka 在 `return` 之前调用但 fire-and-forget，失败不影响返回 |

返回体 `RiskResponse`：`level` / `action` / `fraudScore` / `hitRules`（命中规则码列表）/ `costMs`（决策耗时）。

### 1.1 入参 RiskRequest（银行侧传入）

| 字段 | 含义 | 备注 |
|------|------|------|
| `sourceId` | 交易来源/接入方 | 决定激活哪套规则集（§3.2） |
| `channel` | 渠道 MOBILE / WEB | 决策表分档维度（§3.4） |
| `bizType` | 业务类型 TRANSFER / REMITTANCE | 规则条件 |
| `accountNo` | 账号 | **特征查询主键**，也是 Kafka 分区 key |
| `counterpartyAccount` | 对手方账号 | 模型 `cross_bank` 特征来源 |
| `amount` | 金额，**单位：分** | 规则用分，模型推理时换算成元 |
| `deviceId` / `ip` | 设备指纹 / 来源 IP | |

> 口径坑：`amount` 全链路存"分"。规则 DRL 直接比分（`amount > 5000000` = 5 万元），而 `ModelScorer` 推理时 `/100.0` 换成元（训练用元）。改动任一侧都要对齐。

## 2. 降级与限流（"宁可降级不超时"）

决策接口被 Sentinel 两道规则保护（`SentinelConfig.java`，资源名 `risk-evaluate`）：

- **流控 QPS=20**：超限走 `evaluateBlocked` → 返回保守的 `MEDIUM / CHALLENGE`（命中码 `DEGRADED_RATELIMIT`）。
- **慢调用熔断 RT>50ms**：1s 窗口内 ≥5 次请求且 RT>50ms 占比 >50% → 熔断 5s，期间走 `evaluateFallback` → 返回 `HIGH / REVIEW`（命中码 `FALLBACK_EXCEPTION`）。

业务异常也走 `evaluateFallback`。设计原则：宁可保守误拦/加验证，绝不拖垮 50ms SLA。
此外 Redis 拉特征失败降级空快照、Kafka 发送失败静默吞掉——任一基础设施挂掉，同步决策仍能给出结果。

## 3. 引擎内部：FraudEngineService.evaluate

`fraud-engine/.../FraudEngineService.java`。核心约束：**特征由 gateway 预取传入，引擎内零 IO**；`KieSession` 线程不安全，每次请求新建 + `dispose()`。

处理顺序：

```
1) modelScorer.score(txn, features)  →  把模型欺诈概率塞进 RiskAssessment.fraudScore（规则只读不算）
2) session.insert(txn / features / assessment)  →  三个 Fact 进工作内存
3) binding.resolve(sourceId)  →  解析该来源要激活的 agenda-group 列表
4) 逆序 setFocus（agenda 是 LIFO 栈，逆序压栈让列表首位先触发）
5) session.fireAllRules(MAX_FIRES=1000)  →  失控护栏，单次决策最多触发 1000 条规则
6) return assessment（finally 里 session.dispose()）
```

### 3.1 三个 Fact

- `TransactionEvent`：本笔交易（来自请求）。
- `FeatureSnapshot`：预取的账号特征快照（`common-feature` 契约，`getLong/getBoolean` 读取）。
- `RiskAssessment`：**输出 Fact**，规则命中时往里写。进引擎前 `fraudScore` 已由 `ModelScorer` 算好。

### 3.2 来源 → 规则集绑定（SourceRuleSetBinding）

交易带 `sourceId` 进来，据此解析要激活的 **agenda-group** 列表，规则"写一次、按来源绑定生效"，不在规则里硬编码渠道条件。

- 默认绑定（任何来源至少都过）：`["blacklist", "threshold", "model"]`。
- 当前为内存 Map + 默认值；生产版应由规则管理模块存 MySQL（`t_source_ruleset_bind`）并热加载。
- 规则集名 == DRL 里的 `agenda-group` 名。

### 3.3 决策聚合：只升不降，取最高等级

`RiskAssessment` 用 `escalate(candidate, hitRule)` 抬升等级：候选等级 ordinal 更高才替换，并登记命中规则码。**各规则顺序无关、只升不降**，所以无需 `update/modify`，规避 Drools 死循环。

风险等级 `RiskLevel`（ordinal 升序 = 越严重）→ 处置动作 `getAction()`：

| 等级 | 动作 | 含义 |
|------|------|------|
| LOW | ALLOW | 放行 |
| MEDIUM | CHALLENGE | 加验证 |
| HIGH | REVIEW | 人工复核 |
| REJECT | REJECT | 直接拦截 |

最终决策 = 所有命中规则抬升后的最高等级。

### 3.4 规则清单（fraud-rules.drl + amount-tier.csv）

**agenda-group `blacklist`**（最快，`salience 100`，命中直接拒绝）：

| 规则 | 条件 | 命中码 → 等级 |
|------|------|------|
| 黑名单账户 | `feature.blacklist == true` | `BLACKLIST_ACCOUNT` → REJECT |
| 黑名单设备 | `feature.device_blacklist == true` | `BLACKLIST_DEVICE` → REJECT |

**agenda-group `threshold`**（金额 + 特征聚合，金额单位分）：

| 规则 | 条件 | 命中码 → 等级 |
|------|------|------|
| 大额转账 | `bizType==TRANSFER && amount>5000000`（5万元） | `LARGE_TRANSFER` → HIGH |
| 当日累计超限 | `feature.daily_amount > 10000000`（10万元） | `DAILY_AMOUNT_EXCEEDED` → HIGH |
| 新设备中额 | `amount>1000000 && feature.device_new==true` | `NEW_DEVICE_MID_AMOUNT` → MEDIUM |
| 短时高频 | `feature.txn_count_5m >= 5` | `HIGH_VELOCITY` → HIGH |

**决策表 `amount-tier.csv`**（金额分档，业务方可维护 CSV，编译进 `threshold` 组）：按 `channel + [金额下限, 金额上限)` 命中 `tier()` → HIGH，命中码如 `DT_MOBILE_LARGE` / `DT_MOBILE_HUGE` / `DT_WEB_LARGE`。

**agenda-group `model`**（双轨决策，读 `fraudScore`）：

| 规则 | 条件 | 命中码 → 等级 |
|------|------|------|
| 模型高风险 | `fraudScore > 0.9` | `MODEL_HIGH_RISK` → REJECT |
| 模型中风险 | `0.6 < fraudScore <= 0.9` | `MODEL_MID_RISK` → HIGH |

### 3.5 模型分推理（ModelScorer，双轨决策）

`@PostConstruct` 加载 `model/fraud-rf.pmml`（由 `fraud-model-train` 用 Spark 随机森林训练导出），jpmml-evaluator 进程内推理。每笔交易组装特征 → 输出欺诈概率 `probability(1)` ∈ [0,1] 塞进 `RiskAssessment.fraudScore`，再交给 `model` 规则组裁决。

推理特征（**顺序/口径须与训练侧严格一致**）：`amount`（分→元）、`daily_amount`（分→元）、`daily_count`、`hour`、`cross_bank`（有对手方=1）、`device_new`。

**任何加载/评分异常都降级返回 0 分**，系统退化为纯规则决策。

## 4. 决策返回之后：Kafka 异步扇出

`TransactionPublisher.publishAsync` 把交易发到 topic `txn-events`，**key=accountNo**（同账号有序又均匀分散防倾斜）。发送失败只记日志不抛出——数据流故障绝不影响银行侧同步决策。

下游多个 consumer group 并行消费：

- **`profiling-realtime`**（group `realtime-feature`）→ Redis 原子操作算窗口特征 → 回写 `feature:{账号}` Hash。
- **Logstash**（group `logstash-es`）→ 清洗 + 脱敏（账号掩码 + HMAC 一致性哈希 `acct_hash`）→ 写 ES 按日索引 `txn-YYYY.MM.dd`。

> **特征是 T+ε 的**：本笔交易的行为要等扇出消费完才回写进 Redis，下一笔评估才读得到。所以调试累计/频次类规则（`daily_*`、`txn_count_5m`）时，要**连发多笔**才能看到特征类规则命中。

## 5. 规则热发布（不重启切换）

`POST /rules/reload`，body 为一段 DRL（`text/plain`，须声明 `package rules.fraud` + 所需 import）。

- 成功：`HotReloadService` 用 classpath 基线规则 + 新增 DRL 重建 `KieContainer`，`KieContainerHolder.replace` 原子切换，返回 200。
- 失败：`DroolsConfig.build` 抛 `IllegalStateException`（含行列号），控制层转 **400 + 报错信息**，线上不受影响。
- 进行中的旧 `KieSession` 关联创建时的容器引用，不受切换影响（热加载安全）。

生产正解是 KieScanner + KJAR（规则随代码独立发版）+ 审核流；当前实现适合小步热修。

## 6. 高并发下的扩展性与瓶颈

实时反欺诈请求量很大（中小行峰值几百~几千 TPS，大行/银联级上万、大促瞬时可冲几万~十万级 TPS），但这套**同步拦截架构恰恰是按水平扩展量身设计的**，扛得住。

### 6.1 先澄清：QPS=20 不是容量上限

`SentinelConfig` 里 `flow.setCount(20)` 是**本地单机 demo 的过载护栏**（单机过载时宁可降级不拖垮 SLA），不是生产容量天花板。真实部署按压测结果调高，并按集群规模分摊。

### 6.2 为什么同步接口扛得住

| 设计 | 代码体现 | 效果 |
|------|----------|------|
| **决策节点无状态** | `KieSession` 每请求 new+dispose，特征外置 Redis，无跨请求状态 | 加机器线性扩 QPS，前置 LB 即可，几万 TPS = 几十台无状态实例 |
| **引擎内零 IO** | IO 在进引擎前一次取齐，Drools 纯内存 + jpmml 进程内推理 | 单请求 RT 压在毫秒级（SLA 50ms），RT 越短单机 QPS 越高 |
| **非关键路径全异步** | 算特征/落库经 Kafka 扇出剥离（§4） | 同步路径只做"读特征→跑规则→返回"最轻的活，负载很轻 |

### 6.3 真正的瓶颈点（不在接口）

| 瓶颈点 | 现状 | 高并发下的应对 |
|--------|------|----------------|
| Redis 拉特征 | 每请求一次 `fetch` | Redis 集群分片 / 读副本；本地缓存热账号 |
| KieSession 每请求 new+dispose | 对象创建开销 | 高 QPS 需压测，必要时 stateless session 或对象池 |
| PMML 推理 | 进程内，单次很快 | 一般不是瓶颈，极端场景可异步/批量化 |
| Kafka 扇出 | 按 accountNo 分区 | 分区数决定下游消费并行度，随量级扩 |

### 6.4 为什么不用"请求先进 Kafka 异步决策"

实时拦截要的是**这笔交易当场放不放行**：异步意味着要么阻塞等回调（没省事），要么先放行后补判（钱已走，反欺诈失去意义）。所以正解是**同步 + 水平扩展 + 严格 SLA + 过载降级**。削峰填谷适合离线评级（架构 A，Spark 批作业），不适合实时拦截（架构 B）。详见 [ARCHITECTURE-COMPARISON.md](ARCHITECTURE-COMPARISON.md)。

## 7. 关键约束速查（容易踩坑）

- 请求是**同步接口**，不是 Kafka；Kafka 在决策返回后才发，且 fire-and-forget。
- 金额全链路存**分**；规则比分，模型推理换算成元。
- 引擎内**零 IO**，特征必须由 gateway 预取传入。
- 决策**只升不降取最高等级**；agenda-group 逆序 setFocus（LIFO 栈）让列表首位先执行。
- 特征是 **T+ε**，调累计/频次规则要连发多笔。
- Kafka 双 listener：宿主机进程走 `localhost:9092`，docker 容器内走 `kafka:29092`。
- 任一基础设施缺失（Redis/Kafka/PMML）都能降级跑通：空特征 / 静默失败 / 模型分恒 0。
