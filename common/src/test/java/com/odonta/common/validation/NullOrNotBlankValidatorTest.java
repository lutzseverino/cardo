package com.odonta.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NullOrNotBlankValidatorTest {

  private final NullOrNotBlankValidator validator = new NullOrNotBlankValidator();

  @Test
  void acceptsNullAndText() {
    assertThat(validator.isValid(null, null)).isTrue();
    assertThat(validator.isValid("Clinic", null)).isTrue();
  }

  @Test
  void rejectsEmptyAndWhitespaceOnlyText() {
    assertThat(validator.isValid("", null)).isFalse();
    assertThat(validator.isValid("   ", null)).isFalse();
  }
}
