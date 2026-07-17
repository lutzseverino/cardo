package io.github.lutzseverino.cardo.invite.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationToken(
    UUID id,
    UUID tenantId,
    String tenantResourceType,
    String invitedEmail,
    OffsetDateTime expiresAt) {}
