package com.odonta.invite.authorization;

import com.odonta.authorization.sync.AuthorizationEvent;
import java.util.UUID;

public record InvitationAccepted(
    UUID tenantId, String tenantResourceType, UUID accessProfileId, String authorizationSubject)
    implements AuthorizationEvent {

  public String minimumAction() {
    return "read";
  }
}
