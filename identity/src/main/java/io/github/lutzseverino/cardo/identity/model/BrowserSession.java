package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.common.data.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "browser_sessions")
public class BrowserSession extends AuditedEntity {

  @Id
  @Column(name = "provider_session_id")
  private String providerSessionId;

  @Column(name = "provider_subject", nullable = false)
  private String providerSubject;

  @Column(name = "refresh_credential_hash", nullable = false, length = 64)
  private String refreshCredentialHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BrowserSessionStatus status;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "idle_expires_at", nullable = false)
  private OffsetDateTime idleExpiresAt;

  @Column(name = "absolute_expires_at", nullable = false)
  private OffsetDateTime absoluteExpiresAt;

  @Column(name = "refresh_lease_token")
  private UUID refreshLeaseToken;

  @Column(name = "refresh_lease_until")
  private OffsetDateTime refreshLeaseUntil;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Version private long version;

  public static BrowserSession start(
      String providerSessionId,
      String providerSubject,
      String refreshCredentialHash,
      OffsetDateTime startedAt,
      OffsetDateTime idleExpiresAt,
      OffsetDateTime absoluteExpiresAt) {
    BrowserSession session = new BrowserSession();
    session.providerSessionId = providerSessionId;
    session.providerSubject = providerSubject;
    session.refreshCredentialHash = refreshCredentialHash;
    session.status = BrowserSessionStatus.ACTIVE;
    session.startedAt = startedAt;
    session.idleExpiresAt = idleExpiresAt;
    session.absoluteExpiresAt = absoluteExpiresAt;
    return session;
  }

  public boolean active() {
    return BrowserSessionStatus.ACTIVE.equals(status);
  }

  public boolean ownsRefreshLease(UUID leaseToken) {
    return leaseToken != null && leaseToken.equals(refreshLeaseToken);
  }

  public boolean refreshInProgress() {
    return refreshLeaseToken != null;
  }

  public boolean refreshLeaseExpired(OffsetDateTime now) {
    return refreshLeaseUntil != null && !refreshLeaseUntil.isAfter(now);
  }

  public UUID claimRefresh(OffsetDateTime leaseUntil) {
    UUID token = UUID.randomUUID();
    refreshLeaseToken = token;
    refreshLeaseUntil = leaseUntil;
    return token;
  }

  public void completeRefresh(
      UUID leaseToken, String refreshCredentialHash, OffsetDateTime idleExpiresAt) {
    requireRefreshLease(leaseToken);
    this.refreshCredentialHash = refreshCredentialHash;
    this.idleExpiresAt = idleExpiresAt;
    clearRefreshLease();
  }

  public boolean revokeRefresh(UUID leaseToken, OffsetDateTime now) {
    if (!ownsRefreshLease(leaseToken)) {
      return false;
    }
    revoke(now);
    return true;
  }

  public void revoke(OffsetDateTime now) {
    if (active()) {
      status = BrowserSessionStatus.REVOKED;
      revokedAt = now;
    }
    clearRefreshLease();
  }

  private void requireRefreshLease(UUID leaseToken) {
    if (!ownsRefreshLease(leaseToken)) {
      throw new IllegalStateException("browser session refresh lease is no longer owned");
    }
  }

  private void clearRefreshLease() {
    refreshLeaseToken = null;
    refreshLeaseUntil = null;
  }
}
