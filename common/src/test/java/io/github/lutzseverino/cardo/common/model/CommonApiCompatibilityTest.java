package io.github.lutzseverino.cardo.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommonApiCompatibilityTest {

  @Test
  void exposesFieldUpdateThroughTheCommonAggregate() {
    assertThat(FieldUpdate.present("value").value()).isEqualTo("value");
  }
}
