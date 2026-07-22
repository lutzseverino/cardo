package io.github.lutzseverino.cardo.invite.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardo.invite.product-callers")
public record ProductCallerProperties(Set<String> allowedClientIds) {

  public ProductCallerProperties {
    allowedClientIds =
        allowedClientIds == null
            ? Set.of()
            : allowedClientIds.stream()
                .filter(clientId -> clientId != null && !clientId.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
