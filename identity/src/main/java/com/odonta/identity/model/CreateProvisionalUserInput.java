package com.odonta.identity.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateProvisionalUserInput(@NotBlank @Email String email) {}
