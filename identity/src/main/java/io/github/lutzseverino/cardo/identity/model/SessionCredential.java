package io.github.lutzseverino.cardo.identity.model;

import java.time.OffsetDateTime;
import java.util.Objects;

public record SessionCredential(String token, OffsetDateTime expiresAt) {

  public SessionCredential {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token must not be blank");
    }
    Objects.requireNonNull(expiresAt, "expiresAt");
  }

  @Override
  public String toString() {
    return "SessionCredential[expiresAt=" + expiresAt + "]";
  }
}
