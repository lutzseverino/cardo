package com.odonta.identity.service;

import com.odonta.authorization.token.RequestingPartyTokenClient;
import com.odonta.authorization.token.RequestingPartyTokenRequest;
import com.odonta.common.api.ApiException;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.model.AuthenticatedPrincipal;
import com.odonta.identity.model.AuthenticationMethod;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.identity.model.CreateSessionCommand;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.reader.AuthenticatedPrincipalReader;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final IdentityProvider identityProvider;
  private final AuthenticatedPrincipalReader principals;
  private final RequestingPartyTokenClient requestingPartyTokens;

  public AuthenticationResult authenticate(@Valid CreateSessionCommand command) {
    return authenticate(command.email(), command.password());
  }

  public AuthenticationResult authenticate(String email, String password) {
    IdentityProvider.IssuedIdentityToken token =
        identityProvider.issuePasswordToken(email, password);
    String authorizationToken =
        requestingPartyTokens
            .authorize(
                RequestingPartyTokenRequest.allPermissions(
                    token.token(), IdentityPermissions.CLIENT_ID))
            .token();
    return new AuthenticationResult(principal(token), authorizationToken);
  }

  public AuthenticatedPrincipal getCurrentPrincipal(
      String keycloakSubject, String sessionId, OffsetDateTime expiresAt) {
    return principal(keycloakSubject, sessionId, AuthenticationMethod.OIDC, expiresAt);
  }

  private AuthenticatedPrincipal principal(IdentityProvider.IssuedIdentityToken token) {
    return principal(
        token.subject(), token.sessionId(), AuthenticationMethod.PASSWORD, token.expiresAt());
  }

  private AuthenticatedPrincipal principal(
      String subject,
      String sessionId,
      AuthenticationMethod authenticationMethod,
      java.time.OffsetDateTime expiresAt) {
    return principals
        .findByKeycloakSubject(subject, sessionId, authenticationMethod, expiresAt)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "authenticated_principal_not_found", "Authenticated principal not found."));
  }

  public void revoke(String token) {
    identityProvider.revokeToken(token);
  }
}
