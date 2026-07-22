package io.github.lutzseverino.cardo.invite.client.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.client")
record InviteClientProperties(
    String baseUrl, String serviceTokenScope, Duration connectTimeout, Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  InviteClientProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("cardo.invite.client.base-url is required.");
    }
    if (serviceTokenScope == null || serviceTokenScope.isBlank()) {
      throw new IllegalArgumentException("cardo.invite.client.service-token-scope is required.");
    }
    serviceTokenScope = serviceTokenScope.strip();
    connectTimeout = timeoutOrDefault(connectTimeout, "connect-timeout");
    readTimeout = timeoutOrDefault(readTimeout, "read-timeout");
  }

  private static Duration timeoutOrDefault(Duration timeout, String property) {
    Duration resolved = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (!isMillisecondBound(resolved)) {
      throw new IllegalArgumentException(
          "cardo.invite.client." + property + " must be between 1ms and 2147483647ms.");
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
}
