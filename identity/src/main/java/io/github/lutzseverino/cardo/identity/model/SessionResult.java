package io.github.lutzseverino.cardo.identity.model;

import java.util.Objects;

public record SessionResult(
    AuthenticationResult authentication,
    SessionCredential accessCredential,
    SessionCredential refreshCredential) {

  public SessionResult {
    Objects.requireNonNull(authentication, "authentication");
    Objects.requireNonNull(accessCredential, "accessCredential");
    Objects.requireNonNull(refreshCredential, "refreshCredential");
  }
}
