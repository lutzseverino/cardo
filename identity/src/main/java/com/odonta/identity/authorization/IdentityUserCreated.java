package com.odonta.identity.authorization;

import com.odonta.authorization.sync.AuthorizationEvent;
import com.odonta.identity.model.User;
import java.util.Objects;

public record IdentityUserCreated(User user) implements AuthorizationEvent {

  public IdentityUserCreated {
    Objects.requireNonNull(user, "user");
  }
}
