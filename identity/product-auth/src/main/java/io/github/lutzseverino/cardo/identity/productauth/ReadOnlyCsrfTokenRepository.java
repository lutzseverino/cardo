package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

final class ReadOnlyCsrfTokenRepository implements CsrfTokenRepository {

  private final CookieCsrfTokenRepository delegate;

  ReadOnlyCsrfTokenRepository(String cookieName) {
    delegate = CookieCsrfTokenRepository.withHttpOnlyFalse();
    delegate.setCookieName(cookieName);
    delegate.setHeaderName("X-CSRF-TOKEN");
    delegate.setCookiePath("/");
  }

  @Override
  public CsrfToken generateToken(HttpServletRequest request) {
    return delegate.generateToken(request);
  }

  @Override
  public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    // Identity exclusively owns CSRF cookie creation and expiry.
  }

  @Override
  public CsrfToken loadToken(HttpServletRequest request) {
    return delegate.loadToken(request);
  }
}
