package com.odonta.billing.model;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record PortalSessionRequest(@NotBlank @URL String returnUrl) {}
