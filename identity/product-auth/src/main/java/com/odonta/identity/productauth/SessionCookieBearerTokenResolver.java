package com.odonta.identity.productauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class SessionCookieBearerTokenResolver implements BearerTokenResolver {

  private final DefaultBearerTokenResolver authorizationHeader = new DefaultBearerTokenResolver();
  private final String cookieName;

  public SessionCookieBearerTokenResolver(String cookieName) {
    this.cookieName = cookieName;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    String token = authorizationHeader.resolve(request);
    if (token != null) {
      return token;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
