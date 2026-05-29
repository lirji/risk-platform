package com.lrj.risk.fraud.gateway.dto;

/**
 * 银行侧传入的交易决策请求。
 */
public class RiskRequest {

    private String sourceId;             // 交易来源/接入方
    private String channel;              // 渠道 MOBILE / WEB
    private String bizType;              // 业务类型 TRANSFER / REMITTANCE
    private String accountNo;            // 账号 (特征查询主键)
    private String counterpartyAccount;  // 对手方账号
    private long amount;                 // 金额, 单位: 分
    private String deviceId;             // 设备指纹
    private String ip;                   // 来源 IP

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
