package io.github.lutzseverino.cardo.identity.client.http;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdentityClientPropertiesTest {

  @ParameterizedTest
  @MethodSource("invalidTimeouts")
  void rejectsTimeoutsThatCannotBoundTheHttpClient(Duration timeout) {
    assertThatThrownBy(
            () ->
                new IdentityClientProperties(
                    "https://identity.example", "identity", timeout, timeout))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1ms");
  }

  static java.util.stream.Stream<Duration> invalidTimeouts() {
    return java.util.stream.Stream.of(
        Duration.ofNanos(1),
        Duration.ofMillis(Integer.MAX_VALUE).plusMillis(1),
        Duration.ofSeconds(Long.MAX_VALUE));
  }
}
