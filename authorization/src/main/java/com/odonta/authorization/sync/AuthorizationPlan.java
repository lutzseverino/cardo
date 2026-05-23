package com.odonta.authorization.sync;

import java.util.List;
import java.util.Objects;

public record AuthorizationPlan(List<AuthorizationSyncOperation> operations) {

  public AuthorizationPlan {
    operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
  }

  public static AuthorizationPlan of(List<AuthorizationSyncOperation> operations) {
    return new AuthorizationPlan(operations);
  }

  public boolean isEmpty() {
    return operations.isEmpty();
  }
}
