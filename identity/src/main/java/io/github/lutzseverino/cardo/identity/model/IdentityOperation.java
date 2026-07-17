package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.common.data.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "identity_operations")
public class IdentityOperation extends AuditedEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "provider_subject", nullable = false)
  private String providerSubject;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_type", nullable = false)
  private IdentityOperationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IdentityOperationStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "not_after")
  private OffsetDateTime notAfter;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Version private long version;

  public static IdentityOperation credentialSetup(
      UUID id, UUID userId, String providerSubject, OffsetDateTime notAfter, OffsetDateTime now) {
    IdentityOperation operation =
        new IdentityOperation(
            id, userId, providerSubject, IdentityOperationType.CREDENTIAL_SETUP, now);
    operation.notAfter = notAfter;
    return operation;
  }

  public static IdentityOperation provisionalDeletion(
      UUID id, UUID userId, String providerSubject, OffsetDateTime now) {
    return new IdentityOperation(
        id, userId, providerSubject, IdentityOperationType.PROVISIONAL_DELETION, now);
  }

  private IdentityOperation(
      UUID id,
      UUID userId,
      String providerSubject,
      IdentityOperationType type,
      OffsetDateTime now) {
    this.id = id;
    this.userId = userId;
    this.providerSubject = providerSubject;
    this.type = type;
    this.status = IdentityOperationStatus.REQUESTED;
    this.nextAttemptAt = now;
  }

  public boolean ready(OffsetDateTime now) {
    return (IdentityOperationStatus.REQUESTED.equals(status)
            || IdentityOperationStatus.AWAITING_USER.equals(status))
        && !nextAttemptAt.isAfter(now);
  }

  public boolean hardDeadlineExpired(OffsetDateTime now) {
    return IdentityOperationType.CREDENTIAL_SETUP.equals(type)
        && notAfter != null
        && !notAfter.isAfter(now);
  }

  public void claimUntil(OffsetDateTime leaseUntil) {
    nextAttemptAt = leaseUntil;
  }

  public void awaitUser(OffsetDateTime nextPollAt, OffsetDateTime expiresAt) {
    status = IdentityOperationStatus.AWAITING_USER;
    attemptCount = 0;
    lastError = null;
    nextAttemptAt = nextPollAt;
    this.expiresAt = expiresAt;
  }

  public void reschedule(OffsetDateTime nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public void fail(String error, OffsetDateTime now, Duration retryDelay, int maxAttempts) {
    attemptCount++;
    lastError = error;
    if (attemptCount >= maxAttempts) {
      status = IdentityOperationStatus.FAILED;
      nextAttemptAt = now;
      return;
    }
    nextAttemptAt = now.plus(retryDelay.multipliedBy(1L << Math.min(attemptCount - 1, 10)));
  }

  public void retry(OffsetDateTime now) {
    if (!IdentityOperationStatus.FAILED.equals(status)) {
      return;
    }
    status = IdentityOperationStatus.REQUESTED;
    attemptCount = 0;
    lastError = null;
    nextAttemptAt = now;
    expiresAt = null;
  }

  public boolean credentialSetupExpired(OffsetDateTime now) {
    return IdentityOperationStatus.AWAITING_USER.equals(status)
        && expiresAt != null
        && !expiresAt.isAfter(now);
  }

  public void expire(String error, OffsetDateTime now) {
    status = IdentityOperationStatus.FAILED;
    lastError = error;
    nextAttemptAt = now;
  }

  public void complete(OffsetDateTime now) {
    status = IdentityOperationStatus.COMPLETED;
    completedAt = now;
    lastError = null;
    nextAttemptAt = now;
  }
}
