package com.odonta.identity.service;

import com.odonta.authorization.grant.EffectiveGrant;
import com.odonta.authorization.grant.EffectiveGrantAuthorityReader;
import com.odonta.authorization.keycloak.KeycloakAuthoritiesConverter;
import com.odonta.authorization.token.RequestingPartyTokenClient;
import com.odonta.authorization.token.RequestingPartyTokenRequest;
import com.odonta.common.api.ApiException;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.model.AuthenticateInput;
import com.odonta.identity.model.AuthenticatedPrincipal;
import com.odonta.identity.model.AuthenticationMethod;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.identity.model.UserStatus;
import com.odonta.identity.provider.IdentityProvider;
import com.odonta.identity.reader.AuthenticatedPrincipalReader;
import com.odonta.identity.reader.CurrentJwtReader;
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
