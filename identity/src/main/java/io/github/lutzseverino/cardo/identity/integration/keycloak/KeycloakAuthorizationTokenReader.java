package io.github.lutzseverino.cardo.identity.integration.keycloak;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenReader;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeycloakAuthorizationTokenReader implements AuthorizationTokenReader {

  private final JwtDecoder jwtDecoder;
  private final KeycloakAuthoritiesConverter authorities;
  private final EffectiveGrantAuthorityReader grants;

  @Override
  public AuthorizationTokenResult read(String token) {
    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(token);
    } catch (JwtException exception) {
      throw ApiException.of(
          502,
          "identity_authorization_token_invalid",
          "Identity provider returned an invalid authorization token.");
    }
    if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      throw ApiException.of(
          502,
          "identity_authorization_subject_missing",
          "Identity provider authorization token did not identify a subject.");
    }
    return new AuthorizationTokenResult(
        jwt.getSubject(),
        OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC),
        grants.read(authorities.convert(jwt)));
  }
}
