package io.github.lutzseverino.cardo.authorization.token;

import java.util.List;
import java.util.Objects;

public record RequestedPermission(String resourceId, List<String> actions) {

  public RequestedPermission {
    requireText(resourceId, "resourceId");
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
