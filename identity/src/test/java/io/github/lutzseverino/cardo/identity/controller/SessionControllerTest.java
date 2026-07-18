package io.github.lutzseverino.cardo.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticateRequest;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticatedPrincipalResponse;
import io.github.lutzseverino.cardo.identity.config.SessionProperties;
import io.github.lutzseverino.cardo.identity.config.SessionProperties.Mode;
import io.github.lutzseverino.cardo.identity.mapper.AuthenticationTransportMapper;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.SessionCredential;
import io.github.lutzseverino.cardo.identity.model.SessionResult;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import io.github.lutzseverino.cardo.identity.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

class SessionControllerTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  private final AuthenticationTransportMapper mapper = mock(AuthenticationTransportMapper.class);
  private final AuthenticationService authentication = mock(AuthenticationService.class);
  private final CurrentJwtReader currentJwt = mock(CurrentJwtReader.class);
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final SessionCookiePolicy cookies =
      new SessionCookiePolicy(localProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
  private final SessionController controller =
      new SessionController(mapper, authentication, currentJwt, cookies, request);

  @Test
  void loginSetsSeparateAccessAndRefreshCookiesWithoutReturningTokens() {
    AuthenticateRequest transport = mock(AuthenticateRequest.class);
    AuthenticateInput input = new AuthenticateInput("user@example.com", "password-1");
    SessionResult session = session();
    AuthenticatedPrincipalResponse body = mock(AuthenticatedPrincipalResponse.class);
    when(mapper.toInput(transport)).thenReturn(input);
    when(authentication.authenticate(input)).thenReturn(session);
    when(mapper.toResponse(session.authentication())).thenReturn(body);

    var response = controller.authenticate(transport);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).isSameAs(body);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
        .hasSize(2)
        .anySatisfy(cookie -> assertThat(cookie).startsWith("cardo.session=identity-rpt"))
        .anySatisfy(cookie -> assertThat(cookie).startsWith("cardo.refresh=rotated-refresh"));
  }

  @Test
  void refreshReadsOnlyTheRefreshCookieAndRotatesBothCookies() {
    request.setCookies(new Cookie("cardo.refresh", "old-refresh"));
    SessionResult session = session();
    when(authentication.refresh("old-refresh")).thenReturn(session);
    when(mapper.toResponse(session.authentication()))
        .thenReturn(mock(AuthenticatedPrincipalResponse.class));

    var response = controller.refreshCurrentSession();

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2);
    verify(authentication).refresh("old-refresh");
  }

  @Test
  void logoutWithoutRefreshCredentialIsIdempotentAndExpiresLocalCookies() {
    var response = controller.deleteCurrentSession();

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
        .hasSize(3)
        .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"))
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .startsWith("cardo.csrf=")
                    .contains("Path=/", "SameSite=Lax")
                    .doesNotContain("HttpOnly"));
    verify(authentication, never()).revoke(anyString());
  }

  @Test
  void bootstrapForcesDeferredCsrfTokenCreation() {
    CsrfToken csrfToken = mock(CsrfToken.class);
    request.setAttribute(CsrfToken.class.getName(), csrfToken);

    var response = controller.getCsrfToken();

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(csrfToken).getToken();
  }

  @Test
  void logoutPreservesRetryPathWhenProviderRevocationFails() {
    request.setCookies(new Cookie("cardo.refresh", "provider-refresh"));
    doThrow(ApiException.of(503, "identity_provider_error", "Provider unavailable."))
        .when(authentication)
        .revoke("provider-refresh");

    assertThatThrownBy(controller::deleteCurrentSession)
        .isInstanceOf(ApiException.class)
        .hasMessage("Provider unavailable.");
  }

  private SessionResult session() {
    AuthenticationResult result =
        new AuthenticationResult(mock(AuthenticatedPrincipal.class), List.of());
    return new SessionResult(
        result, credential("identity-rpt", 300), credential("rotated-refresh", 3600));
  }

  private SessionCredential credential(String token, long lifetimeSeconds) {
    return new SessionCredential(
        token, OffsetDateTime.ofInstant(NOW.plusSeconds(lifetimeSeconds), ZoneOffset.UTC));
  }

  private SessionProperties localProperties() {
    return new SessionProperties(
        Mode.LOCAL,
        "cardo.session",
        "cardo.refresh",
        "cardo.csrf",
        "/api/v1/identity/sessions/current",
        false);
  }
}
