package io.github.lutzseverino.cardo.authorization.grant;

import org.springframework.modulith.events.ApplicationModuleListener;

class GrantPlanListener {

  private final GrantProcessor processor;
  private final GrantReceiptStore receipts;
  private final GrantReceiptProcessingLock processingLock;
  private final GrantReceiptFailureRecorder failures;
  private final int maxAttempts;

  GrantPlanListener(
      GrantProcessor processor,
      GrantReceiptStore receipts,
      GrantReceiptProcessingLock processingLock,
      GrantReceiptFailureRecorder failures,
      int maxAttempts) {
    this.processor = processor;
    this.receipts = receipts;
    this.processingLock = processingLock;
    this.failures = failures;
    this.maxAttempts = maxAttempts;
  }

  @ApplicationModuleListener(id = "authorization.grant-plan")
  void apply(StagedGrantPlan staged) {
    if (!processingLock.tryAcquire(staged.receiptId())) {
      throw new IllegalStateException(
          "Grant receipt is already being processed: " + staged.receiptId());
    }
    GrantReceipt receipt =
        receipts
            .find(staged.receiptId())
            .orElseThrow(
                () -> new IllegalStateException("Unknown grant receipt: " + staged.receiptId()));
    if (!GrantReceiptStatus.PENDING.equals(receipt.status())) {
      return;
    }
    try {
      processor.apply(staged.plan());
    } catch (RuntimeException failure) {
      if (!failures.record(staged.receiptId(), maxAttempts)) {
        throw failure;
      }
      return;
    }
    receipts.markApplied(staged.receiptId());
  }
}
