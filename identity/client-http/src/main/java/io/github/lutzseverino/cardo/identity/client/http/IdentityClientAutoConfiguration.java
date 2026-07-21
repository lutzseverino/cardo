package io.github.lutzseverino.cardo.identity.client.http;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.common.api.ApiClientErrors;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.identity.client.http.generated.ApiClient;
import io.github.lutzseverino.cardo.identity.client.http.generated.api.UsersApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  IdentityUsersClient identityUsersClient(
      IdentityClientProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      JsonMapper json) {
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
