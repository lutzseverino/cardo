package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;
import io.github.lutzseverino.cardo.identity.model.CurrentAuthentication;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.reader.AuthenticatedPrincipalReader;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenReader;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

  private static final OffsetDateTime ACCESS_EXPIRES_AT =
      OffsetDateTime.parse("2030-01-01T00:05:00Z");
  private static final OffsetDateTime REFRESH_EXPIRES_AT =
      OffsetDateTime.parse("2030-01-01T08:00:00Z");

  private final IdentityProvider identityProvider = mock(IdentityProvider.class);
  private final AuthenticatedPrincipalReader principals = mock(AuthenticatedPrincipalReader.class);
  private final RequestingPartyTokenClient requestingPartyTokens =
      mock(RequestingPartyTokenClient.class);
  private final AuthorizationTokenReader authorizationTokens = mock(AuthorizationTokenReader.class);
  private final AuthenticationService service =
      new AuthenticationService(
          identityProvider, principals, requestingPartyTokens, authorizationTokens);

  @Test
  void establishesPasswordSessionFromProviderRefreshAndIdentityAudienceCredentials() {
    IdentityProvider.IssuedSession providerSession = providerSession();
    when(identityProvider.issuePasswordSession("user@example.com", "password-1"))
        .thenReturn(providerSession);
    prepareAuthorization(AuthenticationMethod.PASSWORD, UserStatus.ACTIVE);

    var result = service.authenticate("user@example.com", "password-1");

    assertThat(result.accessCredential().token()).isEqualTo("identity-rpt");
    assertThat(result.accessCredential().expiresAt()).isEqualTo(ACCESS_EXPIRES_AT);
    assertThat(result.refreshCredential().token()).isEqualTo("refresh-token");
    assertThat(result.refreshCredential().expiresAt()).isEqualTo(REFRESH_EXPIRES_AT);
    assertThat(result.authentication().principal().authenticationMethod())
        .isEqualTo(AuthenticationMethod.PASSWORD);
    verify(requestingPartyTokens)
        .authorize(
            RequestingPartyTokenRequest.allPermissions(
                providerSession.accessToken(), IdentityPermissions.CLIENT_ID));
  }

  @Test
  void refreshesAndRotatesTheProviderSession() {
    when(identityProvider.refreshSession("old-refresh-token")).thenReturn(providerSession());
    prepareAuthorization(AuthenticationMethod.OIDC, UserStatus.ACTIVE);

    var result = service.refresh("old-refresh-token");

    assertThat(result.refreshCredential().token()).isEqualTo("refresh-token");
    assertThat(result.authentication().principal().authenticationMethod())
        .isEqualTo(AuthenticationMethod.OIDC);
  }

  @Test
  void revokesProviderSessionWhenEstablishmentFails() {
    when(identityProvider.issuePasswordSession("user@example.com", "password-1"))
        .thenReturn(providerSession());
    when(requestingPartyTokens.authorize(eq(identityAuthorizationRequest())))
        .thenThrow(ApiException.of(503, "identity_provider_error", "Provider unavailable."));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "password-1"))
        .isInstanceOf(ApiException.class)
        .hasMessage("Provider unavailable.");

    verify(identityProvider).revokeSession("refresh-token");
  }

  @Test
  void rejectsDisabledUserAndRevokesTheProviderSession() {
    when(identityProvider.issuePasswordSession("user@example.com", "password-1"))
        .thenReturn(providerSession());
    prepareAuthorization(AuthenticationMethod.PASSWORD, UserStatus.DISABLED);

    assertThatThrownBy(() -> service.authenticate("user@example.com", "password-1"))
        .isInstanceOf(ApiException.class)
        .hasMessage("User is disabled.");

    verify(identityProvider).revokeSession("refresh-token");
  }

  @Test
  void readsCurrentAuthenticationWithoutIssuingOrRefreshingCredentials() {
    CurrentAuthentication current =
        new CurrentAuthentication("subject-1", "session-1", ACCESS_EXPIRES_AT, List.of());
    when(principals.findByKeycloakSubject(
            "subject-1", "session-1", AuthenticationMethod.OIDC, ACCESS_EXPIRES_AT))
        .thenReturn(Optional.of(principal(UserStatus.ACTIVE, AuthenticationMethod.OIDC)));

    var result = service.getCurrent(current);

    assertThat(result.principal().keycloakSubject()).isEqualTo("subject-1");
    verify(identityProvider, never()).refreshSession("identity-rpt");
  }

  @Test
  void revokesWithTheRefreshCredential() {
    service.revoke("refresh-token");

    verify(identityProvider).revokeSession("refresh-token");
  }

  private void prepareAuthorization(AuthenticationMethod method, UserStatus status) {
    when(requestingPartyTokens.authorize(identityAuthorizationRequest()))
        .thenReturn(new RequestingPartyToken("identity-rpt"));
    when(authorizationTokens.read("identity-rpt"))
        .thenReturn(new AuthorizationTokenResult(ACCESS_EXPIRES_AT, List.of()));
    when(principals.findByKeycloakSubject("subject-1", "session-1", method, ACCESS_EXPIRES_AT))
        .thenReturn(Optional.of(principal(status, method)));
  }

  private RequestingPartyTokenRequest identityAuthorizationRequest() {
    return RequestingPartyTokenRequest.allPermissions("provider-access-token", "identity");
  }

  private IdentityProvider.IssuedSession providerSession() {
    return new IdentityProvider.IssuedSession(
        "provider-access-token",
        ACCESS_EXPIRES_AT,
        "refresh-token",
        REFRESH_EXPIRES_AT,
        "subject-1",
        "session-1");
  }

  private AuthenticatedPrincipal principal(UserStatus status, AuthenticationMethod method) {
    return new AuthenticatedPrincipal(
        "session-1",
        UUID.randomUUID(),
        "subject-1",
        "user@example.com",
        "User",
        null,
        status,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        method,
        null,
        ACCESS_EXPIRES_AT);
  }
}
