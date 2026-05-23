package com.odonta.billing.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Entitlement(
    UUID id,
    UUID subjectId,
    String product,
    String status,
    Integer tenantLimit,
    Integer seatLimit,
    OffsetDateTime trialEndsAt,
    OffsetDateTime currentPeriodEndsAt) {}
