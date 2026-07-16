package io.github.lutzseverino.cardo.openapi.patch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

class PatchFieldsTest {

  @Test
  void preservesAbsentExplicitNullAndValue() {
    assertThat(PatchFields.update(JsonNullable.<String>undefined()).present()).isFalse();
    assertThat(PatchFields.update(JsonNullable.<String>of(null)))
        .satisfies(
            update -> {
              assertThat(update.present()).isTrue();
              assertThat(update.value()).isNull();
            });
    assertThat(PatchFields.update(JsonNullable.of("value")))
        .satisfies(
            update -> {
              assertThat(update.present()).isTrue();
              assertThat(update.value()).isEqualTo("value");
            });
  }

  @Test
  void convertsPresentValuesWithoutConvertingNull() {
    assertThat(PatchFields.update(JsonNullable.of("42"), Integer::valueOf).value()).isEqualTo(42);
    assertThat(PatchFields.update(JsonNullable.<String>of(null), value -> value.length()).value())
        .isNull();
  }
}
