package io.github.lutzseverino.cardo.identity.workflow;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationWork;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.service.IdentityOperationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconcileIdentityOperationsWorkflowTest {

  private static final UUID OPERATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final OffsetDateTime ACTION_EXPIRES_AT =
      OffsetDateTime.parse("2030-07-17T10:00:00Z");

  @Test
  void requestsKeycloakActionsOnlyAfterTheDurableOperationCanBeClaimed() {
    IdentityOperationService operations = mock(IdentityOperationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    when(operations.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(IdentityOperationStatus.REQUESTED)));

    new ReconcileIdentityOperationsWorkflow(operations, provider).reconcile(OPERATION_ID);

    verify(provider)
        .requestCredentialSetup(
            org.mockito.ArgumentMatchers.eq("subject-1"),
            org.mockito.ArgumentMatchers.any(Duration.class));
    verify(operations).markAwaitingUser(OPERATION_ID, ACTION_EXPIRES_AT);
    verify(operations, never())
        .completeCredentialSetup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void activatesTheLocalUserOnlyAfterProviderStateIsVerified() {
    IdentityOperationService operations = mock(IdentityOperationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    IdentityProvider.CompletedIdentityProfile profile =
        new IdentityProvider.CompletedIdentityProfile("Employee");
    when(operations.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(IdentityOperationStatus.AWAITING_USER)));
    when(provider.completedIdentityProfile("subject-1")).thenReturn(Optional.of(profile));

    new ReconcileIdentityOperationsWorkflow(operations, provider).reconcile(OPERATION_ID);

    verify(operations).completeCredentialSetup(OPERATION_ID, "Employee");
  }

  @Test
  void retainsFailedRemoteWorkForRetry() {
    IdentityOperationService operations = mock(IdentityOperationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    RuntimeException failure = new RuntimeException("keycloak unavailable");
    when(operations.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(IdentityOperationStatus.REQUESTED)));
    doThrow(failure)
        .when(provider)
        .requestCredentialSetup(
            org.mockito.ArgumentMatchers.eq("subject-1"),
            org.mockito.ArgumentMatchers.any(Duration.class));

    new ReconcileIdentityOperationsWorkflow(operations, provider).reconcile(OPERATION_ID);

    verify(operations).recordFailure(OPERATION_ID, failure);
    verify(operations, never())
        .markAwaitingUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void recordsProviderFourHundredsAsTerminalWithoutRetrying() {
    IdentityOperationService operations = mock(IdentityOperationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    ApiException failure = ApiException.of(400, "identity_provider_error", "Invalid request.");
    when(operations.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(IdentityOperationStatus.REQUESTED)));
    doThrow(failure)
        .when(provider)
        .requestCredentialSetup(
            org.mockito.ArgumentMatchers.eq("subject-1"),
            org.mockito.ArgumentMatchers.any(Duration.class));

    new ReconcileIdentityOperationsWorkflow(operations, provider).reconcile(OPERATION_ID);

    verify(operations).recordTerminalFailure(OPERATION_ID, failure);
    verify(operations, never())
        .recordFailure(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void retriesProviderRateLimits() {
    IdentityOperationService operations = mock(IdentityOperationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    ApiException failure = ApiException.of(429, "identity_provider_rate_limited", "Try later.");
    when(operations.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(IdentityOperationStatus.REQUESTED)));
    doThrow(failure)
        .when(provider)
        .requestCredentialSetup(
            org.mockito.ArgumentMatchers.eq("subject-1"),
            org.mockito.ArgumentMatchers.any(Duration.class));

    new ReconcileIdentityOperationsWorkflow(operations, provider).reconcile(OPERATION_ID);

    verify(operations).recordFailure(OPERATION_ID, failure);
    verify(operations, never())
        .recordTerminalFailure(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void retriesProviderDeletionBeforeRemovingTheLocalUser() {
    IdentityOperationService operations = mock(IdentityOperationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    IdentityOperationWork deletion =
        new IdentityOperationWork(
            OPERATION_ID,
            USER_ID,
            "subject-1",
            IdentityOperationType.PROVISIONAL_DELETION,
            IdentityOperationStatus.REQUESTED,
            null);
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(deletion));

    new ReconcileIdentityOperationsWorkflow(operations, provider).reconcile(OPERATION_ID);

    verify(provider).deleteIdentity("subject-1");
    verify(operations).completeProvisionalDeletion(OPERATION_ID);
  }

  private IdentityOperationWork work(IdentityOperationStatus status) {
    return new IdentityOperationWork(
        OPERATION_ID,
        USER_ID,
        "subject-1",
        IdentityOperationType.CREDENTIAL_SETUP,
        status,
        ACTION_EXPIRES_AT);
  }
}
