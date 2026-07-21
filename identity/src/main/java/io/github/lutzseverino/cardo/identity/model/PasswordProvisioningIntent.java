package io.github.lutzseverino.cardo.identity.model;

import java.util.UUID;

public record PasswordProvisioningIntent(
    UUID mutationId, UUID leaseToken, String email, String name, String correlationMarker) {}
