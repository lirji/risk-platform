package com.lrj.risk.admin.cases.application;

/** 案件对象级授权端口；实现由 auth-platform SDK 适配器提供。 */
public interface CaseAuthorization {
    void assign(String tenant, String caseId, String actor);

    void requireWork(String tenant, String caseId, String actor);
}
