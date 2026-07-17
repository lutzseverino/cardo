package io.github.lutzseverino.cardo.invite.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PendingInvitation(
    UUID id,
    String product,
    UUID tenantId,
    String tenantResourceType,
    String accessProfile,
    List<InvitationGrantInput> grants,
    UUID invitedUserId,
    String invitedAuthorizationSubject,
    OffsetDateTime expiresAt) {

  public PendingInvitation {
    grants = List.copyOf(grants);
  }
}
