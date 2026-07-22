package io.github.lutzseverino.cardo.billing.client.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.billing.client")
record BillingClientProperties(
    String baseUrl, String serviceTokenScope, Duration connectTimeout, Duration readTimeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  BillingClientProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("cardo.billing.client.base-url is required.");
    }
    if (serviceTokenScope == null || serviceTokenScope.isBlank()) {
      throw new IllegalArgumentException("cardo.billing.client.service-token-scope is required.");
    }
    serviceTokenScope = serviceTokenScope.strip();
    connectTimeout = timeoutOrDefault(connectTimeout, "connect-timeout");
    readTimeout = timeoutOrDefault(readTimeout, "read-timeout");
  }

  private static Duration timeoutOrDefault(Duration timeout, String property) {
    Duration resolved = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (!isMillisecondBound(resolved)) {
      throw new IllegalArgumentException(
          "cardo.billing.client." + property + " must be between 1ms and 2147483647ms.");
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
