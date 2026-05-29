package com.odonta.invite.model;

import java.util.UUID;

public record CreateInvitationCommand(
    UUID tenantId, String tenantResourceType, String email, UUID accessProfileId) {}
