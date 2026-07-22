package io.github.lutzseverino.cardo.billing.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

class BillingWorkflowMetricsTest {

  @Test
  void exposesPersistedStateAndBoundedProcessingOutcomes() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
    when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(30D);
    BillingWorkflowMetrics metrics = new BillingWorkflowMetrics(registry, jdbc);

    metrics.record("terminal");

    assertThat(
            registry
                .get("cardo.durable.workflow.work")
                .tags(
                    "workflow",
                    "billing-customer-provisioning",
                    "type",
                    "stripe",
                    "state",
                    "terminal")
                .gauge()
                .value())
        .isEqualTo(2D);
    assertThat(
            registry
                .get("cardo.durable.workflow.processing")
                .tags(
                    "workflow",
                    "billing-customer-provisioning",
                    "type",
                    "stripe",
                    "outcome",
                    "terminal")
                .counter()
                .count())
        .isOne();
  }

  @Test
  void metricsDoNotWidenTheManagementRouteSurface() throws IOException {
    try (var input = getClass().getResourceAsStream("/application.yml")) {
      assertThat(input).isNotNull();
      String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(configuration).contains("include: health,info");
      assertThat(configuration).doesNotContain("include: health,info,metrics");
    }
  }
}
