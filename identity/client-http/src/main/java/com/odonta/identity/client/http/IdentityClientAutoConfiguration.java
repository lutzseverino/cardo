package com.odonta.identity.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.common.api.ApiClientErrors;
import com.odonta.common.api.ApiException;
import com.odonta.identity.client.IdentityUsersClient;
import com.odonta.identity.client.http.generated.ApiClient;
import com.odonta.identity.client.http.generated.api.UsersApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;

@AutoConfiguration
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  IdentityUsersClient identityUsersClient(
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
    return new HttpIdentityUsersClient(new UsersApi(apiClient));
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
