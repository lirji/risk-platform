package com.lrj.risk.fraud.gateway;

import java.util.List;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 配置 (落地 PLAN §3.6 的"宁可降级不超时")。
 *
 * <p>两类规则保护决策接口 risk-evaluate:
 * <ul>
 *   <li>流控(QPS): 超过阈值的请求走 blockHandler 快速返回保守决策, 防过载拖垮 50ms SLA</li>
 *   <li>慢调用熔断(RT): 平均响应时间超 50ms 且占比过高时熔断一段时间, 期间直接降级</li>
 * </ul>
 */
@Configuration
public class SentinelConfig {

    public static final String RES_EVALUATE = "risk-evaluate";

    /** 启用 @SentinelResource 注解切面。 */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initRules() {
        // 流控: QPS 上限 20, 超出走 blockHandler
        FlowRule flow = new FlowRule(RES_EVALUATE);
        flow.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flow.setCount(20);
        FlowRuleManager.loadRules(List.of(flow));

        // 慢调用熔断: 1s 窗口内 >=5 次请求且 RT>50ms 占比 >50% → 熔断 5s
        DegradeRule degrade = new DegradeRule(RES_EVALUATE);
        degrade.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        degrade.setCount(50);                 // RT 阈值 50ms (对齐 SLA)
        degrade.setSlowRatioThreshold(0.5);
        degrade.setMinRequestAmount(5);
        degrade.setStatIntervalMs(1000);
        degrade.setTimeWindow(5);             // 熔断持续 5s
        DegradeRuleManager.loadRules(List.of(degrade));
    }
}
