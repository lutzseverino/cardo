package com.odonta.common.web;

import com.odonta.common.api.ApiError;
import com.odonta.common.api.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

public abstract class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  protected ResponseEntity<ApiError> handle(ApiException exception) {
    return ResponseEntity.status(exception.status())
        .body(ApiError.of(exception.code(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ApiError> handleValidationException() {
    return invalidRequest();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  protected ResponseEntity<ApiError> handleUnreadableMessage() {
    return invalidRequest();
  }

  private ResponseEntity<ApiError> invalidRequest() {
    return ResponseEntity.badRequest().body(ApiError.of("invalid_request", "Invalid request."));
  }
}
