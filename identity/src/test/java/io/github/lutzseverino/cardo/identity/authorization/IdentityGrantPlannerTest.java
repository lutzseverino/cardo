package io.github.lutzseverino.cardo.identity.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.identity.model.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityGrantPlannerTest {

  @Test
  void provisionsUserAndGrantsProfileAuthorities() {
    User user = new User("subject-1", "user@example.com", "User");
    ReflectionTestUtils.setField(
        user, "id", UUID.fromString("11111111-1111-1111-1111-111111111111"));

    GrantPlan plan = new IdentityGrantPlanner().creation(user);

    assertThat(plan.resources()).hasSize(1);
    assertThat(plan.resourceGrants()).isEmpty();
    assertThat(plan.authorityGrants())
        .containsExactly(
            new GrantPlan.AuthorityGrant(
                "identity", "subject-1", java.util.List.of("profile:read", "profile:write")));
  }
}
