package io.github.lutzseverino.cardo.authorization.grant;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;

class GrantPlanListener {

  private static final Logger logger = LoggerFactory.getLogger(GrantPlanListener.class);

  private final GrantProcessor processor;
  private final GrantReceiptStore receipts;
  private final GrantReceiptProcessingLock processingLock;
  private final GrantReceiptFailureRecorder failures;
  private final AuthorizationWorkflowMetrics metrics;
  private final int maxAttempts;

  GrantPlanListener(
      GrantProcessor processor,
      GrantReceiptStore receipts,
      GrantReceiptProcessingLock processingLock,
      GrantReceiptFailureRecorder failures,
      AuthorizationWorkflowMetrics metrics,
      int maxAttempts) {
    this.processor = processor;
    this.receipts = receipts;
    this.processingLock = processingLock;
    this.failures = failures;
    this.metrics = metrics;
    this.maxAttempts = maxAttempts;
  }

  @ApplicationModuleListener(id = "authorization.grant-plan")
  void apply(GrantPlanPublication publication) {
    if (publication instanceof GrantPlan legacyPlan) {
      try {
        processor.apply(legacyPlan);
        metrics.grant("success");
      } catch (RuntimeException failure) {
        metrics.grant("retry");
        throw failure;
      }
      return;
    }
    applyStaged((StagedGrantPlan) publication);
  }

  private void applyStaged(StagedGrantPlan staged) {
    if (!processingLock.tryAcquire(staged.receiptId())) {
      metrics.grant("retry");
      log(staged.receiptId(), "retry", "concurrent-processing", null);
      throw new IllegalStateException(
          "Grant receipt is already being processed: " + staged.receiptId());
    }
    GrantReceipt receipt =
        receipts
            .find(staged.receiptId())
            .orElseThrow(
                () -> new IllegalStateException("Unknown grant receipt: " + staged.receiptId()));
    if (!GrantReceiptStatus.PENDING.equals(receipt.status())) {
      log(staged.receiptId(), "ignored", "receipt-not-pending", null);
      return;
    }
    try {
      processor.apply(staged.plan());
    } catch (RuntimeException failure) {
      if (!failures.record(staged.receiptId(), maxAttempts)) {
        metrics.grant("retry");
        log(staged.receiptId(), "retry", "provider-application-failed", failure);
        throw failure;
      }
      metrics.grant("terminal");
      log(staged.receiptId(), "terminal", "attempts-exhausted", failure);
      return;
    }
    receipts.markApplied(staged.receiptId());
    metrics.grant("success");
    log(staged.receiptId(), "success", "provider-state-applied", null);
  }

  private void log(UUID receiptId, String outcome, String reason, RuntimeException failure) {
    var event =
        switch (outcome) {
          case "terminal" -> logger.atError();
          case "retry" -> logger.atWarn();
          default -> logger.atInfo();
        };
    event
        .addKeyValue("receiptId", receiptId)
        .addKeyValue("outcome", outcome)
        .addKeyValue("reason", reason);
    if (failure != null) {
      event.addKeyValue("failureType", failure.getClass().getSimpleName());
    }
    event.log("Authorization grant receipt transition");
  }
}
