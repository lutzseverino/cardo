package io.github.lutzseverino.cardo.identity.productauth;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.product-auth")
public record IdentityProductAuthProperties(
    String sessionCookieName,
    String csrfCookieName,
    String identitySessionAudience,
    String productAudience,
    TokenExchange tokenExchange,
    ActiveTokenValidation activeTokenValidation) {

  public IdentityProductAuthProperties {
    sessionCookieName =
        sessionCookieName == null || sessionCookieName.isBlank()
            ? "cardo.session"
            : sessionCookieName;
    csrfCookieName =
        csrfCookieName == null || csrfCookieName.isBlank() ? "cardo.csrf" : csrfCookieName;
    if (sessionCookieName.equals(csrfCookieName)) {
      throw new IllegalArgumentException("session and CSRF cookie names must be distinct");
    }
    requireText(identitySessionAudience, "identity session audience");
    requireText(productAudience, "product audience");
    if (identitySessionAudience.equals(productAudience)) {
      throw new IllegalArgumentException("identity session and product audiences must be distinct");
    }
    tokenExchange = tokenExchange == null ? new TokenExchange(null, null) : tokenExchange;
    activeTokenValidation =
        activeTokenValidation == null
            ? new ActiveTokenValidation(false, null, null, null, null, null, null, null)
            : activeTokenValidation;
  }

  public record TokenExchange(Duration connectTimeout, Duration readTimeout) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(2);

    public TokenExchange {
      connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
      readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }

    void validate() {
      if (!isMillisecondBound(connectTimeout)) {
        throw new IllegalStateException(
            "Token exchange connect timeout must be between 1ms and 2147483647ms.");
      }
      if (!isMillisecondBound(readTimeout)) {
        throw new IllegalStateException(
            "Token exchange read timeout must be between 1ms and 2147483647ms.");
      }
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  public record ActiveTokenValidation(
      boolean enabled,
      URI introspectionUri,
      String clientId,
      String clientSecret,
      Duration cacheTtl,
      Integer cacheMaxEntries,
      Duration connectTimeout,
      Duration readTimeout) {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(10);
    private static final int DEFAULT_CACHE_MAX_ENTRIES = 2048;
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(2);

    public ActiveTokenValidation {
      cacheTtl = cacheTtl == null ? DEFAULT_CACHE_TTL : cacheTtl;
      cacheMaxEntries = cacheMaxEntries == null ? DEFAULT_CACHE_MAX_ENTRIES : cacheMaxEntries;
      connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
      readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }

    void validate() {
      if (!enabled) {
        return;
      }
      if (introspectionUri == null) {
        throw new IllegalStateException("Active token validation requires an introspection URI.");
      }
      if (clientId == null || clientId.isBlank()) {
        throw new IllegalStateException("Active token validation requires a client id.");
      }
      if (clientSecret == null || clientSecret.isBlank()) {
        throw new IllegalStateException("Active token validation requires a client secret.");
      }
      if (cacheTtl.isNegative()) {
        throw new IllegalStateException("Active token validation cache TTL cannot be negative.");
      }
      if (cacheMaxEntries < 1) {
        throw new IllegalStateException(
            "Active token validation cache max entries must be positive.");
      }
      if (!isMillisecondBound(connectTimeout)) {
        throw new IllegalStateException(
            "Active token validation connect timeout must be between 1ms and 2147483647ms.");
      }
      if (!isMillisecondBound(readTimeout)) {
        throw new IllegalStateException(
            "Active token validation read timeout must be between 1ms and 2147483647ms.");
      }
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
