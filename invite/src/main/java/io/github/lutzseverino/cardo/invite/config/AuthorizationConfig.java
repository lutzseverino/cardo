package io.github.lutzseverino.cardo.invite.config;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.grant.AuthorizationPlanConfiguration;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenSettings;
import io.github.lutzseverino.cardo.invite.authorization.InvitationGrantPlanner;
import io.github.lutzseverino.cardo.invite.model.Invitation;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

@Configuration
@Import(AuthorizationPlanConfiguration.class)
@EntityScan(basePackageClasses = Invitation.class)
@EnableJpaRepositories(basePackageClasses = InvitationRepository.class)
public class AuthorizationConfig {

  @Bean
  KeycloakClientCredentialsTokenProvider keycloakClientCredentialsTokenProvider(
      KeycloakProperties keycloak,
      InviteRuntimeProperties runtime,
      @Qualifier("inviteOutboundRestClientBuilder") RestClient.Builder rest) {
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
  AuthorizationAdminClient keycloakAuthorizationClient(
      KeycloakProperties keycloak,
      @Qualifier("inviteOutboundRestClientBuilder") RestClient.Builder rest,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens) {
    return new KeycloakAuthorizationClient(
        keycloak.baseUrl(),
        keycloak.realm(),
        rest.clone(),
        clientCredentialsTokens::clientCredentialsToken);
  }

  @Bean
  InvitationGrantPlanner invitationGrantPlanner() {
    return new InvitationGrantPlanner();
  }
}
