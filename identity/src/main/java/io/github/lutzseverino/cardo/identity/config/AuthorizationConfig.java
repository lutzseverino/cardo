package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.grant.AuthorizationPlanConfiguration;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenSettings;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRealmAdminClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenClient;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@Configuration
@Import(AuthorizationPlanConfiguration.class)
@EntityScan(basePackageClasses = User.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
public class AuthorizationConfig {

  @Bean
  KeycloakClientCredentialsTokenProvider keycloakClientCredentialsTokenProvider(
      KeycloakProperties keycloak,
      IdentityRuntimeProperties runtime,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest) {
    return new KeycloakClientCredentialsTokenProvider(
        keycloak.baseUrl(),
        keycloak.realm(),
        keycloak.clientId(),
        keycloak.clientSecret(),
        rest.clone(),
        new KeycloakClientCredentialsTokenSettings(
            runtime.connectTimeout(), runtime.readTimeout(), java.time.Duration.ofSeconds(30)));
  }

  @Bean
  KeycloakRealmAdminClient keycloakRealmAdminClient(
      KeycloakProperties keycloak,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    return new KeycloakRealmAdminClient(
        keycloak.baseUrl(),
        keycloak.realm(),
        rest.clone(),
        clientCredentialsTokens::clientCredentialsToken);
  }

  @Bean
  AuthorizationAdminClient keycloakAuthorizationClient(
      KeycloakProperties keycloak,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    return new KeycloakAuthorizationClient(
        keycloak.baseUrl(),
        keycloak.realm(),
        rest.clone(),
        clientCredentialsTokens::clientCredentialsToken);
  }

  @Bean
  RequestingPartyTokenClient requestingPartyTokenClient(
      KeycloakProperties keycloak,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest) {
    return new KeycloakRequestingPartyTokenClient(
        keycloak.baseUrl(), keycloak.realm(), rest.clone());
  }
}
