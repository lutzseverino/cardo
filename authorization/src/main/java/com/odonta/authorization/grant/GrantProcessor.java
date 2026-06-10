package com.odonta.authorization.grant;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.CreatedAuthorizationResource;
import java.util.List;

public class GrantProcessor {

  private final AuthorizationAdminClient authorization;

  public GrantProcessor(AuthorizationAdminClient authorization) {
    this.authorization = authorization;
  }

  public void apply(GrantPlan plan) {
    plan.resources().forEach(this::provision);
    plan.resourceGrants().forEach(this::grant);
    plan.authorityGrants().forEach(this::grant);
  }

  private void provision(AuthorizationResource resource) {
    authorization
        .findResourceByName(resource.resourceServerClientId(), resource.name())
        .orElseGet(() -> authorization.createResource(resource));
  }

  private void grant(GrantPlan.ResourceGrant grant) {
    CreatedAuthorizationResource resource =
        authorization
            .findResourceByName(grant.resourceServerClientId(), grant.resourceName())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authorization resource not found: " + grant.resourceName()));
    List<String> missingActions = missingActions(grant, resource);
    if (!missingActions.isEmpty()) {
      authorization.grantResourceActions(
          new ResourceActionGrant(
              grant.resourceServerClientId(), resource.id(), grant.subject(), missingActions));
    }
  }

  private List<String> missingActions(
      GrantPlan.ResourceGrant grant, CreatedAuthorizationResource resource) {
    List<String> grantedActions =
        authorization
            .findResourceActionGrants(
                new ResourceGrantQuery(
                    grant.resourceServerClientId(), resource.id(), grant.subject(), true))
            .stream()
            .filter(GrantedResourceAction::granted)
            .map(GrantedResourceAction::action)
            .toList();
    return grant.actions().stream().filter(action -> !grantedActions.contains(action)).toList();
  }

  private void grant(GrantPlan.AuthorityGrant grant) {
    authorization.ensureClientRolesAssigned(
        new AuthorityGrant(grant.resourceServerClientId(), grant.subject(), grant.authorities()));
  }
}
