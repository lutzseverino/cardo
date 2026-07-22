package io.github.lutzseverino.cardo.identity.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IdentityClientConfigurationMetadataTest {

  @Test
  void publishesAllOwnedProperties() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(metadata)
        .contains("cardo.identity.client.base-url")
        .contains("cardo.identity.client.service-token-scope")
        .contains("cardo.identity.client.connect-timeout")
        .contains("cardo.identity.client.read-timeout");
  }
}
