package com.odonta.invite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.identity.client.IdentityClientProperties;
import com.odonta.identity.client.IdentityHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityIntegrationConfig {

  @Bean
  IdentityHttpClient identityHttpClient(
      IdentityClientProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      ObjectMapper json) {
    return new IdentityHttpClient(
        properties, clientCredentialsTokens::clientCredentialsToken, json);
  }
}
