package com.odonta.invite.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.GrantAuthorizationResourceActions;
import com.odonta.authorization.sync.ProvisionAuthorizationResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationAcceptedAuthorizationHandlerTest {

  private static final UUID CLINIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ACCESS_PROFILE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock private AccessProfileService accessProfiles;

  @Test
  void grantsBaselineAccessAndSelectedAccessProfileActions() {
    when(accessProfiles.profileGrants(ACCESS_PROFILE_ID))
        .thenReturn(
            List.of(
                grant("clinic:clinic", null, "read"),
                grant("clinic:clinic", null, "write"),
                grant("clinic:chairs", null, "read")));

    AuthorizationPlan plan =
        new InvitationAcceptedAuthorizationHandler(accessProfiles)
            .plan(
                new InvitationAccepted(CLINIC_ID, "clinic:clinic", ACCESS_PROFILE_ID, "subject-1"));

    assertThat(plan.operations()).hasSize(5);
    assertThat(plan.operations().getFirst()).isInstanceOf(ProvisionAuthorizationResource.class);

    GrantAuthorizationResourceActions baseline =
        (GrantAuthorizationResourceActions) plan.operations().get(1);
    assertThat(baseline.uniqueKey())
        .isEqualTo(
            "grant:invitation:access:subject-1:clinic:"
                + "clinic:clinic:11111111-1111-1111-1111-111111111111:read");
    assertThat(baseline.actions()).containsExactly("read");

    GrantAuthorizationResourceActions selectedProfile =
        (GrantAuthorizationResourceActions) plan.operations().get(2);
    assertThat(selectedProfile.uniqueKey())
        .isEqualTo(
            "grant:invitation:access-profile:subject-1:clinic:"
                + "clinic:clinic:11111111-1111-1111-1111-111111111111:write");
    assertThat(selectedProfile.actions()).containsExactly("write");

    assertThat(plan.operations().get(3)).isInstanceOf(ProvisionAuthorizationResource.class);
    GrantAuthorizationResourceActions siblingProfile =
        (GrantAuthorizationResourceActions) plan.operations().get(4);
    assertThat(siblingProfile.uniqueKey())
        .isEqualTo(
            "grant:invitation:access-profile:subject-1:clinic:"
                + "clinic:chairs:11111111-1111-1111-1111-111111111111:read");
    assertThat(siblingProfile.actions()).containsExactly("read");
  }

  private static AccessProfileGrantProjection grant(
      String resourceType, UUID resourceId, String action) {
    return new TestAccessProfileGrant(resourceType, resourceId, action);
  }

  private record TestAccessProfileGrant(String resourceType, UUID resourceId, String action)
      implements AccessProfileGrantProjection {

    @Override
    public UUID getId() {
      return UUID.randomUUID();
    }

    @Override
    public UUID getProfileId() {
      return ACCESS_PROFILE_ID;
    }

    @Override
    public String getResourceType() {
      return resourceType;
    }

    @Override
    public UUID getResourceId() {
      return resourceId;
    }

    @Override
    public String getAction() {
      return action;
    }
  }
}
