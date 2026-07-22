package io.github.lutzseverino.cardo.billing.operations;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
public class BillingWorkflowMetrics {

  private static final String WORKFLOW = "billing-customer-provisioning";

  private final MeterRegistry registry;
  private final Map<String, Counter> outcomes = new ConcurrentHashMap<>();

  BillingWorkflowMetrics(MeterRegistry registry, JdbcOperations jdbc) {
    this.registry = registry;
    gauge(jdbc, "active", "status = 'REQUESTED'");
    gauge(jdbc, "actionable", "status = 'REQUESTED' AND next_attempt_at <= CURRENT_TIMESTAMP");
    gauge(jdbc, "terminal", "status = 'FAILED'");
    Gauge.builder(
            "cardo.durable.workflow.oldest.actionable.age",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(created_at))), 0)"
                        + " FROM billing_customer_provisioning_operations"
                        + " WHERE status = 'REQUESTED' AND next_attempt_at <= CURRENT_TIMESTAMP",
                    Double.class))
        .baseUnit("seconds")
        .description("Age of the oldest currently actionable durable workflow item")
        .tags("workflow", WORKFLOW, "type", "stripe")
        .register(registry);
  }

  public void record(String outcome) {
    requireBoundedOutcome(outcome);
    outcomes
        .computeIfAbsent(
            outcome,
            ignored ->
                Counter.builder("cardo.durable.workflow.processing")
                    .description("Bounded durable workflow processing outcomes")
                    .tags("workflow", WORKFLOW, "type", "stripe", "outcome", outcome)
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

  private void gauge(JdbcOperations jdbc, String state, String predicate) {
    Gauge.builder(
            "cardo.durable.workflow.work",
            jdbc,
            source ->
                source.queryForObject(
                    "SELECT COUNT(*) FROM billing_customer_provisioning_operations WHERE "
                        + predicate,
                    Long.class))
        .description("Complete persisted durable workflow work by state")
        .tags("workflow", WORKFLOW, "type", "stripe", "state", state)
        .register(registry);
  }
}
