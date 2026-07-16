package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevocationPlanBuilderTest {

  @Test
  void mergesRepeatedResourceRevocations() {
    AuthorizationResource resource = resource();

    RevocationPlan plan =
        RevocationPlan.builder()
            .revokeActions("subject-1", resource, List.of("read"))
            .revokeActions("subject-1", resource, List.of("write", "read"))
            .build();

    assertThat(plan.resourceRevocations())
        .containsExactly(
            new RevocationPlan.ResourceRevocation(
                "clinic", "clinic:clinic:123", "subject-1", List.of("read", "write")));
  }

  @Test
  void mergesRepeatedAuthorityRevocations() {
    RevocationPlan plan =
        RevocationPlan.builder()
            .revokeAuthorities("subject-1", "identity", List.of("profile:read"))
            .revokeAuthorities("subject-1", "identity", List.of("profile:write", "profile:read"))
            .build();

    assertThat(plan.authorityRevocations())
        .containsExactly(
            new RevocationPlan.AuthorityRevocation(
                "identity", "subject-1", List.of("profile:read", "profile:write")));
  }

  private AuthorizationResource resource() {
    return new AuthorizationResource(
        "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));
  }
}
