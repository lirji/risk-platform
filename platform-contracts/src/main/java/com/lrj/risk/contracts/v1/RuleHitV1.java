package com.lrj.risk.contracts.v1;

public record RuleHitV1(String ruleCode, String ruleName, int score, String reason) {

    public RuleHitV1 {
        ContractValidation.text(ruleCode, "ruleCode");
        ContractValidation.text(ruleName, "ruleName");
        ContractValidation.text(reason, "reason");
    }
}
