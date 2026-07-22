package io.github.lutzseverino.cardo.billing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Billing-owned local or production runtime policy. */
@ConfigurationProperties(prefix = "cardo.billing.runtime")
public record BillingRuntimeProperties(
    Mode mode, Duration jwkConnectTimeout, Duration jwkReadTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  public BillingRuntimeProperties {
    mode = mode == null ? Mode.LOCAL : mode;
    jwkConnectTimeout = positiveOrDefault(jwkConnectTimeout, "jwk-connect-timeout");
    jwkReadTimeout = positiveOrDefault(jwkReadTimeout, "jwk-read-timeout");
  }

  private static Duration positiveOrDefault(Duration value, String property) {
    Duration resolved = value == null ? DEFAULT_TIMEOUT : value;
    if (resolved.isZero() || resolved.isNegative()) {
      throw new IllegalArgumentException(
          "cardo.billing.runtime." + property + " must be positive.");
    }
    return resolved;
  }

  public enum Mode {
    LOCAL,
    PRODUCTION
  }
}
