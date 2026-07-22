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
    assertOperational(principal);
    return new AuthenticationResult(principal, current.grants());
  }

  public void revoke(@NotBlank String refreshToken) {
    identityProvider.revokeSession(refreshToken);
  }

  private SessionResult establish(
      IdentityProvider.IssuedSession providerSession, AuthenticationMethod authenticationMethod) {
    try {
      String authorizationToken = authorize(providerSession.accessToken());
      AuthorizationTokenResult authorization = authorizationTokens.read(authorizationToken);
      if (!providerSession.subject().equals(authorization.subject())) {
        throw ApiException.of(
            502,
            "identity_authorization_subject_mismatch",
            "Identity provider authorization token did not match the authenticated subject.");
      }
      AuthenticatedPrincipal principal =
          principal(
              providerSession.subject(),
              providerSession.sessionId(),
              authenticationMethod,
              authorization.expiresAt());
      assertOperational(principal);
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

  private String authorize(String providerAccessToken) {
    try {
      return requestingPartyTokens
          .authorize(
              RequestingPartyTokenRequest.allPermissions(
                  providerAccessToken, IdentityPermissions.CLIENT_ID))
          .token();
    } catch (ApiException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      ApiException failure =
          ApiException.of(
              502,
              "identity_authorization_failed",
              "Identity provider did not establish authorization.");
      failure.addSuppressed(exception);
      throw failure;
    }
  }

  private void assertOperational(AuthenticatedPrincipal principal) {
    if (UserStatus.ACTIVE.equals(principal.userStatus())) {
      return;
    }
    if (UserStatus.DISABLED.equals(principal.userStatus())) {
      throw ApiException.forbidden("user_disabled", "User is disabled.");
    }
    if (UserStatus.INVITED.equals(principal.userStatus())) {
      throw ApiException.forbidden(
          "user_invited", "Invited users cannot establish a session before activation.");
    }
    throw ApiException.forbidden("user_inactive", "User is not active.");
  }

  private void revokeAfterFailedEstablishment(String refreshToken, RuntimeException failure) {
    try {
      identityProvider.revokeSession(refreshToken);
    } catch (RuntimeException exception) {
      failure.addSuppressed(exception);
    }
  }
}
