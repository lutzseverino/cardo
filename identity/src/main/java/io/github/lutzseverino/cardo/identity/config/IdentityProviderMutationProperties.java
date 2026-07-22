package io.github.lutzseverino.cardo.identity.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cardo.identity.provider-mutations")
public record IdentityProviderMutationProperties(
    @NotNull Duration dispatchDelay,
    @NotNull Duration retryBaseDelay,
    @NotNull Duration claimLease,
    @Positive int maxAttempts,
    @Positive int batchSize) {

  public IdentityProviderMutationProperties {
    positive(dispatchDelay, "dispatch-delay");
    positive(retryBaseDelay, "retry-base-delay");
    positive(claimLease, "claim-lease");
  }

  private static void positive(Duration value, String property) {
    if (value != null && (value.isZero() || value.isNegative())) {
      throw new IllegalArgumentException(
          "cardo.identity.provider-mutations." + property + " must be positive.");
    }
  }
}
