package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.identity.workflow.InitializeIdentityRuntimeWorkflow;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IdentityRuntimeContractInitializer implements ApplicationRunner {

  private final InitializeIdentityRuntimeWorkflow initializeIdentityRuntime;
  private final KeycloakProperties keycloak;

  IdentityRuntimeContractInitializer(
      InitializeIdentityRuntimeWorkflow initializeIdentityRuntime, KeycloakProperties keycloak) {
    this.initializeIdentityRuntime = initializeIdentityRuntime;
    this.keycloak = keycloak;
  }

  @Override
  public void run(ApplicationArguments args) {
    initializeIdentityRuntime.initialize(keycloak.userIdClaimClientIds());
  }
}
