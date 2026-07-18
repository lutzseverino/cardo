package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class IdentitySessionBearerTokenResolverTest {

  private final IdentitySessionBearerTokenResolver resolver =
      new IdentitySessionBearerTokenResolver("cardo.session", "/api/v1");

  @Test
  void explicitAuthorizationHeaderTakesPrecedence() {
    MockHttpServletRequest request = request("GET", "/api/v1/identity/sessions/current");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-token");
    request.setCookies(new Cookie("cardo.session", "cookie-token"));

    assertThat(resolver.resolve(request)).isEqualTo("header-token");
  }

  @Test
  void resolvesAccessCookieForTheCurrentSessionRead() {
    MockHttpServletRequest request = request("GET", "/api/v1/identity/sessions/current");
    request.setCookies(new Cookie("cardo.session", "cookie-token"));

    assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
  }

  @Test
  void ignoresAmbientAccessCookieOutsideTheCurrentSessionRead() {
    assertCookieIgnored("POST", "/api/v1/identity/sessions");
    assertCookieIgnored("POST", "/api/v1/identity/sessions/current/refresh");
    assertCookieIgnored("DELETE", "/api/v1/identity/sessions/current");
    assertCookieIgnored("GET", "/api/v1/identity/users/me");
    assertCookieIgnored("PATCH", "/api/v1/identity/users/me");
  }

  @Test
  void ignoresAuthorizationHeadersOnSessionEndpointsOwnedByControllerCredentials() {
    assertHeaderIgnored("POST", "/api/v1/identity/sessions");
    assertHeaderIgnored("POST", "/api/v1/identity/sessions/current/refresh");
    assertHeaderIgnored("DELETE", "/api/v1/identity/sessions/current");
  }

  private void assertCookieIgnored(String method, String path) {
    MockHttpServletRequest request = request(method, path);
    request.setCookies(new Cookie("cardo.session", "stale-token"));

    assertThat(resolver.resolve(request)).isNull();
  }

  private void assertHeaderIgnored(String method, String path) {
    MockHttpServletRequest request = request(method, path);
    request.addHeader(HttpHeaders.AUTHORIZATION, "Malformed ambient credential");

    assertThat(resolver.resolve(request)).isNull();
  }

  private MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRequestURI(path);
    return request;
  }
}
