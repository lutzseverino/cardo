package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class IdentityAudienceValidatorTest {

  private final IdentityAudienceValidator validator = new IdentityAudienceValidator("identity");

  @Test
  void acceptsOnlyTheExactIdentityAudience() {
    assertThat(validator.validate(jwt(List.of("identity"))).hasErrors()).isFalse();
  }

  @Test
  void rejectsMissingDifferentAndAdditionalAudiences() {
    assertThat(validator.validate(jwt(List.of())).hasErrors()).isTrue();
    assertThat(validator.validate(jwt(List.of("polity"))).hasErrors()).isTrue();
    assertThat(validator.validate(jwt(List.of("identity", "polity"))).hasErrors()).isTrue();
  }

  private Jwt jwt(List<String> audiences) {
    return Jwt.withTokenValue("token").header("alg", "none").claim("aud", audiences).build();
  }
}
