package io.github.lutzseverino.cardo.identity.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.identity.session")
public record SessionProperties(
    Mode mode,
    String accessCookieName,
    String refreshCookieName,
    String refreshCookiePath,
    boolean secure) {

  private static final String PRODUCTION_ACCESS_COOKIE = "__Host-cardo.session";
  private static final String PRODUCTION_REFRESH_COOKIE = "__Secure-cardo.refresh";
  private static final String CURRENT_SESSION_PATH = "/identity/sessions/current";

  public SessionProperties {
    Objects.requireNonNull(mode, "mode");
    requireText(accessCookieName, "accessCookieName");
    requireText(refreshCookieName, "refreshCookieName");
    requirePath(refreshCookiePath);
    if (Mode.PRODUCTION.equals(mode)) {
      requireProduction(secure, accessCookieName, refreshCookieName);
    } else {
      requireLocal(secure, accessCookieName, refreshCookieName);
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private static void requirePath(String path) {
    requireText(path, "refreshCookiePath");
    if (!path.startsWith("/") || !path.endsWith(CURRENT_SESSION_PATH)) {
      throw new IllegalArgumentException(
          "refreshCookiePath must be an absolute browser path ending in " + CURRENT_SESSION_PATH);
    }
  }

  private static void requireProduction(
      boolean secure, String accessCookieName, String refreshCookieName) {
    if (!secure) {
      throw new IllegalArgumentException("production session cookies must be secure");
    }
    if (!PRODUCTION_ACCESS_COOKIE.equals(accessCookieName)) {
      throw new IllegalArgumentException(
          "production access cookie must be named " + PRODUCTION_ACCESS_COOKIE);
    }
    if (!PRODUCTION_REFRESH_COOKIE.equals(refreshCookieName)) {
      throw new IllegalArgumentException(
          "production refresh cookie must be named " + PRODUCTION_REFRESH_COOKIE);
    }
  }

  private static void requireLocal(
      boolean secure, String accessCookieName, String refreshCookieName) {
    if (secure) {
      throw new IllegalArgumentException("local HTTP session cookies must not be secure");
    }
    if (hasSecurePrefix(accessCookieName) || hasSecurePrefix(refreshCookieName)) {
      throw new IllegalArgumentException("local HTTP session cookies must use non-prefixed names");
    }
  }

  private static boolean hasSecurePrefix(String name) {
    return name.startsWith("__Host-") || name.startsWith("__Secure-");
  }

  public enum Mode {
    LOCAL,
    PRODUCTION
  }
}
