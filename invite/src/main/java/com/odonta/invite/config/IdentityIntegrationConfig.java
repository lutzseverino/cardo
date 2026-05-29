package com.odonta.invite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.common.api.ApiClientErrors;
import com.odonta.common.api.ApiException;
import com.odonta.identity.client.ApiClient;
import com.odonta.identity.client.IdentityClientProperties;
import com.odonta.identity.client.api.UsersApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;

@Configuration
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityIntegrationConfig {

  @Bean
  ApiClient identityApiClient(
      IdentityClientProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      ObjectMapper json) {
    ApiClient apiClient =
        new ApiClient(
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
    apiClient.setBearerToken(() -> serviceToken(clientCredentialsTokens));
    return apiClient;
  }

  @Bean
  UsersApi identityUsersApi(ApiClient identityApiClient) {
    return new UsersApi(identityApiClient);
  }

  private String serviceToken(KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    String token = clientCredentialsTokens.clientCredentialsToken();
    if (token == null || token.isBlank()) {
      throw ApiException.of(
          500, "identity_service_token_missing", "Identity service token provider is missing.");
    }
    return token;
  }
}
