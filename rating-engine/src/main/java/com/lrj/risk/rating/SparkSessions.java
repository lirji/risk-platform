package com.lrj.risk.rating;

import org.apache.spark.sql.SparkSession;

/**
 * 统一构建带 Hive + S3A(MinIO) 的 SparkSession (架构 A 离线层)。
 *
 * <p>Hive: 连 Metastore (thrift), 表元数据进 MySQL 后端。
 * S3A: 表 parquet 数据落 MinIO; path-style + 关 ssl 以适配 MinIO。
 * 地址可使用本地默认值；访问凭据必须由环境显式提供。
 */
final class SparkSessions {

    private SparkSessions() {
    }

    static SparkSession builder(String appName) {
        String metastore = System.getenv().getOrDefault("HIVE_METASTORE_URIS", "thrift://localhost:9083");
        String s3Endpoint = System.getenv().getOrDefault("S3_ENDPOINT", "http://localhost:9000");
        String s3Access = requiredEnv("S3_ACCESS_KEY");
        String s3Secret = requiredEnv("S3_SECRET_KEY");

        SparkSession spark = SparkSession.builder()
                .appName(appName)
                .master(System.getenv().getOrDefault("SPARK_MASTER", "local[*]"))
                .config("spark.ui.enabled", "false")
                .config("spark.sql.catalogImplementation", "hive")
                .config("spark.sql.warehouse.dir", "s3a://warehouse")
                .config("spark.hadoop.hive.metastore.uris", metastore)
                .config("spark.hadoop.fs.s3a.endpoint", s3Endpoint)
                .config("spark.hadoop.fs.s3a.access.key", s3Access)
                .config("spark.hadoop.fs.s3a.secret.key", s3Secret)
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
                .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
                .enableHiveSupport()
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");
        return spark;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
