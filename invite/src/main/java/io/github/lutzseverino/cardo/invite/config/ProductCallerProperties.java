package io.github.lutzseverino.cardo.invite.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.product-callers")
public record ProductCallerProperties(Set<String> allowedClientIds) {

  public ProductCallerProperties {
    if (allowedClientIds == null) {
      allowedClientIds = Set.of();
    } else {
      if (allowedClientIds.stream().anyMatch(clientId -> clientId == null || clientId.isBlank())) {
        throw new IllegalArgumentException(
            "cardo.invite.product-callers.allowed-client-ids must not contain blank values.");
      }
      allowedClientIds = Set.copyOf(allowedClientIds);
    }
  }
}
