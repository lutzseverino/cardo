package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

final class HeaderOnlyCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    return request.getHeader(csrfToken.getHeaderName());
  }
}
