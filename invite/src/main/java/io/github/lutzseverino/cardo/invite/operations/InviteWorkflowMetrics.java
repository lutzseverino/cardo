package io.github.lutzseverino.cardo.invite.operations;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
public class InviteWorkflowMetrics {

  private static final String COMPLETION = "invitation-completion";
  private static final String DELIVERY = "invitation-delivery";

  private final MeterRegistry registry;
  private final Map<String, Counter> outcomes = new ConcurrentHashMap<>();

  InviteWorkflowMetrics(
      MeterRegistry registry,
      JdbcOperations jdbc,
      @Value("${spring.modulith.events.jdbc.schema}") String eventSchema,
      @Value("${cardo.invite.delivery.retry-delay:PT1M}") Duration deliveryRetryDelay) {
    this.registry = registry;
    registerCompletion(jdbc);
    registerDelivery(jdbc, schemaName(eventSchema), deliveryRetryDelay);
  }

  public void completion(String outcome) {
    record(COMPLETION, outcome);
  }

  public void delivery(String outcome) {
    record(DELIVERY, outcome);
  }

  private void registerCompletion(JdbcOperations jdbc) {
    String active = "status IN ('REQUESTED', 'AWAITING_IDENTITY')";
    gauge(jdbc, COMPLETION, "active", "invitation_completion_operations", active);
    gauge(
        jdbc,
        COMPLETION,
        "actionable",
        "invitation_completion_operations",
        active + " AND next_attempt_at <= CURRENT_TIMESTAMP");
    age(
        jdbc,
        COMPLETION,
        "invitation_completion_operations",
        active + " AND next_attempt_at <= CURRENT_TIMESTAMP",
        "created_at");
    gauge(jdbc, COMPLETION, "terminal", "invitation_completion_operations", "status = 'FAILED'");
  }

  private void registerDelivery(JdbcOperations jdbc, String schema, Duration retryDelay) {
    String table = schema + ".event_publication";
    String incomplete = "listener_id = 'invite.invitation-delivery' AND completion_date IS NULL";
    gauge(jdbc, DELIVERY, "active", table, incomplete);
    String actionable =
        incomplete
            + " AND publication_date <= CURRENT_TIMESTAMP - INTERVAL '"
            + retryDelay.toMillis()
            + " milliseconds'";
    gauge(jdbc, DELIVERY, "actionable", table, actionable);
    age(jdbc, DELIVERY, table, actionable, "publication_date");
  }

  private void gauge(
      JdbcOperations jdbc, String workflow, String state, String table, String predicate) {
    Gauge.builder(
            "cardo.durable.workflow.work",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Long.class))
        .description("Complete persisted durable workflow work by state")
        .tags("workflow", workflow, "type", "default", "state", state)
        .register(registry);
  }

  private void age(
      JdbcOperations jdbc, String workflow, String table, String predicate, String timestamp) {
    Gauge.builder(
            "cardo.durable.workflow.oldest.actionable.age",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN("
                        + timestamp
                        + "))), 0) FROM "
                        + table
                        + " WHERE "
                        + predicate,
                    Double.class))
        .baseUnit("seconds")
        .description("Age of the oldest currently actionable durable workflow item")
        .tags("workflow", workflow, "type", "default")
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
      throw new IllegalArgumentException("invite event schema must be a SQL identifier");
    }
    return schema;
  }
}
