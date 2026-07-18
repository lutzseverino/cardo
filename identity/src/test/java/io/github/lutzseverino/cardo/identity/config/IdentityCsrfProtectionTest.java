package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.identity.config.SessionProperties.Mode;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;

class IdentityCsrfProtectionTest {

  private final CookieCsrfTokenRepository tokens =
      new SecurityConfig().csrfTokenRepository(productionProperties());
  private final CsrfFilter filter = filter();

  @Test
  void protectsOnlyUnsafeSessionMutations() throws Exception {
    assertRejected(request("POST", "/api/v1/identity/sessions"));
    assertRejected(request("POST", "/api/v1/identity/sessions/current/refresh"));
    assertRejected(request("DELETE", "/api/v1/identity/sessions/current"));

    assertAllowed(request("GET", "/api/v1/identity/sessions/current"));
    assertAllowed(request("POST", "/api/v1/identity/users"));
  }

  @Test
  void requiresExactHeaderAndDoesNotAcceptARequestParameter() throws Exception {
    MockHttpServletRequest valid = request("POST", "/api/v1/identity/sessions");
    valid.setCookies(new Cookie("__Host-cardo.csrf", "csrf-token"));
    valid.addHeader("X-CSRF-TOKEN", "csrf-token");
    assertAllowed(valid);

    MockHttpServletRequest mismatched = request("POST", "/api/v1/identity/sessions");
    mismatched.setCookies(new Cookie("__Host-cardo.csrf", "csrf-token"));
    mismatched.addHeader("X-CSRF-TOKEN", "different-token");
    assertRejected(mismatched);

    MockHttpServletRequest parameterOnly = request("POST", "/api/v1/identity/sessions");
    parameterOnly.setCookies(new Cookie("__Host-cardo.csrf", "csrf-token"));
    parameterOnly.addParameter("_csrf", "csrf-token");
    assertRejected(parameterOnly);
  }

  @Test
  void authorizationHeaderDoesNotBypassControllerCredentialMutations() throws Exception {
    MockHttpServletRequest request = request("POST", "/api/v1/identity/sessions");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer explicit-token");

    assertRejected(request);
  }

  @Test
  void bootstrapCreatesTheProductionCookieOnlyWhenAbsent() throws Exception {
    MockHttpServletRequest absent = request("GET", "/api/v1/identity/sessions/csrf");
    MockHttpServletResponse created = filter(absent);

    CsrfToken generated = (CsrfToken) absent.getAttribute(CsrfToken.class.getName());
    generated.getToken();

    Cookie createdCookie = created.getCookie("__Host-cardo.csrf");
    assertThat(createdCookie).isNotNull();
    assertThat(createdCookie.getValue()).isNotBlank();
    assertThat(createdCookie.getPath()).isEqualTo("/");
    assertThat(createdCookie.getSecure()).isTrue();
    assertThat(createdCookie.isHttpOnly()).isFalse();
    assertThat(createdCookie.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(createdCookie.getDomain()).isNull();
    assertThat(createdCookie.getMaxAge()).isEqualTo(-1);

    MockHttpServletRequest present = request("GET", "/api/v1/identity/sessions/csrf");
    present.setCookies(new Cookie("__Host-cardo.csrf", "existing-token"));
    MockHttpServletResponse unchanged = filter(present);

    CsrfToken existing = (CsrfToken) present.getAttribute(CsrfToken.class.getName());
    assertThat(existing.getToken()).isEqualTo("existing-token");
    assertThat(unchanged.getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  private CsrfFilter filter() {
    CsrfFilter csrf = new CsrfFilter(tokens);
    csrf.setRequestHandler(new HeaderOnlyCsrfTokenRequestHandler());
    csrf.setRequireCsrfProtectionMatcher(new IdentitySessionCsrfProtectionMatcher("/api/v1"));
    return csrf;
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

  private MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setContextPath("");
    return request;
  }

  private SessionProperties productionProperties() {
    return new SessionProperties(
        Mode.PRODUCTION,
        "__Host-cardo.session",
        "__Secure-cardo.refresh",
        "__Host-cardo.csrf",
        "/api/v1/identity/sessions/current",
        true);
  }
}
