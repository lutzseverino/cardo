package io.github.lutzseverino.cardo.invite.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Invite-owned runtime policy and bounds for synchronous provider calls. */
@ConfigurationProperties(prefix = "cardo.invite.runtime")
public record InviteRuntimeProperties(Mode mode, Duration connectTimeout, Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  public InviteRuntimeProperties {
    mode = mode == null ? Mode.LOCAL : mode;
    connectTimeout = positiveOrDefault(connectTimeout, "connect-timeout");
    readTimeout = positiveOrDefault(readTimeout, "read-timeout");
  }

  private static Duration positiveOrDefault(Duration value, String property) {
    Duration resolved = value == null ? DEFAULT_TIMEOUT : value;
    if (!isMillisecondBound(resolved)) {
      throw new IllegalArgumentException(
          "cardo.invite.runtime." + property + " must be between 1ms and 2147483647ms.");
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
