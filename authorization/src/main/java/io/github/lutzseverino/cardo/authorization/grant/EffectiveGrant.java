package io.github.lutzseverino.cardo.authorization.grant;

import java.util.List;
import java.util.Objects;

public record EffectiveGrant(GrantedResource resource, List<String> actions) {

  public EffectiveGrant {
    Objects.requireNonNull(resource, "resource");
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
