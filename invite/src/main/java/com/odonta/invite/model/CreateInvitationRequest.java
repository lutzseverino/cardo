package com.odonta.invite.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateInvitationRequest(
    @NotNull UUID tenantId,
    @NotBlank String tenantResourceType,
    @Email @NotBlank String email,
    @NotNull UUID accessProfileId) {}
