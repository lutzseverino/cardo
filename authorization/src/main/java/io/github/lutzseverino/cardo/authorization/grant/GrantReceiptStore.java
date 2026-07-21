package io.github.lutzseverino.cardo.authorization.grant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;

final class GrantReceiptStore {

  static final String PROVIDER_APPLICATION_FAILED = "provider_application_failed";

  private final JdbcOperations jdbc;
  private final String table;

  GrantReceiptStore(JdbcOperations jdbc, String schema) {
    this.jdbc = jdbc;
    this.table = schemaName(schema) + ".grant_receipt";
  }

  GrantReceipt create(UUID id, GrantReceiptStatus status) {
    jdbc.update(
        "INSERT INTO "
            + table
            + " (id, status, attempt_count, created_at, updated_at)"
            + " VALUES (?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        id,
        status.name());
    return new GrantReceipt(id, status, null);
  }

  Optional<GrantReceipt> find(UUID id) {
    return jdbc
        .query("SELECT id, status, failure_code FROM " + table + " WHERE id = ?", this::receipt, id)
        .stream()
        .findFirst();
  }

  void markApplied(UUID id) {
    int updated =
        jdbc.update(
            "UPDATE "
                + table
                + " SET status = 'APPLIED', updated_at = CURRENT_TIMESTAMP"
                + " WHERE id = ? AND status = 'PENDING'",
            id);
    if (updated == 0 && !hasStatus(id, GrantReceiptStatus.APPLIED)) {
      throw new IllegalStateException("Grant receipt is not pending: " + id);
    }
  }

  boolean recordFailure(UUID id, int maxAttempts) {
    jdbc.update(
        "UPDATE "
            + table
            + " SET attempt_count = attempt_count + 1,"
            + " status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE status END,"
            + " failure_code = CASE WHEN attempt_count + 1 >= ? THEN ? ELSE failure_code END,"
            + " updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'",
        maxAttempts,
        maxAttempts,
        PROVIDER_APPLICATION_FAILED,
        id);
    GrantReceipt receipt =
        find(id).orElseThrow(() -> new IllegalStateException("Unknown grant receipt: " + id));
    return !GrantReceiptStatus.PENDING.equals(receipt.status());
  }

  private boolean hasStatus(UUID id, GrantReceiptStatus status) {
    return find(id).map(receipt -> status.equals(receipt.status())).orElse(false);
  }

  private GrantReceipt receipt(ResultSet result, int rowNumber) throws SQLException {
    return new GrantReceipt(
        result.getObject("id", UUID.class),
        GrantReceiptStatus.valueOf(result.getString("status")),
        result.getString("failure_code"));
  }

  private static String schemaName(String schema) {
    if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("authorization event schema must be a SQL identifier");
    }
    return schema;
  }
}
