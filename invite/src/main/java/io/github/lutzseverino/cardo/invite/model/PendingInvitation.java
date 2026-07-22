package io.github.lutzseverino.cardo.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PendingInvitation(
    UUID id,
    String product,
    UUID tenantId,
    String tenantResourceType,
    UUID invitedUserId,
    OffsetDateTime expiresAt) {}
