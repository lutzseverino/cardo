package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;
import io.github.lutzseverino.cardo.identity.model.CurrentAuthentication;
import io.github.lutzseverino.cardo.identity.model.SessionCredential;
import io.github.lutzseverino.cardo.identity.model.SessionResult;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.reader.AuthenticatedPrincipalReader;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
  private final AuthorizationTokenReader authorizationTokens;

  public SessionResult authenticate(@Valid AuthenticateInput input) {
    return authenticate(input.email(), input.password());
  }

  public SessionResult authenticate(@NotBlank @Email String email, @NotBlank String password) {
    return establish(
        identityProvider.issuePasswordSession(email, password), AuthenticationMethod.PASSWORD);
  }

  public SessionResult refresh(@NotBlank String refreshToken) {
    return establish(identityProvider.refreshSession(refreshToken), AuthenticationMethod.OIDC);
  }

  public AuthenticationResult getCurrent(CurrentAuthentication current) {
    AuthenticatedPrincipal principal =
        principal(
            current.authorizationSubject(),
            current.sessionId(),
            AuthenticationMethod.OIDC,
            current.expiresAt());
    assertEnabled(principal);
    return new AuthenticationResult(principal, current.grants());
  }

  public void revoke(@NotBlank String refreshToken) {
    identityProvider.revokeSession(refreshToken);
  }

  private SessionResult establish(
      IdentityProvider.IssuedSession providerSession, AuthenticationMethod authenticationMethod) {
    try {
      String authorizationToken =
          requestingPartyTokens
              .authorize(
                  RequestingPartyTokenRequest.allPermissions(
                      providerSession.accessToken(), IdentityPermissions.CLIENT_ID))
              .token();
      AuthorizationTokenResult authorization = authorizationTokens.read(authorizationToken);
      AuthenticatedPrincipal principal =
          principal(
              providerSession.subject(),
              providerSession.sessionId(),
              authenticationMethod,
              authorization.expiresAt());
      assertEnabled(principal);
      return new SessionResult(
          new AuthenticationResult(principal, authorization.grants()),
          new SessionCredential(authorizationToken, authorization.expiresAt()),
          new SessionCredential(
              providerSession.refreshToken(), providerSession.refreshExpiresAt()));
    } catch (RuntimeException exception) {
      revokeAfterFailedEstablishment(providerSession.refreshToken(), exception);
      throw exception;
    }
  }

  private AuthenticatedPrincipal principal(
      String subject,
      String sessionId,
      AuthenticationMethod authenticationMethod,
      OffsetDateTime expiresAt) {
    return principals
        .findByKeycloakSubject(subject, sessionId, authenticationMethod, expiresAt)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "authenticated_principal_not_found", "Authenticated principal not found."));
  }

  private void assertEnabled(AuthenticatedPrincipal principal) {
    if (!UserStatus.DISABLED.equals(principal.userStatus())) {
      return;
    }
    throw ApiException.forbidden("user_disabled", "User is disabled.");
  }

  private void revokeAfterFailedEstablishment(String refreshToken, RuntimeException failure) {
    try {
      identityProvider.revokeSession(refreshToken);
    } catch (RuntimeException exception) {
      failure.addSuppressed(exception);
    }
  }
}
