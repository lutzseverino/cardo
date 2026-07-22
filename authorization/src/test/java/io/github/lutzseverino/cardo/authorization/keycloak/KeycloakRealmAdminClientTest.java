package io.github.lutzseverino.cardo.authorization.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakRealmAdminClientTest {

  @Test
  void preservesWritableUserProfileWhenBindingAnAttribute() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakRealmAdminClient client =
        new KeycloakRealmAdminClient(
            "https://keycloak.example", "cardo", rest, () -> "runtime-token");
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {
                  "id": "subject-1",
                  "username": "owner@example.test",
                  "email": "owner@example.test",
                  "firstName": "Owner",
                  "lastName": "Reference",
                  "enabled": true,
                  "emailVerified": true,
                  "attributes": {"provisioning_correlation": ["marker"]},
                  "access": {"manage": true}
                }
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://keycloak.example/admin/realms/cardo/users/subject-1"))
        .andExpect(method(PUT))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "username": "owner@example.test",
                      "email": "owner@example.test",
                      "firstName": "Owner",
                      "lastName": "Reference",
                      "attributes": {
                        "provisioning_correlation": ["marker"],
                        "cardo_user_id": ["11111111-1111-1111-1111-111111111111"]
                      }
                    }
                    """,
                    JsonCompareMode.STRICT))
        .andRespond(withNoContent());

    client.bindUserAttribute("subject-1", "cardo_user_id", "11111111-1111-1111-1111-111111111111");

    server.verify();
  }

  @Test
  void selectsOnlyTheUniqueExactClientMatch() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakRealmAdminClient client =
        new KeycloakRealmAdminClient(
            "https://keycloak.example", "cardo", rest, () -> "runtime-token");
    server
        .expect(requestTo(Matchers.containsString("clientId=identity")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [
                  {"id":"partial","clientId":"identity-legacy"},
                  {"id":"exact","clientId":"identity"}
                ]
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.clientUuid("identity")).isEqualTo("exact");

    server.verify();
  }

  @Test
  void rejectsAmbiguousExactClients() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakRealmAdminClient client =
        new KeycloakRealmAdminClient(
            "https://keycloak.example", "cardo", rest, () -> "runtime-token");
    server
        .expect(requestTo(Matchers.containsString("clientId=identity")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [
                  {"id":"first","clientId":"identity"},
                  {"id":"second","clientId":"identity"}
                ]
                """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.clientUuid("identity"))
        .isInstanceOf(KeycloakAuthorizationException.class)
        .hasMessage("Expected exactly one Keycloak client: identity")
        .hasMessageNotContaining("runtime-token");

    server.verify();
  }
}
