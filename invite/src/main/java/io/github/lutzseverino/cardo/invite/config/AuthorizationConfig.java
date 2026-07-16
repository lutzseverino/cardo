package io.github.lutzseverino.cardo.invite.config;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.access.AccessProfile;
import io.github.lutzseverino.cardo.authorization.access.AccessProfileGrantRepository;
import io.github.lutzseverino.cardo.authorization.access.AccessProfileRepository;
import io.github.lutzseverino.cardo.authorization.access.AccessProfileService;
import io.github.lutzseverino.cardo.authorization.grant.AuthorizationPlanConfiguration;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.schema.AuthorizationSchemaConfiguration;
import io.github.lutzseverino.cardo.invite.authorization.InvitationGrantPlanner;
import io.github.lutzseverino.cardo.invite.model.Invitation;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@Configuration
@Import({AuthorizationPlanConfiguration.class, AuthorizationSchemaConfiguration.class})
@EntityScan(basePackageClasses = {Invitation.class, AccessProfile.class})
@EnableJpaRepositories(
    basePackageClasses = {InvitationRepository.class, AccessProfileRepository.class})
public class AuthorizationConfig {

  @Bean
  KeycloakClientCredentialsTokenProvider keycloakClientCredentialsTokenProvider(
      KeycloakProperties keycloak, RestClient.Builder rest) {
    return new KeycloakClientCredentialsTokenProvider(
        keycloak.baseUrl(), keycloak.realm(), keycloak.clientId(), keycloak.clientSecret(), rest);
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
  AccessProfileService accessProfileService(
      AccessProfileRepository profiles, AccessProfileGrantRepository grants) {
    return new AccessProfileService(profiles, grants);
  }

  @Bean
  InvitationGrantPlanner invitationGrantPlanner() {
    return new InvitationGrantPlanner();
  }
}
