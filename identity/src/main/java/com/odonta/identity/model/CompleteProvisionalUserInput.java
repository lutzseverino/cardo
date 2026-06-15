package com.odonta.identity.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteProvisionalUserInput(
    @NotBlank String name, @NotBlank @Size(min = 8) String password) {}
