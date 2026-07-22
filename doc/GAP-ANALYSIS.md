# 全链路缺口分析（从端到端视角，哪些还没考虑到）

> **文档状态（2026-07-22）**：这是重构前的审计快照，用于保留问题发现依据，不代表当前实现状态。本文列出的缺口已纳入并执行 [完整交付计划](../docs/delivery/risk-platform-full-stack-ddd/DELIVERY_PLAN.md)；最新验证状态以 [DELIVERY_STATUS](../docs/delivery/risk-platform-full-stack-ddd/DELIVERY_STATUS.md) 为准。

> 本文从「银行请求 → 决策 → 扇出 → 特征/落库 → 离线/重训 → 回流」整条链路逐段排查缺口。
> **区分两类**：
> - **🔴 真盲区**：连 [PLAN.md](PLAN.md) 都没覆盖、当前也没建 —— 本文重点，含代码级证据。
> - **🟡 已规划未建**：PLAN/[CAPABILITIES §10](CAPABILITIES.md) 已知是简化项 —— 简列，补充优先级。
>
> 现状基线：架构 B 7 模块跑通、架构 A 核心引擎已落地（见 CAPABILITIES.md）。下列是「贴近生产还差什么」。

---

## 一、按链路分段的缺口地图

| 链路段 | 🔴 真盲区（未考虑） | 🟡 已规划未建 |
|--------|---------------------|----------------|
| 接入层 fraud-gateway | **交易幂等/去重**、**接入方鉴权**、`/rules/reload` 无鉴权、请求参数校验 | Nacos 配置中心、集群限流 |
| 决策引擎 fraud-engine | **决策快照留痕不全**（无 modelVersion/特征值/规则版本）、名单生命周期管理、规则灰度/shadow mode | Flink CEP 时序规则、来源绑定 MySQL 化 |
| 模型层 | **ModelScorer 用墙钟而非事件时间**（正确性 bug）、training-serving 自动校验 | 模型热加载/版本/灰度、PSI 漂移监控、样本不平衡处理 |
| 扇出/消费 | **消费幂等（重复累加）**、**DLQ/失败重试**、决策结果未落库（回流断链） | exactly-once 端到端 |
| 实时特征 | **device set 无 TTL（内存泄漏）**、**新户特征冷启动**、多实体/图特征 | Caffeine L1 缓存、HBase 海量备选 |
| 离线/数仓 | 案件标签标注闭环（谁打 label、怎么回灌） | ES ILM 冷热分层、真集群（HDFS/YARN） |
| 安全/合规 | **Redis 侧 PII 明文**、规则变更审计、监管可解释报送 | 传输 TLS、密钥管理 |
| 可观测 | **业务指标缺失**（命中率/误杀/分布）、无告警、无决策回放 | SkyWalking 链路追踪、Grafana 看板 |
| 承接闭环 | **CHALLENGE/REVIEW 的人工处置系统**（案件中心） | 预警平台/画像平台 Web UI |

---

## 二、🔴 重点盲区详述（含代码证据）

### P0-1 交易幂等 / 去重 —— 链路根基缺失

**证据**：`TransactionMessage` 与 `RiskRequest` **都没有 `txnId`（交易流水号）字段**。

**风险**：
- 银行网络重试/超时重发同一笔交易 → 同一交易被决策两次、Kafka 发两条 → **实时特征重复累加**（daily_amount 翻倍）、ES 落重复记录。
- 决策不可幂等：同一交易两次可能因 T+ε 特征不同而给出不同结论。

**建议**：请求体加 `txnId`（银行侧唯一）；gateway 用 Redis `SETNX txn:{txnId}` 做幂等键，命中直接返回首次决策结果；Kafka 消息带 `txnId` 供下游去重。**这是其它一切（落库、回流、对账）的前提**。

### P0-2 `/rules/reload` 无任何鉴权 —— 高危安全口子

**证据**：`RuleAdminController.reload` 无 `@PreAuthorize`/token/签名校验，`consumes=text/plain` 直收 DRL。

