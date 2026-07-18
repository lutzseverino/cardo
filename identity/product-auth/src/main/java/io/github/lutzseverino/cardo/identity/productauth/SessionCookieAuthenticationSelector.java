package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

final class SessionCookieAuthenticationSelector {

  private final String cookieName;

  SessionCookieAuthenticationSelector(String cookieName) {
    this.cookieName = cookieName;
  }

  boolean hasAuthorizationHeader(HttpServletRequest request) {
    return request.getHeader(HttpHeaders.AUTHORIZATION) != null;
  }

  boolean selectsSessionCookie(HttpServletRequest request) {
    return !hasAuthorizationHeader(request) && sessionToken(request) != null;
  }

  String sessionToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())
          && cookie.getValue() != null
          && !cookie.getValue().isBlank()) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
