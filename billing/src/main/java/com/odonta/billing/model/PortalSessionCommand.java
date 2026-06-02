package com.odonta.billing.model;

import jakarta.validation.constraints.NotBlank;

public record PortalSessionCommand(@NotBlank String returnUrl) {}
