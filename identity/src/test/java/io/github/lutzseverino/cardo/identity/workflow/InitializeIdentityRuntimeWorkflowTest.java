package io.github.lutzseverino.cardo.identity.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class InitializeIdentityRuntimeWorkflowTest {

  @Test
  void installsProviderMappersWithoutMassStartupRebinding() {
    IdentityProvider identityProvider = mock(IdentityProvider.class);

    new InitializeIdentityRuntimeWorkflow(identityProvider)
        .initialize(List.of("identity", "invite"));

    verify(identityProvider).ensureUserIdClaimMapped(List.of("identity", "invite"));
    verify(identityProvider, never())
        .bindUserId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
