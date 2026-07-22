package com.lrj.risk.admin.cases.adapter.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.lrj.authz.protocol.AuthzEngine;
import com.lrj.authz.protocol.Consistency;
import com.lrj.authz.protocol.RelationshipUpdate;
import com.lrj.authz.protocol.ResourceRef;
import com.lrj.authz.protocol.SubjectRef;
import com.lrj.authz.protocol.ZedTokenView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class RemoteCaseAuthorizationTest {
    @Test
    void assignsTenantScopedCaseAndUsesFreshnessForSubsequentCheck() {
        AuthzEngine engine = mock(AuthzEngine.class);
        when(engine.writeRelationships(any())).thenReturn(new ZedTokenView("zed-1"));
        when(engine.check(any(), any(), any(), any())).thenReturn(true);
        RemoteCaseAuthorization authorization = authorization("enforce", engine);

        authorization.assign("risk-platform", "case-1", "sub-1");
        authorization.requireWork("risk-platform", "case-1", "sub-1");

        verify(engine).writeRelationships(eq(List.of(RelationshipUpdate.touch(
                ResourceRef.of("risk_case", "risk-platform_case-1"), "assignee", SubjectRef.user("sub-1")))));
        verify(engine).check(eq(SubjectRef.user("sub-1")), eq("work"),
                eq(ResourceRef.of("risk_case", "risk-platform_case-1")),
                eq(Consistency.atLeastAsFresh("zed-1")));
    }

    @Test
    void enforceDistinguishesDenyFromDependencyFailure() {
        AuthzEngine engine = mock(AuthzEngine.class);
        when(engine.check(any(), any(), any(), any())).thenReturn(false);
        RemoteCaseAuthorization authorization = authorization("enforce", engine);

        assertThatThrownBy(() -> authorization.requireWork("risk-platform", "case-1", "other"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));

        when(engine.check(any(), any(), any(), any())).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> authorization.requireWork("risk-platform", "case-1", "sub-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void invalidModeFailsFast() {
        assertThatThrownBy(() -> authorization("enfoce", mock(AuthzEngine.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removesAssignmentWhenSurroundingDatabaseTransactionRollsBack() {
        AuthzEngine engine = mock(AuthzEngine.class);
        when(engine.writeRelationships(any()))
                .thenReturn(new ZedTokenView("zed-assign"), new ZedTokenView("zed-compensate"));
        RemoteCaseAuthorization authorization = authorization("enforce", engine);
        RelationshipUpdate assignment = RelationshipUpdate.touch(
                ResourceRef.of("risk_case", "risk-platform_case-1"), "assignee", SubjectRef.user("sub-1"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            authorization.assign("risk-platform", "case-1", "sub-1");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().getFirst()
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(engine).writeRelationships(eq(List.of(assignment)));
        verify(engine).writeRelationships(eq(List.of(RelationshipUpdate.delete(
                assignment.resource(), assignment.relation(), assignment.subject()))));
    }

    private RemoteCaseAuthorization authorization(String mode, AuthzEngine engine) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("authzEngine", engine));
        return new RemoteCaseAuthorization(mode, beans.getBeanProvider(AuthzEngine.class));
    }
}
