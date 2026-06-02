package com.odonta.invite.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteInvitationCommand(
    @NotBlank String name, @NotBlank @Size(min = 8) String password) {}
