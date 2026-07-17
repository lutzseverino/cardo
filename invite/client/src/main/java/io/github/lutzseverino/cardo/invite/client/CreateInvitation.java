package io.github.lutzseverino.cardo.invite.client;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public record CreateInvitation(
    UUID requestId,
    UUID tenantId,
    String tenantResourceType,
    String email,
    String accessProfile,
    List<InvitationGrant> grants,
    UUID invitedBy,
    URI acceptUrlBase) {

  public CreateInvitation {
    grants = List.copyOf(grants);
  }
}
