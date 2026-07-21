package io.github.lutzseverino.cardo.identity.client.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.client")
record IdentityClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  IdentityClientProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("cardo.identity.client.base-url is required.");
    }
    connectTimeout = timeoutOrDefault(connectTimeout, "connect-timeout");
    readTimeout = timeoutOrDefault(readTimeout, "read-timeout");
  }

  private static Duration timeoutOrDefault(Duration timeout, String property) {
    Duration resolved = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (resolved.isZero() || resolved.isNegative()) {
      throw new IllegalArgumentException(
          "cardo.identity.client." + property + " must be positive.");
    }
    return resolved;
  }
}
