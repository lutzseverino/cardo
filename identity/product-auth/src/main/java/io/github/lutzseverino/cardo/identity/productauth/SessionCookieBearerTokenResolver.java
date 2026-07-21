package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

final class SessionCookieBearerTokenResolver implements BearerTokenResolver {

  private final SessionCookieAuthenticationSelector selector;

  SessionCookieBearerTokenResolver(SessionCookieAuthenticationSelector selector) {
    this.selector = selector;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    return selector.selectsSessionCookie(request) ? selector.sessionToken(request) : null;
  }
}
