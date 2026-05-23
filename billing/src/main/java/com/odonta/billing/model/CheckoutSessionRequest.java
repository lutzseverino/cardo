package com.odonta.billing.model;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record CheckoutSessionRequest(
    @NotBlank String product, @NotBlank @URL String successUrl, @NotBlank @URL String cancelUrl) {}
