package io.github.lutzseverino.cardo.authorization.grant;

import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

final class GrantReceiptFailureRecorder {

  private final GrantReceiptStore receipts;
  private final TransactionTemplate transaction;

  GrantReceiptFailureRecorder(GrantReceiptStore receipts, PlatformTransactionManager transactions) {
    this.receipts = receipts;
    this.transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  boolean record(UUID receiptId, int maxAttempts) {
    Boolean terminal =
        transaction.execute(ignored -> receipts.recordFailure(receiptId, maxAttempts));
    return Boolean.TRUE.equals(terminal);
  }
}
