package com.odonta.common.data;

import java.time.OffsetDateTime;
import java.time.Period;

public record MedicalRetentionPolicy(Period retentionPeriod, DataRetentionReason reason)
    implements DataRetentionPolicy {

  public MedicalRetentionPolicy {
    if (retentionPeriod == null || retentionPeriod.isNegative() || retentionPeriod.isZero()) {
      throw new IllegalArgumentException("retentionPeriod must be positive.");
    }
    if (reason == null) {
      throw new IllegalArgumentException("reason is required.");
    }
  }

  public static MedicalRetentionPolicy records(Period retentionPeriod) {
    return new MedicalRetentionPolicy(
        retentionPeriod, DataRetentionReason.MEDICAL_RECORD_RETENTION);
  }

  @Override
  public OffsetDateTime retainUntil(OffsetDateTime archivedAt) {
    if (archivedAt == null) {
      throw new IllegalArgumentException("archivedAt is required.");
    }
    return archivedAt.plus(retentionPeriod);
  }
}
