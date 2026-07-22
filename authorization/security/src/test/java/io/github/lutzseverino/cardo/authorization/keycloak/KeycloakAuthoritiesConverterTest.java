package io.github.lutzseverino.cardo.authorization.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakAuthoritiesConverterTest {

  private final KeycloakAuthoritiesConverter converter = new KeycloakAuthoritiesConverter();

  @Test
  void convertsRealmClientRolesAndAuthorizationPermissions() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(60))
            .claim("realm_access", Map.of("roles", List.of("admin")))
            .claim("resource_access", Map.of("identity", Map.of("roles", List.of("profile:read"))))
            .claim(
                "authorization",
                Map.of(
                    "permissions",
                    List.of(
                        Map.of(
                            "rsname",
                            "identity:user:7f7af229-729c-45ec-8f45-c5ca6d8b1967",
                            "scopes",
                            List.of("read", "write")))))
            .build();

    Collection<String> authorities =
        converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();

    assertThat(authorities)
        .containsExactlyInAnyOrder(
            "realm:admin",
            "identity:profile:read",
            "identity:user:7f7af229-729c-45ec-8f45-c5ca6d8b1967:read",
            "identity:user:7f7af229-729c-45ec-8f45-c5ca6d8b1967:write");
  }
}
