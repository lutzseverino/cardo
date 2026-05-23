package com.odonta.authorization.sync;

import java.util.List;
import java.util.Objects;

public record GrantAuthorizationResourceActions(
    String uniqueKey,
    String resourceServerClientId,
    String resourceName,
    String requesterSubject,
    List<String> actions)
    implements AuthorizationSyncOperation {

  public GrantAuthorizationResourceActions {
    requireText(uniqueKey, "uniqueKey");
    requireText(resourceServerClientId, "resourceServerClientId");
    requireText(resourceName, "resourceName");
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
