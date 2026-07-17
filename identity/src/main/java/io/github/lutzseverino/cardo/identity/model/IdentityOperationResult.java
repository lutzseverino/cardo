package io.github.lutzseverino.cardo.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdentityOperationResult(
    UUID id,
    UUID userId,
    IdentityOperationType type,
    IdentityOperationStatus status,
    int attemptCount,
    String lastError,
    OffsetDateTime expiresAt,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
