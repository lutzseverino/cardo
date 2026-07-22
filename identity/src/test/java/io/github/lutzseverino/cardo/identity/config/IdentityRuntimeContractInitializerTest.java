package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.lutzseverino.cardo.identity.provider.IdentityRuntimeContract;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

class IdentityRuntimeContractInitializerTest {

  @Test
  void validatesWithoutMutatingByDefault() {
    IdentityRuntimeContract runtimeContract = mock(IdentityRuntimeContract.class);
    IdentityRuntimeContractInitializer initializer =
        new IdentityRuntimeContractInitializer(runtimeContract, properties(false));

    initializer.run(new DefaultApplicationArguments());

    verify(runtimeContract, never()).repairLegacyStartupDefinitions();
    verify(runtimeContract).validate();
  }

  @Test
  void repairsBeforeValidatingOnlyWhenTheLegacyFlagIsEnabled() {
    IdentityRuntimeContract runtimeContract = mock(IdentityRuntimeContract.class);
    IdentityRuntimeContractInitializer initializer =
        new IdentityRuntimeContractInitializer(runtimeContract, properties(true));

    initializer.run(new DefaultApplicationArguments());

    InOrder order = inOrder(runtimeContract);
    order.verify(runtimeContract).repairLegacyStartupDefinitions();
    order.verify(runtimeContract).validate();
  }

  @Test
  void propagatesValidationFailure() {
    IdentityRuntimeContract runtimeContract = mock(IdentityRuntimeContract.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("provider drift"))
        .when(runtimeContract)
        .validate();
    IdentityRuntimeContractInitializer initializer =
        new IdentityRuntimeContractInitializer(runtimeContract, properties(false));

    assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider drift");
  }

  private KeycloakProperties properties(boolean legacyMutation) {
    return new KeycloakProperties(
        "https://keycloak.example",
        "cardo",
        "runtime",
        "runtime-secret",
        "authorization-secret",
        "setup",
        URI.create("https://app.example/invitations/completed"),
        List.of("runtime", "identity", "billing"),
        legacyMutation);
  }
}
