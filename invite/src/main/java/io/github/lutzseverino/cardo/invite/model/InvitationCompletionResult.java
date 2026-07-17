package io.github.lutzseverino.cardo.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationCompletionResult(
    UUID id,
    UUID invitationId,
    UUID invitedUserId,
    InvitationCompletionStatus status,
    int attemptCount,
    String lastError,
    OffsetDateTime actionExpiresAt,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
