package io.github.lutzseverino.cardo.authorization.grant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class Grants {

  private final ApplicationEventPublisher events;
  private final GrantReceiptStore receipts;

  Grants(ApplicationEventPublisher events, GrantReceiptStore receipts) {
    this.events = events;
    this.receipts = receipts;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public GrantReceipt stage(GrantPlan plan) {
    Objects.requireNonNull(plan, "plan");
    UUID receiptId = UUID.randomUUID();
    GrantReceipt receipt =
        receipts.create(
            receiptId, plan.isEmpty() ? GrantReceiptStatus.APPLIED : GrantReceiptStatus.PENDING);
    if (!plan.isEmpty()) {
      events.publishEvent(new StagedGrantPlan(receiptId, plan));
    }
    return receipt;
  }

  @Transactional(readOnly = true)
  public Optional<GrantReceipt> find(UUID receiptId) {
    return receipts.find(Objects.requireNonNull(receiptId, "receiptId"));
  }
}
