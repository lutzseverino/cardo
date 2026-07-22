package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class ReferenceIdentitySessionSecurityTest {

  @Test
  void selectsOnlyTheExactAcceptanceAndConvergenceMethodsAndPaths() {
    assertThat(workflow("POST", "/api/reference/invitations/one/accept")).isTrue();
    assertThat(workflow("GET", "/api/reference/convergence/one")).isTrue();

    assertThat(workflow("GET", "/api/reference/invitations/one/accept")).isFalse();
    assertThat(workflow("POST", "/api/reference/invitations")).isFalse();
    assertThat(workflow("GET", "/api/reference/tenants/one")).isFalse();
    assertThat(workflow("GET", "/api/reference/billing/one")).isFalse();
  }

  @Test
  void resolvesOnlyAnUnopposedIdentitySessionCookie() {
    var resolver = new ReferenceIdentitySessionCookieResolver("cardo.session");
    MockHttpServletRequest cookie = request("POST", "/api/reference/invitations/one/accept");
    cookie.setCookies(new Cookie("cardo.session", "identity-session"));
    assertThat(resolver.resolve(cookie)).isEqualTo("identity-session");

    cookie.addHeader(HttpHeaders.AUTHORIZATION, "Bearer identity-session");
    assertThat(resolver.resolve(cookie)).isNull();
    assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
  }

  @Test
  void keepsCsrfReadOnlyAndAcceptsOnlyTheExactHeaderValue() {
    var repository = new ReferenceReadOnlyCsrfTokenRepository("cardo.csrf");
    MockHttpServletRequest request = request("POST", "/api/reference/invitations/one/accept");
    request.setCookies(new Cookie("cardo.csrf", "csrf-value"));
    var token = repository.loadToken(request);
    assertThat(token).isNotNull();
    request.addHeader("X-CSRF-TOKEN", "csrf-value");
    assertThat(
            new ReferenceHeaderOnlyCsrfTokenRequestHandler().resolveCsrfTokenValue(request, token))
        .isEqualTo("csrf-value");

    MockHttpServletResponse response = new MockHttpServletResponse();
    repository.saveToken(
        new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "replacement"), request, response);
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void requiresAValidCardoIdentityUserClaim() {
    assertThat(
            ReferenceIdentitySessionSecurity.validIdentityUser(jwt(UUID.randomUUID().toString()))
                .hasErrors())
        .isFalse();
    assertThat(ReferenceIdentitySessionSecurity.validIdentityUser(jwt(null)).hasErrors()).isTrue();
    assertThat(ReferenceIdentitySessionSecurity.validIdentityUser(jwt("not-a-uuid")).hasErrors())
        .isTrue();
  }

  private boolean workflow(String method, String path) {
    return ReferenceIdentitySessionSecurity.workflowRequests().matches(request(method, path));
  }

  private MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setServletPath(path);
    return request;
  }

  private Jwt jwt(String userId) {
    Jwt.Builder jwt =
        Jwt.withTokenValue("identity-session").header("alg", "RS256").subject("subject");
    if (userId != null) {
      jwt.claim(CardoJwtClaims.IDENTITY_USER_ID, userId);
    }
    return jwt.build();
  }
}
