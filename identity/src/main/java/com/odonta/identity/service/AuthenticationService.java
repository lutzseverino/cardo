package com.odonta.identity.service;

import com.odonta.authorization.token.RequestingPartyTokenClient;
import com.odonta.authorization.token.RequestingPartyTokenRequest;
import com.odonta.common.api.ApiException;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.mapper.AuthenticatedPrincipalMapper;
import com.odonta.identity.model.AuthenticatedPrincipal;
import com.odonta.identity.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.model.AuthenticationMethod;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.identity.model.CreateSessionRequest;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.reader.AuthenticatedPrincipalReader;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

  private final IdentityProvider identityProvider;
  private final AuthenticatedPrincipalReader principals;
  private final RequestingPartyTokenClient requestingPartyTokens;
  private final AuthenticatedPrincipalMapper mapper;

  AuthenticationService(
      IdentityProvider identityProvider,
      AuthenticatedPrincipalReader principals,
      RequestingPartyTokenClient requestingPartyTokens,
      AuthenticatedPrincipalMapper mapper) {
    this.identityProvider = identityProvider;
    this.principals = principals;
    this.requestingPartyTokens = requestingPartyTokens;
    this.mapper = mapper;
  }

  public AuthenticationResult authenticate(CreateSessionRequest request) {
    return authenticate(request.email(), request.password());
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
    return new AuthenticationResult(getPrincipal(token), authorizationToken);
  }

  public AuthenticatedPrincipalResponse getCurrentPrincipal(
      String keycloakSubject, String sessionId, OffsetDateTime expiresAt) {
    return mapper.toResponse(
        principal(keycloakSubject, sessionId, AuthenticationMethod.OIDC, expiresAt));
  }

  private AuthenticatedPrincipalResponse getPrincipal(IdentityProvider.IssuedIdentityToken token) {
    return mapper.toResponse(
        principal(
            token.subject(), token.sessionId(), AuthenticationMethod.PASSWORD, token.expiresAt()));
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
