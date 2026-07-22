package io.github.lutzseverino.cardo.identity.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationWork;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService.FailureAcknowledgement;
import io.github.lutzseverino.cardo.identity.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
    when(provider.findIdentityByCorrelationMarker("marker-1")).thenReturn(Optional.empty());
    when(mutations.recordFailure(
            org.mockito.ArgumentMatchers.eq(work),
            org.mockito.ArgumentMatchers.any(IllegalStateException.class),
            org.mockito.ArgumentMatchers.eq(
                IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED)))
        .thenReturn(FailureAcknowledgement.RETRY_SCHEDULED);

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
    when(mutations.recordTerminalFailure(
            work, missing, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED))
        .thenReturn(FailureAcknowledgement.TERMINAL);

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
    when(provider.findIdentityByCorrelationMarker("marker-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(users).recoverPasswordProvision(work, "subject-1");
  }

  @Test
  void restartRecoversProvisionalIdentityAlreadyOwnedByTheMarker() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, null);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findIdentityByCorrelationMarker("marker-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(users).recoverProvisionalProvision(work, "subject-1");
    verify(provider, never()).provisionProvisionalIdentity("user@example.com", "marker-1");
  }

  @Test
  void restartDispatchesPersistedProvisionalIntentWhenNoProviderEffectExists() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, null);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findIdentityByCorrelationMarker("marker-1")).thenReturn(Optional.empty());
    when(provider.provisionProvisionalIdentity("user@example.com", "marker-1"))
        .thenReturn(new IdentityProvider.ProvisionedIdentity("subject-1"));

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(provider).provisionProvisionalIdentity("user@example.com", "marker-1");
    verify(users).recoverProvisionalProvision(work, "subject-1");
  }

  @Test
  void restartConvergesALostProvisionalCreateResponseByMarker() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, null);
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findIdentityByCorrelationMarker("marker-1"))
        .thenReturn(
            Optional.empty(), Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));
    when(provider.provisionProvisionalIdentity("user@example.com", "marker-1"))
        .thenThrow(new RuntimeException("response lost"));

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(users).recoverProvisionalProvision(work, "subject-1");
    verify(mutations, never())
        .recordFailure(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unrelatedSameEmailProviderIdentityIsTerminalAndNeverAdopted() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, null);
    ApiException conflict = ApiException.conflict("user_exists", "User already exists.");
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findIdentityByCorrelationMarker("marker-1")).thenReturn(Optional.empty());
    when(provider.provisionProvisionalIdentity("user@example.com", "marker-1")).thenThrow(conflict);
    when(mutations.recordTerminalFailure(
            work, conflict, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED))
        .thenReturn(FailureAcknowledgement.TERMINAL);

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(mutations)
        .recordTerminalFailure(
            work, conflict, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED);
    verify(users, never()).recoverProvisionalProvision(work, "subject-1");
    verify(provider, never()).deleteIdentity(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void recoveredProvisionalLocalConflictIsOperatorVisible() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work =
        work(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, null);
    ApiException conflict =
        ApiException.conflict(
            "user_provisioning_conflict", "Provider subject conflicts with local user.");
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    when(provider.findIdentityByCorrelationMarker("marker-1"))
        .thenReturn(Optional.of(new IdentityProvider.ProvisionedIdentity("subject-1")));
    when(users.recoverProvisionalProvision(work, "subject-1")).thenThrow(conflict);
    when(mutations.recordTerminalFailure(
            work, conflict, IdentityProviderMutationTerminalReason.LOCAL_STATE_CONFLICT))
        .thenReturn(FailureAcknowledgement.TERMINAL);

    new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
        .reconcile(MUTATION_ID);

    verify(mutations)
        .recordTerminalFailure(
            work, conflict, IdentityProviderMutationTerminalReason.LOCAL_STATE_CONFLICT);
    verify(provider, never()).deleteIdentity(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void permanentStaleFailureLogCorrelatesWithoutMutationSecrets() {
    IdentityProviderMutationService mutations = mock(IdentityProviderMutationService.class);
    IdentityProvider provider = mock(IdentityProvider.class);
    UserService users = mock(UserService.class);
    IdentityProviderMutationWork work = work(IdentityProviderMutationType.BIND_USER_ID, null);
    ApiException failure =
        ApiException.of(404, "identity_provider_error", "sensitive provider response");
    when(mutations.claim(MUTATION_ID)).thenReturn(Optional.of(work));
    org.mockito.Mockito.doThrow(failure).when(provider).bindUserId("subject-1", USER_ID);
    when(mutations.recordTerminalFailure(
            work, failure, IdentityProviderMutationTerminalReason.PROVIDER_REJECTED))
        .thenReturn(FailureAcknowledgement.STALE);
    Logger logger =
        (Logger) LoggerFactory.getLogger(ReconcileIdentityProviderMutationsWorkflow.class);
    ListAppender<ILoggingEvent> events = new ListAppender<>();
    events.start();
    logger.addAppender(events);

    try {
      new ReconcileIdentityProviderMutationsWorkflow(mutations, users, provider)
          .reconcile(MUTATION_ID);
    } finally {
      logger.detachAppender(events);
    }

    String rendered =
        events.list.stream()
            .map(event -> event.getFormattedMessage() + event.getKeyValuePairs())
            .reduce("", String::concat);
    assertThat(rendered)
        .contains(
            MUTATION_ID.toString(),
            "type=\"BIND_USER_ID\"",
            "outcome=\"stale-ack\"",
            "reason=\"lease-superseded\"",
            "failureType=\"ApiException\"")
        .doesNotContain(
            USER_ID.toString(),
            LEASE_TOKEN.toString(),
            "subject-1",
            "user@example.com",
            "marker-1",
            failure.getMessage());
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
