package io.github.lutzseverino.cardo.identity.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdentityOperation(
    UUID id,
    UUID userId,
    IdentityOperationStatus status,
    int attemptCount,
    String lastError,
    OffsetDateTime expiresAt,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
