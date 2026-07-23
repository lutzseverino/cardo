package io.github.lutzseverino.cardo.billing.client.http;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementsClient;
import io.github.lutzseverino.cardo.billing.client.http.generated.ApiClient;
import io.github.lutzseverino.cardo.billing.client.http.generated.api.EntitlementsApi;
import io.github.lutzseverino.cardo.common.api.ApiClientErrors;
import io.github.lutzseverino.cardo.common.api.ApiException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@ConditionalOnMissingBean(BillingEntitlementsClient.class)
@EnableConfigurationProperties(BillingClientProperties.class)
public class BillingClientAutoConfiguration {

  @Bean
  BillingEntitlementsClient billingEntitlementsClient(
      BillingClientProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      JsonMapper json) {
    ApiClient apiClient =
        new ApiClient(
                ApiClient.buildRestClientBuilder(json)
                    .requestFactory(requestFactory(properties))
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
    apiClient.setBearerToken(
        () -> serviceToken(clientCredentialsTokens, properties.serviceTokenScope()));
    return new HttpBillingEntitlementsClient(new EntitlementsApi(apiClient));
  }

  private SimpleClientHttpRequestFactory requestFactory(BillingClientProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    return factory;
  }

  private String serviceToken(
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens, String scope) {
    String token = clientCredentialsTokens.clientCredentialsToken(scope);
    if (token == null || token.isBlank()) {
      throw ApiException.of(
          500, "billing_service_token_missing", "Billing service token provider is missing.");
    }
    return token;
  }
}
