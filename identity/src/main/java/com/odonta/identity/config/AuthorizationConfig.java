package com.odonta.identity.config;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.grant.GrantConfiguration;
import com.odonta.authorization.keycloak.KeycloakAuthorizationClient;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.authorization.keycloak.KeycloakRealmAdminClient;
import com.odonta.authorization.keycloak.KeycloakRequestingPartyTokenClient;
import com.odonta.authorization.token.RequestingPartyTokenClient;
import com.odonta.identity.model.User;
import com.odonta.identity.repository.UserRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@Configuration
@Import(GrantConfiguration.class)
@EntityScan(basePackageClasses = User.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
public class AuthorizationConfig {

  @Bean
  KeycloakClientCredentialsTokenProvider keycloakClientCredentialsTokenProvider(
      KeycloakProperties keycloak, RestClient.Builder rest) {
    return new KeycloakClientCredentialsTokenProvider(
        keycloak.baseUrl(), keycloak.realm(), keycloak.clientId(), keycloak.clientSecret(), rest);
  }

  @Bean
  KeycloakRealmAdminClient keycloakRealmAdminClient(
      KeycloakProperties keycloak,
      RestClient.Builder rest,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    return new KeycloakRealmAdminClient(
        keycloak.baseUrl(),
        keycloak.realm(),
        rest,
        clientCredentialsTokens::clientCredentialsToken);
  }

  @Bean
  AuthorizationAdminClient keycloakAuthorizationClient(
      KeycloakProperties keycloak,
      RestClient.Builder rest,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    return new KeycloakAuthorizationClient(
        keycloak.baseUrl(),
        keycloak.realm(),
        rest,
        clientCredentialsTokens::clientCredentialsToken);
  }

  @Bean
  RequestingPartyTokenClient requestingPartyTokenClient(
      KeycloakProperties keycloak, RestClient.Builder rest) {
    return new KeycloakRequestingPartyTokenClient(keycloak.baseUrl(), keycloak.realm(), rest);
  }
}
