package io.github.lutzseverino.cardo.invite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.client.RestTemplate;

class SecurityConfigTest {

  private HttpServer server;
  private KeyPair keys;
  private String issuer;

  @BeforeEach
  void startIssuer() throws Exception {
    keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    issuer = "http://localhost:" + server.getAddress().getPort() + "/realms/cardo";
    server.createContext(
        "/realms/cardo/.well-known/openid-configuration",
        exchange ->
            respond(
                exchange,
                "{\"issuer\":\""
                    + issuer
                    + "\",\"jwks_uri\":\""
                    + issuer
                    + "/protocol/openid-connect/certs\"}"));
    RSAKey publicKey =
        new RSAKey.Builder((RSAPublicKey) keys.getPublic()).keyID("test-key").build();
    server.createContext(
        "/realms/cardo/protocol/openid-connect/certs",
        exchange -> respond(exchange, new JWKSet(publicKey).toString()));
    server.start();
  }

  @AfterEach
  void stopIssuer() {
    server.stop(0);
  }

  @Test
  void decoderRequiresIssuerExpirationAndTheConfiguredExactAudience() throws Exception {
    var decoder =
        new SecurityConfig()
            .jwtDecoder(
                issuer,
                new KeycloakProperties(
                    "https://identity.example.com", "cardo", "custom-invite", "secret"),
                boundedRestOperations());

    assertThat(decoder.decode(token(issuer, List.of("custom-invite"), true)).getAudience())
        .containsExactly("custom-invite");
    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("billing"), true)))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(
            () -> decoder.decode(token(issuer, List.of("custom-invite", "billing"), true)))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("custom-invite"), false)))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(
            () ->
                decoder.decode(
                    token("https://wrong-issuer.example", List.of("custom-invite"), true)))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void issuerDiscoveryFailureFailsClosedOnFirstDecodeWithoutPreventingStartup() throws Exception {
    var decoder =
        new SecurityConfig()
            .jwtDecoder(
                issuer,
                new KeycloakProperties(
                    "https://identity.example.com", "cardo", "cardo-invite", "secret"),
                boundedRestOperations());

    assertThat(decoder).isNotNull();
    server.stop(0);

    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("cardo-invite"), true)))
        .isInstanceOf(JwtDecoderInitializationException.class);
  }

  private String token(String tokenIssuer, List<String> audiences, boolean expiring)
      throws Exception {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .subject("caller")
            .issuer(tokenIssuer)
            .audience(audiences)
            .issueTime(Date.from(Instant.now().minusSeconds(5)));
    if (expiring) {
      claims.expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
    token.sign(new RSASSASigner(keys.getPrivate()));
    return token.serialize();
  }

  private RestTemplate boundedRestOperations() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(java.time.Duration.ofSeconds(1));
    factory.setReadTimeout(java.time.Duration.ofSeconds(1));
    return new RestTemplate(factory);
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
      throws IOException {
    byte[] content = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(HttpStatus.OK.value(), content.length);
    exchange.getResponseBody().write(content);
    exchange.close();
  }
}
