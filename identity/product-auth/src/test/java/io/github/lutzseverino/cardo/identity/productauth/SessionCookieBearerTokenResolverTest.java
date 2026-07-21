package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionCookieBearerTokenResolverTest {

  private final SessionCookieBearerTokenResolver resolver =
      new SessionCookieBearerTokenResolver(
          new SessionCookieAuthenticationSelector("cardo.session"));

  @Test
  void resolvesOnlyAnUnopposedSessionCookie() {
    MockHttpServletRequest cookie = new MockHttpServletRequest();
    cookie.setCookies(new Cookie("cardo.session", "identity-token"));
    assertThat(resolver.resolve(cookie)).isEqualTo("identity-token");

    MockHttpServletRequest explicit = new MockHttpServletRequest();
    explicit.addHeader(HttpHeaders.AUTHORIZATION, "Bearer product-token");
    explicit.setCookies(new Cookie("cardo.session", "identity-token"));
    assertThat(resolver.resolve(explicit)).isNull();
    assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
  }
}
