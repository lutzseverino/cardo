package io.github.lutzseverino.cardo.identity.integration.keycloak;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenReader;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeycloakAuthorizationTokenReader implements AuthorizationTokenReader {

  private final JwtDecoder jwtDecoder;
  private final KeycloakAuthoritiesConverter authorities;
  private final EffectiveGrantAuthorityReader grants;

  @Override
  public AuthorizationTokenResult read(String token) {
    Jwt jwt = jwtDecoder.decode(token);
    return new AuthorizationTokenResult(
        OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC),
        grants.read(authorities.convert(jwt)));
  }
}
