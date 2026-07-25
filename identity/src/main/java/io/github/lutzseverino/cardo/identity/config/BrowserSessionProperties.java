package io.github.lutzseverino.cardo.identity.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cardo.identity.browser-session")
public record BrowserSessionProperties(
    @NotNull Duration idleTimeout,
    @NotNull Duration absoluteTimeout,
    @NotNull Duration refreshLease,
    @NotNull Duration retention,
    @NotNull Duration cleanupDelay) {

  public BrowserSessionProperties {
    positive(idleTimeout, "idle-timeout");
    positive(absoluteTimeout, "absolute-timeout");
    positive(refreshLease, "refresh-lease");
    positive(retention, "retention");
    positive(cleanupDelay, "cleanup-delay");
    if (idleTimeout != null
        && absoluteTimeout != null
        && idleTimeout.compareTo(absoluteTimeout) >= 0) {
      throw new IllegalArgumentException(
          "cardo.identity.browser-session.idle-timeout must be shorter than absolute-timeout.");
    }
  }

  private static void positive(Duration value, String property) {
    if (value != null && (value.isZero() || value.isNegative())) {
      throw new IllegalArgumentException(
          "cardo.identity.browser-session." + property + " must be positive.");
    }
  }
}
