package com.odonta.identity.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.common.api.ApiClientErrors;
import com.odonta.common.api.ApiException;
import java.net.HttpCookie;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class IdentityHttpClient {

  private final ObjectMapper json;
  private final RestClient rest;
  private final String sessionCookieName;
  private final Supplier<String> serviceTokens;

  public IdentityHttpClient(
      IdentityClientProperties properties,
      String sessionCookieName,
      ObjectMapper json,
      RestClient.Builder rest) {
    this.json = json;
    this.rest = rest.baseUrl(properties.baseUrl()).build();
    this.sessionCookieName = sessionCookieName;
    this.serviceTokens = null;
  }

  public IdentityHttpClient(
      IdentityClientProperties properties,
      Supplier<String> serviceTokens,
      ObjectMapper json,
      RestClient.Builder rest) {
    this.json = json;
    this.rest = rest.baseUrl(properties.baseUrl()).build();
    this.sessionCookieName = null;
    this.serviceTokens = serviceTokens;
  }

  public CreatedIdentity createPasswordIdentity(String email, String password, String name) {
    IdentityUser user =
        call(() ->
                rest.post()
                    .uri("/identity/users")
                    .body(new CreateUser(email, password, name))
                    .retrieve()
                    .body(IdentityUserResponse.class))
            .toIdentityUser();
    IdentitySession session =
        call(
            () -> {
              ResponseEntity<IdentitySessionResponse> response =
                  rest.post()
                      .uri("/identity/sessions")
                      .body(new CreateSession(email, password))
                      .retrieve()
                      .toEntity(IdentitySessionResponse.class);
              return new IdentitySession(sessionToken(response.getHeaders()));
            });
    return new CreatedIdentity(user, session);
  }

  public IdentityUser createProvisionalUser(String email) {
    return call(() ->
            rest.post()
                .uri("/identity/users/provisional")
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .body(new CreateProvisionalUser(email))
                .retrieve()
                .body(IdentityUserResponse.class))
        .toIdentityUser();
  }

  public IdentityUser completeProvisionalUser(UUID userId, String name, String password) {
    return call(() ->
            rest.post()
                .uri("/identity/users/{id}/completion", userId)
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .body(new CompleteProvisionalUser(name, password))
                .retrieve()
                .body(IdentityUserResponse.class))
        .toIdentityUser();
  }

  private <T> T call(IdentityCall<T> call) {
    try {
      return call.run();
    } catch (RestClientResponseException exception) {
      throw apiException(exception);
    }
  }

  private String sessionToken(HttpHeaders headers) {
    if (sessionCookieName == null) {
      throw ApiException.of(
          500, "identity_session_cookie_name_missing", "Identity session cookie name is missing.");
    }
    return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
        .flatMap(header -> HttpCookie.parse(header).stream())
        .filter(cookie -> sessionCookieName.equals(cookie.getName()))
        .map(HttpCookie::getValue)
        .findFirst()
        .orElseThrow(
            () ->
                ApiException.of(
                    502,
                    "identity_session_cookie_missing",
                    "Identity did not return a session cookie."));
  }

  private String serviceToken() {
    if (serviceTokens == null) {
      throw ApiException.of(
          500, "identity_service_token_missing", "Identity service token provider is missing.");
    }
    return serviceTokens.get();
  }

  private ApiException apiException(RestClientResponseException exception) {
    return ApiClientErrors.from(
        exception, json, "identity_client_error", "Identity request failed.");
  }

  private interface IdentityCall<T> {
    T run();
  }

  private record CreateUser(String email, String password, String name) {}

  private record CreateSession(String email, String password) {}

  private record CreateProvisionalUser(String email) {}

  private record CompleteProvisionalUser(String name, String password) {}

  private record IdentityUserResponse(
      UUID id, String authorizationSubject, String email, String name) {

    IdentityUser toIdentityUser() {
      return new IdentityUser(id, authorizationSubject, email, name);
    }
  }

  private record IdentitySessionResponse() {}
}
