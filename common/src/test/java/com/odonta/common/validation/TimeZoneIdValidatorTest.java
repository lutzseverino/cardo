package com.odonta.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimeZoneIdValidatorTest {

  private final TimeZoneIdValidator validator = new TimeZoneIdValidator();

  @Test
  void acceptsNullAndValidTimeZoneIds() {
    assertThat(validator.isValid(null, null)).isTrue();
    assertThat(validator.isValid("Europe/Madrid", null)).isTrue();
  }

  @Test
  void rejectsInvalidTimeZoneIds() {
    assertThat(validator.isValid("Mars/Base", null)).isFalse();
  }
}
