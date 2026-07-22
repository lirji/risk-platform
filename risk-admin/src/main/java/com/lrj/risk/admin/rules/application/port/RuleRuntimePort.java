package com.lrj.risk.admin.rules.application.port;

import java.util.List;

import com.lrj.risk.admin.rules.domain.RuleRelease;

public interface RuleRuntimePort {

    void activate(String sourceId, RuleRelease release, List<String> ruleSets, int rolloutPercentage,
                  RuleRelease previous, RuleRelease shadow);
}
