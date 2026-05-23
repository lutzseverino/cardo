package com.odonta.authorization.plan;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.AuthorizationResourceType;
import com.odonta.authorization.resource.TargetableAuthorizationResource;
import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.AuthorizationSyncOperation;
import java.util.ArrayList;
import java.util.List;
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
    operations.add(AuthorizationSyncOperation.provision(resourceKey(resource), resource));
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
    List<AccessProfileGrantProjection> tenantGrants =
        grants.stream().filter(grant -> isWildcardTenantGrant(grant, tenantResourceType)).toList();
    if (tenantGrants.isEmpty()) {
      return this;
    }
    provision(tenantResource(tenantResourceType, tenantId));
    tenantGrants.forEach(
        grant ->
            grantResourceActions(
                source, subject, tenantResourceType, tenantId, List.of(grant.getAction())));
    return this;
  }

  public AuthorizationPlan build() {
    return AuthorizationPlan.of(operations);
  }

  private boolean isWildcardTenantGrant(
      AccessProfileGrantProjection grant, AuthorizationResourceType tenantResourceType) {
    return tenantResourceType.typeName().equals(grant.getResourceType())
        && grant.getResourceId() == null;
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
