#!/usr/bin/env bash
# 初始化离线层 Hive (架构 A): 拉起 MinIO + Hive Metastore, 建 risk_dw 库 + 两张宽表并灌数。
# 存算分离: 元数据进 Metastore(后端复用 risk-mysql), parquet 数据落 MinIO/S3A。
# 前置: docker compose up -d mysql      用法: ./scripts/setup-hive.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "[1/5] 备 MySQL 驱动 jar 给 Metastore 容器挂载..."
if [ ! -f libs/mysql-connector-j.jar ]; then
  ./mvnw -q -pl rating-engine dependency:copy-dependencies \
    -DincludeArtifactIds=mysql-connector-j -DoutputDirectory="$PWD/libs"
  cp libs/mysql-connector-j-*.jar libs/mysql-connector-j.jar
fi

echo "[2/5] 拉起 MinIO + 建桶 + Hive Metastore (profile=hive)..."
docker compose --profile hive up -d minio minio-init hive-metastore

echo "[3/5] 等 MinIO(9000) + Metastore(9083) 就绪..."
for i in $(seq 1 60); do
  if nc -z localhost 9000 2>/dev/null && nc -z localhost 9083 2>/dev/null; then break; fi
  sleep 2
done
nc -z localhost 9083 2>/dev/null || { echo "Metastore 未就绪, 看日志: docker logs risk-hive-metastore"; exit 1; }

echo "[4/5] 建库建表灌数 (HiveSeedJob: dwd_cust_feature + dwd_fraud_train)..."
./mvnw -q -pl rating-engine -am compile
./mvnw -q -pl rating-engine exec:exec -DratingMainClass=com.lrj.risk.rating.HiveSeedJob

echo "[5/5] 完成。验证: 评级引擎 ./mvnw -q -pl rating-engine exec:exec ；训练 ./mvnw -q -pl fraud-model-train exec:exec"
