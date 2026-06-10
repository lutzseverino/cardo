package com.odonta.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrantPlanBuilderTest {

  @Test
  void provisionsResourcesBeforeGrantingAccess() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));

    GrantPlan plan = GrantPlan.builder().grantFullAccess("subject-1", resource).build();

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
        GrantPlan.builder()
            .grantActions("subject-1", resource, List.of("read"))
            .grantActions("subject-2", resource, List.of("write"))
            .build();

    assertThat(plan.resources()).containsExactly(resource);
    assertThat(plan.resourceGrants()).hasSize(2);
  }

  @Test
  void grantsAuthorities() {
    GrantPlan plan =
        GrantPlan.builder()
            .grantAuthorities("subject-1", "identity", List.of("profile:read", "profile:write"))
            .build();

    assertThat(plan.authorityGrants())
        .containsExactly(
            new GrantPlan.AuthorityGrant(
                "identity", "subject-1", List.of("profile:read", "profile:write")));
  }

  @Test
  void mergesResourceCapabilitiesAndRepeatedGrants() {
    AuthorizationResource readable =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read"));
    AuthorizationResource writable =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("write"));

    GrantPlan plan =
        GrantPlan.builder()
            .grantActions("subject-1", readable, List.of("read"))
            .grantActions("subject-1", writable, List.of("write", "read"))
            .build();

    assertThat(plan.resources())
        .singleElement()
        .extracting(AuthorizationResource::actions)
        .isEqualTo(List.of("read", "write"));
    assertThat(plan.resourceGrants())
        .singleElement()
        .extracting(GrantPlan.ResourceGrant::actions)
        .isEqualTo(List.of("read", "write"));
  }

  @Test
  void rejectsConflictingResourceDefinitions() {
    AuthorizationResource owned =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", "owner-1", List.of("read"));
    AuthorizationResource conflicting =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", "owner-2", List.of("read"));

    assertThatThrownBy(() -> GrantPlan.builder().provision(owned).provision(conflicting))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Conflicting authorization resource definition: clinic:clinic:123");
  }
}
