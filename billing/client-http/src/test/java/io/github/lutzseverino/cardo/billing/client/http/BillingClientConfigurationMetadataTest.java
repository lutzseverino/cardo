package io.github.lutzseverino.cardo.billing.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BillingClientConfigurationMetadataTest {

  @Test
  void publishesAllOwnedProperties() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(metadata)
        .contains("cardo.billing.client.base-url")
        .contains("cardo.billing.client.service-token-scope")
        .contains("cardo.billing.client.connect-timeout")
        .contains("cardo.billing.client.read-timeout");
  }
}
