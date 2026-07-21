package io.github.lutzseverino.cardo.identity.model;

import java.util.UUID;

public record IdentityProviderMutationWork(
    UUID id,
    UUID leaseToken,
    IdentityProviderMutationType type,
    UUID userId,
    String providerSubject,
    String email,
    String name,
    String correlationMarker,
    Boolean desiredEnabled,
    int desiredVersion) {}
