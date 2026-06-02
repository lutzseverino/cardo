package com.odonta.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import org.junit.jupiter.api.Test;

class TimeZoneIdsTest {

  @Test
  void normalizesValidTimeZoneIds() {
    assertThat(TimeZoneIds.normalize("Europe/Madrid")).isEqualTo("Europe/Madrid");
  }

  @Test
  void rejectsInvalidTimeZoneIds() {
    assertThat(TimeZoneIds.valid("Mars/Base")).isFalse();
    assertThatThrownBy(() -> TimeZoneIds.normalize("Mars/Base"))
        .isInstanceOf(DateTimeException.class);
  }
}
