package com.lrj.risk.feature;

/**
 * 在线特征查询客户端 (共享特征层对外的统一出口)。
 *
 * <p>反欺诈同步链路在进规则引擎前调用本接口一次取齐特征; 画像项目负责写入特征。
 * 实现需保证低延迟 (一次往返取齐, 见 PLAN §3.6) 且在存储不可用时降级返回空快照, 不抛异常阻塞主链路。
 */
public interface FeatureClient {

    /**
     * 按实体主键(账号/卡号)取该实体的全部在线特征。
     *
     * @param entityKey 实体主键, 如账号
     * @return 特征快照; 无数据或存储异常时返回 {@link FeatureSnapshot#empty()}
     */
    FeatureSnapshot fetch(String entityKey);
}
