package io.github.lutzseverino.cardo.identity.workflow;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationWork;
import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.service.IdentityOperationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconcileIdentityOperationsWorkflow {

  private static final Logger logger =
      LoggerFactory.getLogger(ReconcileIdentityOperationsWorkflow.class);

  private final IdentityOperationService operations;
  private final IdentityProvider provider;

  @Scheduled(fixedDelayString = "${cardo.identity.operations.dispatch-delay:PT5S}")
  public void reconcile() {
    operations.readyIds().forEach(this::reconcile);
  }

  void reconcile(java.util.UUID operationId) {
    operations.claim(operationId).ifPresent(this::process);
  }

  private void process(IdentityOperationWork work) {
    try {
      if (IdentityOperationType.PROVISIONAL_DELETION.equals(work.type())) {
        provider.deleteIdentity(work.providerSubject());
        operations.completeProvisionalDeletion(work.id());
        return;
      }
      if (IdentityOperationStatus.REQUESTED.equals(work.status())) {
        Duration lifespan = Duration.between(OffsetDateTime.now(), work.actionExpiresAt());
        if (lifespan.isZero() || lifespan.isNegative()) {
          throw new IllegalStateException("Credential setup deadline passed before dispatch.");
        }
        provider.requestCredentialSetup(work.providerSubject(), lifespan);
        operations.markAwaitingUser(work.id(), work.actionExpiresAt());
        return;
      }
      provider
          .completedIdentityProfile(work.providerSubject())
          .ifPresentOrElse(
              profile -> operations.completeCredentialSetup(work.id(), profile.name()),
              () -> operations.reschedulePoll(work.id()));
    } catch (RuntimeException failure) {
      if (permanent(failure)) {
        logger.error("Identity operation {} failed permanently", work.id(), failure);
        operations.recordTerminalFailure(work.id(), failure);
      } else {
        logger.warn("Identity operation {} failed and will be retried", work.id(), failure);
        operations.recordFailure(work.id(), failure);
      }
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
