package com.odonta.invite.config;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.access.AccessProfile;
import com.odonta.authorization.access.AccessProfileGrantRepository;
import com.odonta.authorization.access.AccessProfileRepository;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.grant.AuthorizationPlanConfiguration;
import com.odonta.authorization.keycloak.KeycloakAuthorizationClient;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.invite.authorization.InvitationGrantPlanner;
import com.odonta.invite.model.Invitation;
import com.odonta.invite.repository.InvitationRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@Configuration
@Import(AuthorizationPlanConfiguration.class)
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
