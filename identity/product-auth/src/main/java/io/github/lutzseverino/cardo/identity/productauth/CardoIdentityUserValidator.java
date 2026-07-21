package io.github.lutzseverino.cardo.identity.productauth;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class CardoIdentityUserValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_USER =
      new OAuth2Error("invalid_token", "The Cardo identity user claim is not valid.", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String value = token.getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID);
    if (value != null) {
      try {
        UUID.fromString(value);
        return OAuth2TokenValidatorResult.success();
      } catch (IllegalArgumentException ignored) {
        // Report the same credential error for absent and malformed user identifiers.
      }
    }
    return OAuth2TokenValidatorResult.failure(INVALID_USER);
  }
}
