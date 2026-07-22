package io.github.lutzseverino.cardo.identity.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

class IdentityWorkflowMetricsTest {

  @Test
  void exposesCompleteStateGaugesAndBoundedOutcomesForEveryIdentityOwner() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);
    when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(42D);
    IdentityWorkflowMetrics metrics = new IdentityWorkflowMetrics(registry, jdbc);

    metrics.operation(IdentityOperationType.CREDENTIAL_SETUP, "success");
    metrics.mutation(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER, "retry");

    assertThat(
            registry
                .get("cardo.durable.workflow.work")
                .tags(
                    "workflow", "identity-operation", "type", "credential-setup", "state", "active")
                .gauge()
                .value())
        .isEqualTo(7D);
    assertThat(
            registry
                .get("cardo.durable.workflow.oldest.actionable.age")
                .tags(
                    "workflow", "identity-provider-mutation", "type", "provision-provisional-user")
                .gauge()
                .value())
        .isEqualTo(42D);
    assertThat(
            registry
                .get("cardo.durable.workflow.processing")
                .tags(
                    "workflow",
                    "identity-provider-mutation",
                    "type",
                    "provision-provisional-user",
                    "outcome",
                    "retry")
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
