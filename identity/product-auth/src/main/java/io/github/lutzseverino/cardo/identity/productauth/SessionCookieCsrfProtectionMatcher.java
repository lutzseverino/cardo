package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

final class SessionCookieCsrfProtectionMatcher implements RequestMatcher {

  private final SessionCookieBearerTokenResolver bearerTokens;

  SessionCookieCsrfProtectionMatcher(SessionCookieBearerTokenResolver bearerTokens) {
    this.bearerTokens = bearerTokens;
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    return CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
        && bearerTokens.selectsSessionCookie(request);
  }
}
