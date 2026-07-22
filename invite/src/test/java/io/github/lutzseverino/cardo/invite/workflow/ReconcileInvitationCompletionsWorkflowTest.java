package io.github.lutzseverino.cardo.invite.workflow;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.client.IdentityOperation;
import io.github.lutzseverino.cardo.identity.client.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionWork;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconcileInvitationCompletionsWorkflowTest {

  private static final UUID OPERATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID LEASE_TOKEN = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-17T10:00:00Z");

  @Test
  void dispatchesThePersistedRequestIdempotentlyToIdentity() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    when(completions.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(InvitationCompletionStatus.REQUESTED)));
    when(identity.requestCredentialSetup(USER_ID, OPERATION_ID, NOW.plusDays(1)))
        .thenReturn(identity(IdentityOperationStatus.REQUESTED));

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(identity).requestCredentialSetup(USER_ID, OPERATION_ID, NOW.plusDays(1));
    verify(completions).markAwaitingIdentity(OPERATION_ID, LEASE_TOKEN, null);
  }

  @Test
  void completesOnlyAfterIdentityReportsVerifiedProviderCompletion() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    when(completions.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(InvitationCompletionStatus.AWAITING_IDENTITY)));
    when(identity.getCredentialSetup(USER_ID, OPERATION_ID))
        .thenReturn(identity(IdentityOperationStatus.COMPLETED));

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(completions).complete(OPERATION_ID, LEASE_TOKEN);
  }

  @Test
  void retainsAmbiguousHttpFailuresForRetryWithTheSameOperationId() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    RuntimeException failure = new RuntimeException("response lost");
    when(completions.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(InvitationCompletionStatus.REQUESTED)));
    doThrow(failure).when(identity).requestCredentialSetup(USER_ID, OPERATION_ID, NOW.plusDays(1));

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(completions).recordFailure(OPERATION_ID, LEASE_TOKEN, failure);
    verify(completions, never())
        .markAwaitingIdentity(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void propagatesTerminalIdentityFailureWithoutRetryingItAsTransient() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    when(completions.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(InvitationCompletionStatus.AWAITING_IDENTITY)));
    when(identity.getCredentialSetup(USER_ID, OPERATION_ID))
        .thenReturn(
            new IdentityOperation(
                OPERATION_ID,
                USER_ID,
                IdentityOperationStatus.FAILED,
                0,
                "Credential setup expired before completion.",
                NOW,
                null,
                NOW,
                NOW));

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(completions)
        .recordIdentityFailure(
            OPERATION_ID, LEASE_TOKEN, "Credential setup expired before completion.");
    verify(completions, never())
        .recordFailure(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void recordsIdentityFourHundredsAsTerminalWithoutRetrying() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    ApiException failure = ApiException.of(409, "identity_operation_conflict", "Conflict.");
    when(completions.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(InvitationCompletionStatus.REQUESTED)));
    doThrow(failure).when(identity).requestCredentialSetup(USER_ID, OPERATION_ID, NOW.plusDays(1));

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(completions).recordTerminalFailure(OPERATION_ID, LEASE_TOKEN, failure);
    verify(completions, never())
        .recordFailure(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void retriesIdentityRateLimits() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    ApiException failure = ApiException.of(429, "identity_rate_limited", "Try later.");
    when(completions.claim(OPERATION_ID))
        .thenReturn(Optional.of(work(InvitationCompletionStatus.REQUESTED)));
    doThrow(failure).when(identity).requestCredentialSetup(USER_ID, OPERATION_ID, NOW.plusDays(1));

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(completions).recordFailure(OPERATION_ID, LEASE_TOKEN, failure);
    verify(completions, never())
        .recordTerminalFailure(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void doesNotCrossTheIdentityBoundaryWhenRevocationPreventsClaim() {
    InvitationCompletionService completions = mock(InvitationCompletionService.class);
    IdentityUsersClient identity = mock(IdentityUsersClient.class);
    when(completions.claim(OPERATION_ID)).thenReturn(Optional.empty());

    new ReconcileInvitationCompletionsWorkflow(completions, identity).reconcile(OPERATION_ID);

    verify(identity, never())
        .requestCredentialSetup(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    verify(identity, never())
        .getCredentialSetup(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  private InvitationCompletionWork work(InvitationCompletionStatus status) {
    return new InvitationCompletionWork(
        OPERATION_ID, LEASE_TOKEN, USER_ID, status, NOW.plusDays(1));
  }

  private IdentityOperation identity(IdentityOperationStatus status) {
    return new IdentityOperation(OPERATION_ID, USER_ID, status, 0, null, null, null, NOW, NOW);
  }
}
