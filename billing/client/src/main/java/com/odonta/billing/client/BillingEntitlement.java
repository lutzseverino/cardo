package com.odonta.billing.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingEntitlement(
    UUID id,
    UUID subjectId,
    String product,
    BillingEntitlementStatus status,
    Integer tenantLimit,
    Integer seatLimit,
    OffsetDateTime trialEndsAt,
    OffsetDateTime currentPeriodEndsAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
