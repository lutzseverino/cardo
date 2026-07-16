package com.odonta.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odonta.authorization.grant.EffectiveGrantAuthorityReader;
import com.odonta.authorization.keycloak.KeycloakAuthoritiesConverter;
import com.odonta.authorization.token.RequestingPartyTokenClient;
import com.odonta.common.api.ApiException;
import com.odonta.identity.model.AuthenticatedPrincipal;
import com.odonta.identity.model.AuthenticationMethod;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.reader.AuthenticatedPrincipalReader;
import com.odonta.identity.reader.CurrentJwtReader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AuthenticationServiceTest {

  private final IdentityProvider identityProvider = mock(IdentityProvider.class);
  private final AuthenticatedPrincipalReader principals = mock(AuthenticatedPrincipalReader.class);
  private final RequestingPartyTokenClient requestingPartyTokens =
      mock(RequestingPartyTokenClient.class);
  private final CurrentJwtReader currentJwt = mock(CurrentJwtReader.class);
  private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
  private final AuthenticationService service =
      new AuthenticationService(
          identityProvider,
          principals,
          requestingPartyTokens,
          currentJwt,
          jwtDecoder,
          new KeycloakAuthoritiesConverter(),
          new EffectiveGrantAuthorityReader());

  @Test
  void rejectsAndRevokesPasswordTokenForDisabledUser() {
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
    when(identityProvider.issuePasswordToken("user@example.com", "password-1"))
        .thenReturn(
            new IdentityProvider.IssuedIdentityToken(
                "access-token", expiresAt, "subject-1", "session-1"));
    when(principals.findByKeycloakSubject(
            eq("subject-1"), eq("session-1"), eq(AuthenticationMethod.PASSWORD), eq(expiresAt)))
        .thenReturn(Optional.of(principal(UserStatus.DISABLED, expiresAt)));

    assertThatThrownBy(() -> service.authenticate("user@example.com", "password-1"))
        .isInstanceOf(ApiException.class)
        .hasMessage("User is disabled.");

    verify(identityProvider).revokeToken("access-token");
    verify(requestingPartyTokens, never()).authorize(any());
  }

  private AuthenticatedPrincipal principal(UserStatus status, OffsetDateTime expiresAt) {
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
        AuthenticationMethod.PASSWORD,
        null,
        expiresAt);
  }
}
