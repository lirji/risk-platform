package com.lrj.risk.fraud.engine.model;

/**
 * 交易事件 (规则引擎的输入 Fact)。承载一笔交易的上下文。
 *
 * <p>{@code channel} / {@code bizType} 是 PLAN §3.1 的正交场景维度;
 * {@code sourceId} 是 §3.5 的交易来源, 用于路由到绑定的规则集。
 * 金额用分(long)存, 避免浮点精度问题。
 */
public class TransactionEvent {

    private String sourceId;          // 交易来源/接入方, 决定走哪些规则集
    private String channel;           // 渠道: MOBILE / WEB / ...
    private String bizType;           // 业务类型: TRANSFER / REMITTANCE / ...
    private String accountNo;         // 账号 (特征查询主键)
    private String counterpartyAccount; // 对手方账号
    private long amount;              // 金额, 单位: 分
    private String deviceId;          // 设备指纹
    private String ip;                // 来源 IP

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getCounterpartyAccount() {
        return counterpartyAccount;
    }

    public void setCounterpartyAccount(String counterpartyAccount) {
        this.counterpartyAccount = counterpartyAccount;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
