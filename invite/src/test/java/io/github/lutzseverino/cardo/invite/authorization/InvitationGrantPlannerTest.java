package io.github.lutzseverino.cardo.invite.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.access.AccessGrant;
import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvitationGrantPlannerTest {

  private static final UUID CLINIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void grantsBaselineAccessAndSelectedProfileActions() {
    GrantPlan plan =
        new InvitationGrantPlanner()
            .acceptance(
                CLINIC_ID,
                "clinic:clinic",
                "subject-1",
                List.of(
                    grant("clinic:clinic", null, "read"),
                    grant("clinic:clinic", null, "write"),
                    grant("clinic:chairs", null, "read")));

    assertThat(plan.resources()).hasSize(2);
    assertThat(plan.resources().getFirst().actions()).containsExactly("read", "write");
    assertThat(plan.resourceGrants())
        .extracting(GrantPlan.ResourceGrant::resourceName, GrantPlan.ResourceGrant::actions)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "clinic:clinic:11111111-1111-1111-1111-111111111111", List.of("read", "write")),
            org.assertj.core.groups.Tuple.tuple(
                "clinic:chairs:11111111-1111-1111-1111-111111111111", List.of("read")));
  }

  private AccessGrant grant(String resourceType, UUID resourceId, String action) {
    return new AccessGrant(resourceType, resourceId, action);
  }
}
