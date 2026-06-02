package com.odonta.billing.model;

import jakarta.validation.constraints.NotBlank;

public record CheckoutSessionCommand(
    @NotBlank String product, @NotBlank String successUrl, @NotBlank String cancelUrl) {}
