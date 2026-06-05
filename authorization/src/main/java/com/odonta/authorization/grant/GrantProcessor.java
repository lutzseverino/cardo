package com.odonta.authorization.grant;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.resource.CreatedAuthorizationResource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GrantProcessor {

  private final AuthorizationAdminClient authorization;

  public GrantProcessor(AuthorizationAdminClient authorization) {
    this.authorization = authorization;
  }

  public void apply(GrantPlan plan) {
    Map<ResourceKey, CreatedAuthorizationResource> resources = new LinkedHashMap<>();
    plan.resources()
        .forEach(
            resource ->
                resources.put(
                    new ResourceKey(resource.resourceServerClientId(), resource.name()),
                    authorization.ensureResource(resource)));
    plan.resourceGrants().forEach(grant -> grant(grant, resources));
    plan.authorityGrants().forEach(this::grant);
  }

  private void grant(
      GrantPlan.ResourceGrant grant, Map<ResourceKey, CreatedAuthorizationResource> resources) {
    CreatedAuthorizationResource resource =
        resources.get(new ResourceKey(grant.resourceServerClientId(), grant.resourceName()));
    List<String> missingActions = missingActions(grant, resource);
    if (!missingActions.isEmpty()) {
      authorization.grantResourceActions(
          new ResourceActionAssignment(
              grant.resourceServerClientId(), resource.id(), grant.subject(), missingActions));
    }
  }

  private List<String> missingActions(
      GrantPlan.ResourceGrant grant, CreatedAuthorizationResource resource) {
    List<String> grantedActions =
        authorization
            .findResourceActionGrants(
                ResourceGrantQuery.forResourceId(
                    grant.resourceServerClientId(), resource.id(), grant.subject()))
            .stream()
            .filter(GrantedResourceAction::granted)
            .map(GrantedResourceAction::action)
            .toList();
    return grant.actions().stream().filter(action -> !grantedActions.contains(action)).toList();
  }

  private void grant(GrantPlan.AuthorityGrant grant) {
    authorization.ensureClientRolesAssigned(
        new ClientRoleAssignment(
            grant.resourceServerClientId(), grant.subject(), grant.authorities()));
  }

  private record ResourceKey(String resourceServerClientId, String resourceName) {}
}
