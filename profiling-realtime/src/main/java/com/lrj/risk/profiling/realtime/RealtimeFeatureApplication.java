package com.lrj.risk.profiling.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 实时特征计算服务 (画像项目的实时层雏形)。
 *
 * <p>消费 Kafka 交易流, 用 Redis 原子操作维护窗口特征 (当日累计/笔数、新设备),
 * 写回 {@code feature:{账号}} 供反欺诈同步链路读取, 形成"交易→特征→下一笔评估"闭环。
 * 当前为轻量进程内实现, 后续可平替为 Flink 作业 (PLAN §2.2)。
 */
@SpringBootApplication(scanBasePackages = "com.lrj.risk")
public class RealtimeFeatureApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeFeatureApplication.class, args);
    }
}
