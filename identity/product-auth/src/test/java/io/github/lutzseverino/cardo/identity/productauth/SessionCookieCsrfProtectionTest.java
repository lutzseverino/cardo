package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfFilter;

class SessionCookieCsrfProtectionTest {

  private final SessionCookieBearerTokenResolver bearerTokens =
      new SessionCookieBearerTokenResolver("cardo.session");
  private final CsrfFilter filter = filter();

  @Test
  void protectsUnsafeRequestsOnlyWhenTheSessionCookieIsSelected() throws Exception {
    MockHttpServletRequest cookieMutation = request("POST");
    cookieMutation.setCookies(new Cookie("cardo.session", "session-token"));
    assertRejected(cookieMutation);

    MockHttpServletRequest anonymousMutation = request("POST");
    assertAllowed(anonymousMutation);

    MockHttpServletRequest safeCookieRequest = request("GET");
    safeCookieRequest.setCookies(new Cookie("cardo.session", "session-token"));
    assertAllowed(safeCookieRequest);
  }

  @Test
  void acceptsOnlyAnExactCookieAndFixedHeaderPair() throws Exception {
    MockHttpServletRequest valid = cookieMutation();
    valid.addHeader("X-CSRF-TOKEN", "csrf-token");
    assertAllowed(valid);

    MockHttpServletRequest mismatch = cookieMutation();
    mismatch.addHeader("X-CSRF-TOKEN", "different-token");
    assertRejected(mismatch);

    MockHttpServletRequest parameterOnly = cookieMutation();
    parameterOnly.addParameter("_csrf", "csrf-token");
    assertRejected(parameterOnly);
  }

  @Test
  void anyAuthorizationHeaderSelectsBearerAndBypassesCookieCsrf() throws Exception {
    MockHttpServletRequest validBearer = cookieMutation();
    validBearer.addHeader(HttpHeaders.AUTHORIZATION, "Bearer explicit-token");
    assertAllowed(validBearer);

    MockHttpServletRequest malformedBearer = cookieMutation();
    malformedBearer.addHeader(HttpHeaders.AUTHORIZATION, "Basic malformed-credential");
    assertAllowed(malformedBearer);
    assertThat(bearerTokens.resolve(malformedBearer)).isNull();
  }

  @Test
  void productEnforcementNeverCreatesOrExpiresTheIdentityOwnedCookie() throws Exception {
    MockHttpServletRequest request = request("POST");
    request.setCookies(new Cookie("cardo.session", "session-token"));
    MockHttpServletResponse response = filter(request);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  private CsrfFilter filter() {
    CsrfFilter csrf = new CsrfFilter(new ReadOnlyCsrfTokenRepository("cardo.csrf"));
    csrf.setRequestHandler(new HeaderOnlyCsrfTokenRequestHandler());
    csrf.setRequireCsrfProtectionMatcher(new SessionCookieCsrfProtectionMatcher(bearerTokens));
    return csrf;
  }

  private MockHttpServletRequest cookieMutation() {
    MockHttpServletRequest request = request("POST");
    request.setCookies(
        new Cookie("cardo.session", "session-token"), new Cookie("cardo.csrf", "csrf-token"));
    return request;
  }

  private void assertAllowed(MockHttpServletRequest request) throws Exception {
    AtomicBoolean invoked = new AtomicBoolean();
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> invoked.set(true));
    assertThat(invoked).isTrue();
  }

  private void assertRejected(MockHttpServletRequest request) throws Exception {
    AtomicBoolean invoked = new AtomicBoolean();
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));
    assertThat(invoked).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
  }

  private MockHttpServletResponse filter(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});
    return response;
  }

  private MockHttpServletRequest request(String method) {
    return new MockHttpServletRequest(method, "/api/v1/accounts");
  }
}
