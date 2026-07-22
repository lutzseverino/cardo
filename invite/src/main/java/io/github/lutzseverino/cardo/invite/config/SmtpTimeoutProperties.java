package io.github.lutzseverino.cardo.invite.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Finite bounds for one synchronous SMTP delivery attempt. */
@ConfigurationProperties(prefix = "cardo.invite.smtp")
public record SmtpTimeoutProperties(
    Duration connectTimeout, Duration readTimeout, Duration writeTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  public SmtpTimeoutProperties {
    connectTimeout = positiveOrDefault(connectTimeout, "connect-timeout");
    readTimeout = positiveOrDefault(readTimeout, "read-timeout");
    writeTimeout = positiveOrDefault(writeTimeout, "write-timeout");
  }

  private static Duration positiveOrDefault(Duration value, String property) {
    Duration resolved = value == null ? DEFAULT_TIMEOUT : value;
    if (resolved.isZero() || resolved.isNegative()) {
      throw new IllegalArgumentException("cardo.invite.smtp." + property + " must be positive.");
    }
    return resolved;
  }
}
