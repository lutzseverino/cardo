package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BrowserSessionPropertiesTest {

  @Test
  void requiresIdleTimeoutToBeShorterThanAbsoluteTimeout() {
    assertThatThrownBy(
            () ->
                new BrowserSessionProperties(
                    Duration.ofHours(8),
                    Duration.ofHours(8),
                    Duration.ofSeconds(30),
                    Duration.ofDays(7),
                    Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idle-timeout must be shorter");
  }

  @Test
  void rejectsNonPositiveLifecycleDurations() {
    assertThatThrownBy(
            () ->
                new BrowserSessionProperties(
                    Duration.ofMinutes(30),
                    Duration.ofHours(8),
                    Duration.ZERO,
                    Duration.ofDays(7),
                    Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("refresh-lease must be positive");
  }
}
