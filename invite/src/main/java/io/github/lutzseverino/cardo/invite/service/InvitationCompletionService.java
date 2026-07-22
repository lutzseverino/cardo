package io.github.lutzseverino.cardo.invite.service;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.config.InvitationCompletionProperties;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionOperation;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionResult;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionWork;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.model.PendingInvitation;
import io.github.lutzseverino.cardo.invite.repository.InvitationCompletionOperationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationCompletionService {

  private final Clock clock = Clock.systemUTC();
  private final InvitationCompletionOperationRepository operations;
  private final InvitationCompletionProperties properties;
  private final InvitationService invitations;

  @Transactional
  public InvitationCompletionResult request(String token, String product) {
    PendingInvitation invitation = invitations.requirePendingForUpdate(token, product);
    OffsetDateTime now = now();
    Optional<InvitationCompletionOperation> existing = operations.findById(invitation.id());
    if (existing.isPresent()) {
      InvitationCompletionOperation operation = existing.orElseThrow();
      requireOwner(operation, product);
      operation.retry(now);
      return toResult(operation);
    }
    return toResult(
        operations.saveAndFlush(
            new InvitationCompletionOperation(
                invitation.id(),
                invitation.invitedUserId(),
                product,
                invitation.expiresAt(),
                now)));
  }

  @Transactional(readOnly = true)
  public InvitationCompletionResult get(String token, String product) {
    UUID invitationId = invitations.requireOwnedId(token, product);
    InvitationCompletionOperation operation = requireOperation(invitationId);
    requireOwner(operation, product);
    return toResult(operation);
  }

  @Transactional(readOnly = true)
  public List<UUID> readyIds() {
    return operations.findReadyIds(now(), PageRequest.of(0, properties.batchSize()));
  }

  @Transactional
  public Optional<InvitationCompletionWork> claim(UUID operationId) {
    InvitationStatus invitationStatus = invitations.lockStatus(operationId);
    InvitationCompletionOperation operation = requireLockedOperation(operationId);
    OffsetDateTime now = now();
    if (InvitationStatus.REVOKED.equals(invitationStatus)) {
      operation.revoke(now);
      return Optional.empty();
    }
    if (!operation.ready(now)) {
      return Optional.empty();
    }
    if (operation.expired(now)) {
      operation.failTerminal("Invitation expired before credential setup completed.", now);
      return Optional.empty();
    }
    operation.claimUntil(now.plus(properties.claimLease()));
    return Optional.of(
        new InvitationCompletionWork(
            operation.getId(),
            operation.getInvitedUserId(),
            operation.getStatus(),
            operation.getExpiresAt()));
  }

  @Transactional
  public void markAwaitingIdentity(UUID operationId, OffsetDateTime actionExpiresAt) {
    requireLockedOperation(operationId)
        .awaitIdentity(now().plus(properties.pollDelay()), actionExpiresAt);
  }

  @Transactional
  public void reschedule(UUID operationId, OffsetDateTime actionExpiresAt) {
    requireLockedOperation(operationId)
        .reschedule(now().plus(properties.pollDelay()), actionExpiresAt);
  }

  @Transactional
  public void complete(UUID operationId) {
    InvitationCompletionOperation operation = requireLockedOperation(operationId);
    OffsetDateTime now = now();
    if (operation.expired(now)) {
      operation.failTerminal("Invitation expired before credential setup completed.", now);
      return;
    }
    operation.complete(now);
  }

  @Transactional
  public void recordFailure(UUID operationId, RuntimeException failure) {
    requireLockedOperation(operationId)
        .fail(safeMessage(failure), now(), properties.retryBaseDelay(), properties.maxAttempts());
  }

  @Transactional
  public void recordTerminalFailure(UUID operationId, RuntimeException failure) {
    requireLockedOperation(operationId).failTerminal(safeMessage(failure), now());
  }

  @Transactional
  public void recordIdentityFailure(UUID operationId, String identityError) {
    String detail = Objects.requireNonNullElse(identityError, "unknown identity failure");
    requireLockedOperation(operationId)
        .failTerminal(safeMessage("Identity credential setup failed: " + detail), now());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void revoke(UUID invitationId) {
    invitations.lockStatus(invitationId);
    operations
        .findEntityByIdForUpdate(invitationId)
        .ifPresent(operation -> operation.revoke(now()));
  }

  private InvitationCompletionOperation requireOperation(UUID operationId) {
    return operations
        .findById(operationId)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "invitation_completion_not_found", "Invitation completion was not requested."));
  }

  private InvitationCompletionOperation requireLockedOperation(UUID operationId) {
    return operations
        .findEntityByIdForUpdate(operationId)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "invitation_completion_not_found", "Invitation completion was not requested."));
  }

  private void requireOwner(InvitationCompletionOperation operation, String product) {
    if (!operation.getProduct().equals(product)) {
      throw ApiException.forbidden(
          "invitation_product_mismatch", "This invitation belongs to another product.");
    }
  }

  private InvitationCompletionResult toResult(InvitationCompletionOperation operation) {
    return new InvitationCompletionResult(
        operation.getId(),
        operation.getInvitationId(),
        operation.getInvitedUserId(),
        operation.getStatus(),
        operation.getAttemptCount(),
        operation.getLastError(),
        operation.getActionExpiresAt(),
        operation.getCompletedAt(),
        operation.getCreatedAt(),
        operation.getUpdatedAt());
  }

  private String safeMessage(RuntimeException failure) {
    return safeMessage(
        Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getSimpleName()));
  }

  private String safeMessage(String value) {
    return value.substring(0, Math.min(1000, value.length()));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
