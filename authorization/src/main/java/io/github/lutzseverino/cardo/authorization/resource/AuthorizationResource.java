package io.github.lutzseverino.cardo.authorization.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizationResource(
    String resourceServerClientId,
    String name,
    String type,
    String ownerSubject,
    List<String> actions) {

  public AuthorizationResource {
    requireText(resourceServerClientId, "resourceServerClientId");
    requireText(name, "name");
    requireText(type, "type");
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
