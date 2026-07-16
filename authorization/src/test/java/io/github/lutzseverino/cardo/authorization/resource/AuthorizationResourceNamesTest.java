package io.github.lutzseverino.cardo.authorization.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.authorization.spring.AuthorizationAuthorityNames;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationResourceNamesTest {

  @Test
  void buildsSpecificResourceName() {
    UUID id = UUID.fromString("7f7af229-729c-45ec-8f45-c5ca6d8b1967");

    assertThat(AuthorizationResourceNames.resource("clinic", "patient", id))
        .isEqualTo("clinic:patient:7f7af229-729c-45ec-8f45-c5ca6d8b1967");
  }

  @Test
  void buildsAllResourcesName() {
    assertThat(AuthorizationResourceNames.all("clinic", "patient")).isEqualTo("clinic:patient:*");
  }

  @Test
  void buildsResourceActionAuthority() {
    assertThat(AuthorizationAuthorityNames.resourceAction("clinic:patient:abc", "read"))
        .isEqualTo("clinic:patient:abc:read");
  }
}
