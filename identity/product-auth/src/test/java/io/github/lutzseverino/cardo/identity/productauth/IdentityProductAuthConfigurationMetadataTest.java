package io.github.lutzseverino.cardo.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IdentityProductAuthConfigurationMetadataTest {

  @Test
  void publishesOwnedExchangeIntrospectionAndCacheProperties() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(metadata)
        .contains("cardo.identity.product-auth.token-exchange.connect-timeout")
        .contains("cardo.identity.product-auth.token-exchange.read-timeout")
        .contains("cardo.identity.product-auth.active-token-validation.introspection-uri")
        .contains("cardo.identity.product-auth.active-token-validation.connect-timeout")
        .contains("cardo.identity.product-auth.active-token-validation.read-timeout");
  }
}
