package io.github.lutzseverino.cardo.billing.model;

public record EntitlementSyncItem(String product, Integer tenantLimit, Integer seatLimit) {}
