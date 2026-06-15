package com.odonta.billing.repository;

import com.odonta.billing.model.EntitlementStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface EntitlementProjection {

  UUID getId();

  UUID getSubjectId();

  String getProduct();

  EntitlementStatus getStatus();

  Integer getTenantLimit();

  Integer getSeatLimit();

  OffsetDateTime getTrialEndsAt();

  OffsetDateTime getCurrentPeriodEndsAt();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getUpdatedAt();
}
