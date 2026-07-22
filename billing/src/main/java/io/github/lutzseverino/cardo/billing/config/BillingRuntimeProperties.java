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
    if (!isMillisecondBound(resolved)) {
      throw new IllegalArgumentException(
          "cardo.billing.runtime." + property + " must be between 1ms and 2147483647ms.");
    }
    return resolved;
  }

  private static boolean isMillisecondBound(Duration value) {
    try {
      long milliseconds = value.toMillis();
      return milliseconds >= 1 && milliseconds <= Integer.MAX_VALUE;
    } catch (ArithmeticException exception) {
      return false;
    }
  }

  public enum Mode {
    LOCAL,
    PRODUCTION
  }
}
