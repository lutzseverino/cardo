package io.github.lutzseverino.cardo.billing.model;

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
@Entity
@Table(name = "billing_customer_provisioning_operations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerProvisioningOperation extends AuditedEntity {

  @Id private UUID id;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(nullable = false)
  private String provider;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CustomerProvisioningStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "remote_attempted_at")
  private OffsetDateTime remoteAttemptedAt;

  @Column(name = "lease_token")
  private UUID leaseToken;

  @Column(name = "provider_customer_id")
  private String providerCustomerId;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Version private long version;

  public static CustomerProvisioningOperation request(
      UUID id, UUID subjectId, String provider, OffsetDateTime now) {
    CustomerProvisioningOperation operation = new CustomerProvisioningOperation();
    operation.id = id;
    operation.subjectId = subjectId;
    operation.provider = provider;
    operation.status = CustomerProvisioningStatus.REQUESTED;
    operation.nextAttemptAt = now;
    return operation;
  }

  public boolean ready(OffsetDateTime now) {
    return CustomerProvisioningStatus.REQUESTED.equals(status) && !nextAttemptAt.isAfter(now);
  }

  public boolean claim(UUID leaseToken, OffsetDateTime now, Duration lease) {
    boolean firstRemoteAttempt = remoteAttemptedAt == null;
    if (firstRemoteAttempt) {
      remoteAttemptedAt = now;
    }
    this.leaseToken = leaseToken;
    nextAttemptAt = now.plus(lease);
    return firstRemoteAttempt;
  }

  public boolean ownsLease(UUID leaseToken, OffsetDateTime now) {
    return CustomerProvisioningStatus.REQUESTED.equals(status)
        && this.leaseToken != null
        && this.leaseToken.equals(leaseToken)
        && nextAttemptAt.isAfter(now);
  }

  public void complete(String providerCustomerId, OffsetDateTime now) {
    this.providerCustomerId = providerCustomerId;
    status = CustomerProvisioningStatus.COMPLETED;
    completedAt = now;
    lastError = null;
    leaseToken = null;
    nextAttemptAt = now;
  }

  public void fail(String error, OffsetDateTime now, Duration retryDelay, int maxAttempts) {
    attemptCount++;
    lastError = error;
    leaseToken = null;
    if (attemptCount >= maxAttempts) {
      status = CustomerProvisioningStatus.FAILED;
      nextAttemptAt = now;
      return;
    }
    nextAttemptAt = now.plus(retryDelay.multipliedBy(1L << Math.min(attemptCount - 1, 10)));
  }

  public void failTerminal(String error, OffsetDateTime now) {
    attemptCount++;
    status = CustomerProvisioningStatus.FAILED;
    lastError = error;
    leaseToken = null;
    nextAttemptAt = now;
  }
}
