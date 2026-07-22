package com.lrj.risk.feature.application.port;

import com.lrj.risk.feature.domain.FeatureSnapshot;

/** Output port for loading one point-in-time feature snapshot. */
public interface FeatureReader {

    FeatureSnapshot fetch(String entityKey);
}
