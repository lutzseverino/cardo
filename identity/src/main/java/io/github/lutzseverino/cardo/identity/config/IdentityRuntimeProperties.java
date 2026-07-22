package io.github.lutzseverino.cardo.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Identity-owned runtime policy and bounds for synchronous provider calls. */
@ConfigurationProperties(prefix = "cardo.identity.runtime")
public record IdentityRuntimeProperties(Mode mode, Duration connectTimeout, Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  public IdentityRuntimeProperties {
    mode = mode == null ? Mode.LOCAL : mode;
    connectTimeout = positiveOrDefault(connectTimeout, "connect-timeout");
    readTimeout = positiveOrDefault(readTimeout, "read-timeout");
  }

  private static Duration positiveOrDefault(Duration value, String property) {
    Duration resolved = value == null ? DEFAULT_TIMEOUT : value;
    if (!isMillisecondBound(resolved)) {
      throw new IllegalArgumentException(
          "cardo.identity.runtime." + property + " must be between 1ms and 2147483647ms.");
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
