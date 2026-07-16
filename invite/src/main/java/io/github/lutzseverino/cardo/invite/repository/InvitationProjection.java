package io.github.lutzseverino.cardo.invite.repository;

import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
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
