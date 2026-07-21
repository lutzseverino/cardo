package io.github.lutzseverino.cardo.identity.productauth;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class ExactAudienceValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_AUDIENCE =
      new OAuth2Error(
          "invalid_token", "The token audience is not valid for this credential.", null);

  private final String audience;

  ExactAudienceValidator(String audience) {
    this.audience = audience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    return List.of(audience).equals(token.getAudience())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
  }
}
