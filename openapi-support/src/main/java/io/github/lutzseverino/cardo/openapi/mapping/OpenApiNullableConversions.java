package io.github.lutzseverino.cardo.openapi.mapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.mapstruct.Named;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;

/** Common MapStruct conversions for OpenAPI-generated nullable fields. */
@Component
public class OpenApiNullableConversions {

  @Named("fromNullableString")
  public String fromNullableString(JsonNullable<String> value) {
    return value == null ? null : value.orElse(null);
  }

  @Named("fromNullableBigDecimal")
  public BigDecimal fromNullableBigDecimal(JsonNullable<BigDecimal> value) {
    return value == null ? null : value.orElse(null);
  }

  public JsonNullable<String> toNullableString(String value) {
    return JsonNullable.of(value);
  }

  public JsonNullable<UUID> toNullableUuid(UUID value) {
    return JsonNullable.of(value);
  }

  public JsonNullable<LocalDate> toNullableLocalDate(LocalDate value) {
    return JsonNullable.of(value);
  }

  public JsonNullable<OffsetDateTime> toNullableOffsetDateTime(OffsetDateTime value) {
    return JsonNullable.of(value);
  }

  public JsonNullable<BigDecimal> toNullableBigDecimal(BigDecimal value) {
    return JsonNullable.of(value);
  }

  public JsonNullable<Double> toNullableDouble(Double value) {
    return JsonNullable.of(value);
  }
}
