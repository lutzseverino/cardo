package io.github.lutzseverino.cardo.billing.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
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
  void managementConfigurationExposesReadinessDatabaseWithoutWideningRoutes() throws IOException {
    var source =
        new YamlPropertySourceLoader()
            .load("billing-application", new ClassPathResource("application.yml"))
            .getFirst();

    assertThat(source.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info");
    assertThat(source.getProperty("management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState,db");
    assertThat(source.getProperty("management.endpoint.health.group.readiness.show-components"))
        .isEqualTo("always");
    assertThat(source.getProperty("management.endpoint.health.show-components")).isNull();
    assertThat(source.getProperty("management.endpoint.health.show-details")).isNull();
  }
}
