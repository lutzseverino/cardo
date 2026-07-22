package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.identity.provider.IdentityRuntimeContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public final class IdentityRuntimeContractInitializer implements ApplicationRunner {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IdentityRuntimeContractInitializer.class);

  private final IdentityRuntimeContract runtimeContract;
  private final KeycloakProperties keycloak;

  IdentityRuntimeContractInitializer(
      IdentityRuntimeContract runtimeContract, KeycloakProperties keycloak) {
    this.runtimeContract = runtimeContract;
    this.keycloak = keycloak;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (keycloak.legacyStartupMutationEnabled()) {
      LOGGER.warn(
          "Legacy Keycloak startup mutation is enabled; this temporary compatibility mode "
              + "requires broad provider authority and should be disabled after migration");
      runtimeContract.repairLegacyStartupDefinitions();
    }
    runtimeContract.validate();
  }
}
