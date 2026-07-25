package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.AuthenticationSource;
import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;
import io.github.lutzseverino.cardo.identity.model.BrowserSessionMetadata;
import io.github.lutzseverino.cardo.identity.model.BrowserSessionRefresh;
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
import java.util.List;
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
  private final BrowserSessionService browserSessions;

  public SessionResult authenticate(@Valid AuthenticateInput input) {
    return authenticate(input.email(), input.password());
  }

  public SessionResult authenticate(@NotBlank @Email String email, @NotBlank String password) {
    IdentityProvider.IssuedSession providerSession =
        identityProvider.issuePasswordSession(email, password);
    try {
      return establishNew(providerSession, AuthenticationMethod.PASSWORD);
    } catch (RuntimeException failure) {
      revokeAfterFailedEstablishment(providerSession.refreshToken(), failure);
      throw failure;
    }
  }

  public SessionResult refresh(@NotBlank String refreshToken) {
    BrowserSessionRefresh refresh = browserSessions.claimRefresh(refreshToken);
    IdentityProvider.IssuedSession providerSession;
    try {
      providerSession = identityProvider.refreshSession(refreshToken);
    } catch (ApiException failure) {
      if ("invalid_session".equals(failure.code())) {
        ApiException revoked = sessionRevoked(failure);
        revokeRefreshIfOwned(refresh, revoked);
        throw revoked;
      }
      revokeRefreshIfOwned(refresh, failure);
      throw failure;
    } catch (RuntimeException failure) {
      revokeRefreshIfOwned(refresh, failure);
      throw failure;
    }

    try {
      Authorization authorization = authorizePrincipal(providerSession, AuthenticationMethod.OIDC);
      BrowserSessionMetadata browserSession =
          browserSessions.completeRefresh(
              refresh,
              providerSession.sessionId(),
              providerSession.subject(),
              providerSession.refreshToken(),
              providerSession.refreshExpiresAt());
      return result(providerSession, authorization, browserSession);
    } catch (RuntimeException failure) {
      if (revokeRefreshIfOwned(refresh, failure)) {
        revokeAfterFailedEstablishment(providerSession.refreshToken(), failure);
      }
      throw failure;
    }
  }

  public AuthenticationResult getCurrent(CurrentAuthentication current) {
    AuthenticatedPrincipal principal =
        principal(current.authorizationSubject(), current.sessionId(), AuthenticationMethod.OIDC);
    assertOperational(principal);
    var browserSession =
        browserSessions.current(current.sessionId(), current.authorizationSubject());
    if (AuthenticationSource.BROWSER_SESSION.equals(current.source()) && browserSession.isEmpty()) {
      throw ApiException.of(401, "session_revoked", "The browser session was revoked.");
    }
    return new AuthenticationResult(
        principal, current.grants(), current.authorizationExpiresAt(), browserSession.orElse(null));
  }

  public void revoke(@NotBlank String refreshToken) {
    browserSessions.revoke(refreshToken);
    identityProvider.revokeSession(refreshToken);
  }

  private SessionResult establishNew(
      IdentityProvider.IssuedSession providerSession, AuthenticationMethod authenticationMethod) {
    Authorization authorization = authorizePrincipal(providerSession, authenticationMethod);
    BrowserSessionMetadata browserSession =
        browserSessions.start(
            providerSession.sessionId(),
            providerSession.subject(),
            providerSession.refreshToken(),
            providerSession.refreshExpiresAt());
    return result(providerSession, authorization, browserSession);
  }

  private Authorization authorizePrincipal(
      IdentityProvider.IssuedSession providerSession, AuthenticationMethod authenticationMethod) {
    String authorizationToken = authorize(providerSession.accessToken());
    AuthorizationTokenResult authorization = authorizationTokens.read(authorizationToken);
    if (!providerSession.subject().equals(authorization.subject())) {
      throw ApiException.of(
          502,
          "identity_authorization_subject_mismatch",
          "Identity provider authorization token did not match the authenticated subject.");
    }
    AuthenticatedPrincipal principal =
        principal(providerSession.subject(), providerSession.sessionId(), authenticationMethod);
    assertOperational(principal);
    return new Authorization(
        authorizationToken, authorization.expiresAt(), authorization.grants(), principal);
  }

  private SessionResult result(
      IdentityProvider.IssuedSession providerSession,
      Authorization authorization,
      BrowserSessionMetadata browserSession) {
    return new SessionResult(
        new AuthenticationResult(
            authorization.principal(),
            authorization.grants(),
            authorization.expiresAt(),
            browserSession),
        new SessionCredential(authorization.token(), authorization.expiresAt()),
        new SessionCredential(providerSession.refreshToken(), providerSession.refreshExpiresAt()));
  }

  private AuthenticatedPrincipal principal(
      String subject, String sessionId, AuthenticationMethod authenticationMethod) {
    return principals
        .findByKeycloakSubject(subject, sessionId, authenticationMethod)
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

  private ApiException sessionRevoked(ApiException providerFailure) {
    ApiException failure =
        ApiException.of(401, "session_revoked", "The browser session was revoked.");
    failure.addSuppressed(providerFailure);
    return failure;
  }

  private boolean revokeRefreshIfOwned(
      BrowserSessionRefresh refresh, RuntimeException originalFailure) {
    try {
      return browserSessions.revokeRefresh(refresh);
    } catch (RuntimeException compensationFailure) {
      originalFailure.addSuppressed(compensationFailure);
      return true;
    }
  }

  private record Authorization(
      String token,
      OffsetDateTime expiresAt,
      List<EffectiveGrant> grants,
      AuthenticatedPrincipal principal) {}
}
