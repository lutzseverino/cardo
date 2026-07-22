package io.github.lutzseverino.cardo.identity.operations;

import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
public class IdentityWorkflowMetrics {

  private static final String OPERATION = "identity-operation";
  private static final String MUTATION = "identity-provider-mutation";

  private final MeterRegistry registry;
  private final Map<String, Counter> outcomes = new ConcurrentHashMap<>();

  IdentityWorkflowMetrics(MeterRegistry registry, JdbcOperations jdbc) {
    this.registry = registry;
    for (IdentityOperationType type : IdentityOperationType.values()) {
      register(
          jdbc,
          OPERATION,
          tag(type),
          "identity_operations",
          "operation_type",
          type.name(),
          "status IN ('REQUESTED', 'AWAITING_USER')");
    }
    for (IdentityProviderMutationType type : IdentityProviderMutationType.values()) {
      register(
          jdbc,
          MUTATION,
          tag(type),
          "identity_provider_mutations",
          "mutation_type",
          type.name(),
          "status = 'REQUESTED'");
    }
  }

  public void operation(IdentityOperationType type, String outcome) {
    record(OPERATION, tag(type), outcome);
  }

  public void mutation(IdentityProviderMutationType type, String outcome) {
    record(MUTATION, tag(type), outcome);
  }

  private void register(
      JdbcOperations jdbc,
      String workflow,
      String type,
      String table,
      String typeColumn,
      String typeValue,
      String active) {
    String where = typeColumn + " = '" + typeValue + "' AND ";
    gauge(jdbc, workflow, type, "active", table, where + active);
    gauge(
        jdbc,
        workflow,
        type,
        "actionable",
        table,
        where + active + " AND next_attempt_at <= CURRENT_TIMESTAMP");
    age(jdbc, workflow, type, table, where + active + " AND next_attempt_at <= CURRENT_TIMESTAMP");
    gauge(jdbc, workflow, type, "terminal", table, where + "status = 'FAILED'");
  }

  private void gauge(
      JdbcOperations jdbc,
      String workflow,
      String type,
      String state,
      String table,
      String predicate) {
    Gauge.builder(
            "cardo.durable.workflow.work",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Long.class))
        .description("Complete persisted durable workflow work by state")
        .tags("workflow", workflow, "type", type, "state", state)
        .register(registry);
  }

  private void age(
      JdbcOperations jdbc, String workflow, String type, String table, String predicate) {
    Gauge.builder(
            "cardo.durable.workflow.oldest.actionable.age",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(created_at))), 0)"
                        + " FROM "
                        + table
                        + " WHERE "
                        + predicate,
                    Double.class))
        .baseUnit("seconds")
        .description("Age of the oldest currently actionable durable workflow item")
        .tags("workflow", workflow, "type", type)
        .register(registry);
  }

  private void record(String workflow, String type, String outcome) {
    requireBoundedOutcome(outcome);
    String key = workflow + ':' + type + ':' + outcome;
    outcomes
        .computeIfAbsent(
            key,
            ignored ->
                Counter.builder("cardo.durable.workflow.processing")
                    .description("Bounded durable workflow processing outcomes")
                    .tags("workflow", workflow, "type", type, "outcome", outcome)
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

  private static String tag(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
