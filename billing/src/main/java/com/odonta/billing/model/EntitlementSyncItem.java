package com.odonta.billing.model;

public record EntitlementSyncItem(String product, Integer tenantLimit, Integer seatLimit) {}
