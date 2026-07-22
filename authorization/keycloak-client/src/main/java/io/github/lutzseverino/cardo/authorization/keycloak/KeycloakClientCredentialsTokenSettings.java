package io.github.lutzseverino.cardo.authorization.keycloak;

import java.time.Duration;

/** Runtime bounds for acquiring and reusing a Keycloak client-credentials token. */
public record KeycloakClientCredentialsTokenSettings(
    Duration connectTimeout, Duration readTimeout, Duration refreshSkew) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(30);

  public KeycloakClientCredentialsTokenSettings {
    requirePositive(connectTimeout, "connectTimeout");
    requirePositive(readTimeout, "readTimeout");
    if (refreshSkew == null || refreshSkew.isNegative()) {
      throw new IllegalArgumentException("refreshSkew must not be negative.");
    }
  }

  public static KeycloakClientCredentialsTokenSettings defaults() {
    return new KeycloakClientCredentialsTokenSettings(
        DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_REFRESH_SKEW);
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || !isMillisecondBound(value)) {
      throw new IllegalArgumentException(name + " must be between 1ms and 2147483647ms.");
    }
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
