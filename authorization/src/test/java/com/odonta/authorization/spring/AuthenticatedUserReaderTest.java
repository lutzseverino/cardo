package com.odonta.authorization.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedUserReaderTest {

  private final AuthenticatedUserReader reader = new AuthenticatedUserReader();

  @Test
  void readsAuthenticatedUserFromOdontaJwtClaims() {
    UUID userId = UUID.randomUUID();
    JwtAuthenticationToken authentication =
        new JwtAuthenticationToken(
            jwtBuilder()
                .subject("keycloak-subject")
                .claim(OdontaJwtClaims.IDENTITY_USER_ID, userId.toString())
                .claim("name", "Ada Lovelace")
                .build());

    AuthenticatedUser user = reader.currentUser(authentication);

    assertThat(user.id()).isEqualTo(userId);
    assertThat(user.authorizationSubject()).isEqualTo("keycloak-subject");
    assertThat(user.name()).isEqualTo("Ada Lovelace");
  }

  @Test
  void rejectsTokensWithoutCanonicalIdentityUserId() {
    JwtAuthenticationToken authentication =
        new JwtAuthenticationToken(jwtBuilder().subject("keycloak-subject").build());

    assertThatThrownBy(() -> reader.currentUser(authentication))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Missing Odonta identity user claim.");
  }

  private Jwt.Builder jwtBuilder() {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.EPOCH)
        .expiresAt(Instant.EPOCH.plusSeconds(60));
  }
}
