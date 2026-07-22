package com.lrj.risk.admin.models.application;

public interface ModelRuntimePort {
    void activate(String version, String artifactUri, String checksum, int rolloutPercentage);
}
