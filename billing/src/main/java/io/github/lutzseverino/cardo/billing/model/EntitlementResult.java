package io.github.lutzseverino.cardo.billing.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EntitlementResult(
    UUID id,
    UUID subjectId,
    String product,
    EntitlementStatus status,
    Integer tenantLimit,
    Integer seatLimit,
    OffsetDateTime trialEndsAt,
    OffsetDateTime currentPeriodEndsAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
