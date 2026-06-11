package com.odonta.billing.model;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutSessionCommand(
    @NotBlank String product, @NotBlank String successUrl, @NotBlank String cancelUrl) {}
