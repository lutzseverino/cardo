package com.odonta.common.data;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class RetainedDataEntity extends AuditedEntity {

  @Column(name = "restricted_at")
  private OffsetDateTime restrictedAt;

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  @Column(name = "retention_until")
  private OffsetDateTime retentionUntil;

  @Enumerated(EnumType.STRING)
  @Column(name = "retention_reason")
  private DataRetentionReason retentionReason;

  @Column(name = "purged_at")
  private OffsetDateTime purgedAt;

  public void restrictProcessing(OffsetDateTime restrictedAt) {
    this.restrictedAt = restrictedAt;
  }

  public void unrestrictProcessing() {
    restrictedAt = null;
  }

  public void archive(OffsetDateTime archivedAt, DataRetentionPolicy retentionPolicy) {
    if (retentionPolicy == null) {
      throw new IllegalArgumentException("retentionPolicy is required.");
    }
    this.archivedAt = archivedAt;
    retentionUntil = retentionPolicy.retainUntil(archivedAt);
    retentionReason = retentionPolicy.reason();
  }

  public void restore() {
    archivedAt = null;
    retentionUntil = null;
    retentionReason = null;
  }

  public boolean archived() {
    return archivedAt != null;
  }

  public boolean retentionExpired(OffsetDateTime now) {
    return retentionUntil != null && !retentionUntil.isAfter(now);
  }
}
