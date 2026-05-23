package com.odonta.authorization.sync;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public class AuthorizationSyncEventHandler {

  private final AuthorizationSyncProcessor processor;

  public AuthorizationSyncEventHandler(AuthorizationSyncProcessor processor) {
    this.processor = processor;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void processAfterCommit(AuthorizationSyncRequested event) {
    processor.processPending();
  }
}
