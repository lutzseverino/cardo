package com.odonta.authorization.sync;

import java.util.List;
import java.util.Objects;

public record AssignAuthorizationAuthorities(
    String uniqueKey,
    String resourceServerClientId,
    String requesterSubject,
    List<String> authorities)
    implements AuthorizationSyncOperation {

  public AssignAuthorizationAuthorities {
    requireText(uniqueKey, "uniqueKey");
    requireText(resourceServerClientId, "resourceServerClientId");
    requireText(requesterSubject, "requesterSubject");
    authorities = List.copyOf(Objects.requireNonNull(authorities, "authorities"));
    if (authorities.isEmpty()) {
      throw new IllegalArgumentException("authorities must not be empty");
    }
    authorities.forEach(authority -> requireText(authority, "authority"));
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
