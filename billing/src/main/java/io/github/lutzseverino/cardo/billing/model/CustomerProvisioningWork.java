package io.github.lutzseverino.cardo.billing.model;

import java.util.UUID;

public record CustomerProvisioningWork(
    UUID id, UUID subjectId, String provider, UUID leaseToken, boolean firstRemoteAttempt) {}
