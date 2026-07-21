package io.github.lutzseverino.cardo.identity.productauth;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class SessionCookieAuthenticationManager implements AuthenticationManager {

  private static final OAuth2Error INVALID_SESSION =
      new OAuth2Error("invalid_token", "The browser session could not be exchanged.", null);

  private final CardoProductTokenDecoder tokens;
  private final RequestingPartyTokenClient exchange;
  private final String productAudience;
  private final AuthenticationManager productTokens;

  SessionCookieAuthenticationManager(
      CardoProductTokenDecoder tokens,
      RequestingPartyTokenClient exchange,
      String productAudience,
      AuthenticationManager productTokens) {
    this.tokens = tokens;
    this.exchange = exchange;
    this.productAudience = productAudience;
    this.productTokens = productTokens;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    try {
      String sessionToken = ((BearerTokenAuthenticationToken) authentication).getToken();
      var identitySession = tokens.decodeIdentitySession(sessionToken);
      RequestingPartyToken productToken =
          exchange.authorize(
              RequestingPartyTokenRequest.allPermissions(sessionToken, productAudience));
      Authentication productAuthentication =
          productTokens.authenticate(new BearerTokenAuthenticationToken(productToken.token()));
      if (!(productAuthentication instanceof JwtAuthenticationToken productCredential)
          || !Objects.equals(
              identitySession.getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID),
              productCredential.getToken().getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID))) {
        throw new OAuth2AuthenticationException(INVALID_SESSION);
      }
      return productAuthentication;
    } catch (RuntimeException failure) {
      if (failure instanceof OAuth2AuthenticationException oauth2Failure
          && INVALID_SESSION.equals(oauth2Failure.getError())) {
        throw oauth2Failure;
      }
      throw new OAuth2AuthenticationException(INVALID_SESSION, failure);
    }
  }
}
