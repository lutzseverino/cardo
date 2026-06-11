package com.odonta.billing.model;

import jakarta.validation.constraints.NotBlank;

public record CreatePortalSessionCommand(@NotBlank String returnUrl) {}
