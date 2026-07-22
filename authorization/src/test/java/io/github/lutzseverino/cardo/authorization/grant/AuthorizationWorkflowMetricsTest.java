package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

class AuthorizationWorkflowMetricsTest {

  @Test
  void exposesReceiptAndPublicationStateWithBoundedOutcomes() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);
    when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(60D);
    AuthorizationWorkflowMetrics metrics =
        new AuthorizationWorkflowMetrics(registry, jdbc, "identity_events", Duration.ofMinutes(1));

    metrics.grant("success");
    metrics.revocation("retry");

    assertThat(
            registry
                .get("cardo.durable.workflow.work")
                .tags("workflow", "authorization-grant", "type", "receipt", "state", "active")
                .gauge()
                .value())
        .isEqualTo(5D);
    assertThat(
            registry
                .get("cardo.durable.workflow.processing")
                .tags("workflow", "authorization-revocation", "type", "default", "outcome", "retry")
                .counter()
                .count())
        .isOne();
  }
}
