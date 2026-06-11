package com.odonta.billing.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.billing.client.BillingEntitlementsClient;
import com.odonta.billing.client.http.generated.ApiClient;
import com.odonta.billing.client.http.generated.api.EntitlementsApi;
import com.odonta.common.api.ApiClientErrors;
import com.odonta.common.api.ApiException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;

@AutoConfiguration
@EnableConfigurationProperties(BillingClientProperties.class)
public class BillingClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  BillingEntitlementsClient billingEntitlementsClient(
      BillingClientProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      ObjectMapper json) {
    ApiClient apiClient =
        new ApiClient(
                ApiClient.buildRestClientBuilder(json)
                    .defaultStatusHandler(
                        HttpStatusCode::isError,
                        (request, response) -> {
                          throw ApiClientErrors.from(
                              response, json, "billing_client_error", "Billing request failed.");
                        })
                    .build(),
                json,
                ApiClient.createDefaultDateFormat())
            .setBasePath(properties.baseUrl());
    apiClient.setBearerToken(() -> serviceToken(clientCredentialsTokens));
    return new HttpBillingEntitlementsClient(new EntitlementsApi(apiClient));
  }

  private String serviceToken(KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    String token = clientCredentialsTokens.clientCredentialsToken();
    if (token == null || token.isBlank()) {
      throw ApiException.of(
          500, "billing_service_token_missing", "Billing service token provider is missing.");
    }
    return token;
  }
}
