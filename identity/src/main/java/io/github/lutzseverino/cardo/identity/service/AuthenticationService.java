package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.CurrentAuthentication;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.reader.AuthenticatedPrincipalReader;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenGrantReader;
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
  private final AuthorizationTokenGrantReader tokenGrants;

  public AuthenticationResult authenticate(@Valid AuthenticateInput input) {
    return authenticate(input.email(), input.password());
  }

  public AuthenticationResult authenticate(
      @NotBlank @Email String email, @NotBlank String password) {
    IdentityProvider.IssuedIdentityToken token =
        identityProvider.issuePasswordToken(email, password);
    AuthenticatedPrincipal principal = principal(token);
    assertEnabled(principal, token.token());
    String authorizationToken =
        requestingPartyTokens
            .authorize(
                RequestingPartyTokenRequest.allPermissions(
                    token.token(), IdentityPermissions.CLIENT_ID))
            .token();
    return new AuthenticationResult(
        principal, authorizationToken, tokenGrants.read(authorizationToken));
  }

  public AuthenticationResult getCurrent(CurrentAuthentication current) {
    AuthenticatedPrincipal principal =
        principal(
            current.authorizationSubject(),
            current.sessionId(),
            AuthenticationMethod.OIDC,
            current.expiresAt());
    assertEnabled(principal, current.accessToken());
    return new AuthenticationResult(principal, current.accessToken(), current.grants());
  }

  private AuthenticatedPrincipal principal(IdentityProvider.IssuedIdentityToken token) {
    return principal(
        token.subject(), token.sessionId(), AuthenticationMethod.PASSWORD, token.expiresAt());
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

  public void revoke(String accessToken) {
    identityProvider.revokeToken(accessToken);
  }

  private void assertEnabled(AuthenticatedPrincipal principal, String token) {
    if (!UserStatus.DISABLED.equals(principal.userStatus())) {
      return;
    }
    ApiException disabled = ApiException.forbidden("user_disabled", "User is disabled.");
    try {
      identityProvider.revokeToken(token);
    } catch (RuntimeException exception) {
      disabled.addSuppressed(exception);
    }
    throw disabled;
  }
}
