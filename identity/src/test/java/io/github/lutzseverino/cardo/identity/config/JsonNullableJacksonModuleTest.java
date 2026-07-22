package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lutzseverino.cardo.identity.api.model.UpdateUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.github.lutzseverino.cardo.identity.api.model.UserStatus;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonNullableJacksonModuleTest {

  private final JsonMapper mapper = mapper();

  @Test
  void serializesPresentValuesAsTheirWireTypes() throws Exception {
    UserResponse response =
        response().name("Invited").avatarUrl(URI.create("https://example.test/a"));

    JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

    assertThat(json.get("name").asString()).isEqualTo("Invited");
    assertThat(json.get("avatarUrl").asString()).isEqualTo("https://example.test/a");
    assertThat(json.get("name").isObject()).isFalse();
    assertThat(json.get("avatarUrl").isObject()).isFalse();
  }

  @Test
  void serializesPresentNullAndOmitsUndefinedValues() throws Exception {
    UserResponse response = response().name(null);

    JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

    assertThat(json.has("name")).isTrue();
    assertThat(json.get("name").isNull()).isTrue();
    assertThat(json.has("avatarUrl")).isFalse();
  }

  @Test
  void distinguishesMissingExplicitNullAndTypedPatchValues() throws Exception {
    UpdateUserRequest missing = mapper.readValue("{}", UpdateUserRequest.class);
    UpdateUserRequest explicitNull =
        mapper.readValue("{\"avatarUrl\":null}", UpdateUserRequest.class);
    UpdateUserRequest typed =
        mapper.readValue(
            "{\"avatarUrl\":\"https://example.test/avatar.png\"}", UpdateUserRequest.class);

    assertThat(missing.getAvatarUrl().isUndefined()).isTrue();
    assertThat(explicitNull.getAvatarUrl().isPresent()).isTrue();
    assertThat(explicitNull.getAvatarUrl().orElse(URI.create("https://unexpected.test"))).isNull();
    assertThat(typed.getAvatarUrl().orElseThrow())
        .isEqualTo(URI.create("https://example.test/avatar.png"));
  }

  private static UserResponse response() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-22T12:00:00Z");
    return new UserResponse()
        .id(UUID.fromString("d27b3737-ab1c-40f4-88cc-0688361bd28d"))
        .authorizationSubject("subject-1")
        .email("invited@example.test")
        .status(UserStatus.INVITED)
        .emailVerified(false)
        .createdAt(now)
        .updatedAt(now);
  }

  private static JsonMapper mapper() {
    return JsonMapper.builder()
        .addModule(new OpenApiConfiguration().jsonNullableRuntimeSupport())
        .build();
  }
}
