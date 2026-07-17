package io.github.lutzseverino.cardo.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdentityOperationWork(
    UUID id,
    UUID userId,
    String providerSubject,
    IdentityOperationType type,
    IdentityOperationStatus status,
    OffsetDateTime actionExpiresAt) {}
