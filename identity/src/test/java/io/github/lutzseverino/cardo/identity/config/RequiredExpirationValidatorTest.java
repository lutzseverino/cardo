package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.spring.RequiredExpirationValidator;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RequiredExpirationValidatorTest {

  private final RequiredExpirationValidator validator = new RequiredExpirationValidator();

  @Test
  void requiresTokenExpiration() {
    assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();
    assertThat(validator.validate(jwt(Instant.parse("2030-01-01T00:00:00Z"))).hasErrors())
        .isFalse();
  }

  private Jwt jwt(Instant expiresAt) {
    Jwt.Builder jwt =
        Jwt.withTokenValue("token").header("alg", "none").claim("sub", "identity-user");
    if (expiresAt != null) {
      jwt.expiresAt(expiresAt);
    }
    return jwt.build();
  }
}
