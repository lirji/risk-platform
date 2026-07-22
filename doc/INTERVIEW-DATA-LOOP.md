# 面试速记卡：数据流闭环 & T+ε 特征

> 历史面试说明：本文保留用于讲解概念；当前实现已改用版本化 `transaction.v1` / `decision.v1`、事务 Outbox 与幂等消费。以根目录 `README.md` 和 `docs/architecture/ddd-context-map.md` 为准。

> 配套详解见 [REQUEST-FLOW.md §1/§4](REQUEST-FLOW.md)、[INTERVIEW-ENGINE-LATENCY.md](INTERVIEW-ENGINE-LATENCY.md)。

## 🎯 主线

> **同步路径只做决策、当场返回银行；决策完成后才把交易异步发 Kafka 扇出，下游各取所需算特征/落库，回写的特征供下一笔评估用——所以特征是 T+ε 的，这是用一点特征新鲜度换关键路径低延迟。**

## 闭环长什么样

```
银行交易 → fraud-gateway 同步决策(返回银行) ──异步──→ Kafka txn-events(按账号分区)
                                                       │ 多 consumer group 并行
                  ┌────────────────────────────────────┼────────────────────────────┐
                  ▼                                                                   ▼
   profiling-realtime(group realtime-feature)               Logstash(group logstash-es)
     → 算实时特征 → Redis feature:{账号}                      → 清洗+脱敏 → ES txn-YYYY.MM.dd
                  ▼                                                                   ▼
   下一笔评估 gateway 读到新特征 → 特征类规则命中              Kibana 案件调查/检索
```

## 关键设计点

1. **决策后才发、且异步**：`publisher.publishAsync` 在返回前调用但 fire-and-forget，**失败只记日志不抛**——数据流故障绝不影响银行侧同步决策
2. **按账号分区**（key=accountNo）：同账号有序、又均匀分散防热点倾斜
3. **一份消息多 group 并行消费**：特征计算（Redis）和落库检索（ES）互不阻塞，各自独立扩缩
4. **闭环**：决策 → 发 Kafka → 算特征回写 Redis → 下一笔读到 → 特征规则生效

## ⚠️ 核心概念：T+ε 特征

- 本笔交易的行为要**等扇出消费完才回写 Redis**，本笔评估时还读不到 → 下一笔才生效
- 实战影响：调累计/频次规则（`daily_amount`、`txn_count_5m`）要**连发多笔**才看到命中
- **这是有意的 trade-off**：关键路径不等特征写完，换确定性低延迟；反欺诈"当场放不放行"比"特征绝对最新"重要

## 为什么不同步算特征再返回

- 同步算 = 关键路径上多一堆写操作（Redis 累加、ES 落库），RT 飙升、扛不住 50ms SLA
- 把重活挪到路径外异步化，是保时效性的核心手段（见 [INTERVIEW-ENGINE-LATENCY.md](INTERVIEW-ENGINE-LATENCY.md)）

## 易追问点

| 追问 | 答 |
|------|-----|
| 异步发失败了特征不就丢了？ | 单笔特征丢失可接受（窗口会自愈），决策已正确返回；要强保证可加重试/本地暂存，但反欺诈场景不值当 |
| 连发多笔之间的窗口竞态？ | Redis 版用 Lua 原子脚本、Flink 版用键控状态，都防并发读改写竞态 |
| 闭环和离线层怎么衔接？ | ES 交易 + 决策结果回流成离线模型重训样本；离线评级标签下沉成在线特征（见 [ARCHITECTURE-COMPARISON.md §4](ARCHITECTURE-COMPARISON.md)） |

## 🏁 收尾

> 闭环的本质是**关键路径与数据加工解耦**：同步只管当场决策，异步管特征沉淀与落库；代价是特征 T+ε，收益是关键路径确定性低延迟 + 下游可独立扩展。

## 关键词

`决策后异步发Kafka` `fire-and-forget失败不影响决策` `按账号分区防倾斜` `多consumer group并行` `T+ε特征` `连发多笔才命中` `路径外异步化保SLA` `闭环反哺离线样本`
