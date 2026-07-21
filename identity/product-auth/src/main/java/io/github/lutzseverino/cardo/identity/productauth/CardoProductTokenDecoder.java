package io.github.lutzseverino.cardo.identity.productauth;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

final class CardoProductTokenDecoder {

  private final JwtDecoder signatureDecoder;
  private final OAuth2TokenValidator<Jwt> identitySession;
  private final OAuth2TokenValidator<Jwt> product;

  CardoProductTokenDecoder(
      JwtDecoder signatureDecoder, String identitySessionAudience, String productAudience) {
    this.signatureDecoder = signatureDecoder;
    CardoIdentityUserValidator identityUser = new CardoIdentityUserValidator();
    this.identitySession =
        new DelegatingOAuth2TokenValidator<>(
            new ExactAudienceValidator(identitySessionAudience), identityUser);
    this.product =
        new DelegatingOAuth2TokenValidator<>(
            new ExactAudienceValidator(productAudience), identityUser);
  }

  Jwt decodeIdentitySession(String token) {
    return decode(token, identitySession);
  }

  Jwt decodeProduct(String token) {
    return decode(token, product);
  }

  private Jwt decode(String token, OAuth2TokenValidator<Jwt> validator) {
    Jwt jwt = signatureDecoder.decode(token);
    OAuth2TokenValidatorResult result = validator.validate(jwt);
    if (result.hasErrors()) {
      throw new JwtValidationException(
          "The JWT does not satisfy the Cardo token contract.", result.getErrors());
    }
    return jwt;
  }
}
