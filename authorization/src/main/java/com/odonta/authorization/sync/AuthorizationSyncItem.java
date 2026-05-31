package com.odonta.authorization.sync;

import com.odonta.authorization.AuthorizationSyncStatus;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.common.data.PersonalDataEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "authorization_sync_items",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_authorization_sync_item_key", columnNames = "unique_key"))
public class AuthorizationSyncItem implements PersonalDataEntity {

  private static final String ACTION_SEPARATOR = ",";

  @Id @GeneratedValue private UUID id;

  @Column(name = "unique_key", nullable = false, updatable = false)
  private String uniqueKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthorizationSyncOperationType operation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthorizationSyncStatus status = AuthorizationSyncStatus.PENDING;

  @Column(name = "resource_server_client_id", nullable = false)
  private String resourceServerClientId;

  @Column(name = "resource_name", nullable = false)
  private String resourceName;

  @Column(name = "resource_type")
  private String resourceType;

  @Column(name = "owner_subject")
  private String ownerSubject;

  @Column(name = "requester_subject")
  private String requesterSubject;

  @Column(nullable = false)
  private String actions;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_attempted_at")
  private OffsetDateTime lastAttemptedAt;

  @Column(name = "synced_at")
  private OffsetDateTime syncedAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

  public static AuthorizationSyncItem from(AuthorizationSyncOperation operation) {
    return switch (operation) {
      case ProvisionAuthorizationResource provision -> fromProvision(provision);
      case GrantAuthorizationResourceActions grant -> fromGrant(grant);
      case AssignAuthorizationAuthorities authorities -> fromAuthorityAssignment(authorities);
    };
  }

  public AuthorizationResource authorizationResource() {
    return new AuthorizationResource(
        resourceServerClientId, resourceName, resourceType, ownerSubject, actionList());
  }

  public List<String> actionList() {
    return Arrays.stream(actions.split(ACTION_SEPARATOR))
        .filter(action -> !action.isBlank())
        .toList();
  }

  public void markAttempted() {
    attemptCount += 1;
    lastAttemptedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void markSynced() {
    status = AuthorizationSyncStatus.SYNCED;
    syncedAt = OffsetDateTime.now(ZoneOffset.UTC);
    lastError = null;
  }

  public void markFailed(String message) {
    status = AuthorizationSyncStatus.FAILED;
    lastError = message;
  }

  public void markPending() {
    status = AuthorizationSyncStatus.PENDING;
    lastError = null;
  }

  private static AuthorizationSyncItem fromProvision(ProvisionAuthorizationResource operation) {
    AuthorizationResource resource = operation.resource();
    AuthorizationSyncItem item = new AuthorizationSyncItem();
    item.uniqueKey = operation.uniqueKey();
    item.operation = AuthorizationSyncOperationType.PROVISION_RESOURCE;
    item.resourceServerClientId = resource.resourceServerClientId();
    item.resourceName = resource.name();
    item.resourceType = resource.type();
    item.ownerSubject = resource.ownerSubject();
    item.actions = join(resource.actions());
    return item;
  }

  private static AuthorizationSyncItem fromGrant(GrantAuthorizationResourceActions operation) {
    AuthorizationSyncItem item = new AuthorizationSyncItem();
    item.uniqueKey = operation.uniqueKey();
    item.operation = AuthorizationSyncOperationType.GRANT_RESOURCE_ACTIONS;
    item.resourceServerClientId = operation.resourceServerClientId();
    item.resourceName = operation.resourceName();
    item.requesterSubject = operation.requesterSubject();
    item.actions = join(operation.actions());
    return item;
  }

  private static AuthorizationSyncItem fromAuthorityAssignment(
      AssignAuthorizationAuthorities operation) {
    AuthorizationSyncItem item = new AuthorizationSyncItem();
    item.uniqueKey = operation.uniqueKey();
    item.operation = AuthorizationSyncOperationType.ASSIGN_AUTHORITIES;
    item.resourceServerClientId = operation.resourceServerClientId();
    item.resourceName = operation.resourceServerClientId() + ":authorities";
    item.requesterSubject = operation.requesterSubject();
    item.actions = join(operation.authorities());
    return item;
  }

  private static String join(List<String> actions) {
    return String.join(ACTION_SEPARATOR, actions);
  }
}
