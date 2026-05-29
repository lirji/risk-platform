package com.lrj.risk.common.event;

import java.io.Serializable;

/**
 * 交易事件的 Kafka 消息体 (§2.2 交易数据流的载荷)。
 *
 * <p>gateway 决策后异步发布到 topic {@code txn-events} (按账号分区, 见 §2.2.1 防倾斜);
 * profiling-realtime 消费后算实时特征写回 Redis, 形成"交易→特征→下一笔评估"的闭环。
 *
 * <p>无参构造 + getter/setter 供 Jackson(JsonSerializer/JsonDeserializer) 使用。
 */
public class TransactionMessage implements Serializable {

    private String sourceId;
    private String channel;
    private String bizType;
    private String accountNo;
    private String counterpartyAccount;
    private long amount;            // 单位: 分
    private String deviceId;
    private String ip;
    private long eventTime;         // 交易发生时间 (epoch millis)

    public TransactionMessage() {
    }

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

    public long getEventTime() {
        return eventTime;
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }
}
