package io.github.lutzseverino.cardo.invite.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.invite.config.KeycloakProperties;
import io.github.lutzseverino.cardo.invite.config.ProductCallerProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ProductCallerReaderTest {

  private final ProductCallerReader reader =
      new ProductCallerReader(
          new KeycloakProperties("https://identity.example.com", "cardo", "cardo-invite", "secret"),
          new ProductCallerProperties(List.of("polity", "clinic")));

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void readsTheAuthorizedPartyFromAServiceToken() {
    authenticate(jwtBuilder().claim("azp", "polity").build());

    assertThat(reader.currentProduct()).isEqualTo("polity");
  }

  @Test
  void fallsBackToTheOAuthClientIdentifier() {
    authenticate(jwtBuilder().claim("client_id", "clinic").build());

    assertThat(reader.currentProduct()).isEqualTo("clinic");
  }

  @Test
  void rejectsEndUserTokensAtTheProductBoundary() {
    authenticate(
        jwtBuilder()
            .claim("azp", "polity")
            .claim(CardoJwtClaims.IDENTITY_USER_ID, UUID.randomUUID().toString())
            .build());

    assertThatThrownBy(reader::currentProduct)
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("product service token");
  }

  @Test
  void rejectsTokensWithoutTheDedicatedProductServiceAuthority() {
    authenticate(jwtBuilder().claim("azp", "polity").build(), List.of());

    assertThatThrownBy(reader::currentProduct)
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("product-service authority");
  }

  @Test
  void rejectsServiceAuthoritiesFromTokensWithoutTheInviteAudience() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(60))
            .claim("azp", "polity")
            .build();
    authenticate(jwt);

    assertThatThrownBy(reader::currentProduct)
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("not intended for Invite");
  }

  @Test
  void rejectsAServiceAccountThatIsNotExplicitlyAllowed() {
    authenticate(jwtBuilder().claim("azp", "unknown-product").build());

    assertThatThrownBy(reader::currentProduct)
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("not allowed");
  }

  private void authenticate(Jwt jwt) {
    authenticate(jwt, List.of(new SimpleGrantedAuthority("cardo-invite:product-service")));
  }

  private void authenticate(Jwt jwt, List<SimpleGrantedAuthority> authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
  }

  private Jwt.Builder jwtBuilder() {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.EPOCH)
        .expiresAt(Instant.EPOCH.plusSeconds(60))
        .audience(List.of("cardo-invite"));
  }
}
