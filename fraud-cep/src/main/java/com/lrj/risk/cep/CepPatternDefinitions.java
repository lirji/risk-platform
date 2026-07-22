package com.lrj.risk.cep;

import java.time.Duration;

import com.lrj.risk.contracts.v1.TransactionEventV1;
import com.lrj.risk.contracts.v1.TransactionStatus;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;

public final class CepPatternDefinitions {

    private CepPatternDefinitions() {
    }

    public static Pattern<TransactionEventV1, TransactionEventV1> probeThenLarge() {
        return Pattern.<TransactionEventV1>begin("probe")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(TransactionEventV1 event) {
                        return event.amountMinor() <= 10_000;
                    }
                })
                .followedBy("large")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(TransactionEventV1 event) {
                        return event.amountMinor() >= 1_000_000;
                    }
                })
                .within(Duration.ofMinutes(10));
    }

    public static Pattern<TransactionEventV1, TransactionEventV1> failedThenSuccess() {
        return Pattern.<TransactionEventV1>begin("failed")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(TransactionEventV1 event) {
                        return event.transactionStatus() == TransactionStatus.FAILED;
                    }
                })
                .timesOrMore(3)
                .consecutive()
                .followedBy("success")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(TransactionEventV1 event) {
                        return event.transactionStatus() == TransactionStatus.SUCCESS;
                    }
                })
                .within(Duration.ofMinutes(30));
    }
}
