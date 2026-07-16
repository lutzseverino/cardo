package io.github.lutzseverino.cardo.authorization.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationResourceTypeTest {

  private static final UUID RESOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void parsesCanonicalTypeName() {
    AuthorizationResourceType type =
        AuthorizationResourceType.parse("clinic:chairs", List.of("read", "write"));

    assertThat(type)
        .isEqualTo(AuthorizationResourceType.of("clinic", "chairs", List.of("read", "write")));
  }

  @Test
  void rejectsTypeNameWithoutResource() {
    assertThatThrownBy(() -> AuthorizationResourceType.parse("clinic", List.of("read")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("typeName must use the product:resource format");
  }

  @Test
  void createsResourceForId() {
    AuthorizationResource resource =
        AuthorizationResourceType.of("clinic", "chairs", List.of("read", "write"))
            .resource(RESOURCE_ID);

    assertThat(resource)
        .isEqualTo(
            new AuthorizationResource(
                "clinic",
                "clinic:chairs:11111111-1111-1111-1111-111111111111",
                "clinic:chairs",
                null,
                List.of("read", "write")));
  }
}
