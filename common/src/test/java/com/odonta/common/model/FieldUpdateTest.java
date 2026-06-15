package com.odonta.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FieldUpdateTest {

  @Test
  void rejectsValuesOnAbsentUpdates() {
    assertThatThrownBy(() -> new FieldUpdate<>(false, "value"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("An absent field update cannot carry a value.");
  }

  @Test
  void exposesTheThreeSupportedStates() {
    assertThat(FieldUpdate.absent()).isEqualTo(new FieldUpdate<>(false, null));
    assertThat(FieldUpdate.present(null)).isEqualTo(new FieldUpdate<>(true, null));
    assertThat(FieldUpdate.present("value")).isEqualTo(new FieldUpdate<>(true, "value"));
  }
}
