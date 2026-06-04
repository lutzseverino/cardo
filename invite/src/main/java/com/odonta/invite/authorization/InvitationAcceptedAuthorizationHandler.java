package com.odonta.invite.authorization;

import static com.odonta.authorization.plan.AuthorizationPlanBuilder.authorizationPlan;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.AuthorizationResourceType;
import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.AuthorizationPlanHandler;
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
    AuthorizationResourceType tenantResourceType = tenantResourceType(event.tenantResourceType());
    AuthorizationResource tenantResource = tenantResource(tenantResourceType, event.tenantId());
    var plan =
        authorizationPlan()
            .provision(tenantResource)
            .grantResourceActions(
                "invitation:access",
                event.authorizationSubject(),
                tenantResource,
                List.of(event.minimumAction()));
    plan.applyTenantResourceProfile(
        "invitation:access-profile",
        event.authorizationSubject(),
        tenantResourceType,
        event.tenantId(),
        accessProfileGrants(event.tenantResourceType(), event.minimumAction(), grants));
    return plan.build();
  }

  private AuthorizationResourceType tenantResourceType(String resourceType) {
    String[] parts = resourceType.split(":", 2);
    return AuthorizationResourceType.of(parts[0], parts[1], List.of("read", "write"));
  }

  private List<AccessProfileGrantProjection> accessProfileGrants(
      String tenantResourceType, String minimumAction, List<AccessProfileGrantProjection> grants) {
    return grants.stream()
        .filter(grant -> grant.getResourceId() == null)
        .filter(
            grant ->
                !tenantResourceType.equals(grant.getResourceType())
                    || !minimumAction.equals(grant.getAction()))
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
