package com.odonta.authorization.grant;

import java.util.List;
import java.util.Objects;

public record ResourceActionAssignment(
    String resourceServerClientId,
    String resourceId,
    String requesterSubject,
    List<String> actions) {

  public ResourceActionAssignment {
    requireText(resourceServerClientId, "resourceServerClientId");
    requireText(resourceId, "resourceId");
    requireText(requesterSubject, "requesterSubject");
    actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    if (actions.isEmpty()) {
      throw new IllegalArgumentException("actions must not be empty");
    }
    actions.forEach(action -> requireText(action, "action"));
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
