package io.github.lutzseverino.cardo.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationTokenResult(
    UUID id,
    UUID tenantId,
    String tenantResourceType,
    String invitedEmail,
    OffsetDateTime expiresAt) {}
