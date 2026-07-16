package io.github.lutzseverino.cardo.common.web;

import io.github.lutzseverino.cardo.common.api.ApiError;
import io.github.lutzseverino.cardo.common.api.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

public abstract class ApiExceptionHandler {

  private MessageSource messages;

  @Autowired(required = false)
  void setMessages(MessageSource messages) {
    this.messages = messages;
  }

  @ExceptionHandler(ApiException.class)
  protected ResponseEntity<ApiError> handle(ApiException exception) {
    return ResponseEntity.status(exception.status())
        .body(
            ApiError.of(
                exception.code(),
                message(exception.code(), exception.getMessage()),
                exception.details()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ApiError> handleValidationException() {
    return invalidRequest();
  }

  @ExceptionHandler(ConstraintViolationException.class)
  protected ResponseEntity<ApiError> handleConstraintViolation() {
    return invalidRequest();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  protected ResponseEntity<ApiError> handleUnreadableMessage() {
    return invalidRequest();
  }

  private ResponseEntity<ApiError> invalidRequest() {
    return ResponseEntity.badRequest()
        .body(ApiError.of("invalid_request", message("invalid_request", "Invalid request.")));
  }

  private String message(String code, String fallback) {
    if (messages == null) {
      return fallback;
    }
    return messages.getMessage(
        "api_error." + code, null, fallback, LocaleContextHolder.getLocale());
  }
}
