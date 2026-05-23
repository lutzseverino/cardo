package com.odonta.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

public final class ApiClientErrors {

  private ApiClientErrors() {}

  public static ApiException from(
      RestClientResponseException exception,
      ObjectMapper json,
      String fallbackCode,
      String fallbackMessage) {
    try {
      ApiError error = json.readValue(exception.getResponseBodyAsString(), ApiError.class);
      return ApiException.of(
          exception.getStatusCode().value(), error.error().code(), error.error().message());
    } catch (Exception parseException) {
      return ApiException.of(exception.getStatusCode().value(), fallbackCode, fallbackMessage);
    }
  }
}
