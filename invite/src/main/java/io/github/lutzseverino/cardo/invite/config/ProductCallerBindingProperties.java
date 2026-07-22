package io.github.lutzseverino.cardo.invite.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.product-callers")
record ProductCallerBindingProperties(List<String> allowedClientIds) {

  ProductCallerBindingProperties {
    if (allowedClientIds == null) {
      allowedClientIds = List.of();
    } else {
      if (allowedClientIds.stream().anyMatch(clientId -> clientId == null || clientId.isBlank())) {
        throw new IllegalArgumentException(
            "cardo.invite.product-callers.allowed-client-ids must not contain blank values.");
      }
      if (new HashSet<>(allowedClientIds).size() != allowedClientIds.size()) {
        throw new IllegalArgumentException(
            "cardo.invite.product-callers.allowed-client-ids must contain distinct values.");
      }
      allowedClientIds = List.copyOf(allowedClientIds);
    }
  }

  ProductCallerProperties toPublicProperties() {
    return new ProductCallerProperties(Set.copyOf(allowedClientIds));
  }
}
