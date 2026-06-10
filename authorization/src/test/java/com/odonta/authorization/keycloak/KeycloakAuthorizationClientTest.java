package com.odonta.authorization.keycloak;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAuthorizationClientTest {

  @Test
  void widensExistingResourceScopes() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "odonta", rest, () -> "protection-token");
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("exactName=true")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"_id":"resource-1","name":"clinic:clinic:123"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.endsWith("/resource_set/resource-1")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {
                  "_id": "resource-1",
                  "name": "clinic:clinic:123",
                  "resource_scopes": [{"name": "read"}]
                }
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.endsWith("/resource_set/resource-1")))
        .andExpect(method(PUT))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "name": "clinic:clinic:123",
                      "type": "clinic:clinic",
                      "resource_scopes": ["read", "write"]
                    }
                    """))
        .andRespond(withNoContent());

    client.ensureResource(resource);

    server.verify();
  }

  @Test
  void preservesExistingResourceScopes() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "odonta", rest, () -> "protection-token");
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read"));
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("exactName=true")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"_id":"resource-1","name":"clinic:clinic:123"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.endsWith("/resource_set/resource-1")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {
                  "_id": "resource-1",
                  "name": "clinic:clinic:123",
                  "resource_scopes": [{"name": "read"}, {"name": "write"}]
                }
                """,
                MediaType.APPLICATION_JSON));

    client.ensureResource(resource);

    server.verify();
  }
}
