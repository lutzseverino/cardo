package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.IdentityProviderMutationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutation;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationWork;
import io.github.lutzseverino.cardo.identity.model.PasswordProvisioningIntent;
import io.github.lutzseverino.cardo.identity.repository.IdentityProviderMutationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityProviderMutationService {

  private final Clock clock = Clock.systemUTC();
  private final IdentityProviderMutationRepository mutations;
  private final IdentityProviderMutationProperties properties;

  @Transactional
  public PasswordProvisioningIntent requestPasswordProvision(String email, String name) {
    OffsetDateTime now = now();
    IdentityProviderMutation mutation =
        mutations
            .findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
                email, IdentityProviderMutationType.PROVISION_PASSWORD_USER)
            .map(existing -> reusablePasswordProvision(existing, name, now))
            .orElseGet(
                () ->
                    IdentityProviderMutation.passwordProvision(
                        UUID.randomUUID(), email, name, UUID.randomUUID().toString(), now));
    if (!mutation.ready(now)) {
      throw ApiException.conflict(
          "user_provisioning_in_progress", "User provisioning is already in progress.");
    }
    UUID leaseToken = mutation.claim(now.plus(properties.claimLease()));
    try {
      mutations.saveAndFlush(mutation);
    } catch (DataIntegrityViolationException failure) {
      throw ApiException.conflict(
          "user_provisioning_in_progress", "User provisioning is already in progress.");
    }
    return new PasswordProvisioningIntent(
        mutation.getId(), leaseToken, email, name, mutation.getCorrelationMarker());
  }

  @Transactional
  public IdentityProviderMutationWork requestProvisionalProvision(String email) {
    OffsetDateTime now = now();
    IdentityProviderMutation mutation =
        mutations
            .findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
                email, IdentityProviderMutationType.PROVISION_PROVISIONAL_USER)
            .map(existing -> reusableProvisionalProvision(existing, now))
            .orElseGet(
                () ->
                    IdentityProviderMutation.provisionalProvision(
                        UUID.randomUUID(), email, UUID.randomUUID().toString(), now));
    if (!mutation.ready(now)) {
      throw ApiException.conflict(
          "user_provisioning_in_progress", "User provisioning is already in progress.");
    }
    UUID leaseToken = mutation.claim(now.plus(properties.claimLease()));
    try {
      mutations.saveAndFlush(mutation);
    } catch (DataIntegrityViolationException failure) {
      throw ApiException.conflict(
          "user_provisioning_in_progress", "User provisioning is already in progress.");
    }
    return toWork(mutation, leaseToken);
  }

  @Transactional
  public UUID completePasswordProvision(
      PasswordProvisioningIntent intent, String providerSubject, UUID userId) {
    IdentityProviderMutation mutation = requireLocked(intent.mutationId());
    mutation.bindProvisionedUser(intent.leaseToken(), providerSubject, userId, now());
    IdentityProviderMutation binding =
        IdentityProviderMutation.bindUser(UUID.randomUUID(), userId, providerSubject, now());
    mutations.save(binding);
    return binding.getId();
  }

  @Transactional
  public UUID completeRecoveredPasswordProvision(
      IdentityProviderMutationWork work, String providerSubject, UUID userId) {
    IdentityProviderMutation mutation = requireLocked(work.id());
    mutation.bindProvisionedUser(work.leaseToken(), providerSubject, userId, now());
    IdentityProviderMutation binding =
        IdentityProviderMutation.bindUser(UUID.randomUUID(), userId, providerSubject, now());
    mutations.save(binding);
    return binding.getId();
  }

  @Transactional
  public UUID completeProvisionalProvision(
      IdentityProviderMutationWork work, String providerSubject, UUID userId) {
    IdentityProviderMutation mutation = requireLocked(work.id());
    mutation.bindProvisionedUser(work.leaseToken(), providerSubject, userId, now());
    IdentityProviderMutation binding =
        IdentityProviderMutation.bindUser(UUID.randomUUID(), userId, providerSubject, now());
    mutations.save(binding);
    return binding.getId();
  }

  @Transactional
  public void requestEnabledState(UUID userId, String providerSubject, boolean desiredEnabled) {
    OffsetDateTime now = now();
    Optional<IdentityProviderMutation> active =
        mutations.findFirstEntityByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
            userId,
            IdentityProviderMutationType.SET_IDENTITY_ENABLED,
            IdentityProviderMutationStatus.REQUESTED);
    if (active.isPresent()) {
      active.orElseThrow().changeEnabledTarget(desiredEnabled, now);
      return;
    }
    mutations.save(
        IdentityProviderMutation.enabledState(
            UUID.randomUUID(), userId, providerSubject, desiredEnabled, now));
  }

  @Transactional(readOnly = true)
  public List<UUID> readyIds() {
    return mutations.findReadyIds(now(), PageRequest.of(0, properties.batchSize()));
  }

  @Transactional
  public Optional<IdentityProviderMutationWork> claim(UUID mutationId) {
    IdentityProviderMutation mutation = requireLocked(mutationId);
    OffsetDateTime now = now();
    if (!mutation.ready(now)) {
      return Optional.empty();
    }
    UUID leaseToken = mutation.claim(now.plus(properties.claimLease()));
    return Optional.of(toWork(mutation, leaseToken));
  }

  @Transactional
  public boolean complete(IdentityProviderMutationWork work) {
    return requireLocked(work.id()).complete(work.leaseToken(), work.desiredVersion(), now());
  }

  @Transactional
  public boolean recordFailure(
      IdentityProviderMutationWork work,
      RuntimeException failure,
      IdentityProviderMutationTerminalReason exhaustedReason) {
    return requireLocked(work.id())
        .fail(
            work.leaseToken(),
            safeMessage(failure),
            now(),
            properties.retryBaseDelay(),
            properties.maxAttempts(),
            exhaustedReason);
  }

  @Transactional
  public boolean recordTerminalFailure(
      IdentityProviderMutationWork work,
      RuntimeException failure,
      IdentityProviderMutationTerminalReason reason) {
    return requireLocked(work.id())
        .terminal(work.leaseToken(), safeMessage(failure), reason, now());
  }

  @Transactional
  public void recordPasswordDispatchFailure(
      PasswordProvisioningIntent intent, RuntimeException failure) {
    IdentityProviderMutation mutation = requireLocked(intent.mutationId());
    mutation.fail(
        intent.leaseToken(),
        safeMessage(failure),
        now(),
        properties.retryBaseDelay(),
        properties.maxAttempts(),
        IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
  }

  @Transactional
  public void recordPasswordDispatchRejection(
      PasswordProvisioningIntent intent,
      RuntimeException failure,
      IdentityProviderMutationTerminalReason reason) {
    if (reason != IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED
        && reason != IdentityProviderMutationTerminalReason.PROVIDER_REJECTED) {
      throw new IllegalArgumentException("Unsupported password provisioning terminal reason.");
    }
    requireLocked(intent.mutationId())
        .terminal(intent.leaseToken(), safeMessage(failure), reason, now());
  }

  @Transactional
  public boolean recordPasswordCompletionConflict(
      PasswordProvisioningIntent intent, RuntimeException failure) {
    return requireLocked(intent.mutationId())
        .terminal(
            intent.leaseToken(),
            safeMessage(failure),
            IdentityProviderMutationTerminalReason.LOCAL_STATE_CONFLICT,
            now());
  }

  private IdentityProviderMutation reusablePasswordProvision(
      IdentityProviderMutation mutation, String name, OffsetDateTime now) {
    if (!Objects.equals(mutation.getName(), name)) {
      throw ApiException.conflict(
          "user_provisioning_conflict",
          "User provisioning was already requested with different profile data.");
    }
    if (IdentityProviderMutationStatus.COMPLETED.equals(mutation.getStatus())) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    if (IdentityProviderMutationStatus.FAILED.equals(mutation.getStatus())) {
      if (!IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED.equals(
          mutation.getTerminalReason())) {
        throw ApiException.conflict(
            "user_provisioning_failed",
            "User provisioning failed and requires operator repair before retrying.");
      }
    } else if (mutation.hasActiveLease(now)) {
      throw ApiException.conflict(
          "user_provisioning_in_progress", "User provisioning is already in progress.");
    }
    mutation.resumePasswordProvision(now);
    return mutation;
  }

  private IdentityProviderMutation reusableProvisionalProvision(
      IdentityProviderMutation mutation, OffsetDateTime now) {
    if (IdentityProviderMutationStatus.COMPLETED.equals(mutation.getStatus())) {
      throw ApiException.conflict("user_exists", "A user with this email already exists.");
    }
    if (IdentityProviderMutationStatus.FAILED.equals(mutation.getStatus())) {
      throw ApiException.conflict(
          "user_provisioning_failed",
          "User provisioning failed and requires operator repair before retrying.");
    }
    if (mutation.hasActiveLease(now)) {
      throw ApiException.conflict(
          "user_provisioning_in_progress", "User provisioning is already in progress.");
    }
    return mutation;
  }

  private IdentityProviderMutation requireLocked(UUID mutationId) {
    return mutations
        .findEntityByIdForUpdate(mutationId)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "identity_provider_mutation_not_found",
                    "Identity provider mutation not found."));
  }

  private IdentityProviderMutationWork toWork(IdentityProviderMutation mutation, UUID leaseToken) {
    return new IdentityProviderMutationWork(
        mutation.getId(),
        leaseToken,
        mutation.getType(),
        mutation.getUserId(),
        mutation.getProviderSubject(),
        mutation.getEmail(),
        mutation.getName(),
        mutation.getCorrelationMarker(),
        mutation.getDesiredEnabled(),
        mutation.getDesiredVersion());
  }

  private String safeMessage(RuntimeException failure) {
    String value =
        Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName());
    return value.substring(0, Math.min(value.length(), 1000));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
