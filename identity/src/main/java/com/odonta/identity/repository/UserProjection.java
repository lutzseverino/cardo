package com.odonta.identity.repository;

import com.odonta.identity.model.UserStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface UserProjection {

  UUID getId();

  String getKeycloakSubject();

  String getEmail();

  String getName();

  String getAvatarUrl();

  UserStatus getStatus();

  boolean isEmailVerified();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getUpdatedAt();
}