**风险**：任何能访问 8082 的人都能热替换线上规则 → 可植入"全部放行"规则使反欺诈失效，或写死循环规则（虽有 MAX_FIRES 护栏但仍可拒服务）。**金融系统这是红线**。

**建议**：reload 接口加鉴权（mTLS/AK-SK/OAuth）+ 操作审计（谁、何时、改了什么 DRL）+ 审核流（PLAN §3.5 的 fraud-rule-mgmt 审核），生产应走 KieScanner+KJAR 而非裸 HTTP 直推。

### P0-3 接入方鉴权缺失

**风险**：`sourceId` 是明文自报，无身份校验 → 任何人可伪造 sourceId 调 `/risk/evaluate`，绕过/探测风控逻辑。

**建议**：每个接入方分配 AK/SK 或 mTLS 证书，网关校验签名后才信任其 `sourceId`；与 PLAN §3.5 的 `t_source` 注册表打通。

### P1-1 ModelScorer 用墙钟时间而非交易事件时间 —— 正确性 bug

**证据**：`ModelScorer.score()`：
```java
args.put("hour", (double) java.time.LocalTime.now().getHour());  // ← 用的是"现在几点"
```
而 `TransactionMessage` 明明带了 `eventTime`（交易发生时间）。

**风险**：模型 `hour` 特征用的是**评估时刻**而非**交易时刻**。重放历史数据、跨时区、消息延迟时全错；training（用样本交易时间）与 serving（用墙钟）**口径不一致**，正是 training-serving skew。

**建议**：改用 `txn.getEventTime()` 推导小时；训练侧也统一用交易事件时间。

### P1-2 消费幂等 / 重复累加

**风险**：Kafka 至少一次语义 + 消费重试/重平衡 → 同条消息被消费两次。轻量版 `RealtimeFeatureService` 的 `HINCRBY daily_amount` **会重复累加**；Flink 版状态精确一次但 **Redis sink 仍是至少一次**。

**建议**：消费侧按 `txnId` 去重（配合 P0-1）；或特征写幂等化（用集合记已处理 txnId，或 Redis Lua 内判重）。

### P1-3 DLQ / 消费失败重试缺失

**风险**：profiling/Logstash 消费时若反序列化失败或 Redis/ES 暂不可用，消息处理失败 → 当前要么静默丢、要么无限重试卡住分区。无死信队列兜底。

**建议**：配置消费重试 + 死信 topic（`txn-events.DLT`），失败消息进 DLQ 旁路告警，不阻塞主流。

### P1-4 决策结果未落库 —— 回流闭环断链

**证据**：扇出的 `TransactionMessage` **只含交易字段，不含决策结果**（level/action/fraudScore/hitRules）。PLAN §1 列了 `fraud-decision-log` 模块但未建。

**风险**：决策本身没有任何持久化载体 → 无法对账、无法做误杀/漏杀复盘、**模型重训没有 label 来源**（PLAN §2.4 的回流闭环缺了承载体），申诉/监管无据可查。

**建议**：决策后异步落一条"决策日志"（交易 + 决策快照 + 命中明细 + 模型版本），入 ES/Hive；案件确认结果回灌成训练 label。

### P1-5 决策快照留痕不全（可解释/可审计）

**证据**：`RiskResponse` 只回 `hitRules`，**无 requestId/decisionId、无模型版本、无各特征实际取值、无规则集版本**。

**风险**：拒绝一笔交易后，无法回答"为什么拒"——客户申诉、监管检查、内部复盘都缺证据；问题决策无法精确回放。

**建议**：每次决策生成 `decisionId`，留快照（输入特征值 + 模型版本 + 规则/绑定版本 + 命中明细），与决策日志一起落库。

### P2-1 device set 无 TTL —— 内存泄漏

**证据**：`RealtimeFeatureService`：`SADD acct:{账号}:devices` **从不设过期**，集合随账号历史设备无限增长。

**风险**：长期运行 Redis 内存持续膨胀；`device_new` 语义其实应是"近 N 天首见"，永久集合会让老设备永远判为"非新"。

