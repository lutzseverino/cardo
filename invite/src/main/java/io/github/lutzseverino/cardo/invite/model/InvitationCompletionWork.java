package io.github.lutzseverino.cardo.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationCompletionWork(
    UUID id, UUID invitedUserId, InvitationCompletionStatus status, OffsetDateTime expiresAt) {}
