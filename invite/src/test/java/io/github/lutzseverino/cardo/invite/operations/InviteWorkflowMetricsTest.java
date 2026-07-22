package io.github.lutzseverino.cardo.invite.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcOperations;

class InviteWorkflowMetricsTest {

  @Test
  void exposesCompletionAndDeliveryStateAndOutcomesWithoutBusinessTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
    when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(15D);
    InviteWorkflowMetrics metrics =
        new InviteWorkflowMetrics(registry, jdbc, "invite_events", Duration.ofMinutes(1));

    metrics.completion("stale-ack");
    metrics.delivery("retry");

    assertThat(
            registry
                .get("cardo.durable.workflow.work")
                .tags("workflow", "invitation-delivery", "type", "default", "state", "active")
                .gauge()
                .value())
        .isEqualTo(3D);
    assertThat(
            registry
                .get("cardo.durable.workflow.processing")
                .tags(
                    "workflow", "invitation-completion", "type", "default", "outcome", "stale-ack")
                .counter()
                .count())
        .isOne();
  }

  @Test
  void managementConfigurationExposesReadinessDatabaseWithoutWideningRoutes() throws IOException {
    var source =
        new YamlPropertySourceLoader()
            .load("invite-application", new ClassPathResource("application.yml"))
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
