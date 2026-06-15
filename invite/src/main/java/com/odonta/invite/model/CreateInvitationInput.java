package com.odonta.invite.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateInvitationInput(
    @NotNull UUID tenantId,
    @NotBlank @Pattern(regexp = "^[^:]+:[^:]+$") String tenantResourceType,
    @NotBlank @Email String email,
    @NotNull UUID accessProfileId) {}
