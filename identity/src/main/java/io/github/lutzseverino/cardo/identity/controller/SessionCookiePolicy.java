package io.github.lutzseverino.cardo.identity.controller;

import io.github.lutzseverino.cardo.identity.config.SessionProperties;
import io.github.lutzseverino.cardo.identity.model.SessionCredential;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class SessionCookiePolicy {

  private static final String ACCESS_PATH = "/";
  private static final String SAME_SITE = "Lax";

  private final SessionProperties properties;
  private final Clock clock;

  @Autowired
  SessionCookiePolicy(SessionProperties properties) {
    this(properties, Clock.systemUTC());
  }

  SessionCookiePolicy(SessionProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  ResponseCookie access(SessionCredential credential) {
    return create(
        properties.accessCookieName(), ACCESS_PATH, credential.token(), credential.expiresAt());
  }

  ResponseCookie refresh(SessionCredential credential) {
    return create(
        properties.refreshCookieName(),
        properties.refreshCookiePath(),
        credential.token(),
        credential.expiresAt());
  }

  ResponseCookie expireAccess() {
    return expire(properties.accessCookieName(), ACCESS_PATH);
  }

  ResponseCookie expireRefresh() {
    return expire(properties.refreshCookieName(), properties.refreshCookiePath());
  }

  Optional<String> refresh(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (properties.refreshCookieName().equals(cookie.getName())
          && cookie.getValue() != null
          && !cookie.getValue().isBlank()) {
        return Optional.of(cookie.getValue());
      }
    }
    return Optional.empty();
  }

  private ResponseCookie create(
      String name, String path, String value, java.time.OffsetDateTime expiresAt) {
    Duration maxAge = Duration.between(Instant.now(clock), expiresAt.toInstant());
    if (maxAge.isZero() || maxAge.isNegative()) {
      throw new IllegalArgumentException("session credential must expire in the future");
    }
    return base(name, path, value).maxAge(maxAge).build();
  }

  private ResponseCookie expire(String name, String path) {
    return base(name, path, "").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String name, String path, String value) {
    return ResponseCookie.from(name, value)
        .secure(properties.secure())
        .httpOnly(true)
        .sameSite(SAME_SITE)
        .path(path);
  }
}
