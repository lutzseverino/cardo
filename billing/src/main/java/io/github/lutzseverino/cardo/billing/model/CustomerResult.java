package io.github.lutzseverino.cardo.billing.model;

import java.util.UUID;

public record CustomerResult(UUID id, UUID subjectId, String provider, String providerCustomerId) {}
