package io.github.lutzseverino.cardo.identity.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

final class IdentitySessionBearerTokenResolver implements BearerTokenResolver {

  private final DefaultBearerTokenResolver authorizationHeader = new DefaultBearerTokenResolver();
  private final String cookieName;
  private final String currentSessionPath;

  IdentitySessionBearerTokenResolver(String cookieName, String basePath) {
    this.cookieName = cookieName;
    this.currentSessionPath = basePath + "/identity/sessions/current";
  }

  @Override
  public String resolve(HttpServletRequest request) {
    String token = authorizationHeader.resolve(request);
    if (token != null || !acceptsAccessCookie(request)) {
      return token;
    }
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

  private boolean acceptsAccessCookie(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return HttpMethod.GET.matches(request.getMethod()) && currentSessionPath.equals(path);
  }
}
