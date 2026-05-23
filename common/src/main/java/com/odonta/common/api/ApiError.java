package com.odonta.common.api;

import java.util.Map;

public record ApiError(ErrorBody error) {

  public static ApiError of(String code, String message) {
    return new ApiError(new ErrorBody(code, message, Map.of()));
  }

  public record ErrorBody(String code, String message, Map<String, Object> details) {}
}
