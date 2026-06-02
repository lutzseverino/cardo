package com.odonta.identity.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateProvisionalUserCommand(@NotBlank @Email String email) {}
