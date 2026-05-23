package com.odonta.invite.authorization;

import static com.odonta.authorization.plan.AuthorizationPlanBuilder.authorizationPlan;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.AuthorizationResourceType;
import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.AuthorizationPlanHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class InvitationAcceptedAuthorizationHandler
    implements AuthorizationPlanHandler<InvitationAccepted> {

  private final AccessProfileService accessProfiles;

  InvitationAcceptedAuthorizationHandler(AccessProfileService accessProfiles) {
    this.accessProfiles = accessProfiles;
  }

  @Override
  public Class<InvitationAccepted> eventType() {
    return InvitationAccepted.class;
  }

  @Override
  public AuthorizationPlan plan(InvitationAccepted event) {
    List<AccessProfileGrantProjection> grants =
        accessProfiles.profileGrants(event.accessProfileId());
    AuthorizationResourceType tenantResourceType =
        tenantResourceType(event.tenantResourceType(), grants);
    AuthorizationResource tenantResource = tenantResource(tenantResourceType, event.tenantId());
    var plan =
        authorizationPlan()
            .provision(tenantResource)
            .grantResourceActions(
                "invitation:access",
                event.authorizationSubject(),
                tenantResource,
                List.of(event.minimumAction()));
    List<String> accessProfileActions =
        accessProfileActions(event.tenantResourceType(), event.minimumAction(), grants);
    if (!accessProfileActions.isEmpty()) {
      plan.grantResourceActions(
          "invitation:access-profile",
          event.authorizationSubject(),
          tenantResource,
          accessProfileActions);
    }
    return plan.build();
  }

  private AuthorizationResourceType tenantResourceType(
      String resourceType, List<AccessProfileGrantProjection> grants) {
    String[] parts = resourceType.split(":", 2);
    List<String> actions = new ArrayList<>();
    actions.add("read");
    grants.stream()
        .filter(grant -> resourceType.equals(grant.getResourceType()))
        .map(AccessProfileGrantProjection::getAction)
        .filter(action -> !actions.contains(action))
        .forEach(actions::add);
    return AuthorizationResourceType.of(parts[0], parts[1], actions);
  }

  private List<String> accessProfileActions(
      String tenantResourceType, String minimumAction, List<AccessProfileGrantProjection> grants) {
    return grants.stream()
        .filter(
            grant ->
                tenantResourceType.equals(grant.getResourceType())
                    && grant.getResourceId() == null
                    && !minimumAction.equals(grant.getAction()))
        .map(AccessProfileGrantProjection::getAction)
        .toList();
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
}
