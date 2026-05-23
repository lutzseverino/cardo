package com.odonta.billing.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EntitlementResponse(
    UUID id,
    UUID subjectId,
    String product,
    String status,
    Integer tenantLimit,
    Integer seatLimit,
    OffsetDateTime trialEndsAt,
    OffsetDateTime currentPeriodEndsAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
