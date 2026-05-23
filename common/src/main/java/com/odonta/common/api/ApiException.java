package com.odonta.common.api;

public final class ApiException extends RuntimeException {

  private final String code;
  private final int status;

  private ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException of(int status, String code, String message) {
    return new ApiException(status, code, message);
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(400, code, message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(409, code, message);
  }

  public static ApiException forbidden(String code, String message) {
    return new ApiException(403, code, message);
  }

  public static ApiException gone(String code, String message) {
    return new ApiException(410, code, message);
  }

  public static ApiException notFound(String code, String message) {
    return new ApiException(404, code, message);
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
