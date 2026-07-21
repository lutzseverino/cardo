package io.github.lutzseverino.cardo.identity.productauth;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

final class ProductTokenBearerTokenResolver implements BearerTokenResolver {

  private static final OAuth2Error INVALID_SESSION =
      new OAuth2Error("invalid_token", "The browser session could not be exchanged.", null);

  private final DefaultBearerTokenResolver authorizationHeader = new DefaultBearerTokenResolver();
  private final SessionCookieAuthenticationSelector selector;
  private final CardoProductTokenDecoder tokens;
  private final RequestingPartyTokenClient exchange;
  private final String productAudience;

  ProductTokenBearerTokenResolver(
      SessionCookieAuthenticationSelector selector,
      CardoProductTokenDecoder tokens,
      RequestingPartyTokenClient exchange,
      String productAudience) {
    this.selector = selector;
    this.tokens = tokens;
    this.exchange = exchange;
    this.productAudience = productAudience;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    if (selector.hasAuthorizationHeader(request)) {
      return authorizationHeader.resolve(request);
    }
    String sessionToken = selector.sessionToken(request);
    if (sessionToken == null) {
      return null;
    }
    try {
      var identitySession = tokens.decodeIdentitySession(sessionToken);
      RequestingPartyToken productToken =
          exchange.authorize(
              RequestingPartyTokenRequest.allPermissions(sessionToken, productAudience));
      var productCredential = tokens.decodeProduct(productToken.token());
      if (!Objects.equals(
          identitySession.getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID),
          productCredential.getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID))) {
        throw new OAuth2AuthenticationException(INVALID_SESSION);
      }
      return productToken.token();
    } catch (RuntimeException failure) {
      throw new OAuth2AuthenticationException(INVALID_SESSION, failure);
    }
  }
}
