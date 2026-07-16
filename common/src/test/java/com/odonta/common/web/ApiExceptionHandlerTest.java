package com.odonta.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.odonta.common.api.ApiError;
import com.odonta.common.api.ApiException;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler() {};

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void localizesApiExceptionMessagesFromTheRequestLocale() {
    StaticMessageSource messages = new StaticMessageSource();
    messages.addMessage("api_error.polity_not_found", Locale.ENGLISH, "Localized polity missing.");
    handler.setMessages(messages);
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ResponseEntity<ApiError> response =
        handler.handle(ApiException.notFound("polity_not_found", "Polity not found."));

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("polity_not_found");
    assertThat(response.getBody().error().message()).isEqualTo("Localized polity missing.");
  }

  @Test
  void keepsTheExceptionMessageAsFallbackWhenNoLocalizedMessageExists() {
    StaticMessageSource messages = new StaticMessageSource();
    handler.setMessages(messages);
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ResponseEntity<ApiError> response =
        handler.handle(ApiException.notFound("polity_not_found", "Polity not found."));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().message()).isEqualTo("Polity not found.");
  }

  @Test
  void localizesInvalidRequestResponses() {
    StaticMessageSource messages = new StaticMessageSource();
    messages.addMessage("api_error.invalid_request", Locale.ENGLISH, "Localized invalid request.");
    handler.setMessages(messages);
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ResponseEntity<ApiError> response = handler.handleUnreadableMessage();

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("invalid_request");
    assertThat(response.getBody().error().message()).isEqualTo("Localized invalid request.");
  }

  @Test
  void preservesApiExceptionDetails() {
    StaticMessageSource messages = new StaticMessageSource();
    messages.addMessage("api_error.sanction_duration_too_short", Locale.ENGLISH, "Too short.");
    handler.setMessages(messages);
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ResponseEntity<ApiError> response =
        handler.handle(
            ApiException.badRequest(
                "sanction_duration_too_short",
                "Sanction duration is too short.",
                Map.of("minimumDurationDays", 3)));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().message()).isEqualTo("Too short.");
    assertThat(response.getBody().error().details()).containsEntry("minimumDurationDays", 3);
  }
}
