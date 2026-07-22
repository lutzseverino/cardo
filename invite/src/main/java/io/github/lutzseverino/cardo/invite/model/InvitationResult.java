package io.github.lutzseverino.cardo.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResult(
    UUID id,
    UUID requestId,
    UUID tenantId,
    String tenantResourceType,
    String invitedEmail,
    UUID invitedUserId,
    UUID invitedBy,
    InvitationStatus status,
    OffsetDateTime expiresAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime revokedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
