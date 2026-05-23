package com.odonta.authorization.sync;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.Objects;

public record ProvisionAuthorizationResource(String uniqueKey, AuthorizationResource resource)
    implements AuthorizationSyncOperation {

  public ProvisionAuthorizationResource {
    requireText(uniqueKey, "uniqueKey");
    Objects.requireNonNull(resource, "resource");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
