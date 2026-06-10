package com.odonta.authorization.grant;

import java.util.List;
import java.util.Objects;

public record ClientRoleRevocation(
    String resourceServerClientId, String requesterSubject, List<String> authorities) {

  public ClientRoleRevocation {
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
