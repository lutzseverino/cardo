package com.odonta.authorization.grant;

import static com.odonta.authorization.grant.GrantPlanBuilder.grantPlan;
import static org.assertj.core.api.Assertions.assertThat;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrantPlanBuilderTest {

  @Test
  void provisionsResourcesBeforeGrantingAccess() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));

    GrantPlan plan = grantPlan().grantFullAccess("subject-1", resource).build();

    assertThat(plan.resources()).containsExactly(resource);
    assertThat(plan.resourceGrants())
        .containsExactly(
            new GrantPlan.ResourceGrant(
                "clinic", "clinic:clinic:123", "subject-1", List.of("read", "write")));
  }

  @Test
  void provisionsEachResourceOnce() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));

    GrantPlan plan =
        grantPlan()
            .grantActions("subject-1", resource, List.of("read"))
            .grantActions("subject-2", resource, List.of("write"))
            .build();

    assertThat(plan.resources()).containsExactly(resource);
    assertThat(plan.resourceGrants()).hasSize(2);
  }

  @Test
  void grantsAuthorities() {
    GrantPlan plan =
        grantPlan()
            .grantAuthorities("subject-1", "identity", List.of("profile:read", "profile:write"))
            .build();

    assertThat(plan.authorityGrants())
        .containsExactly(
            new GrantPlan.AuthorityGrant(
                "identity", "subject-1", List.of("profile:read", "profile:write")));
  }
}
