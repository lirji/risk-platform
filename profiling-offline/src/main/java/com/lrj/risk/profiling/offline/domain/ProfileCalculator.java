package com.lrj.risk.profiling.offline.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

public final class ProfileCalculator {

    private ProfileCalculator() { }

    public static CustomerProfile calculate(String customerId, List<TransactionFact> facts,
                                            Instant asOf, ZoneId zone) {
        List<TransactionFact> scoped = facts.stream()
                .filter(fact -> customerId.equals(fact.customerId()))
                .filter(fact -> !fact.eventTime().isBefore(asOf.minus(Duration.ofDays(90))))
                .filter(fact -> !fact.eventTime().isAfter(asOf)).toList();
        long amount = scoped.stream().mapToLong(TransactionFact::amountMinor).sum();
        long counterparties = scoped.stream().map(TransactionFact::counterpartyId)
                .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.toSet()).size();
        long night = scoped.stream().filter(fact -> {
            int hour = fact.eventTime().atZone(zone).getHour();
            return hour < 6 || hour >= 23;
        }).count();
        Instant last = scoped.stream().map(TransactionFact::eventTime).max(Instant::compareTo).orElse(asOf);
        return new CustomerProfile(customerId, amount, scoped.size(), counterparties,
                Duration.between(last, asOf).toDays(), scoped.isEmpty() ? 0 : (double) night / scoped.size(),
                scoped.stream().filter(TransactionFact::fraud).count());
    }
}
