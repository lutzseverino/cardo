package io.github.lutzseverino.cardo.common.api;

import java.util.Map;

public record ApiError(ErrorBody error) {

  public static ApiError of(String code, String message) {
    return of(code, message, Map.of());
  }

  public static ApiError of(String code, String message, Map<String, Object> details) {
    return new ApiError(
        new ErrorBody(code, message, details == null ? Map.of() : Map.copyOf(details)));
  }

  public record ErrorBody(String code, String message, Map<String, Object> details) {}
}
