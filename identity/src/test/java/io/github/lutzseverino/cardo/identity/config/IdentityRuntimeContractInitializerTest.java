package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

class IdentityRuntimeContractInitializerTest {

  @Test
  void validatesWithoutMutatingByDefault() {
    IdentityKeycloakProviderContractValidator validator =
        mock(IdentityKeycloakProviderContractValidator.class);
    IdentityKeycloakLegacyStartupRepair repair = mock(IdentityKeycloakLegacyStartupRepair.class);
    IdentityRuntimeContractInitializer initializer =
        new IdentityRuntimeContractInitializer(
            validator, repair, IdentityKeycloakProviderContractValidatorTest.properties(false));

    initializer.run(new DefaultApplicationArguments());

    verify(repair, never()).repair();
    verify(validator).validate();
  }

  @Test
  void repairsBeforeValidatingOnlyWhenTheLegacyFlagIsEnabled() {
    IdentityKeycloakProviderContractValidator validator =
        mock(IdentityKeycloakProviderContractValidator.class);
    IdentityKeycloakLegacyStartupRepair repair = mock(IdentityKeycloakLegacyStartupRepair.class);
    IdentityRuntimeContractInitializer initializer =
        new IdentityRuntimeContractInitializer(
            validator, repair, IdentityKeycloakProviderContractValidatorTest.properties(true));

    initializer.run(new DefaultApplicationArguments());

    InOrder order = inOrder(repair, validator);
    order.verify(repair).repair();
    order.verify(validator).validate();
  }

  @Test
  void propagatesValidationFailure() {
    IdentityKeycloakProviderContractValidator validator =
        mock(IdentityKeycloakProviderContractValidator.class);
    IdentityKeycloakLegacyStartupRepair repair = mock(IdentityKeycloakLegacyStartupRepair.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("provider drift"))
        .when(validator)
        .validate();
    IdentityRuntimeContractInitializer initializer =
        new IdentityRuntimeContractInitializer(
            validator, repair, IdentityKeycloakProviderContractValidatorTest.properties(false));

    assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider drift");
  }
}
