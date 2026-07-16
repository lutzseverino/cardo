package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RevocationPlanSerializationTest {

  @Test
  void roundTripsDurablePublicationPayload() throws Exception {
    RevocationPlan plan =
        new RevocationPlan(
            List.of(
                new RevocationPlan.ResourceRevocation(
                    "clinic", "clinic:clinic:123", "subject-1", List.of("read", "write"))),
            List.of(
                new RevocationPlan.AuthorityRevocation(
                    "identity", "subject-1", List.of("profile:read"))));
    JsonMapper mapper = JsonMapper.builder().build();

    RevocationPlan restored =
        mapper.readValue(mapper.writeValueAsString(plan), RevocationPlan.class);

    assertThat(restored).isEqualTo(plan);
  }
}
