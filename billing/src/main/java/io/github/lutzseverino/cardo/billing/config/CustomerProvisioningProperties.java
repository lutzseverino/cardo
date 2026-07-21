package io.github.lutzseverino.cardo.billing.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cardo.billing.customer-provisioning")
public record CustomerProvisioningProperties(
    @NotNull Duration dispatchDelay,
    @NotNull Duration retryBaseDelay,
    @NotNull Duration claimLease,
    @Positive int maxAttempts,
    @Positive int batchSize) {}
