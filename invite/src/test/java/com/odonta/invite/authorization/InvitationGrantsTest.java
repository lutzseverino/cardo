package com.odonta.invite.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.access.AccessProfileService;
import com.odonta.authorization.grant.GrantPlan;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationGrantsTest {

  private static final UUID CLINIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ACCESS_PROFILE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock private AccessProfileService accessProfiles;

  @Test
  void grantsBaselineAccessAndSelectedProfileActions() {
    when(accessProfiles.profileGrants(ACCESS_PROFILE_ID))
        .thenReturn(
            List.of(
                grant("clinic:clinic", null, "read"),
                grant("clinic:clinic", null, "write"),
                grant("clinic:chairs", null, "read")));

    GrantPlan plan =
        new InvitationGrants(accessProfiles)
            .acceptance(CLINIC_ID, "clinic:clinic", ACCESS_PROFILE_ID, "subject-1");

    assertThat(plan.resources()).hasSize(2);
    assertThat(plan.resourceGrants())
        .extracting(GrantPlan.ResourceGrant::resourceName, GrantPlan.ResourceGrant::actions)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "clinic:clinic:11111111-1111-1111-1111-111111111111", List.of("read")),
            org.assertj.core.groups.Tuple.tuple(
                "clinic:clinic:11111111-1111-1111-1111-111111111111", List.of("write")),
            org.assertj.core.groups.Tuple.tuple(
                "clinic:chairs:11111111-1111-1111-1111-111111111111", List.of("read")));
  }

  private AccessProfileGrantProjection grant(String resourceType, UUID resourceId, String action) {
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
