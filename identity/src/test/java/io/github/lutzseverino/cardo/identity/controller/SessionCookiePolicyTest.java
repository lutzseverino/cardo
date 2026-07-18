package io.github.lutzseverino.cardo.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.identity.config.SessionProperties;
import io.github.lutzseverino.cardo.identity.config.SessionProperties.Mode;
import io.github.lutzseverino.cardo.identity.model.SessionCredential;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionCookiePolicyTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private final SessionCookiePolicy cookies =
      new SessionCookiePolicy(productionProperties(), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void createsProductionAccessAndRefreshCookiesFromCredentialExpiry() {
    var access = cookies.access(credential("access-secret", Duration.ofMinutes(5)));
    var refresh = cookies.refresh(credential("refresh-secret", Duration.ofHours(8)));

    assertThat(access.getName()).isEqualTo("__Host-cardo.session");
    assertThat(access.getPath()).isEqualTo("/");
    assertThat(access.isSecure()).isTrue();
    assertThat(access.isHttpOnly()).isTrue();
    assertThat(access.getSameSite()).isEqualTo("Lax");
    assertThat(access.getDomain()).isNull();
    assertThat(access.getMaxAge()).isEqualTo(Duration.ofMinutes(5));

    assertThat(refresh.getName()).isEqualTo("__Secure-cardo.refresh");
    assertThat(refresh.getPath()).isEqualTo("/api/v1/identity/sessions/current");
    assertThat(refresh.getMaxAge()).isEqualTo(Duration.ofHours(8));
  }

  @Test
  void expiresCookiesWithSymmetricAttributes() {
    var access = cookies.expireAccess();
    var refresh = cookies.expireRefresh();
    var csrf = cookies.expireCsrf();

    assertThat(access.toString())
        .contains("__Host-cardo.session=", "Path=/", "Secure", "HttpOnly", "SameSite=Lax")
        .contains("Max-Age=0")
        .doesNotContain("Domain=");
    assertThat(refresh.toString())
        .contains(
            "__Secure-cardo.refresh=",
            "Path=/api/v1/identity/sessions/current",
            "Secure",
            "HttpOnly",
            "SameSite=Lax",
            "Max-Age=0")
        .doesNotContain("Domain=");
    assertThat(csrf.toString())
        .contains("__Host-cardo.csrf=", "Path=/", "Secure", "SameSite=Lax", "Max-Age=0")
        .doesNotContain("HttpOnly", "Domain=");
  }

  @Test
  void readsOnlyTheConfiguredRefreshCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("cardo.session", "access-secret"),
        new Cookie("__Secure-cardo.refresh", "refresh-secret"));

    assertThat(cookies.refresh(request)).contains("refresh-secret");
  }

  @Test
  void localPolicyCreatesExplicitNonSecureCookies() {
    SessionCookiePolicy local =
        new SessionCookiePolicy(
            new SessionProperties(
                Mode.LOCAL,
                "cardo.session",
                "cardo.refresh",
                "cardo.csrf",
                "/api/v1/identity/sessions/current",
                false),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(local.access(credential("access-secret", Duration.ofMinutes(5))).toString())
        .startsWith("cardo.session=")
        .doesNotContain("Secure");
  }

  @Test
  void credentialStringRepresentationDoesNotExposeTheToken() {
    assertThat(credential("access-secret", Duration.ofMinutes(5)).toString())
        .doesNotContain("access-secret");
  }

  private SessionCredential credential(String token, Duration lifetime) {
    return new SessionCredential(
        token, OffsetDateTime.ofInstant(NOW.plus(lifetime), ZoneOffset.UTC));
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
