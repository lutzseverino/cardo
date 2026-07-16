package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IdentityRuntimeContractInitializer implements ApplicationRunner {

  private final IdentityProvider identityProvider;
  private final KeycloakProperties keycloak;
  private final UserRepository users;

  IdentityRuntimeContractInitializer(
      IdentityProvider identityProvider, KeycloakProperties keycloak, UserRepository users) {
    this.identityProvider = identityProvider;
    this.keycloak = keycloak;
    this.users = users;
  }

  @Override
  public void run(ApplicationArguments args) {
    identityProvider.ensureUserIdClaimMapped(keycloak.userIdClaimClientIds());
    users
        .findAll()
        .forEach(user -> identityProvider.bindUserId(user.getKeycloakSubject(), user.getId()));
  }
}
