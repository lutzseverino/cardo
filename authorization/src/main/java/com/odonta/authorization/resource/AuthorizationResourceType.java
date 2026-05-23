package com.odonta.authorization.resource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AuthorizationResourceType(String product, String resourceType, List<String> actions) {

  public AuthorizationResourceType {
    requireText(product, "product");
    requireText(resourceType, "resourceType");
    actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    if (actions.isEmpty()) {
      throw new IllegalArgumentException("actions must not be empty");
    }
    actions.forEach(action -> requireText(action, "action"));
  }

  public static AuthorizationResourceType of(
      String product, String resourceType, List<String> actions) {
    return new AuthorizationResourceType(product, resourceType, actions);
  }

  public String typeName() {
    return product + ":" + resourceType;
  }

  public String resourceName(UUID resourceId) {
    return AuthorizationResourceNames.resource(product, resourceType, resourceId);
  }

  public String allResourcesName() {
    return AuthorizationResourceNames.all(product, resourceType);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
