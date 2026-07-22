#!/usr/bin/env bash
# 初始化离线层 Hive (架构 A): 拉起 MinIO + Hive Metastore, 建 risk_dw 库 + 两张宽表并灌数。
# 存算分离: 元数据进 Metastore(后端复用 risk-mysql), parquet 数据落 MinIO/S3A。
# 前置: docker compose up -d mysql      用法: ./scripts/setup-hive.sh
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
: "${MYSQL_ROOT_PASSWORD:?copy .env.example to .env and set MYSQL_ROOT_PASSWORD}"
: "${S3_ACCESS_KEY:?set S3_ACCESS_KEY in .env}"
: "${S3_SECRET_KEY:?set S3_SECRET_KEY in .env}"

echo "[1/5] 备 MySQL 驱动 jar 给 Metastore 容器挂载..."
if [ ! -f libs/mysql-connector-j.jar ]; then
  ./mvnw -q -pl rating-engine dependency:copy-dependencies \
    -DincludeArtifactIds=mysql-connector-j -DoutputDirectory="$PWD/libs"
  cp libs/mysql-connector-j-*.jar libs/mysql-connector-j.jar
fi

echo "[2/5] 拉起 MinIO + 建桶 + Hive Metastore (profile=hive)..."
docker compose --env-file .env --profile hive up -d minio minio-init hive-metastore

echo "[3/5] 等 MinIO(9000) + Metastore(9083) 就绪..."
for i in $(seq 1 60); do
  if nc -z localhost 9000 2>/dev/null && nc -z localhost 9083 2>/dev/null; then break; fi
  sleep 2
done
nc -z localhost 9083 2>/dev/null || { echo "Metastore 未就绪, 看日志: docker logs risk-hive-metastore"; exit 1; }

echo "[4/5] 建库并写入本地脱敏 fixture（正式事实表结构 + 评级兼容表）..."
./mvnw -q -pl rating-engine -am compile
ALLOW_FIXTURE_SEED=true ./mvnw -q -pl rating-engine exec:exec -DratingMainClass=com.lrj.risk.rating.HiveSeedJob

echo "[5/5] 完成。可运行离线画像、评级与案件标签模型训练；生产事实入湖使用 RiskFactIngestionJob。"
