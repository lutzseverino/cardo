package com.odonta.common.model;

/** A field in a partial update: either absent, explicitly cleared, or assigned a value. */
public record FieldUpdate<T>(boolean present, T value) {

  public FieldUpdate {
    if (!present && value != null) {
      throw new IllegalArgumentException("An absent field update cannot carry a value.");
    }
  }

  public static <T> FieldUpdate<T> absent() {
    return new FieldUpdate<>(false, null);
  }

  public static <T> FieldUpdate<T> present(T value) {
    return new FieldUpdate<>(true, value);
  }
}
