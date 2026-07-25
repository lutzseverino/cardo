package io.github.lutzseverino.cardo.identity.model;

import java.time.OffsetDateTime;
import java.util.Objects;

public record BrowserSessionMetadata(
    OffsetDateTime serverTime,
    OffsetDateTime sessionStartedAt,
    OffsetDateTime idleExpiresAt,
    OffsetDateTime absoluteExpiresAt,
    boolean refreshable) {

  public BrowserSessionMetadata {
    Objects.requireNonNull(serverTime, "serverTime");
    Objects.requireNonNull(sessionStartedAt, "sessionStartedAt");
    Objects.requireNonNull(idleExpiresAt, "idleExpiresAt");
    Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
  }

  public OffsetDateTime expiresAt() {
    return idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
  }
}
