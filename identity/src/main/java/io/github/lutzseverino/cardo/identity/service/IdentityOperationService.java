package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.config.IdentityOperationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityOperation;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationResult;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationWork;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.repository.IdentityOperationRepository;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityOperationService {

  private static final Set<IdentityOperationStatus> ACTIVE_STATUSES =
      Set.of(IdentityOperationStatus.REQUESTED, IdentityOperationStatus.AWAITING_USER);

  private final Clock clock = Clock.systemUTC();
  private final IdentityOperationRepository operations;
  private final UserRepository users;
  private final IdentityOperationProperties properties;

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public IdentityOperationResult requestCredentialSetup(
      UUID operationId, UUID userId, OffsetDateTime notAfter) {
    OffsetDateTime now = now();
    User user = requireLockedUser(userId);
    Optional<IdentityOperation> existing = operations.findById(operationId);
    if (existing.isPresent()) {
      IdentityOperation operation = existing.orElseThrow();
      requireSameOperation(operation, userId, IdentityOperationType.CREDENTIAL_SETUP);
      requireSameDeadline(operation, notAfter);
      if (IdentityOperationStatus.COMPLETED.equals(operation.getStatus())) {
        return toResult(operation);
      }
      if (!notAfter.isAfter(now)) {
        operation.expire("Invitation expired before credential setup completed.", now);
        return toResult(operation);
      }
      requireNoDeletion(userId);
      requireCredentialSetupStatus(user, operation, now);
      if (IdentityOperationStatus.FAILED.equals(operation.getStatus())) {
        requireNoOtherActiveCredentialSetup(userId, operationId);
      }
      operation.retry(now);
      return toResult(operation);
    }
    if (notAfter == null || !notAfter.isAfter(now)) {
      throw ApiException.conflict(
          "credential_setup_expired", "Credential setup deadline already passed.");
    }
    findActiveOperation(userId, IdentityOperationType.CREDENTIAL_SETUP)
        .ifPresent(
            operation -> {
              throw ApiException.conflict(
                  "identity_operation_conflict",
                  "Credential setup was already requested with another operation identifier.");
            });
    requireNoDeletion(userId);
    IdentityOperation operation =
        IdentityOperation.credentialSetup(
            operationId, userId, user.getKeycloakSubject(), notAfter, now);
    requireCredentialSetupStatus(user, operation, now);
    return toResult(operations.saveAndFlush(operation));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public IdentityOperationResult getCredentialSetup(UUID operationId, UUID userId) {
    IdentityOperation operation = requireOperation(operationId);
    requireSameOperation(operation, userId, IdentityOperationType.CREDENTIAL_SETUP);
    return toResult(operation);
  }

  @Transactional
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public IdentityOperationResult requestProvisionalDeletion(UUID userId) {
    OffsetDateTime now = now();
    Optional<IdentityOperation> observed =
        findLatestOperation(userId, IdentityOperationType.PROVISIONAL_DELETION);
    if (observed
        .map(IdentityOperation::getStatus)
        .filter(IdentityOperationStatus.COMPLETED::equals)
        .isPresent()) {
      return toResult(observed.orElseThrow());
    }

    Optional<User> lockedUser = users.findEntityByIdForUpdate(userId);
    if (lockedUser.isEmpty()) {
      return operations
          .findFirstEntityByUserIdAndTypeOrderByCreatedAtDesc(
              userId, IdentityOperationType.PROVISIONAL_DELETION)
          .filter(operation -> IdentityOperationStatus.COMPLETED.equals(operation.getStatus()))
          .map(this::toResult)
          .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
    }

    Optional<IdentityOperation> existing =
        findLatestOperation(userId, IdentityOperationType.PROVISIONAL_DELETION);
    if (existing.isPresent()) {
      IdentityOperation operation = existing.orElseThrow();
      if (IdentityOperationStatus.COMPLETED.equals(operation.getStatus())) {
        return toResult(operation);
      }
      requireInvitedUser(lockedUser.orElseThrow());
      requireNoCredentialSetup(userId);
      operation.retry(now);
      return toResult(operation);
    }
    requireNoCredentialSetup(userId);
    User user = lockedUser.orElseThrow();
    requireInvitedUser(user);
    return toResult(
        operations.saveAndFlush(
            IdentityOperation.provisionalDeletion(
                UUID.randomUUID(), userId, user.getKeycloakSubject(), now)));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAuthority('" + IdentityPermissions.USER_PROVISION_AUTHORITY + "')")
  public IdentityOperationResult getProvisionalDeletion(UUID userId) {
    return operations
        .findFirstEntityByUserIdAndTypeOrderByCreatedAtDesc(
            userId, IdentityOperationType.PROVISIONAL_DELETION)
        .map(this::toResult)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "identity_operation_not_found", "Identity operation not found."));
  }

  @Transactional(readOnly = true)
  public List<UUID> readyIds() {
    return operations.findReadyIds(now(), PageRequest.of(0, properties.batchSize()));
  }

  @Transactional
  public Optional<IdentityOperationWork> claim(UUID operationId) {
    IdentityOperation operation =
        operations
            .findEntityByIdForUpdate(operationId)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        "identity_operation_not_found", "Identity operation not found."));
    OffsetDateTime now = now();
    if (!operation.ready(now)) {
      return Optional.empty();
    }
    if (operation.hardDeadlineExpired(now)) {
      operation.expire("Invitation expired before credential setup completed.", now);
      return Optional.empty();
    }
    operation.claimUntil(now.plus(properties.claimLease()));
    return Optional.of(toWork(operation, now));
  }

  @Transactional
  public void markAwaitingUser(UUID operationId, OffsetDateTime actionExpiresAt) {
    IdentityOperation operation = requireLockedOperation(operationId);
    OffsetDateTime now = now();
    if (operation.hardDeadlineExpired(now) || !actionExpiresAt.isAfter(now)) {
      operation.expire("Invitation expired before credential setup started.", now);
      return;
    }
    operation.awaitUser(now.plus(properties.pollDelay()), actionExpiresAt);
  }

  @Transactional
  public void reschedulePoll(UUID operationId) {
    IdentityOperation operation = requireLockedOperation(operationId);
    OffsetDateTime now = now();
    if (operation.credentialSetupExpired(now)) {
      operation.expire("Credential setup expired before completion.", now);
      return;
    }
    operation.reschedule(now.plus(properties.pollDelay()));
  }

  @Transactional
  public void completeCredentialSetup(UUID operationId, String name) {
    IdentityOperation operation = requireLockedOperation(operationId);
    requireType(operation, IdentityOperationType.CREDENTIAL_SETUP);
    OffsetDateTime now = now();
    if (operation.hardDeadlineExpired(now) || operation.credentialSetupExpired(now)) {
      operation.expire("Invitation expired before credential setup completed.", now);
      return;
    }
    User user = requireUser(operation.getUserId());
    if (UserStatus.INVITED.equals(user.getStatus())) {
      user.complete(name);
    } else if (!UserStatus.ACTIVE.equals(user.getStatus())) {
      throw ApiException.conflict(
          "user_status_conflict", "Identity user cannot complete credential setup.");
    }
    operation.complete(now);
  }

  @Transactional
  public void completeProvisionalDeletion(UUID operationId) {
    IdentityOperation operation = requireLockedOperation(operationId);
    requireType(operation, IdentityOperationType.PROVISIONAL_DELETION);
    users
        .findById(operation.getUserId())
        .ifPresent(
            user -> {
              if (!UserStatus.INVITED.equals(user.getStatus())) {
                throw ApiException.conflict(
                    "user_status_conflict", "Only an invited user can be deleted provisionally.");
              }
              users.delete(user);
            });
    operation.complete(now());
  }

  @Transactional
  public void recordFailure(UUID operationId, RuntimeException failure) {
    IdentityOperation operation = requireLockedOperation(operationId);
    operation.fail(
        safeMessage(failure), now(), properties.retryBaseDelay(), properties.maxAttempts());
  }

  @Transactional
  public void recordTerminalFailure(UUID operationId, RuntimeException failure) {
    requireLockedOperation(operationId).expire(safeMessage(failure), now());
  }

  private void requireNoDeletion(UUID userId) {
    findLatestOperation(userId, IdentityOperationType.PROVISIONAL_DELETION)
        .ifPresent(
            operation -> {
              throw ApiException.conflict(
                  "identity_deletion_pending", "Provisional identity deletion is pending.");
            });
  }

  private void requireCredentialSetupStatus(
      User user, IdentityOperation operation, OffsetDateTime now) {
    if (UserStatus.ACTIVE.equals(user.getStatus())) {
      operation.complete(now);
    } else if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict(
          "user_not_invited", "Only an invited user can start credential setup.");
    }
  }

  private void requireInvitedUser(User user) {
    if (!UserStatus.INVITED.equals(user.getStatus())) {
      throw ApiException.conflict(
          "user_already_complete", "An active identity cannot be cancelled as provisional.");
    }
  }

  private void requireNoCredentialSetup(UUID userId) {
    findActiveOperation(userId, IdentityOperationType.CREDENTIAL_SETUP)
        .ifPresent(
            operation -> {
              throw ApiException.conflict(
                  "identity_activation_pending", "Credential setup is pending for this identity.");
            });
  }

  private void requireNoOtherActiveCredentialSetup(UUID userId, UUID operationId) {
    findActiveOperation(userId, IdentityOperationType.CREDENTIAL_SETUP)
        .filter(operation -> !operation.getId().equals(operationId))
        .ifPresent(
            operation -> {
              throw ApiException.conflict(
                  "identity_operation_conflict",
                  "Credential setup is already active under another operation identifier.");
            });
  }

  private Optional<IdentityOperation> findLatestOperation(UUID userId, IdentityOperationType type) {
    return operations.findFirstEntityByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
  }

  private Optional<IdentityOperation> findActiveOperation(UUID userId, IdentityOperationType type) {
    return operations.findFirstEntityByUserIdAndTypeAndStatusInOrderByCreatedAtDesc(
        userId, type, ACTIVE_STATUSES);
  }

  private User requireUser(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }

  private User requireLockedUser(UUID userId) {
    return users
        .findEntityByIdForUpdate(userId)
        .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
  }

  private IdentityOperation requireOperation(UUID operationId) {
    return operations
        .findById(operationId)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "identity_operation_not_found", "Identity operation not found."));
  }

  private IdentityOperation requireLockedOperation(UUID operationId) {
    return operations
        .findEntityByIdForUpdate(operationId)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "identity_operation_not_found", "Identity operation not found."));
  }

  private void requireSameOperation(
      IdentityOperation operation, UUID userId, IdentityOperationType type) {
    if (!operation.getUserId().equals(userId) || !operation.getType().equals(type)) {
      throw ApiException.conflict(
          "identity_operation_conflict",
          "Operation identifier was already used for another identity operation.");
    }
  }

  private void requireType(IdentityOperation operation, IdentityOperationType type) {
    if (!type.equals(operation.getType())) {
      throw ApiException.conflict(
          "identity_operation_conflict", "Identity operation has an unexpected type.");
    }
  }

  private void requireSameDeadline(IdentityOperation operation, OffsetDateTime notAfter) {
    if (operation.getNotAfter() == null || !operation.getNotAfter().isEqual(notAfter)) {
      throw ApiException.conflict(
          "identity_operation_conflict",
          "Operation identifier was already used with another credential setup deadline.");
    }
  }

  private IdentityOperationResult toResult(IdentityOperation operation) {
    return new IdentityOperationResult(
        operation.getId(),
        operation.getUserId(),
        operation.getType(),
        operation.getStatus(),
        operation.getAttemptCount(),
        operation.getLastError(),
        operation.getExpiresAt(),
        operation.getCompletedAt(),
        operation.getCreatedAt(),
        operation.getUpdatedAt());
  }

  private IdentityOperationWork toWork(IdentityOperation operation, OffsetDateTime now) {
    OffsetDateTime actionExpiresAt = operation.getExpiresAt();
    if (IdentityOperationStatus.REQUESTED.equals(operation.getStatus())) {
      actionExpiresAt =
          earlier(operation.getNotAfter(), now.plus(properties.credentialSetupTimeout()));
    }
    return new IdentityOperationWork(
        operation.getId(),
        operation.getUserId(),
        operation.getProviderSubject(),
        operation.getType(),
        operation.getStatus(),
        actionExpiresAt);
  }

  private OffsetDateTime earlier(OffsetDateTime left, OffsetDateTime right) {
    return left.isBefore(right) ? left : right;
  }

  private String safeMessage(RuntimeException failure) {
    String message = failure.getMessage();
    return Objects.requireNonNullElse(message, failure.getClass().getSimpleName())
        .substring(
            0,
            Math.min(
                1000,
                Objects.requireNonNullElse(message, failure.getClass().getSimpleName()).length()));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
