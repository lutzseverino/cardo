package io.github.lutzseverino.cardo.authorization.grant;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class GrantReceiptProcessingLock {

  private final JdbcOperations jdbc;

  GrantReceiptProcessingLock(JdbcOperations jdbc) {
    this.jdbc = jdbc;
  }

  boolean tryAcquire(UUID receiptId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("grant receipt processing requires an active transaction");
    }
    long key = receiptId.getMostSignificantBits() ^ receiptId.getLeastSignificantBits();
    return Boolean.TRUE.equals(
        jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, key));
  }
}
