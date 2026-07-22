package io.github.lutzseverino.cardo.invite.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InviteClientConfigurationMetadataTest {

  @Test
  void publishesAllOwnedProperties() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(metadata)
        .contains("cardo.invite.client.base-url")
        .contains("cardo.invite.client.service-token-scope")
        .contains("cardo.invite.client.connect-timeout")
        .contains("cardo.invite.client.read-timeout");
  }
}
