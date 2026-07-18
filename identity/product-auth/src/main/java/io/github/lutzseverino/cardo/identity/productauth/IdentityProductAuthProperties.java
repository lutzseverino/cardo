package io.github.lutzseverino.cardo.identity.productauth;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.product-auth")
public record IdentityProductAuthProperties(
    List<String> publicPaths,
    String sessionCookieName,
    String csrfCookieName,
    ActiveTokenValidation activeTokenValidation) {

  public IdentityProductAuthProperties {
    publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    sessionCookieName =
        sessionCookieName == null || sessionCookieName.isBlank()
            ? "cardo.session"
            : sessionCookieName;
    csrfCookieName =
        csrfCookieName == null || csrfCookieName.isBlank() ? "cardo.csrf" : csrfCookieName;
    if (sessionCookieName.equals(csrfCookieName)) {
      throw new IllegalArgumentException("session and CSRF cookie names must be distinct");
    }
    activeTokenValidation =
        activeTokenValidation == null
            ? new ActiveTokenValidation(false, null, null, null, null, null, null, null)
            : activeTokenValidation;
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
      if (connectTimeout.isNegative() || connectTimeout.isZero()) {
        throw new IllegalStateException(
            "Active token validation connect timeout must be positive.");
      }
      if (readTimeout.isNegative() || readTimeout.isZero()) {
        throw new IllegalStateException("Active token validation read timeout must be positive.");
      }
    }
  }
}
