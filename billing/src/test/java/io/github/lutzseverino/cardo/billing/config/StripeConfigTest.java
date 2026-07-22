package io.github.lutzseverino.cardo.billing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.exception.ApiConnectionException;
import com.stripe.net.RequestOptions;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StripeConfigTest {

  @Test
  void configuredClientBoundsAStalledResponseWithoutRetrying() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    CountDownLatch requestEntered = new CountDownLatch(1);
    CountDownLatch releaseResponse = new CountDownLatch(1);
    AtomicInteger requests = new AtomicInteger();
    server.createContext(
        "/v1/customers",
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
    server.start();
    try {
      var client =
          new StripeConfig()
              .stripeClient(
                  new StripeProperties(
                      "sk_test_bounded",
                      "whsec_test",
                      List.of(),
                      Duration.ofMillis(100),
                      Duration.ofMillis(100)));
      RequestOptions localEndpoint =
          RequestOptions.builder()
              .setBaseUrl("http://localhost:" + server.getAddress().getPort())
              .build();

      org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () ->
              assertThatThrownBy(() -> client.customers().list(localEndpoint))
                  .isInstanceOf(ApiConnectionException.class));
      assertThat(requestEntered.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(requests).hasValue(1);
    } finally {
      releaseResponse.countDown();
      server.stop(0);
    }
  }

  @Test
  void rejectsSubMillisecondAndOverflowingStripeTimeouts() {
    assertThatThrownBy(
            () ->
                new StripeProperties(
                    "sk_test", "whsec_test", List.of(), Duration.ofNanos(1), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1ms");
    assertThatThrownBy(
            () ->
                new StripeProperties(
                    "sk_test",
                    "whsec_test",
                    List.of(),
                    Duration.ofSeconds(Long.MAX_VALUE),
                    Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1ms");
  }
}
