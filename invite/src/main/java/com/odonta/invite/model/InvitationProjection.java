package com.odonta.invite.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface InvitationProjection {

  UUID getId();

  UUID getTenantId();

  String getTenantResourceType();

  UUID getAccessProfileId();

  String getInvitedEmail();

  UUID getInvitedUserId();

  String getInvitedAuthorizationSubject();

  UUID getInvitedBy();

  String getToken();

  InvitationStatus getStatus();

  OffsetDateTime getAcceptedAt();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getUpdatedAt();
}
