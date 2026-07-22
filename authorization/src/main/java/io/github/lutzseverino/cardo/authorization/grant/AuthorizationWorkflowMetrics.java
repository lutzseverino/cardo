package io.github.lutzseverino.cardo.authorization.grant;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcOperations;

final class AuthorizationWorkflowMetrics {

  private static final String GRANT = "authorization-grant";
  private static final String REVOCATION = "authorization-revocation";

  private final MeterRegistry registry;
  private final Map<String, Counter> outcomes = new ConcurrentHashMap<>();

  AuthorizationWorkflowMetrics(
      MeterRegistry registry, JdbcOperations jdbc, String schema, Duration retryDelay) {
    this.registry = registry;
    String prefix = schemaName(schema) + '.';
    registerGrant(jdbc, prefix, retryDelay);
    registerPublication(jdbc, prefix, REVOCATION, "authorization.revocation-plan", retryDelay);
  }

  void grant(String outcome) {
    record(GRANT, outcome);
  }

  void revocation(String outcome) {
    record(REVOCATION, outcome);
  }

  private void registerGrant(JdbcOperations jdbc, String prefix, Duration retryDelay) {
    receiptGauge(jdbc, prefix, "active", "status = 'PENDING'");
    receiptGauge(jdbc, prefix, "terminal", "status = 'FAILED'");
    registerPublication(jdbc, prefix, GRANT, "authorization.grant-plan", retryDelay);
  }

  private void registerPublication(
      JdbcOperations jdbc, String prefix, String workflow, String listener, Duration retryDelay) {
    String incomplete = "listener_id = '" + listener + "' AND completion_date IS NULL";
    publicationGauge(jdbc, prefix, workflow, "active", incomplete);
    String actionable =
        incomplete
            + " AND publication_date <= CURRENT_TIMESTAMP - INTERVAL '"
            + retryDelay.toMillis()
            + " milliseconds'";
    publicationGauge(jdbc, prefix, workflow, "actionable", actionable);
    Gauge.builder(
            "cardo.durable.workflow.oldest.actionable.age",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP"
                        + " - MIN(publication_date))), 0) FROM "
                        + prefix
                        + "event_publication WHERE "
                        + actionable,
                    Double.class))
        .baseUnit("seconds")
        .description("Age of the oldest currently actionable durable workflow item")
        .tags("workflow", workflow, "type", "publication")
        .register(registry);
  }

  private void receiptGauge(JdbcOperations jdbc, String prefix, String state, String predicate) {
    Gauge.builder(
            "cardo.durable.workflow.work",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COUNT(*) FROM " + prefix + "grant_receipt WHERE " + predicate,
                    Long.class))
        .description("Complete persisted durable workflow work by state")
        .tags("workflow", GRANT, "type", "receipt", "state", state)
        .register(registry);
  }

  private void publicationGauge(
      JdbcOperations jdbc, String prefix, String workflow, String state, String predicate) {
    Gauge.builder(
            "cardo.durable.workflow.work",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COUNT(*) FROM " + prefix + "event_publication WHERE " + predicate,
                    Long.class))
        .description("Complete persisted durable workflow work by state")
        .tags("workflow", workflow, "type", "publication", "state", state)
        .register(registry);
  }

  private void record(String workflow, String outcome) {
    requireBoundedOutcome(outcome);
    String key = workflow + ':' + outcome;
    outcomes
        .computeIfAbsent(
            key,
            ignored ->
                Counter.builder("cardo.durable.workflow.processing")
                    .description("Bounded durable workflow processing outcomes")
                    .tags("workflow", workflow, "type", "default", "outcome", outcome)
                    .register(registry))
        .increment();
  }

  private static void requireBoundedOutcome(String outcome) {
    switch (outcome) {
      case "success", "retry", "stale-ack", "terminal" -> {
        // These are the complete, deliberately bounded processing outcomes.
      }
      default -> throw new IllegalArgumentException("Unsupported durable workflow outcome.");
    }
  }

  private static String schemaName(String schema) {
    if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("authorization event schema must be a SQL identifier");
    }
    return schema;
  }
}
