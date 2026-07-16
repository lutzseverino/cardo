package io.github.lutzseverino.cardo.invite.authorization;

import io.github.lutzseverino.cardo.authorization.access.AccessGrant;
import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.authorization.grant.GrantPlanBuilder;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResourceType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InvitationGrantPlanner {

  private static final String MINIMUM_ACTION = "read";

  public GrantPlan acceptance(
      UUID tenantId,
      String tenantResourceType,
      String authorizationSubject,
      List<AccessGrant> accessGrants) {
    AuthorizationResourceType tenantType =
        AuthorizationResourceType.parse(tenantResourceType, List.of(MINIMUM_ACTION));
    GrantPlanBuilder plan =
        GrantPlan.builder()
            .grantActions(
                authorizationSubject, tenantType.resource(tenantId), List.of(MINIMUM_ACTION));
    profileActions(tenantResourceType, accessGrants)
        .forEach(
            (typeName, actions) -> {
              AuthorizationResourceType type =
                  AuthorizationResourceType.parse(typeName, List.copyOf(actions));
              plan.grantActions(
                  authorizationSubject, type.resource(tenantId), List.copyOf(actions));
            });
    return plan.build();
  }

  private Map<String, Set<String>> profileActions(
      String tenantResourceType, List<AccessGrant> accessGrants) {
    Map<String, Set<String>> actionsByResourceType = new LinkedHashMap<>();
    accessGrants.stream()
        .filter(grant -> grant.resourceId() == null)
        .filter(grant -> !isMinimumTenantGrant(tenantResourceType, grant))
        .forEach(
            grant ->
                actionsByResourceType
                    .computeIfAbsent(grant.resourceType(), ignored -> new LinkedHashSet<>())
                    .add(grant.action()));
    return actionsByResourceType;
  }

  private boolean isMinimumTenantGrant(String tenantResourceType, AccessGrant grant) {
    return tenantResourceType.equals(grant.resourceType()) && MINIMUM_ACTION.equals(grant.action());
  }
}