**建议**：给设备集合设滑动 TTL（如 90 天）或改用带时间衰减的结构（HyperLogLog/带时间戳的 ZSet）。

### P2-2 新户特征冷启动

**风险**：新账号在 Redis 无特征 → `fetch` 返回空快照 → 累计/频次类规则全不命中 → 新户可能成为欺诈突破口（首单大额）。

**建议**：空快照时走更保守策略（新户首单加验证），或用离线画像（架构 A）给新户兜底基线特征。

### P2-3 CHALLENGE/REVIEW 无人工承接系统

**风险**：决策能输出 CHALLENGE（加验证）/REVIEW（人工复核），但**下游没有任何系统承接**——验证码谁发、复核工单进哪、处置结果如何回写决策状态，全缺。决策只是"建议"，没有闭环执行。

**建议**：建案件/工单中心（对应 PLAN 的预警平台），承接 REVIEW 队列，处置结果回写并回流为样本。

---

## 三、🟡 已规划未建（PLAN/CAPABILITIES 已知，补优先级）

| 项 | 出处 | 建议优先级 |
|----|------|-----------|
| 模型热加载 + 版本/灰度/回滚 | CAPABILITIES §10、PLAN §4.4 | P1（重训后需重启才生效，运维痛点） |
| PSI 特征漂移 + 模型分分布监控 | PLAN §4.4 | P1（模型失效无预警） |
| Flink CEP 时序规则（fraud-cep） | PLAN §3.4，M5 未做 | P2（试探性小额后大额等串谋模式） |
| 来源↔规则集绑定 MySQL 化 + 管理后台 | CAPABILITIES §10、PLAN §3.5 | P1（现为内存 Map，重启即失） |
| KieScanner + KJAR + 审核流 | CAPABILITIES §10 | P1（与 P0-2 鉴权一并做） |
| 集群限流（Sentinel + token server） | PLAN §6 | P2（现 QPS=20 是单机硬编码） |
| Caffeine L1 缓存（名单/规则元数据） | PLAN §3.6 | P2（进一步压 RT） |
| ES ILM 冷热分层 + 多副本 | CAPABILITIES §10、PLAN §2.5 | P2（监管留存 + 成本） |
| Nacos / SkyWalking / Grafana / K8s | CAPABILITIES §10、PLAN §6 | P2（生产化基建） |
| 多实体/图特征（设备/IP/对手方、团伙） | PLAN §2.7 | P2（团伙欺诈识别） |
| 样本不平衡处理（weightCol/采样） | PLAN §4.2 | P1（欺诈<1%，现合成数据未体现） |

---

## 四、优先级总表（建议落地顺序）

**P0（安全/正确性根基，先做）**
1. 交易 `txnId` + 幂等去重（P0-1）— 解锁落库/回流/对账
2. `/rules/reload` 鉴权 + 审计（P0-2）
3. 接入方鉴权（P0-3）

**P1（闭环与可信，紧接着做）**
4. ModelScorer 改用事件时间（P1-1，纯 bug 修）
5. 决策结果落库 + 决策快照留痕（P1-4 + P1-5）— 打通回流与可解释
6. 消费幂等 + DLQ（P1-2 + P1-3）
7. 模型热加载/版本、PSI 监控、来源绑定 MySQL 化、样本不平衡

**P2（增强与生产化）**
8. device set TTL、新户冷启动、人工承接系统
9. Flink CEP、图特征、Caffeine、ES ILM、Nacos/SkyWalking/K8s

---

## 五、一句话总结（面试可用）

> 当前 demo 把"决策主链路"跑通了，但贴近生产还差三类东西：**① 链路根基**——交易幂等（没有 txnId 一切对账/回流都立不住）；**② 闭环**——决策结果落库 + 案件标签回流 + 人工处置承接，现在决策完就"断了"；**③ 安全与可信**——reload/接入方无鉴权、决策不可解释、事件时间用错。这些不补，规则模型再好也只是"能跑"，不是"敢上生产"。
