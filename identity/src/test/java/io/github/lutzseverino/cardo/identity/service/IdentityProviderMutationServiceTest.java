package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.IdentityProviderMutationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutation;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.PasswordProvisioningIntent;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.repository.IdentityProviderMutationRepository;
import io.github.lutzseverino.cardo.identity.workflow.ReconcileIdentityProviderMutationsWorkflow;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityProviderMutationServiceTest {

  @Test
  void freshCredentialsResumeBackingOffWorkWithTheSameCorrelationMarker() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now);
    UUID firstLease = mutation.claim(now.plusMinutes(1));
    mutation.fail(
        firstLease,
        "response lost",
        now,
        Duration.ofHours(1),
        3,
        IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
    when(mutations.findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
            "user@example.com", IdentityProviderMutationType.PROVISION_PASSWORD_USER))
        .thenReturn(Optional.of(mutation));

    PasswordProvisioningIntent resumed =
        service(mutations).requestPasswordProvision("user@example.com", "User");

    assertThat(resumed.mutationId()).isEqualTo(mutation.getId());
    assertThat(resumed.correlationMarker()).isEqualTo("marker-1");
    assertThat(resumed.leaseToken()).isNotEqualTo(firstLease);
    assertThat(mutation.getAttemptCount()).isZero();
    assertThat(mutation.getLastError()).isNull();
    verify(mutations).saveAndFlush(mutation);
  }

  @Test
  void activeProvisioningLeaseStillConflictsWithFreshCredentials() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now);
    mutation.claim(now.plusHours(1));
    when(mutations.findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
            "user@example.com", IdentityProviderMutationType.PROVISION_PASSWORD_USER))
        .thenReturn(Optional.of(mutation));

    assertThatThrownBy(
            () -> service(mutations).requestPasswordProvision("user@example.com", "User"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("user_provisioning_in_progress"));

    verify(mutations, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void credentialResubmissionReusesTheFailedRowsMarkerInsteadOfCreatingAnotherIdentity() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now);
    UUID originalId = mutation.getId();
    UUID firstLease = mutation.claim(now.plusMinutes(1));
    mutation.fail(
        firstLease,
        "not found by marker",
        now,
        Duration.ZERO,
        1,
        IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
    when(mutations.findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
            "user@example.com", IdentityProviderMutationType.PROVISION_PASSWORD_USER))
        .thenReturn(Optional.of(mutation));

    PasswordProvisioningIntent resumed =
        service(mutations).requestPasswordProvision("user@example.com", "User");

    assertThat(resumed.mutationId()).isEqualTo(originalId);
    assertThat(resumed.correlationMarker()).isEqualTo("marker-1");
    verify(mutations).saveAndFlush(mutation);
  }

  @Test
  void correctedCredentialsResumeTheSameMarkerAfterDefiniteProviderRejection() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now);
    UUID firstLease = mutation.claim(now.plusMinutes(1));
    PasswordProvisioningIntent rejectedIntent =
        new PasswordProvisioningIntent(
            mutation.getId(), firstLease, mutation.getEmail(), mutation.getName(), "marker-1");
    when(mutations.findEntityByIdForUpdate(mutation.getId())).thenReturn(Optional.of(mutation));

    service(mutations)
        .recordPasswordDispatchRejection(
            rejectedIntent,
            ApiException.of(400, "identity_provider_error", "password rejected"),
            IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);

    when(mutations.findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
            "user@example.com", IdentityProviderMutationType.PROVISION_PASSWORD_USER))
        .thenReturn(Optional.of(mutation));
    PasswordProvisioningIntent corrected =
        service(mutations).requestPasswordProvision("user@example.com", "User");

    assertThat(corrected.mutationId()).isEqualTo(mutation.getId());
    assertThat(corrected.correlationMarker()).isEqualTo("marker-1");
    assertThat(corrected.leaseToken()).isNotEqualTo(firstLease);
    assertThat(mutation.getStatus()).isEqualTo(IdentityProviderMutationStatus.REQUESTED);
    assertThat(mutation.getTerminalReason()).isNull();
  }

  @Test
  void completionConflictTerminalsTheOwnedPasswordProvisioningLease() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now);
    UUID lease = mutation.claim(now.plusMinutes(1));
    PasswordProvisioningIntent intent =
        new PasswordProvisioningIntent(
            mutation.getId(), lease, mutation.getEmail(), mutation.getName(), "marker-1");
    when(mutations.findEntityByIdForUpdate(mutation.getId())).thenReturn(Optional.of(mutation));

    assertThat(
            service(mutations)
                .recordPasswordCompletionConflict(
                    intent, ApiException.conflict("user_exists", "User already exists.")))
        .isTrue();

    assertThat(mutation.getStatus()).isEqualTo(IdentityProviderMutationStatus.FAILED);
    assertThat(mutation.getTerminalReason())
        .isEqualTo(IdentityProviderMutationTerminalReason.LOCAL_STATE_CONFLICT);
  }

  @Test
  void expiredLeaseConflictRemainsRecoverableUntilTheOriginalMarkerBecomesVisible() {
    IdentityProviderMutationRepository repository = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now.minusHours(1));
    UUID expiredLease = mutation.claim(now.minusMinutes(1));
    PasswordProvisioningIntent expiredOwner =
        new PasswordProvisioningIntent(
            mutation.getId(), expiredLease, mutation.getEmail(), mutation.getName(), "marker-1");
    when(repository.findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
            "user@example.com", IdentityProviderMutationType.PROVISION_PASSWORD_USER))
        .thenReturn(Optional.of(mutation));
    when(repository.findEntityByIdForUpdate(mutation.getId())).thenReturn(Optional.of(mutation));
    when(repository.findReadyIds(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(mutation.getId()));
    IdentityProviderMutationService service = service(repository, Duration.ZERO);

    PasswordProvisioningIntent newOwner =
        service.requestPasswordProvision("user@example.com", "User");
    assertThatThrownBy(
            () -> service.completePasswordProvision(expiredOwner, "subject-1", UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Identity provider mutation lease was lost.");
    service.recordPasswordDispatchFailure(
        newOwner, ApiException.conflict("user_exists", "User already exists."));

    assertThat(mutation.getStatus()).isEqualTo(IdentityProviderMutationStatus.REQUESTED);
    assertThat(mutation.getTerminalReason()).isNull();
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    when(provider.findPasswordIdentityByCorrelationMarker("marker-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));

    new ReconcileIdentityProviderMutationsWorkflow(service, users, provider).reconcile();

    verify(users)
        .recoverPasswordProvision(
            org.mockito.ArgumentMatchers.argThat(
                work ->
                    mutation.getId().equals(work.id())
                        && "marker-1".equals(work.correlationMarker())),
            org.mockito.ArgumentMatchers.eq("subject-1"));
  }

  @Test
  void unsafeTerminalProvisioningReasonRequiresOperatorRepair() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation mutation = passwordProvision(now);
    UUID lease = mutation.claim(now.plusMinutes(1));
    mutation.fail(
        lease,
        "provider rejected",
        now,
        Duration.ZERO,
        1,
        IdentityProviderMutationTerminalReason.PROVIDER_REJECTED);
    when(mutations.findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
            "user@example.com", IdentityProviderMutationType.PROVISION_PASSWORD_USER))
        .thenReturn(Optional.of(mutation));

    assertThatThrownBy(
            () -> service(mutations).requestPasswordProvision("user@example.com", "User"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("user_provisioning_failed"));

    verify(mutations, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void coalescesANewerEnabledTargetIntoTheActiveMutation() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    IdentityProviderMutation mutation =
        IdentityProviderMutation.enabledState(
            UUID.randomUUID(), UUID.randomUUID(), "subject-1", false, OffsetDateTime.now());
    when(mutations.findFirstEntityByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
            mutation.getUserId(),
            IdentityProviderMutationType.SET_IDENTITY_ENABLED,
            IdentityProviderMutationStatus.REQUESTED))
        .thenReturn(Optional.of(mutation));

    service(mutations).requestEnabledState(mutation.getUserId(), "subject-1", true);

    assertThat(mutation.getDesiredEnabled()).isTrue();
    assertThat(mutation.getDesiredVersion()).isEqualTo(2);
    verify(mutations, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void insertsTheFirstEnabledTargetAsDurableWork() {
    IdentityProviderMutationRepository mutations = mock(IdentityProviderMutationRepository.class);
    UUID userId = UUID.randomUUID();
    when(mutations.findFirstEntityByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
            userId,
            IdentityProviderMutationType.SET_IDENTITY_ENABLED,
            IdentityProviderMutationStatus.REQUESTED))
        .thenReturn(Optional.empty());

    service(mutations).requestEnabledState(userId, "subject-1", false);

    verify(mutations)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                mutation ->
                    mutation.getType() == IdentityProviderMutationType.SET_IDENTITY_ENABLED
                        && userId.equals(mutation.getUserId())
                        && Boolean.FALSE.equals(mutation.getDesiredEnabled())));
  }

  private IdentityProviderMutationService service(IdentityProviderMutationRepository mutations) {
    return service(mutations, Duration.ofSeconds(1));
  }

  private IdentityProviderMutationService service(
      IdentityProviderMutationRepository mutations, Duration retryBaseDelay) {
    return new IdentityProviderMutationService(
        mutations,
        new IdentityProviderMutationProperties(
            Duration.ofSeconds(5), retryBaseDelay, Duration.ofMinutes(1), 3, 50));
  }

  private IdentityProviderMutation passwordProvision(OffsetDateTime now) {
    return IdentityProviderMutation.passwordProvision(
        UUID.randomUUID(), "user@example.com", "User", "marker-1", now);
  }
}
