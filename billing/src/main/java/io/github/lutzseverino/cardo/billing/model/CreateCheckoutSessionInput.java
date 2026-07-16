package io.github.lutzseverino.cardo.billing.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

public record CreateCheckoutSessionInput(
    @NotBlank String product, @NotNull URI successUrl, @NotNull URI cancelUrl) {}
