package io.github.lutzseverino.cardo.invite.authorization;

import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.authorization.grant.GrantPlanBuilder;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResourceType;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantInput;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InvitationGrantPlanner {

  public GrantPlan acceptance(
      UUID tenantId, String authorizationSubject, List<InvitationGrantInput> grants) {
    GrantPlanBuilder plan = GrantPlan.builder();
    actionsByResourceType(grants)
        .forEach(
            (typeName, actions) -> {
              AuthorizationResourceType type =
                  AuthorizationResourceType.parse(typeName, List.copyOf(actions));
              plan.grantActions(
                  authorizationSubject, type.resource(tenantId), List.copyOf(actions));
            });
    return plan.build();
  }

  private Map<String, Set<String>> actionsByResourceType(List<InvitationGrantInput> grants) {
    Map<String, Set<String>> actionsByResourceType = new LinkedHashMap<>();
    grants.stream()
        .forEach(
            grant ->
                actionsByResourceType
                    .computeIfAbsent(grant.resourceType(), ignored -> new LinkedHashSet<>())
                    .add(grant.action()));
    return actionsByResourceType;
  }
}
