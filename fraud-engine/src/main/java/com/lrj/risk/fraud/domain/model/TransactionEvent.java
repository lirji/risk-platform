package com.lrj.risk.fraud.domain.model;

import java.time.Instant;
import com.lrj.risk.contracts.v1.TransactionStatus;

/** Transaction fact evaluated by the decision domain. Monetary values use minor currency units. */
public class TransactionEvent {

    private String txnId;
    private String sourceId;
    private String channel;
    private String bizType;
    private String accountNo;
    private String counterpartyAccount;
    private long amount;
    private String currency = "CNY";
    private String deviceId;
    private String ip;
    private Instant eventTime;
    private TransactionStatus transactionStatus = TransactionStatus.UNKNOWN;

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getCounterpartyAccount() { return counterpartyAccount; }
    public void setCounterpartyAccount(String counterpartyAccount) { this.counterpartyAccount = counterpartyAccount; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
    public TransactionStatus getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus == null ? TransactionStatus.UNKNOWN : transactionStatus;
    }
}
