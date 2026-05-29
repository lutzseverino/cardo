package com.odonta.identity.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.common.api.ApiClientErrors;
import com.odonta.common.api.ApiException;
import com.odonta.identity.client.api.SessionsApi;
import com.odonta.identity.client.api.UsersApi;
import java.net.HttpCookie;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

public class IdentityHttpClient {

  private final ObjectMapper json;
  private final SessionsApi sessions;
  private final String sessionCookieName;
  private final Supplier<String> serviceTokens;
  private final UsersApi users;

  public IdentityHttpClient(
      IdentityClientProperties properties, String sessionCookieName, ObjectMapper json) {
    this.json = json;
    this.sessionCookieName = sessionCookieName;
    this.serviceTokens = null;
    ApiClient apiClient = apiClient(properties, json);
    this.users = new UsersApi(apiClient);
    this.sessions = new SessionsApi(apiClient);
  }

  public IdentityHttpClient(
      IdentityClientProperties properties, Supplier<String> serviceTokens, ObjectMapper json) {
    this.json = json;
    this.sessionCookieName = null;
    this.serviceTokens = serviceTokens;
    ApiClient apiClient = apiClient(properties, json);
    apiClient.setBearerToken(this::serviceToken);
    this.users = new UsersApi(apiClient);
    this.sessions = new SessionsApi(apiClient);
  }

  public CreatedIdentity createPasswordIdentity(String email, String password, String name) {
    IdentityUser user =
        call(
            () ->
                toIdentityUser(
                    users.createUser(
                        new CreateUserRequest().email(email).password(password).name(name))));
    IdentitySession session =
        call(
            () -> {
              ResponseEntity<AuthenticatedPrincipalResponse> response =
                  sessions.createSessionWithHttpInfo(
                      new CreateSessionRequest().email(email).password(password));
              return new IdentitySession(sessionToken(response.getHeaders()));
            });
    return new CreatedIdentity(user, session);
  }

  public IdentityUser createProvisionalUser(String email) {
    return call(
        () ->
            toIdentityUser(
                users.createProvisionalUser(new CreateProvisionalUserRequest().email(email))));
  }

  public IdentityUser completeProvisionalUser(UUID userId, String name, String password) {
    return call(
        () ->
            toIdentityUser(
                users.completeProvisionalUser(
                    userId, new CompleteProvisionalUserRequest().name(name).password(password))));
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

  private ApiClient apiClient(IdentityClientProperties properties, ObjectMapper json) {
    return new ApiClient(
            ApiClient.buildRestClientBuilder(json)
                .defaultStatusHandler(
                    HttpStatusCode::isError,
                    (request, response) -> {
                      throw ApiClientErrors.from(
                          response, json, "identity_client_error", "Identity request failed.");
                    })
                .build(),
            json,
            ApiClient.createDefaultDateFormat())
        .setBasePath(properties.baseUrl());
  }

  private IdentityUser toIdentityUser(UserResponse user) {
    return new IdentityUser(
        user.getId(), user.getAuthorizationSubject(), user.getEmail(), user.getName());
  }

  private interface IdentityCall<T> {
    T run();
  }
}
