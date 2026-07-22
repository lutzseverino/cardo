package io.github.lutzseverino.cardo.invite.config;

import java.util.Set;

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
