package io.github.lutzseverino.cardo.billing.model;

import jakarta.validation.constraints.NotNull;
import java.net.URI;

public record CreatePortalSessionInput(@NotNull URI returnUrl) {}
