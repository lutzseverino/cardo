package com.odonta.authorization.plan;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.AuthorizationResourceType;
import com.odonta.authorization.resource.TargetableAuthorizationResource;
import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.AuthorizationSyncOperation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuthorizationPlanBuilder {

  private final List<AuthorizationSyncOperation> operations = new ArrayList<>();

  private AuthorizationPlanBuilder() {}

  public static AuthorizationPlanBuilder authorizationPlan() {
    return new AuthorizationPlanBuilder();
  }

  public AuthorizationPlanBuilder provision(TargetableAuthorizationResource resource) {
    return provision(resource.toAuthorizationResource());
  }

  public AuthorizationPlanBuilder provision(AuthorizationResource resource) {
    String key = resourceKey(resource);
    if (operations.stream().noneMatch(operation -> operation.uniqueKey().equals(key))) {
      operations.add(AuthorizationSyncOperation.provision(key, resource));
    }
    return this;
  }

  public AuthorizationPlanBuilder grantFullResourceAccess(
      String source, String subject, TargetableAuthorizationResource resource) {
    return grantFullResourceAccess(source, subject, resource.toAuthorizationResource());
  }

  public AuthorizationPlanBuilder grantFullResourceAccess(
      String source, String subject, AuthorizationResource resource) {
    provision(resource);
    return grantResourceActions(source, subject, resource, resource.actions());
  }

  public AuthorizationPlanBuilder grantResourceActions(
      String source, String subject, AuthorizationResource resource, List<String> actions) {
    operations.add(
        AuthorizationSyncOperation.grant(
            grantKey(source, subject, resource.resourceServerClientId(), resource.name(), actions),
            resource.resourceServerClientId(),
            resource.name(),
            subject,
            actions));
    return this;
  }

  public AuthorizationPlanBuilder grantResourceActions(
      String source,
      String subject,
      AuthorizationResourceType resourceType,
      UUID resourceId,
      List<String> actions) {
    String resourceName = resourceType.resourceName(resourceId);
    operations.add(
        AuthorizationSyncOperation.grant(
            grantKey(source, subject, resourceType.product(), resourceName, actions),
            resourceType.product(),
            resourceName,
            subject,
            actions));
    return this;
  }

  public AuthorizationPlanBuilder assignAuthorities(
      String subject, String resourceServerClientId, List<String> authorities) {
    operations.add(
        AuthorizationSyncOperation.assignAuthorities(
            authorityKey(resourceServerClientId, subject, authorities),
            resourceServerClientId,
            subject,
            authorities));
    return this;
  }

  public AuthorizationPlanBuilder applyTenantResourceProfile(
      String source,
      String subject,
      AuthorizationResourceType tenantResourceType,
      UUID tenantId,
      List<AccessProfileGrantProjection> grants) {
    Map<String, List<String>> actionsByResourceType = new LinkedHashMap<>();
    grants.stream()
        .filter(grant -> grant.getResourceId() == null)
        .forEach(
            grant -> addAction(actionsByResourceType, grant.getResourceType(), grant.getAction()));
    actionsByResourceType.forEach(
        (resourceType, actions) -> {
          AuthorizationResourceType type =
              tenantResourceType.typeName().equals(resourceType)
                  ? tenantResourceType
                  : resourceType(resourceType, actions);
          AuthorizationResource resource = tenantResource(type, tenantId);
          provision(resource);
          grantResourceActions(source, subject, resource, actions);
        });
    return this;
  }

  public AuthorizationPlan build() {
    return AuthorizationPlan.of(operations);
  }

  private void addAction(
      Map<String, List<String>> actionsByResourceType, String resourceType, String action) {
    List<String> actions =
        actionsByResourceType.computeIfAbsent(resourceType, ignored -> new ArrayList<>());
    if (!actions.contains(action)) {
      actions.add(action);
    }
  }

  private AuthorizationResourceType resourceType(String resourceType, List<String> actions) {
    String[] parts = resourceType.split(":", 2);
    return AuthorizationResourceType.of(parts[0], parts[1], actions);
  }

  private AuthorizationResource tenantResource(
      AuthorizationResourceType tenantResourceType, UUID tenantId) {
    return new AuthorizationResource(
        tenantResourceType.product(),
        tenantResourceType.resourceName(tenantId),
        tenantResourceType.typeName(),
        null,
        tenantResourceType.actions());
  }

  private String resourceKey(AuthorizationResource resource) {
    return "resource:%s:%s".formatted(resource.resourceServerClientId(), resource.name());
  }

  private String grantKey(
      String source,
      String subject,
      String resourceServerClientId,
      String resourceName,
      List<String> actions) {
    return "grant:%s:%s:%s:%s:%s"
        .formatted(
            source, subject, resourceServerClientId, resourceName, String.join(",", actions));
  }

  private String authorityKey(
      String resourceServerClientId, String subject, List<String> authorities) {
    return "authorities:%s:%s:%s"
        .formatted(resourceServerClientId, subject, String.join(",", authorities));
  }
}
