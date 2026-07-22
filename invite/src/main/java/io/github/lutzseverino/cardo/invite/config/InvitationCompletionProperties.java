package io.github.lutzseverino.cardo.invite.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cardo.invite.completion")
public record InvitationCompletionProperties(
    @NotNull Duration dispatchDelay,
    @NotNull Duration pollDelay,
    @NotNull Duration retryBaseDelay,
    @NotNull Duration claimLease,
    @Positive int maxAttempts,
    @Positive int batchSize) {

  public InvitationCompletionProperties {
    positive(dispatchDelay, "dispatch-delay");
    positive(pollDelay, "poll-delay");
    positive(retryBaseDelay, "retry-base-delay");
    positive(claimLease, "claim-lease");
  }

  private static void positive(Duration value, String property) {
    if (value != null && (value.isZero() || value.isNegative())) {
      throw new IllegalArgumentException(
          "cardo.invite.completion." + property + " must be positive.");
    }
  }
}
