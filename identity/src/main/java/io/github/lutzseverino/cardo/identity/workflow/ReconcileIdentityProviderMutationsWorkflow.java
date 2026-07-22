package io.github.lutzseverino.cardo.identity.workflow;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationTerminalReason;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationWork;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService.FailureAcknowledgement;
import io.github.lutzseverino.cardo.identity.service.UserService;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconcileIdentityProviderMutationsWorkflow {

  private static final Logger logger =
      LoggerFactory.getLogger(ReconcileIdentityProviderMutationsWorkflow.class);

  private final IdentityProviderMutationService mutations;
  private final UserService users;
  private final IdentityProvider provider;

  @Scheduled(fixedDelayString = "${cardo.identity.provider-mutations.dispatch-delay:PT5S}")
  public void reconcile() {
    mutations.readyIds().forEach(this::reconcile);
  }

  void reconcile(UUID mutationId) {
    mutations.claim(mutationId).ifPresent(this::process);
  }

  private void process(IdentityProviderMutationWork work) {
    logger.info(
        "identity_provider_mutation_started id={} type={} attempt_target_version={}",
        work.id(),
        work.type(),
        work.desiredVersion());
    try {
      switch (work.type()) {
        case PROVISION_PASSWORD_USER -> recoverPasswordProvision(work);
        case PROVISION_PROVISIONAL_USER -> recoverProvisionalProvision(work);
        case BIND_USER_ID -> {
          provider.bindUserId(work.providerSubject(), work.userId());
          complete(work);
        }
        case SET_IDENTITY_ENABLED -> {
          provider.setIdentityEnabled(
              work.providerSubject(), Boolean.TRUE.equals(work.desiredEnabled()));
          complete(work);
        }
      }
    } catch (RuntimeException failure) {
      recordFailure(work, failure);
    }
  }

  private void recoverPasswordProvision(IdentityProviderMutationWork work) {
    Optional<IdentityProvider.ProvisionedIdentity> identity =
        provider.findIdentityByCorrelationMarker(work.correlationMarker());
    if (identity.isEmpty()) {
      RuntimeException failure =
          new IllegalStateException(
              "Provider dispatch could not be proven by the provisioning correlation marker.");
      FailureAcknowledgement acknowledgement =
          mutations.recordFailure(
              work,
              failure,
              IdentityProviderMutationTerminalReason.CREDENTIAL_RESUBMISSION_REQUIRED);
      logFailure(work, acknowledgement, "credential-resubmission-required", failure);
      return;
    }
    users.recoverPasswordProvision(work, identity.orElseThrow().subject());
    logger.info(
        "identity_provider_mutation_completed id={} type={} recovered=true",
        work.id(),
        work.type());
  }

  private void recoverProvisionalProvision(IdentityProviderMutationWork work) {
    Optional<IdentityProvider.ProvisionedIdentity> identity =
        provider.findIdentityByCorrelationMarker(work.correlationMarker());
    if (identity.isEmpty()) {
      try {
        identity =
            Optional.of(
                provider.provisionProvisionalIdentity(work.email(), work.correlationMarker()));
      } catch (RuntimeException dispatchFailure) {
        try {
          identity = provider.findIdentityByCorrelationMarker(work.correlationMarker());
        } catch (RuntimeException recoveryFailure) {
          dispatchFailure.addSuppressed(recoveryFailure);
        }
        if (identity.isEmpty()) {
          throw dispatchFailure;
        }
      }
    }
    users.recoverProvisionalProvision(work, identity.orElseThrow().subject());
    logger.info(
        "identity_provider_mutation_completed id={} type={} recovered=true",
        work.id(),
        work.type());
  }

  private void complete(IdentityProviderMutationWork work) {
    boolean completed = mutations.complete(work);
    if (completed) {
      logger.info(
          "identity_provider_mutation_completed id={} type={} target_version={}",
          work.id(),
          work.type(),
          work.desiredVersion());
    } else {
      logger.info(
          "identity_provider_mutation_stale id={} type={} target_version={}",
          work.id(),
          work.type(),
          work.desiredVersion());
    }
  }

  private void recordFailure(IdentityProviderMutationWork work, RuntimeException failure) {
    if (permanent(failure)) {
      IdentityProviderMutationTerminalReason reason =
          failure instanceof ApiException api
                  && api.status() == 409
                  && "user_provisioning_conflict".equals(api.code())
              ? IdentityProviderMutationTerminalReason.LOCAL_STATE_CONFLICT
              : IdentityProviderMutationTerminalReason.PROVIDER_REJECTED;
      FailureAcknowledgement acknowledgement =
          mutations.recordTerminalFailure(work, failure, reason);
      logFailure(
          work, acknowledgement, reason.name().toLowerCase(Locale.ROOT).replace('_', '-'), failure);
      return;
    }
    FailureAcknowledgement acknowledgement =
        mutations.recordFailure(
            work, failure, IdentityProviderMutationTerminalReason.RETRY_EXHAUSTED);
    logFailure(work, acknowledgement, "retry-exhausted", failure);
  }

  private void logFailure(
      IdentityProviderMutationWork work,
      FailureAcknowledgement acknowledgement,
      String terminalReason,
      RuntimeException failure) {
    switch (acknowledgement) {
      case STALE ->
          logger
              .atInfo()
              .addKeyValue("id", work.id())
              .addKeyValue("type", work.type())
              .addKeyValue("outcome", "stale-ack")
              .addKeyValue("reason", "lease-superseded")
              .addKeyValue("failureType", failure.getClass().getSimpleName())
              .log("Ignored stale identity provider mutation failure acknowledgement");
      case RETRY_SCHEDULED ->
          logger
              .atWarn()
              .addKeyValue("id", work.id())
              .addKeyValue("type", work.type())
              .addKeyValue("outcome", "retry")
              .addKeyValue("reason", "retry-scheduled")
              .addKeyValue("failureType", failure.getClass().getSimpleName())
              .log("Identity provider mutation failure scheduled for retry");
      case TERMINAL ->
          logger
              .atError()
              .addKeyValue("id", work.id())
              .addKeyValue("type", work.type())
              .addKeyValue("outcome", "terminal")
              .addKeyValue("reason", terminalReason)
              .addKeyValue("failureType", failure.getClass().getSimpleName())
              .log("Identity provider mutation requires operator inspection");
    }
  }

  private boolean permanent(RuntimeException failure) {
    if (!(failure instanceof ApiException api)) {
      return false;
    }
    int status = api.status();
    return status >= 400 && status < 500 && status != 408 && status != 425 && status != 429;
  }
}
