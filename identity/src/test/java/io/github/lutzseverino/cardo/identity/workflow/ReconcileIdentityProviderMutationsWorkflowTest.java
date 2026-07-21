package io.github.lutzseverino.cardo.identity.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationWork;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService;
import io.github.lutzseverino.cardo.identity.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconcileIdentityProviderMutationsWorkflowTest {

  private static final UUID MUTATION_ID = UUID.randomUUID();
  private static final UUID LEASE_TOKEN = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  void appliesDesiredDisableAndCompletesItsExactTarget() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.SET_IDENTITY_ENABLED, false);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(mutations.complete(work)).thenReturn(true);

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(provider).setIdentityEnabled("subject-1", false);
    verify(mutations).complete(work);
  }

  @Test
  void missingProvisionCorrelationRetriesTowardCredentialResubmission() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PASSWORD_USER, null);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findPasswordIdentityByCorrelationMarker("marker-1")).thenReturn(Optional.empty());

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(mutations)
        .recordFailure(
            org.mockito.ArgumentMatchers.eq(work),
            org.mockito.ArgumentMatchers.any(IllegalStateException.class),
            org.mockito.ArgumentMatchers.eq(
                IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED));
    verify(users, never())
        .recoverPasswordProvision(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void knownMissingProviderSubjectIsTerminal() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work = work(IdentityProviderMutationType.BIND_USER_ID, null);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    ApiException missing = ApiException.of(404, "identity_provider_error", "missing");
    org.mockito.Mockito.doThrow(missing).when(provider).bindUserId("subject-1", USER_ID);

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(mutations)
        .recordTerminalFailure(
            work, missing, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED);
  }

  @Test
  void recoveredProvisionCreatesTheLocalUserWithoutCredentials() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PASSWORD_USER, null);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findPasswordIdentityByCorrelationMarker("marker-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(users).recoverPasswordProvision(work, "subject-1");
  }

  private IdentityProviderMutationWork work(
      IdentityProviderMutationType type, Boolean desiredEnabled) {
    return new IdentityProviderMutationWork(
        MUTATION_ID,
        LEASE_TOKEN,
        type,
        USER_ID,
        "subject-1",
        "user@example.com",
        "User",
        "marker-1",
        desiredEnabled,
        1);
  }
}
