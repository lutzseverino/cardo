package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceProcessTest {

  @TempDir Path temporaryDirectory;

  @Test
  void startsEachJarOutsideTheMavenModulesExternalConfigurationDirectory() throws Exception {
    Path module = temporaryDirectory.resolve("identity");
    Path jar = module.resolve("target/identity.jar");
    Files.createDirectories(jar.getParent());
    Files.writeString(module.resolve("application.yml"), "spring.application.name: wrong-service");
    Files.write(jar, new byte[] {0});

    ProcessBuilder builder = ReferenceProcess.processBuilder(jar);

    assertThat(builder.directory().toPath()).isEqualTo(jar.getParent());
    assertThat(builder.directory().toPath()).isNotEqualTo(module);
    assertThat(builder.command()).containsSubsequence("-jar", jar.toString());
  }

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
