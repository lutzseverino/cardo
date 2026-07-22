package io.github.lutzseverino.cardo.identity.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cardo.identity.operations")
public record IdentityOperationProperties(
    @NotNull Duration dispatchDelay,
    @NotNull Duration pollDelay,
    @NotNull Duration credentialSetupTimeout,
    @NotNull Duration retryBaseDelay,
    @NotNull Duration claimLease,
    @Positive int maxAttempts,
    @Positive int batchSize) {

  public IdentityOperationProperties {
    positive(dispatchDelay, "dispatch-delay");
    positive(pollDelay, "poll-delay");
    positive(credentialSetupTimeout, "credential-setup-timeout");
    positive(retryBaseDelay, "retry-base-delay");
    positive(claimLease, "claim-lease");
  }

  private static void positive(Duration value, String property) {
    if (value != null && (value.isZero() || value.isNegative())) {
      throw new IllegalArgumentException(
          "cardo.identity.operations." + property + " must be positive.");
    }
  }
}
