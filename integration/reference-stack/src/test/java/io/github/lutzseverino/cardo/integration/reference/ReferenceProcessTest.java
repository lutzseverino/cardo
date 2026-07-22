package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceProcessTest {

  @Test
  void stripsInheritedAndSuppliedBootstrapAuthorityFromServiceProcesses() {
    Map<String, String> environment = new HashMap<>();
    environment.put("KC_BOOTSTRAP_ADMIN_USERNAME", "inherited-admin");
    environment.put("SERVICE_SETTING", "inherited");

    ReferenceProcess.configureEnvironment(
        environment,
        Map.of(
            "KC_BOOTSTRAP_ADMIN_PASSWORD", "must-not-cross-boundary",
            "SERVICE_SETTING", "fixture"));

    assertThat(environment)
        .doesNotContainKeys("KC_BOOTSTRAP_ADMIN_USERNAME", "KC_BOOTSTRAP_ADMIN_PASSWORD")
        .containsEntry("SERVICE_SETTING", "fixture");
  }
}
