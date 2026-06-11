package com.odonta.identity.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthenticateCommand(@NotBlank @Email String email, @NotBlank String password) {}
