package com.odonta.authorization.jpa;

import com.odonta.authorization.AuthorizationSyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class KeycloakAuthorizationResource {

  @Column(name = "keycloak_resource_id")
  private String keycloakResourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "authorization_sync_status", nullable = false)
  private AuthorizationSyncStatus authorizationSyncStatus = AuthorizationSyncStatus.PENDING;

  @Column(name = "authorization_synced_at")
  private OffsetDateTime authorizationSyncedAt;

  @Column(name = "authorization_sync_error")
  private String authorizationSyncError;

  public void markAuthorizationSynced(String keycloakResourceId) {
    this.keycloakResourceId = keycloakResourceId;
    this.authorizationSyncStatus = AuthorizationSyncStatus.SYNCED;
    this.authorizationSyncedAt = OffsetDateTime.now(ZoneOffset.UTC);
    this.authorizationSyncError = null;
  }

  public void markAuthorizationSyncFailed(String message) {
    this.authorizationSyncStatus = AuthorizationSyncStatus.FAILED;
    this.authorizationSyncError = message;
  }
}
