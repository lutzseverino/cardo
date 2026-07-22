package io.github.lutzseverino.cardo.invite.model;

import java.util.Objects;
import java.util.UUID;

public record InvitationGrantConvergenceResult(
    UUID invitationId, InvitationGrantConvergenceStatus status, String failureCode) {

  public InvitationGrantConvergenceResult {
    Objects.requireNonNull(invitationId, "invitationId");
    Objects.requireNonNull(status, "status");
    if (InvitationGrantConvergenceStatus.FAILED.equals(status)) {
      if (failureCode == null || failureCode.isBlank()) {
        throw new IllegalArgumentException("failed convergence requires a failure code");
      }
    } else if (failureCode != null) {
      throw new IllegalArgumentException("only failed convergence may have a failure code");
    }
  }
}
