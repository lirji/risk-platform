# risk-platform

实时交易反欺诈 + 客户画像平台。完整规划见根目录上层的 [`../PLAN.md`](../PLAN.md)。

当前为 **最小可跑核心 (M1+M4 雏形)**：一笔交易经接入层 → 拉特征 → Drools 规则引擎 → 返回风险等级，端到端跑通。

## 模块

| 模块 | 职责 |
|------|------|
| `common-feature` | 共享层：`FeatureSnapshot` + `FeatureClient`/`RedisFeatureClient`(降级容错) + Kafka 消息体 `TransactionMessage` |
| `fraud-engine` | Drools 规则引擎：场景规则集(agenda-group) + 来源绑定 + 决策聚合 |
| `fraud-gateway` | 同步决策接入层 (Spring Boot, 8082)：拉特征 + 调引擎 + 返回风险等级 + 决策后异步发 Kafka |
| `profiling-realtime` | 实时特征计算：消费 Kafka 交易流 → Redis 原子算窗口特征(当日累计/笔数、新设备) → 写回 `feature:{账号}` |

### 数据流闭环 (§2.2 / §2.4)

```
交易 → fraud-gateway 同步决策(返回银行) ──异步──→ Kafka(txn-events, 按账号分区)
                                                      ↓
                          profiling-realtime 消费 → 算实时特征 → Redis feature:{账号}
                                                      ↓
                                   下一笔交易评估时被 fraud-gateway 读到 → 规则命中
```

> `drools-demo/`（上层目录）为规则引擎能力参考脚手架，本平台的 fraud-engine 借鉴其模式但独立实现。

## 技术基线

Java 21 / Spring Boot 3.3.5 / Drools 8.44.2.Final（与 drools-demo 同验证组合）。

## 快速开始

```bash
# 1) (可选) 起本地基础设施: MySQL + Redis + Kafka + ES
docker compose up -d

# 2) 构建 + 跑规则测试 (真实编译 DRL 并触发规则)
./mvnw clean package

# 3) 启动实时特征消费者 + 接入层 (Redis/Kafka 没起也能跑, 特征降级为空快照、发 Kafka 静默失败)
java -jar profiling-realtime/target/profiling-realtime-0.0.1-SNAPSHOT.jar &
java -jar fraud-gateway/target/fraud-gateway-0.0.1-SNAPSHOT.jar
```

### 看数据流闭环

对同一账号连发 4 笔 4 万元交易：前 3 笔 daily_amount 累积到 12 万 (consumer 异步写 Redis)，
第 4 笔评估时读到 >10 万阈值 → `HIGH` / `DAILY_AMOUNT_EXCEEDED`。

```bash
for i in 1 2 3 4; do
  curl -s -X POST localhost:8082/risk/evaluate -H "Content-Type: application/json" \
    -d '{"sourceId":"MOBILE_TRANSFER","bizType":"TRANSFER","accountNo":"ACC200","amount":4000000}'; echo
  sleep 1.5
done
# 查实时特征: docker exec risk-redis redis-cli HGETALL feature:ACC200
```

## 试一笔

```bash
# 大额转账 → HIGH / REVIEW, 命中 LARGE_TRANSFER
curl -s -X POST localhost:8082/risk/evaluate -H "Content-Type: application/json" \
  -d '{"sourceId":"MOBILE_TRANSFER","channel":"MOBILE","bizType":"TRANSFER","accountNo":"ACC002","amount":6000000}'
# => {"riskLevel":"HIGH","action":"REVIEW","fraudScore":0.0,"hitRules":["LARGE_TRANSFER"],"costMs":11}
```

### 让特征类规则生效 (需 Redis)

特征以 Hash 存于 `feature:{账号}`：

```bash
docker exec -it risk-redis redis-cli HSET feature:ACC003 blacklist true
# 之后对 ACC003 的任意交易 → REJECT, 命中 BLACKLIST_ACCOUNT
```

支持的特征键：`blacklist` / `device_blacklist` / `daily_amount`(分) / `device_new`。

## 现有规则 (`fraud-engine/.../rules/fraud/fraud-rules.drl`)

| 规则集(agenda-group) | 规则 | 触发 → 等级 |
|------|------|------|
| blacklist | 黑名单账户 / 黑名单设备 | → REJECT |
| threshold | 大额转账(>5万) / 当日累计超限(>10万) / 新设备中额(>1万) | → HIGH / HIGH / MEDIUM |

决策聚合：取所有命中规则的最高等级；等级 → 动作映射 LOW=ALLOW / MEDIUM=CHALLENGE / HIGH=REVIEW / REJECT=REJECT。

## 监控

`GET /actuator/prometheus`（P99 / 命中率埋点后续接入，参考 drools-demo Step 15）。

## 下一步 (按 PLAN 里程碑)

- M5：Flink CEP 时序规则 + 决策表 + KieScanner 规则热发布
- M6：Spark MLlib 随机森林 → MLeap 推理 → `fraudScore` 接入双轨决策
- 接 Kafka 交易数据流、Flink 实时特征写 Redis、超时熔断降级(Sentinel)
# risk-platform
