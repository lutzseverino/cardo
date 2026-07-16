package io.github.lutzseverino.cardo.authorization.grant;

import java.util.List;
import java.util.Objects;

public record SubjectGrants(String authorizationSubject, List<EffectiveGrant> grants) {

  public SubjectGrants {
    requireText(authorizationSubject, "authorizationSubject");
    grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
    if (grants.isEmpty()) {
      throw new IllegalArgumentException("grants must not be empty");
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
