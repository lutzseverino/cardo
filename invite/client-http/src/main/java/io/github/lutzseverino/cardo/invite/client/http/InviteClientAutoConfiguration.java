package io.github.lutzseverino.cardo.invite.client.http;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.common.api.ApiClientErrors;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.client.InvitationsClient;
import io.github.lutzseverino.cardo.invite.client.http.generated.ApiClient;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationTokensApi;
import io.github.lutzseverino.cardo.invite.client.http.generated.api.InvitationsApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@EnableConfigurationProperties(InviteClientProperties.class)
public class InviteClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  InvitationsClient invitationsClient(
      InviteClientProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      JsonMapper json) {
    ApiClient apiClient =
        new ApiClient(
                ApiClient.buildRestClientBuilder(json)
                    .defaultStatusHandler(
                        HttpStatusCode::isError,
                        (request, response) -> {
                          throw ApiClientErrors.from(
                              response, json, "invite_client_error", "Invite request failed.");
                        })
                    .build(),
                json,
                ApiClient.createDefaultDateFormat())
            .setBasePath(properties.baseUrl());
    apiClient.setBearerToken(() -> serviceToken(clientCredentialsTokens));
    return new HttpInvitationsClient(
        new InvitationsApi(apiClient), new InvitationTokensApi(apiClient));
  }

  private String serviceToken(KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    String token = clientCredentialsTokens.clientCredentialsToken();
    if (token == null || token.isBlank()) {
      throw ApiException.of(
          500, "invite_service_token_missing", "Invite service token provider is missing.");
    }
    return token;
  }
}
