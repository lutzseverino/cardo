package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class SessionCookieBearerTokenResolver implements BearerTokenResolver {

  private final DefaultBearerTokenResolver authorizationHeader = new DefaultBearerTokenResolver();
  private final SessionCookieAuthenticationSelector selector;

  public SessionCookieBearerTokenResolver(String cookieName) {
    this(new SessionCookieAuthenticationSelector(cookieName));
  }

  SessionCookieBearerTokenResolver(SessionCookieAuthenticationSelector selector) {
    this.selector = selector;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    if (selector.hasAuthorizationHeader(request)) {
      return authorizationHeader.resolve(request);
    }
    return selector.sessionToken(request);
  }

  boolean selectsSessionCookie(HttpServletRequest request) {
    return selector.selectsSessionCookie(request);
  }
}
