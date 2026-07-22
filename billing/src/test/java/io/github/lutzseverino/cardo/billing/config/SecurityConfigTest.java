package io.github.lutzseverino.cardo.billing.config;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
  void decoderRequiresIssuerExpirationAndExactBillingAudience() throws Exception {
    var decoder = new SecurityConfig().jwtDecoder(issuer, boundedRestOperations());

    assertThat(decoder.decode(token(issuer, List.of("billing"), true)).getAudience())
        .containsExactly("billing");
    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("identity"), true)))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("billing", "identity"), true)))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("billing"), false)))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(
            () -> decoder.decode(token("https://wrong-issuer.example", List.of("billing"), true)))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void issuerDiscoveryFailureFailsClosedOnFirstDecodeWithoutPreventingStartup() throws Exception {
    var decoder = new SecurityConfig().jwtDecoder(issuer, boundedRestOperations());

    assertThat(decoder).isNotNull();
    server.stop(0);

    assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("billing"), true)))
        .isInstanceOf(JwtDecoderInitializationException.class);
  }

  @Test
  void lazyDecoderBoundsAStalledJwkResponse() throws Exception {
    String jwkPath = "/realms/cardo/protocol/openid-connect/certs";
    CountDownLatch requestEntered = new CountDownLatch(1);
    CountDownLatch releaseResponse = new CountDownLatch(1);
    AtomicInteger requests = new AtomicInteger();
    server.removeContext(jwkPath);
    server.createContext(
        jwkPath,
        exchange -> {
          requests.incrementAndGet();
          requestEntered.countDown();
          try {
            releaseResponse.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    var rest =
        new BillingRuntimeConfiguration()
            .billingJwkRestOperations(
                new BillingRuntimeProperties(
                    BillingRuntimeProperties.Mode.LOCAL,
                    Duration.ofMillis(100),
                    Duration.ofMillis(100)));
    var decoder = new SecurityConfig().jwtDecoder(issuer, rest);

    assertThat(decoder).isNotNull();
    assertThat(requests).hasValue(0);
    try {
      org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () ->
              assertThatThrownBy(() -> decoder.decode(token(issuer, List.of("billing"), true)))
                  .isInstanceOf(JwtDecoderInitializationException.class));
      assertThat(requestEntered.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(requests).hasValue(1);
    } finally {
      releaseResponse.countDown();
    }
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
