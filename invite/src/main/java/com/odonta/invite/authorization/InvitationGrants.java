package com.odonta.invite.authorization;

import com.odonta.authorization.access.AccessGrant;
import com.odonta.authorization.grant.GrantPlan;
import com.odonta.authorization.grant.GrantPlanBuilder;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.AuthorizationResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InvitationGrants {

  private static final String MINIMUM_ACTION = "read";

  public GrantPlan acceptance(
      UUID tenantId,
      String tenantResourceType,
      String authorizationSubject,
      List<AccessGrant> accessGrants) {
    AuthorizationResourceType tenantType =
        resourceType(tenantResourceType, List.of(MINIMUM_ACTION));
    GrantPlanBuilder plan =
        GrantPlan.builder()
            .grantActions(
                authorizationSubject, resource(tenantType, tenantId), List.of(MINIMUM_ACTION));
    profileActions(tenantResourceType, accessGrants)
        .forEach(
            (typeName, actions) -> {
              AuthorizationResourceType type = resourceType(typeName, actions);
              plan.grantActions(authorizationSubject, resource(type, tenantId), actions);
            });
    return plan.build();
  }

  private Map<String, List<String>> profileActions(
      String tenantResourceType, List<AccessGrant> accessGrants) {
    Map<String, List<String>> actionsByResourceType = new LinkedHashMap<>();
    accessGrants.stream()
        .filter(grant -> grant.resourceId() == null)
        .filter(grant -> !isMinimumTenantGrant(tenantResourceType, grant))
        .forEach(grant -> addAction(actionsByResourceType, grant.resourceType(), grant.action()));
    return actionsByResourceType;
  }

  private boolean isMinimumTenantGrant(String tenantResourceType, AccessGrant grant) {
    return tenantResourceType.equals(grant.resourceType()) && MINIMUM_ACTION.equals(grant.action());
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

  private AuthorizationResource resource(AuthorizationResourceType type, UUID tenantId) {
    return new AuthorizationResource(
        type.product(), type.resourceName(tenantId), type.typeName(), null, type.actions());
  }
}
