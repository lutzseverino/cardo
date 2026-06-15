package com.odonta.openapi.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

class OpenApiNullableConversionsTest {

  private final OpenApiNullableConversions nullable = new OpenApiNullableConversions();
  private final UriResponseConversions uris = new UriResponseConversions();

  @Test
  void unwrapsNullableValues() {
    assertThat(nullable.fromNullableString(JsonNullable.of("value"))).isEqualTo("value");
    assertThat(nullable.fromNullableString(JsonNullable.of(null))).isNull();
    assertThat(nullable.fromNullableBigDecimal(JsonNullable.of(BigDecimal.TEN)))
        .isEqualByComparingTo(BigDecimal.TEN);
  }

  @Test
  void wrapsCommonJavaValues() {
    UUID uuid = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 6, 13);
    OffsetDateTime dateTime = OffsetDateTime.parse("2026-06-13T18:00:00+02:00");

    assertThat(nullable.toNullableString("value").get()).isEqualTo("value");
    assertThat(nullable.toNullableUuid(uuid).get()).isEqualTo(uuid);
    assertThat(nullable.toNullableLocalDate(date).get()).isEqualTo(date);
    assertThat(nullable.toNullableOffsetDateTime(dateTime).get()).isEqualTo(dateTime);
    assertThat(nullable.toNullableBigDecimal(BigDecimal.ONE).get()).isEqualTo(BigDecimal.ONE);
    assertThat(nullable.toNullableDouble(1.5).get()).isEqualTo(1.5);
  }

  @Test
  void convertsUriStrings() {
    assertThat(uris.toNullableUri("https://odonta.example/logo").get())
        .isEqualTo(URI.create("https://odonta.example/logo"));
    assertThat(uris.toNullableUri(null).orElse(null)).isNull();
  }
}
