package io.github.lutzseverino.cardo.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

class ApiClientErrorsTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void parsesApiErrorsWithJackson3() {
    MockClientHttpResponse response =
        new MockClientHttpResponse(
            """
            {"error":{"code":"user_not_found","message":"User not found.","details":{}}}
            """
                .getBytes(StandardCharsets.UTF_8),
            HttpStatus.NOT_FOUND);

    ApiException exception =
        ApiClientErrors.from(response, json, "identity_client_error", "Identity request failed.");

    assertThat(exception.status()).isEqualTo(404);
    assertThat(exception.code()).isEqualTo("user_not_found");
    assertThat(exception).hasMessage("User not found.");
  }
}
