package io.github.lutzseverino.cardo.authorization.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ResourceTokenValidatorsTest {

  private final ExactAudienceValidator audience = new ExactAudienceValidator("billing");
  private final RequiredExpirationValidator expiration = new RequiredExpirationValidator();

  @Test
  void acceptsOnlyTheExactSingleConfiguredAudience() {
    assertThat(audience.validate(jwt(List.of("billing"), Instant.MAX)).hasErrors()).isFalse();
    assertThat(audience.validate(jwt(List.of(), Instant.MAX)).hasErrors()).isTrue();
    assertThat(audience.validate(jwt(List.of("identity"), Instant.MAX)).hasErrors()).isTrue();
    assertThat(audience.validate(jwt(List.of("billing", "identity"), Instant.MAX)).hasErrors())
        .isTrue();
  }

  @Test
  void requiresANonBlankConfiguredAudience() {
    assertThatThrownBy(() -> new ExactAudienceValidator("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("audience must not be blank.");
  }

  @Test
  void requiresTokenExpiration() {
    assertThat(expiration.validate(jwt(List.of("billing"), null)).hasErrors()).isTrue();
    assertThat(expiration.validate(jwt(List.of("billing"), Instant.MAX)).hasErrors()).isFalse();
  }

  private Jwt jwt(List<String> audiences, Instant expiresAt) {
    Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "none").claim("aud", audiences);
    if (expiresAt != null) {
      jwt.expiresAt(expiresAt);
    }
    return jwt.build();
  }
}
