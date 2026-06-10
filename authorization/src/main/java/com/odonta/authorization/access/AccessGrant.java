package com.odonta.authorization.access;

import java.util.UUID;

public record AccessGrant(String resourceType, UUID resourceId, String action) {

  public AccessGrant {
    requireText(resourceType, "resourceType");
    requireText(action, "action");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
