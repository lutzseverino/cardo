package io.github.lutzseverino.cardo.identity.exception;

import io.github.lutzseverino.cardo.common.api.ApiError;
import io.github.lutzseverino.cardo.common.web.ApiExceptionHandler;
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
