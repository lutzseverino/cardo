package io.github.lutzseverino.cardo.invite.workflow;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.client.IdentityOperation;
import io.github.lutzseverino.cardo.identity.client.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionWork;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconcileInvitationCompletionsWorkflow {

  private static final Logger logger =
      LoggerFactory.getLogger(ReconcileInvitationCompletionsWorkflow.class);

  private final InvitationCompletionService completions;
  private final IdentityUsersClient identityUsers;

  @Scheduled(fixedDelayString = "${cardo.invite.completion.dispatch-delay:PT5S}")
  public void reconcile() {
    completions.readyIds().forEach(this::reconcile);
  }

  void reconcile(UUID operationId) {
    completions.claim(operationId).ifPresent(this::process);
  }

  private void process(InvitationCompletionWork work) {
    try {
      IdentityOperation identity =
          InvitationCompletionStatus.REQUESTED.equals(work.status())
              ? identityUsers.requestCredentialSetup(
                  work.invitedUserId(), work.id(), work.expiresAt())
              : identityUsers.getCredentialSetup(work.invitedUserId(), work.id());
      reconcile(work, identity);
    } catch (RuntimeException failure) {
      if (permanent(failure)) {
        logger
            .atError()
            .addKeyValue("operationId", work.id())
            .addKeyValue("outcome", "terminal")
            .addKeyValue("reason", "identity-rejected")
            .log("Invitation completion requires operator inspection");
        logAcknowledgement(
            work, completions.recordTerminalFailure(work.id(), work.leaseToken(), failure));
      } else {
        logger
            .atWarn()
            .addKeyValue("operationId", work.id())
            .addKeyValue("outcome", "retry")
            .addKeyValue("failureType", failure.getClass().getSimpleName())
            .log("Invitation completion will be retried");
        logAcknowledgement(work, completions.recordFailure(work.id(), work.leaseToken(), failure));
      }
    }
  }

  private void reconcile(InvitationCompletionWork work, IdentityOperation identity) {
    if (IdentityOperationStatus.COMPLETED.equals(identity.status())) {
      logAcknowledgement(work, completions.complete(work.id(), work.leaseToken()));
    } else if (IdentityOperationStatus.FAILED.equals(identity.status())) {
      logAcknowledgement(
          work,
          completions.recordIdentityFailure(work.id(), work.leaseToken(), identity.lastError()));
    } else if (IdentityOperationStatus.REQUESTED.equals(identity.status())) {
      logAcknowledgement(
          work,
          completions.markAwaitingIdentity(work.id(), work.leaseToken(), identity.expiresAt()));
    } else {
      logAcknowledgement(
          work, completions.reschedule(work.id(), work.leaseToken(), identity.expiresAt()));
    }
  }

  private void logAcknowledgement(InvitationCompletionWork work, boolean accepted) {
    if (!accepted) {
      logger.info("invitation_completion_stale_ack id={}", work.id());
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
