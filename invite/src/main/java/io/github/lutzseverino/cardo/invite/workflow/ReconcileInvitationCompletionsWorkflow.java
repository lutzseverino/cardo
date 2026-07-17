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
      reconcile(work.id(), identity);
    } catch (RuntimeException failure) {
      if (permanent(failure)) {
        logger.error("Invitation completion {} failed permanently", work.id(), failure);
        completions.recordTerminalFailure(work.id(), failure);
      } else {
        logger.warn("Invitation completion {} failed and will be retried", work.id(), failure);
        completions.recordFailure(work.id(), failure);
      }
    }
  }

  private void reconcile(UUID operationId, IdentityOperation identity) {
    if (IdentityOperationStatus.COMPLETED.equals(identity.status())) {
      completions.complete(operationId);
    } else if (IdentityOperationStatus.FAILED.equals(identity.status())) {
      completions.recordIdentityFailure(operationId, identity.lastError());
    } else if (IdentityOperationStatus.REQUESTED.equals(identity.status())) {
      completions.markAwaitingIdentity(operationId, identity.expiresAt());
    } else {
      completions.reschedule(operationId, identity.expiresAt());
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
