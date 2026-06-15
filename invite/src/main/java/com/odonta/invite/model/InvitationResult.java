package com.odonta.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResult(
    UUID id,
    UUID tenantId,
    String tenantResourceType,
    UUID accessProfileId,
    String invitedEmail,
    UUID invitedUserId,
    InvitationStatus status,
    OffsetDateTime acceptedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
