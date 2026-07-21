package io.github.lutzseverino.cardo.billing.client.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.billing.client")
record BillingClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  BillingClientProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("cardo.billing.client.base-url is required.");
    }
    connectTimeout = timeoutOrDefault(connectTimeout, "connect-timeout");
    readTimeout = timeoutOrDefault(readTimeout, "read-timeout");
  }

  private static Duration timeoutOrDefault(Duration timeout, String property) {
    Duration resolved = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (resolved.isZero() || resolved.isNegative()) {
      throw new IllegalArgumentException("cardo.billing.client." + property + " must be positive.");
    }
    return resolved;
  }
}
