package io.github.lutzseverino.cardo.openapi.patch;

import io.github.lutzseverino.cardo.common.model.FieldUpdate;
import java.util.function.Function;
import org.openapitools.jackson.nullable.JsonNullable;

/** Converts OpenAPI nullable fields into application-owned partial-update values. */
public final class PatchFields {

  private PatchFields() {}

  public static <T> FieldUpdate<T> update(JsonNullable<T> value) {
    return update(value, Function.identity());
  }

  public static <S, T> FieldUpdate<T> update(
      JsonNullable<S> value, Function<? super S, ? extends T> conversion) {
    if (value == null || !value.isPresent()) {
      return FieldUpdate.absent();
    }
    S presentValue = value.orElse(null);
    return FieldUpdate.present(presentValue == null ? null : conversion.apply(presentValue));
  }
}
