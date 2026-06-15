package com.odonta.invite.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteInvitationInput(
    @NotBlank String name, @NotBlank @Size(min = 8) String password) {}
