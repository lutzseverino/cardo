package com.odonta.authorization.grant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrantPlanTest {

  @Test
  void rejectsGrantWithoutResourceDefinition() {
    GrantPlan.ResourceGrant grant =
        new GrantPlan.ResourceGrant("clinic", "clinic:clinic:123", "subject-1", List.of("read"));

    assertThatThrownBy(() -> new GrantPlan(List.of(), List.of(grant), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Resource grant has no resource definition: clinic:clinic:123");
  }

  @Test
  void rejectsGrantForUndefinedResourceAction() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read"));
    GrantPlan.ResourceGrant grant =
        new GrantPlan.ResourceGrant("clinic", "clinic:clinic:123", "subject-1", List.of("write"));

    assertThatThrownBy(() -> new GrantPlan(List.of(resource), List.of(grant), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Resource grant contains undefined actions: clinic:clinic:123");
  }
}
