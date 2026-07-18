package io.github.lutzseverino.cardo.identity.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthoritiesConverter;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class KeycloakAuthorizationTokenReaderTest {

  @Test
  void reportsInvalidProviderAuthorizationTokenAsBadGateway() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("identity-rpt")).thenThrow(new JwtException("invalid token"));
    var reader = reader(decoder);

    assertThatThrownBy(() -> reader.read("identity-rpt"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(502);
              assertThat(exception.code()).isEqualTo("identity_authorization_token_invalid");
            });
  }

  @Test
  void rejectsAuthorizationTokenWithoutASubject() {
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("identity-rpt"))
        .thenReturn(
            Jwt.withTokenValue("identity-rpt")
                .header("alg", "none")
                .claim("aud", "identity")
                .expiresAt(Instant.parse("2030-01-01T00:05:00Z"))
                .build());
    var reader = reader(decoder);

    assertThatThrownBy(() -> reader.read("identity-rpt"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(502);
              assertThat(exception.code()).isEqualTo("identity_authorization_subject_missing");
            });
  }

  private KeycloakAuthorizationTokenReader reader(JwtDecoder decoder) {
    return new KeycloakAuthorizationTokenReader(
        decoder,
        mock(KeycloakAuthoritiesConverter.class),
        mock(EffectiveGrantAuthorityReader.class));
  }
}
