package io.github.lutzseverino.cardo.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
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

  public static ApiException from(
      ClientHttpResponse response, ObjectMapper json, String fallbackCode, String fallbackMessage) {
    try {
      String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
      ApiError error = json.readValue(body, ApiError.class);
      return ApiException.of(
          response.getStatusCode().value(), error.error().code(), error.error().message());
    } catch (Exception parseException) {
      try {
        return ApiException.of(response.getStatusCode().value(), fallbackCode, fallbackMessage);
      } catch (Exception statusException) {
        return ApiException.of(500, fallbackCode, fallbackMessage);
      }
    }
  }
}
