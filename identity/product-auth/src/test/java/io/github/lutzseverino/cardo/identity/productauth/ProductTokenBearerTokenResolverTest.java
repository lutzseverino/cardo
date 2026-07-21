package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.spring.CardoJwtClaims;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyToken;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class ProductTokenBearerTokenResolverTest {

  private final SessionCookieAuthenticationSelector selector =
      new SessionCookieAuthenticationSelector("cardo.session");
  private final CardoProductTokenDecoder tokens = mock(CardoProductTokenDecoder.class);
  private final RequestingPartyTokenClient exchange = mock(RequestingPartyTokenClient.class);
  private final ProductTokenBearerTokenResolver resolver =
      new ProductTokenBearerTokenResolver(selector, tokens, exchange, "polity");

  @Test
  void validatesSessionExchangesAndValidatesProductCredential() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("cardo.session", "identity-token"));
    String identityUserId = UUID.randomUUID().toString();
    Jwt identitySession = jwt(identityUserId);
    Jwt productCredential = jwt(identityUserId);
    when(tokens.decodeIdentitySession("identity-token")).thenReturn(identitySession);
    when(exchange.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity")))
        .thenReturn(new RequestingPartyToken("product-token"));
    when(tokens.decodeProduct("product-token")).thenReturn(productCredential);

    assertThat(resolver.resolve(request)).isEqualTo("product-token");

    verify(tokens).decodeIdentitySession("identity-token");
    verify(tokens).decodeProduct("product-token");
  }

  @Test
  void explicitProductBearerTakesPrecedenceWithoutBrowserExchange() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer explicit-product-token");
    request.setCookies(new Cookie("cardo.session", "identity-token"));

    assertThat(resolver.resolve(request)).isEqualTo("explicit-product-token");

    verify(exchange, never()).authorize(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void malformedAuthorizationHeaderNeverFallsBackToBrowserSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Basic malformed-credential");
    request.setCookies(new Cookie("cardo.session", "identity-token"));

    assertThat(resolver.resolve(request)).isNull();

    verify(exchange, never()).authorize(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void invalidSessionFailsClosedBeforeExchange() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("cardo.session", "invalid-session"));
    when(tokens.decodeIdentitySession("invalid-session"))
        .thenThrow(new JwtException("invalid signature"));

    assertThatThrownBy(() -> resolver.resolve(request))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(
            failure ->
                assertThat(((OAuth2AuthenticationException) failure).getError().getDescription())
                    .isEqualTo("The browser session could not be exchanged."));

    verify(exchange, never()).authorize(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void invalidExchangedCredentialFailsClosed() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("cardo.session", "identity-token"));
    when(exchange.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity")))
        .thenReturn(new RequestingPartyToken("wrong-product-token"));
    when(tokens.decodeProduct("wrong-product-token")).thenThrow(new JwtException("wrong audience"));

    assertThatThrownBy(() -> resolver.resolve(request))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejectsExchangedCredentialForADifferentIdentityUser() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("cardo.session", "identity-token"));
    Jwt identitySession = jwt(UUID.randomUUID().toString());
    Jwt productCredential = jwt(UUID.randomUUID().toString());
    when(tokens.decodeIdentitySession("identity-token")).thenReturn(identitySession);
    when(exchange.authorize(RequestingPartyTokenRequest.allPermissions("identity-token", "polity")))
        .thenReturn(new RequestingPartyToken("product-token"));
    when(tokens.decodeProduct("product-token")).thenReturn(productCredential);

    assertThatThrownBy(() -> resolver.resolve(request))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  private Jwt jwt(String identityUserId) {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getClaimAsString(CardoJwtClaims.IDENTITY_USER_ID)).thenReturn(identityUserId);
    return jwt;
  }
}
