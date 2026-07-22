package io.github.lutzseverino.cardo.authorization.spring;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Requires a JWT to publish exactly one audience matching the configured resource. */
public final class ExactAudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_AUDIENCE =
      new OAuth2Error("invalid_token", "The token audience is not valid for this resource.", null);

  private final String audience;

  public ExactAudienceValidator(String audience) {
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("audience must not be blank.");
    }
    this.audience = audience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    return List.of(audience).equals(token.getAudience())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
  }
}
