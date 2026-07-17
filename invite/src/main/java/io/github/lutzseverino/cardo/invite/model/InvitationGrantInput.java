package io.github.lutzseverino.cardo.invite.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InvitationGrantInput(
    @NotBlank @Pattern(regexp = "^[^:]+:[^:]+$") String resourceType, @NotBlank String action) {}
