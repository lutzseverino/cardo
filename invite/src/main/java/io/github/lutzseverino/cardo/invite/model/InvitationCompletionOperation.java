package io.github.lutzseverino.cardo.invite.model;

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
@Table(name = "invitation_completion_operations")
public class InvitationCompletionOperation extends AuditedEntity {

  @Id private UUID id;

  @Column(name = "invitation_id", nullable = false, unique = true)
  private UUID invitationId;

  @Column(name = "invited_user_id", nullable = false)
  private UUID invitedUserId;

  @Column(nullable = false)
  private String product;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvitationCompletionStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  @Column(name = "lease_token")
  private UUID leaseToken;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "action_expires_at")
  private OffsetDateTime actionExpiresAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Version private long version;

  public InvitationCompletionOperation(
      UUID invitationId,
      UUID invitedUserId,
      String product,
      OffsetDateTime expiresAt,
      OffsetDateTime now) {
    this.id = invitationId;
    this.invitationId = invitationId;
    this.invitedUserId = invitedUserId;
    this.product = product;
    this.expiresAt = expiresAt;
    this.status = InvitationCompletionStatus.REQUESTED;
    this.nextAttemptAt = now;
  }

  public boolean ready(OffsetDateTime now) {
    return (InvitationCompletionStatus.REQUESTED.equals(status)
            || InvitationCompletionStatus.AWAITING_IDENTITY.equals(status))
        && !nextAttemptAt.isAfter(now);
  }

  public boolean expired(OffsetDateTime now) {
    return !expiresAt.isAfter(now);
  }

  public UUID claimUntil(OffsetDateTime leaseUntil) {
    leaseToken = UUID.randomUUID();
    nextAttemptAt = leaseUntil;
    return leaseToken;
  }

  public boolean ownsLease(UUID token, OffsetDateTime now) {
    return (InvitationCompletionStatus.REQUESTED.equals(status)
            || InvitationCompletionStatus.AWAITING_IDENTITY.equals(status))
        && leaseToken != null
        && leaseToken.equals(token)
        && nextAttemptAt.isAfter(now);
  }

  public void awaitIdentity(OffsetDateTime nextPollAt, OffsetDateTime actionExpiresAt) {
    if (revoked()) {
      return;
    }
    status = InvitationCompletionStatus.AWAITING_IDENTITY;
    attemptCount = 0;
    lastError = null;
    nextAttemptAt = nextPollAt;
    this.actionExpiresAt = actionExpiresAt;
    leaseToken = null;
  }

  public void reschedule(OffsetDateTime nextPollAt, OffsetDateTime actionExpiresAt) {
    if (revoked()) {
      return;
    }
    nextAttemptAt = nextPollAt;
    if (actionExpiresAt != null) {
      this.actionExpiresAt = actionExpiresAt;
    }
    leaseToken = null;
  }

  public void fail(String error, OffsetDateTime now, Duration retryDelay, int maxAttempts) {
    if (revoked()) {
      return;
    }
    attemptCount++;
    lastError = error;
    leaseToken = null;
    if (attemptCount >= maxAttempts) {
      status = InvitationCompletionStatus.FAILED;
      nextAttemptAt = now;
      return;
    }
    nextAttemptAt = now.plus(retryDelay.multipliedBy(1L << Math.min(attemptCount - 1, 10)));
  }

  public void retry(OffsetDateTime now) {
    if (!InvitationCompletionStatus.FAILED.equals(status)) {
      return;
    }
    status = InvitationCompletionStatus.REQUESTED;
    attemptCount = 0;
    lastError = null;
    nextAttemptAt = now;
    actionExpiresAt = null;
    leaseToken = null;
  }

  public void failTerminal(String error, OffsetDateTime now) {
    if (revoked()) {
      return;
    }
    status = InvitationCompletionStatus.FAILED;
    lastError = error;
    nextAttemptAt = now;
    leaseToken = null;
  }

  public void complete(OffsetDateTime now) {
    if (revoked()) {
      return;
    }
    status = InvitationCompletionStatus.COMPLETED;
    completedAt = now;
    lastError = null;
    nextAttemptAt = now;
    leaseToken = null;
  }

  public void revoke(OffsetDateTime now) {
    if (InvitationCompletionStatus.REQUESTED.equals(status)
        || InvitationCompletionStatus.AWAITING_IDENTITY.equals(status)) {
      status = InvitationCompletionStatus.REVOKED;
      nextAttemptAt = now;
      lastError = null;
      leaseToken = null;
    }
  }

  private boolean revoked() {
    return InvitationCompletionStatus.REVOKED.equals(status);
  }
}
