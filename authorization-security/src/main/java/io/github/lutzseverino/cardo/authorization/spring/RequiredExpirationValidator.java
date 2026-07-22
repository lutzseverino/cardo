package io.github.lutzseverino.cardo.authorization.spring;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Requires a JWT to contain an expiration claim. */
public final class RequiredExpirationValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error MISSING_EXPIRATION =
      new OAuth2Error("invalid_token", "The token expiration is required.", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    return token.getExpiresAt() == null
        ? OAuth2TokenValidatorResult.failure(MISSING_EXPIRATION)
        : OAuth2TokenValidatorResult.success();
  }
}
