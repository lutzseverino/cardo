package io.github.lutzseverino.cardo.invite.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Invitation(
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
