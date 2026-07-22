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
@Table(name = "identity_provider_mutations")
public class IdentityProviderMutation extends AuditedEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "mutation_type", nullable = false)
  private IdentityProviderMutationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IdentityProviderMutationStatus status;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "provider_subject")
  private String providerSubject;

  @Column(name = "normalized_email")
  private String email;

  @Column(name = "display_name")
  private String name;

  @Column(name = "correlation_marker")
  private String correlationMarker;

  @Column(name = "desired_enabled")
  private Boolean desiredEnabled;

  @Column(name = "desired_version", nullable = false)
  private int desiredVersion;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  @Column(name = "lease_token")
  private UUID leaseToken;

  @Column(name = "lease_until")
  private OffsetDateTime leaseUntil;

  @Column(name = "last_error")
  private String lastError;

  @Enumerated(EnumType.STRING)
  @Column(name = "terminal_reason")
  private IdentityProviderMutationTerminalReason terminalReason;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Version private long version;

  public static IdentityProviderMutation passwordProvision(
      UUID id, String email, String name, String correlationMarker, OffsetDateTime now) {
    IdentityProviderMutation mutation =
        new IdentityProviderMutation(id, IdentityProviderMutationType.PROVISION_PASSWORD_USER, now);
    mutation.email = email;
    mutation.name = name;
    mutation.correlationMarker = correlationMarker;
    return mutation;
  }

  public static IdentityProviderMutation provisionalProvision(
      UUID id, String email, String correlationMarker, OffsetDateTime now) {
    IdentityProviderMutation mutation =
        new IdentityProviderMutation(
            id, IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, now);
    mutation.email = email;
    mutation.correlationMarker = correlationMarker;
    return mutation;
  }

  public static IdentityProviderMutation bindUser(
      UUID id, UUID userId, String providerSubject, OffsetDateTime now) {
    IdentityProviderMutation mutation =
        new IdentityProviderMutation(id, IdentityProviderMutationType.BIND_USER_ID, now);
    mutation.userId = userId;
    mutation.providerSubject = providerSubject;
    return mutation;
  }

  public static IdentityProviderMutation enabledState(
      UUID id, UUID userId, String providerSubject, boolean desiredEnabled, OffsetDateTime now) {
    IdentityProviderMutation mutation =
        new IdentityProviderMutation(id, IdentityProviderMutationType.SET_IDENTITY_ENABLED, now);
    mutation.userId = userId;
    mutation.providerSubject = providerSubject;
    mutation.desiredEnabled = desiredEnabled;
    mutation.desiredVersion = 1;
    return mutation;
  }

  private IdentityProviderMutation(UUID id, IdentityProviderMutationType type, OffsetDateTime now) {
    this.id = id;
    this.type = type;
    this.status = IdentityProviderMutationStatus.REQUESTED;
    this.nextAttemptAt = now;
  }

  public boolean ready(OffsetDateTime now) {
    return IdentityProviderMutationStatus.REQUESTED.equals(status)
        && !nextAttemptAt.isAfter(now)
        && (leaseUntil == null || !leaseUntil.isAfter(now));
  }

  public UUID claim(OffsetDateTime leaseUntil) {
    leaseToken = UUID.randomUUID();
    this.leaseUntil = leaseUntil;
    return leaseToken;
  }

  public boolean ownsLease(UUID token) {
    return status == IdentityProviderMutationStatus.REQUESTED
        && leaseToken != null
        && leaseToken.equals(token);
  }

  public void changeEnabledTarget(boolean enabled, OffsetDateTime now) {
    if (!Boolean.valueOf(enabled).equals(desiredEnabled)) {
      desiredEnabled = enabled;
      desiredVersion++;
    }
    status = IdentityProviderMutationStatus.REQUESTED;
    attemptCount = 0;
    nextAttemptAt = now;
    leaseToken = null;
    leaseUntil = null;
    lastError = null;
    terminalReason = null;
    completedAt = null;
  }

  public boolean hasActiveLease(OffsetDateTime now) {
    return leaseUntil != null && leaseUntil.isAfter(now);
  }

  public void resumePasswordProvision(OffsetDateTime now) {
    status = IdentityProviderMutationStatus.REQUESTED;
    attemptCount = 0;
    nextAttemptAt = now;
    leaseToken = null;
    leaseUntil = null;
    lastError = null;
    terminalReason = null;
    completedAt = null;
  }

  public boolean complete(UUID token, int appliedDesiredVersion, OffsetDateTime now) {
    if (!ownsLease(token)) {
      return false;
    }
    if (type == IdentityProviderMutationType.SET_IDENTITY_ENABLED
        && desiredVersion != appliedDesiredVersion) {
      release(now);
      return false;
    }
    status = IdentityProviderMutationStatus.COMPLETED;
    completedAt = now;
    lastError = null;
    terminalReason = null;
    leaseToken = null;
    leaseUntil = null;
    nextAttemptAt = now;
    return true;
  }

  public void bindProvisionedUser(UUID token, String subject, UUID userId, OffsetDateTime now) {
    if (!ownsLease(token)) {
      throw new IllegalStateException("Identity provider mutation lease was lost.");
    }
    providerSubject = subject;
    this.userId = userId;
    status = IdentityProviderMutationStatus.COMPLETED;
    completedAt = now;
    lastError = null;
    terminalReason = null;
    leaseToken = null;
    leaseUntil = null;
    nextAttemptAt = now;
  }

  public boolean fail(
      UUID token,
      String error,
      OffsetDateTime now,
      Duration retryBaseDelay,
      int maxAttempts,
      IdentityProviderMutationTerminalReason exhaustedReason) {
    if (!ownsLease(token)) {
      return false;
    }
    attemptCount++;
    lastError = error;
    leaseToken = null;
    leaseUntil = null;
    if (attemptCount >= maxAttempts) {
      terminal(error, exhaustedReason, now);
      return true;
    }
    nextAttemptAt = now.plus(retryBaseDelay.multipliedBy(1L << Math.min(attemptCount - 1, 10)));
    return false;
  }

  public boolean terminal(
      UUID token, String error, IdentityProviderMutationTerminalReason reason, OffsetDateTime now) {
    if (!ownsLease(token)) {
      return false;
    }
    terminal(error, reason, now);
    return true;
  }

  private void terminal(
      String error, IdentityProviderMutationTerminalReason reason, OffsetDateTime now) {
    status = IdentityProviderMutationStatus.FAILED;
    lastError = error;
    terminalReason = reason;
    leaseToken = null;
    leaseUntil = null;
    nextAttemptAt = now;
  }

  private void release(OffsetDateTime now) {
    leaseToken = null;
    leaseUntil = null;
    nextAttemptAt = now;
  }
}
