package io.github.lutzseverino.cardo.identity.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.repository.UserProjection;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InitializeIdentityRuntimeWorkflowTest {

  @Test
  void coordinatesProviderContractSetupAndExistingUserBindings() {
    IdentityProvider identityProvider = mock(IdentityProvider.class);
    UserRepository users = mock(UserRepository.class);
    UserProjection user = mock(UserProjection.class);
    UUID userId = UUID.randomUUID();
    when(user.getId()).thenReturn(userId);
    when(user.getKeycloakSubject()).thenReturn("subject-1");
    when(users.findAllProjectedBy()).thenReturn(List.of(user));

    new InitializeIdentityRuntimeWorkflow(identityProvider, users)
        .initialize(List.of("identity", "invite"));

    verify(identityProvider).ensureUserIdClaimMapped(List.of("identity", "invite"));
    verify(identityProvider).bindUserId("subject-1", userId);
  }
}
