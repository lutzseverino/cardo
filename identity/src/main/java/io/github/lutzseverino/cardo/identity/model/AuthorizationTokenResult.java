package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record AuthorizationTokenResult(OffsetDateTime expiresAt, List<EffectiveGrant> grants) {

  public AuthorizationTokenResult {
    Objects.requireNonNull(expiresAt, "expiresAt");
    grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
  }
}
