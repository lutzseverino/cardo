package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SessionCookieAuthenticationManagerTest {

  private final CardoProductTokenDecoder tokens = mock(CardoProductTokenDecoder.class);
  private final RequestingPartyTokenClient exchange = mock(RequestingPartyTokenClient.class);
  private final AuthenticationManager productTokens = mock(AuthenticationManager.class);
  private final SessionCookieAuthenticationManager authentication =
      new SessionCookieAuthenticationManager(tokens, exchange, "polity", productTokens);

  @Test
  void validatesAndExchangesTheSessionBeforeAuthenticatingTheProductTokenOnce() {
    String identityUserId = UUID.randomUUID().toString();
    Jwt identitySession = jwt("identity-token", identityUserId);
    Jwt productCredential = jwt("product-token", identityUserId);
    JwtAuthenticationToken expected = new JwtAuthenticationToken(productCredential);
    when(tokens.decodeIdentitySession("identity-token")).thenReturn(identitySession);
    when(exchange.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity")))
        .thenReturn(new RequestingPartyToken("product-token"));
    when(productTokens.authenticate(any())).thenReturn(expected);

    assertThat(authentication.authenticate(new BearerTokenAuthenticationToken("identity-token")))
        .isSameAs(expected);

    ArgumentCaptor<BearerTokenAuthenticationToken> product =
        ArgumentCaptor.forClass(BearerTokenAuthenticationToken.class);
    verify(productTokens).authenticate(product.capture());
    assertThat(product.getValue().getToken()).isEqualTo("product-token");
    verify(tokens, never()).decodeProduct(any());
  }

  @Test
  void rejectsAnInvalidSessionBeforeExchange() {
    when(tokens.decodeIdentitySession("invalid-session"))
        .thenThrow(new JwtException("invalid signature"));

    assertInvalidSession("invalid-session");

    verify(exchange, never()).authorize(any());
    verify(productTokens, never()).authenticate(any());
  }

  @Test
  void rejectsAnInvalidExchangedCredential() {
    when(tokens.decodeIdentitySession("identity-token"))
        .thenReturn(jwt("identity-token", UUID.randomUUID().toString()));
    when(exchange.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity")))
        .thenReturn(new RequestingPartyToken("wrong-product-token"));
    when(productTokens.authenticate(any())).thenThrow(new JwtException("wrong audience"));

    assertInvalidSession("identity-token");
  }

  @Test
  void rejectsAnExchangedCredentialForADifferentIdentityUser() {
    when(tokens.decodeIdentitySession("identity-token"))
        .thenReturn(jwt("identity-token", UUID.randomUUID().toString()));
    when(exchange.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity")))
        .thenReturn(new RequestingPartyToken("product-token"));
    when(productTokens.authenticate(any()))
        .thenReturn(new JwtAuthenticationToken(jwt("product-token", UUID.randomUUID().toString())));

    assertInvalidSession("identity-token");
  }

  private void assertInvalidSession(String token) {
    assertThatThrownBy(() -> authentication.authenticate(new BearerTokenAuthenticationToken(token)))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(
            failure ->
                assertThat(((OAuth2AuthenticationException) failure).getError().getDescription())
                    .isEqualTo("The browser session could not be exchanged."));
  }

  private Jwt jwt(String token, String identityUserId) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject("subject-1")
        .claim(CardoJwtClaims.IDENTITY_USER_ID, identityUserId)
        .build();
  }
}
