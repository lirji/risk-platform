# 面试速记卡：规则热发布（不重启切换）

> 配套详解见 [REQUEST-FLOW.md §5](REQUEST-FLOW.md)。

## 🎯 主线

> **运行时把新 DRL 编译进一个全新 KieContainer，成功就原子替换当前容器、不重启切换；编译失败抛异常转 400+行号，线上老容器照常服务——"先编译验证、成功才切、失败不影响线上"。**

## 链路

```
POST /rules/reload (text/plain DRL)
  → HotReloadService.reload(drl)
  → DroolsConfig.build(drl)   // classpath 基线规则 + 这段额外 DRL 一起重新编译
       ├─ 编译成功 → KieContainerHolder.replace(新容器)  原子切换 → 200
       └─ 编译失败 → 抛 IllegalStateException(含文件/行列号) → 控制层转 400
```

## 关键设计点

1. **全量重建而非增量打补丁**：基线规则 + 新 DRL 一起 `buildAll`，避免增量带来的规则间不一致
2. **原子切换**：`KieContainerHolder` 持 `volatile KieContainer`，`replace` 一行换引用；`FraudEngineService` 每请求 `holder.get()` 拿最新容器
3. **切换安全**：进行中的旧 `KieSession` 关联创建时的容器引用，**不受切换影响**（老请求跑完老规则，新请求用新规则）
4. **失败隔离**：`buildAll().hasMessages(ERROR)` 有错就抛、不替换 → 线上一直是上一个**编译通过**的容器
5. **程序化构建**（踩坑）：不用 `getKieClasspathContainer()`（fat jar 的 `jar:nested:` 协议读 kmodule.xml 失败），改 `KieFileSystem` + Spring 资源加载，兼容 fat jar 与展开目录

## ⚠️ 易追问点

| 追问 | 答 |
|------|-----|
| 切换瞬间正在跑的请求会乱吗？ | 不会，旧 session 绑旧容器引用，新请求才走新容器，无中间态 |
| 编译失败会影响线上吗？ | 不会，失败不 replace，老容器继续服务，只给调用方返回 400+行号 |
| volatile 够吗，要不要锁？ | 引用替换是原子的，volatile 保可见性即可；规则只读不存在写竞争 |
| 生产级正解是什么？ | KieScanner + KJAR（规则随代码独立发版）+ 审核流；当前实现适合小步热修 |
| 怎么回滚？ | 重新发上一版 DRL 即可；生产版用 KJAR 版本切换 |

## 🏁 收尾

> 热发布的核心是**"编译期验证 + 原子切换 + 失败隔离"**：新规则先在独立容器里编译验证，通过才原子换上，失败完全不碰线上。既要改规则不重启的敏捷，又要线上稳定不被坏规则拖垮。

## 关键词

`运行时重建KieContainer` `原子volatile替换` `旧session绑旧容器(切换安全)` `编译失败不replace(失败隔离)` `400+行号反馈` `全量重建非增量` `KieFileSystem程序化构建(避fat jar坑)` `生产正解KieScanner+KJAR`
