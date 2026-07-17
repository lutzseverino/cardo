package io.github.lutzseverino.cardo.identity.integration.keycloak;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.identity.reader.AuthorizationTokenGrantReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeycloakAuthorizationTokenGrantReader implements AuthorizationTokenGrantReader {

  private final JwtDecoder jwtDecoder;
  private final KeycloakAuthoritiesConverter authorities;
  private final EffectiveGrantAuthorityReader grants;

  @Override
  public List<EffectiveGrant> read(String token) {
    return grants.read(authorities.convert(jwtDecoder.decode(token)));
  }
}
