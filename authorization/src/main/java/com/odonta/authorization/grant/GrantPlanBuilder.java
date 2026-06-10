package com.odonta.authorization.grant;

import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.TargetableAuthorizationResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GrantPlanBuilder {

  private final Map<String, AuthorizationResource> resources = new LinkedHashMap<>();
  private final List<GrantPlan.ResourceGrant> resourceGrants = new ArrayList<>();
  private final List<GrantPlan.AuthorityGrant> authorityGrants = new ArrayList<>();

  private GrantPlanBuilder() {}

  public static GrantPlanBuilder grantPlan() {
    return new GrantPlanBuilder();
  }

  public GrantPlanBuilder provision(TargetableAuthorizationResource resource) {
    return provision(resource.toAuthorizationResource());
  }

  public GrantPlanBuilder provision(AuthorizationResource resource) {
    resources.putIfAbsent(resourceKey(resource), resource);
    return this;
  }

  public GrantPlanBuilder grantFullAccess(
      String subject, TargetableAuthorizationResource resource) {
    return grantFullAccess(subject, resource.toAuthorizationResource());
  }

  public GrantPlanBuilder grantFullAccess(String subject, AuthorizationResource resource) {
    return grantActions(subject, resource, resource.actions());
  }

  public GrantPlanBuilder grantActions(
      String subject, AuthorizationResource resource, List<String> actions) {
    provision(resource);
    resourceGrants.add(
        new GrantPlan.ResourceGrant(
            resource.resourceServerClientId(), resource.name(), subject, actions));
    return this;
  }

  public GrantPlanBuilder grantAuthorities(
      String subject, String resourceServerClientId, List<String> authorities) {
    authorityGrants.add(new GrantPlan.AuthorityGrant(resourceServerClientId, subject, authorities));
    return this;
  }

  public GrantPlan build() {
    return new GrantPlan(
        List.copyOf(resources.values()), List.copyOf(resourceGrants), List.copyOf(authorityGrants));
  }

  private String resourceKey(AuthorizationResource resource) {
    return resource.resourceServerClientId() + ":" + resource.name();
  }
}
