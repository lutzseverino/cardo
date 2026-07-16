package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.reader.AuthenticatedPrincipalReader;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final IdentityProvider identityProvider;
  private final AuthenticatedPrincipalReader principals;
  private final RequestingPartyTokenClient requestingPartyTokens;
  private final CurrentJwtReader currentJwt;
  private final JwtDecoder jwtDecoder;
  private final KeycloakAuthoritiesConverter authorities;
  private final EffectiveGrantAuthorityReader grantReader;

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
    return new AuthenticationResult(principal, authorizationToken, grants(authorizationToken));
  }

  public AuthenticationResult getCurrentPrincipal() {
    var current = currentJwt.current();
    Jwt jwt = current.getToken();
    AuthenticatedPrincipal principal =
        principal(
            jwt.getSubject(),
            jwt.getClaimAsString("sid"),
            AuthenticationMethod.OIDC,
            expiresAt(jwt));
    assertEnabled(principal, jwt.getTokenValue());
    return new AuthenticationResult(
        principal, jwt.getTokenValue(), grantReader.read(current.getAuthorities()));
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

  public void revokeCurrent() {
    identityProvider.revokeToken(currentJwt.current().getToken().getTokenValue());
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

  private OffsetDateTime expiresAt(Jwt jwt) {
    return jwt.getExpiresAt() == null
        ? null
        : OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC);
  }

  private List<EffectiveGrant> grants(String token) {
    Collection<GrantedAuthority> grantedAuthorities = authorities.convert(jwtDecoder.decode(token));
    return grantReader.read(grantedAuthorities);
  }
}
