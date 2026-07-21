package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class CardoProductTokenDecoderTest {

  private final JwtDecoder signatureDecoder = mock(JwtDecoder.class);
  private final CardoProductTokenDecoder tokens =
      new CardoProductTokenDecoder(signatureDecoder, "identity", "polity");

  @Test
  void distinguishesIdentitySessionAndProductAudiences() {
    Jwt identity = jwt("identity-token", List.of("identity"), UUID.randomUUID().toString());
    Jwt product = jwt("product-token", List.of("polity"), UUID.randomUUID().toString());
    when(signatureDecoder.decode("identity-token")).thenReturn(identity);
    when(signatureDecoder.decode("product-token")).thenReturn(product);

    assertThat(tokens.decodeIdentitySession("identity-token")).isSameAs(identity);
    assertThat(tokens.decodeProduct("product-token")).isSameAs(product);
  }

  @Test
  void rejectsProductTokenForIdentitySessionAndMultipleAudiences() {
    when(signatureDecoder.decode("wrong-audience"))
        .thenReturn(jwt("wrong-audience", List.of("polity"), UUID.randomUUID().toString()));
    when(signatureDecoder.decode("multiple-audiences"))
        .thenReturn(
            jwt("multiple-audiences", List.of("identity", "polity"), UUID.randomUUID().toString()));

    assertThatThrownBy(() -> tokens.decodeIdentitySession("wrong-audience"))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> tokens.decodeIdentitySession("multiple-audiences"))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void requiresValidCardoIdentityUserClaimOnBothCredentials() {
    when(signatureDecoder.decode("missing-user"))
        .thenReturn(jwt("missing-user", List.of("identity"), null));
    when(signatureDecoder.decode("malformed-user"))
        .thenReturn(jwt("malformed-user", List.of("polity"), "not-a-uuid"));

    assertThatThrownBy(() -> tokens.decodeIdentitySession("missing-user"))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> tokens.decodeProduct("malformed-user"))
        .isInstanceOf(JwtValidationException.class);
  }

  private Jwt jwt(String token, List<String> audiences, String identityUserId) {
    Jwt.Builder jwt =
        Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject("subject-1")
            .audience(audiences)
            .issuedAt(Instant.parse("2026-07-21T12:00:00Z"))
            .expiresAt(Instant.parse("2026-07-21T12:05:00Z"));
    if (identityUserId != null) {
      jwt.claim(CardoJwtClaims.IDENTITY_USER_ID, identityUserId);
    }
    return jwt.build();
  }
}
