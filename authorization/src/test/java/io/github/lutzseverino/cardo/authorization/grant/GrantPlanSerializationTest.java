package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GrantPlanSerializationTest {

  @Test
  void roundTripsDurablePublicationPayload() throws Exception {
    GrantPlan plan =
        new GrantPlan(
            List.of(
                new AuthorizationResource(
                    "clinic",
                    "clinic:clinic:123",
                    "clinic:clinic",
                    null,
                    List.of("read", "write"))),
            List.of(
                new GrantPlan.ResourceGrant(
                    "clinic", "clinic:clinic:123", "subject-1", List.of("read"))),
            List.of(
                new GrantPlan.AuthorityGrant("identity", "subject-1", List.of("profile:read"))));
    JsonMapper mapper = JsonMapper.builder().build();

    GrantPlan restored = mapper.readValue(mapper.writeValueAsString(plan), GrantPlan.class);

    assertThat(restored).isEqualTo(plan);
  }
}
