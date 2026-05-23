package com.odonta.authorization.sync;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.AuthorizationSyncStatus;
import com.odonta.authorization.grant.AuthorityGrant;
import com.odonta.authorization.grant.GrantedResourceAction;
import com.odonta.authorization.grant.ResourceActionGrant;
import com.odonta.authorization.grant.ResourceGrantQuery;
import com.odonta.authorization.resource.CreatedAuthorizationResource;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class AuthorizationSyncProcessor {

  private final AuthorizationAdminClient authorization;
  private final AuthorizationSyncItemRepository items;

  public AuthorizationSyncProcessor(
      AuthorizationAdminClient authorization, AuthorizationSyncItemRepository items) {
    this.authorization = authorization;
    this.items = items;
  }

  @Transactional
  public void processPending() {
    items
        .findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus.PENDING)
        .forEach(this::process);
  }

  private void process(AuthorizationSyncItem item) {
    item.markAttempted();
    try {
      switch (item.getOperation()) {
        case PROVISION_RESOURCE -> provisionResource(item);
        case GRANT_RESOURCE_ACTIONS -> grantResourceActions(item);
        case ASSIGN_AUTHORITIES -> assignAuthorities(item);
      }
      item.markSynced();
    } catch (RuntimeException exception) {
      item.markFailed(exception.getMessage());
    }
  }

  private void provisionResource(AuthorizationSyncItem item) {
    authorization
        .findResourceByName(item.getResourceServerClientId(), item.getResourceName())
        .orElseGet(() -> authorization.createResource(item.authorizationResource()));
  }

  private void grantResourceActions(AuthorizationSyncItem item) {
    CreatedAuthorizationResource resource =
        authorization
            .findResourceByName(item.getResourceServerClientId(), item.getResourceName())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authorization resource not found: " + item.getResourceName()));
    List<String> missingActions = missingGrantedActions(item, resource);
    if (missingActions.isEmpty()) {
      return;
    }
    authorization.grantResourceActions(
        new ResourceActionGrant(
            item.getResourceServerClientId(),
            resource.id(),
            item.getRequesterSubject(),
            missingActions));
  }

  private List<String> missingGrantedActions(
      AuthorizationSyncItem item, CreatedAuthorizationResource resource) {
    List<String> grantedActions =
        authorization
            .findResourceActionGrants(
                new ResourceGrantQuery(
                    item.getResourceServerClientId(),
                    resource.id(),
                    item.getRequesterSubject(),
                    true))
            .stream()
            .filter(GrantedResourceAction::granted)
            .map(GrantedResourceAction::action)
            .toList();
    return item.actionList().stream().filter(action -> !grantedActions.contains(action)).toList();
  }

  private void assignAuthorities(AuthorizationSyncItem item) {
    authorization.ensureClientRolesAssigned(
        new AuthorityGrant(
            item.getResourceServerClientId(), item.getRequesterSubject(), item.actionList()));
  }
}
