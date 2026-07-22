package io.github.lutzseverino.cardo.invite.repository;

import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface InvitationProjection {

  UUID getId();

  UUID getRequestId();

  String getProduct();

  UUID getTenantId();

  String getTenantResourceType();

  String getInvitedEmail();

  UUID getInvitedUserId();

  UUID getInvitedBy();

  String getAcceptUrlBase();

  OffsetDateTime getExpiresAt();

  String getToken();

  InvitationStatus getStatus();

  OffsetDateTime getAcceptedAt();

  OffsetDateTime getRevokedAt();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getUpdatedAt();
}
