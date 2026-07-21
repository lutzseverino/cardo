package io.github.lutzseverino.cardo.authorization.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class KeycloakClientCredentialsTokenProviderTest {

  private final AtomicInteger requests = new AtomicInteger();
  private final AtomicReference<String> response =
      new AtomicReference<>("{\"access_token\":\"service-token-1\",\"expires_in\":60}");
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/realms/cardo/protocol/openid-connect/token",
        exchange -> {
          requests.incrementAndGet();
          byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(HttpStatus.OK.value(), body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void reusesTheTokenUntilItsRefreshSkew() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-21T10:00:00Z"));
    KeycloakClientCredentialsTokenProvider provider = provider(clock, Duration.ofSeconds(10));

    assertThat(provider.clientCredentialsToken()).isEqualTo("service-token-1");
    assertThat(provider.clientCredentialsToken()).isEqualTo("service-token-1");
    assertThat(requests).hasValue(1);

    response.set("{\"access_token\":\"service-token-2\",\"expires_in\":60}");
    clock.advance(Duration.ofSeconds(50));

    assertThat(provider.clientCredentialsToken()).isEqualTo("service-token-2");
    assertThat(requests).hasValue(2);
  }

  @Test
  void rejectsATokenWithoutAUsableExpiry() {
    response.set("{\"access_token\":\"service-token\"}");

    assertThatThrownBy(
            () -> provider(new MutableClock(Instant.EPOCH), Duration.ZERO).clientCredentialsToken())
        .isInstanceOf(KeycloakAuthorizationException.class)
        .hasMessage("Keycloak did not return a valid client credentials token expiry.");
  }

  @Test
  void collapsesConcurrentRefreshes() throws Exception {
    KeycloakClientCredentialsTokenProvider provider =
        provider(new MutableClock(Instant.EPOCH), Duration.ZERO);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      List<Future<String>> tokens =
          java.util.stream.IntStream.range(0, 8)
              .mapToObj(
                  ignored ->
                      executor.submit(
                          () -> {
                            start.await();
                            return provider.clientCredentialsToken();
                          }))
              .toList();
      start.countDown();

      for (Future<String> token : tokens) {
        assertThat(token.get()).isEqualTo("service-token-1");
      }
    }
    assertThat(requests).hasValue(1);
  }

  @Test
  void validatesRuntimeBounds() {
    assertThatThrownBy(
            () ->
                new KeycloakClientCredentialsTokenSettings(
                    Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("connectTimeout must be positive.");
    assertThatThrownBy(
            () ->
                new KeycloakClientCredentialsTokenSettings(
                    Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("refreshSkew must not be negative.");
  }

  private KeycloakClientCredentialsTokenProvider provider(Clock clock, Duration refreshSkew) {
    return new KeycloakClientCredentialsTokenProvider(
        "http://localhost:" + server.getAddress().getPort(),
        "cardo",
        "service",
        "secret",
        RestClient.builder(),
        new KeycloakClientCredentialsTokenSettings(
            Duration.ofSeconds(1), Duration.ofSeconds(1), refreshSkew),
        clock);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
