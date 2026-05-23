package com.odonta.invite.config;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.access.AccessProfile;
import com.odonta.authorization.access.AccessProfileGrantRepository;
import com.odonta.authorization.access.AccessProfileRepository;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.keycloak.KeycloakAuthorizationClient;
import com.odonta.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import com.odonta.authorization.sync.AuthorizationPlanHandler;
import com.odonta.authorization.sync.AuthorizationSyncEventHandler;
import com.odonta.authorization.sync.AuthorizationSyncItem;
import com.odonta.authorization.sync.AuthorizationSyncItemRepository;
import com.odonta.authorization.sync.AuthorizationSyncProcessor;
import com.odonta.authorization.sync.AuthorizationSyncService;
import com.odonta.invite.model.Invitation;
import com.odonta.invite.repository.InvitationRepository;
import java.util.List;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@Configuration
@EntityScan(
    basePackageClasses = {Invitation.class, AuthorizationSyncItem.class, AccessProfile.class})
@EnableJpaRepositories(
    basePackageClasses = {
      InvitationRepository.class,
      AuthorizationSyncItemRepository.class,
      AccessProfileRepository.class
    })
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
  AuthorizationSyncService authorizationSyncService(
      ApplicationEventPublisher events,
      AuthorizationSyncItemRepository items,
      List<AuthorizationPlanHandler<?>> handlers) {
    return new AuthorizationSyncService(events, items, handlers);
  }

  @Bean
  AuthorizationSyncProcessor authorizationSyncProcessor(
      AuthorizationAdminClient authorization, AuthorizationSyncItemRepository items) {
    return new AuthorizationSyncProcessor(authorization, items);
  }

  @Bean
  AuthorizationSyncEventHandler authorizationSyncEventHandler(
      AuthorizationSyncProcessor processor) {
    return new AuthorizationSyncEventHandler(processor);
  }
}
