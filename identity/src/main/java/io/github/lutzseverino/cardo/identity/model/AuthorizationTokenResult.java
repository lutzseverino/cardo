package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record AuthorizationTokenResult(
    String subject, OffsetDateTime expiresAt, List<EffectiveGrant> grants) {

  public AuthorizationTokenResult {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }
    Objects.requireNonNull(expiresAt, "expiresAt");
    grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
  }
}
