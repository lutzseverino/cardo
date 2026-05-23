package com.odonta.identity.exception;

import com.odonta.common.api.ApiError;
import com.odonta.common.web.ApiExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler extends ApiExceptionHandler {

  @ExceptionHandler(MissingRequestCookieException.class)
  ResponseEntity<ApiError> handleMissingCookie() {
    return ResponseEntity.status(404)
        .body(
            ApiError.of("authenticated_principal_not_found", "Authenticated principal not found."));
  }
}
