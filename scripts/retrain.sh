#!/usr/bin/env bash
# 模型重训: Spark 随机森林 → 评估 → 导出 PMML 到 fraud-engine 资源目录。
# 由 DolphinScheduler 的 Shell 任务按 cron 调用 (worker 需有本仓库 + JDK21 + Maven wrapper)。
# 用法: scripts/retrain.sh   (在仓库根目录执行)
set -euo pipefail
cd "$(dirname "$0")/.."

echo "[retrain] $(date) 开始重训随机森林..."
./mvnw -q -pl fraud-model-train -am compile
./mvnw -q -pl fraud-model-train exec:exec
echo "[retrain] $(date) 完成, PMML 已更新: fraud-engine/src/main/resources/model/fraud-rf.pmml"
echo "[retrain] 提示: 线上 fraud-engine 重启或调用 /rules/reload 后加载新模型 (热加载模型可后续接入)"
