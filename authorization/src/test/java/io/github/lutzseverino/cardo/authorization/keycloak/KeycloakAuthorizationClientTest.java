package io.github.lutzseverino.cardo.authorization.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.lutzseverino.cardo.authorization.grant.ClientRoleAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleRevocation;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResourceAction;
import io.github.lutzseverino.cardo.authorization.grant.ResourceGrantQuery;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAuthorizationClientTest {

  @Test
  void continuesEnsuringResourceAfterConcurrentCreation() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "protection-token");
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("exactName=true")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.endsWith("/authz/protection/resource_set")))
        .andExpect(method(POST))
        .andRespond(withStatus(HttpStatus.CONFLICT));
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

    assertThat(client.ensureResource(resource).id()).isEqualTo("resource-1");

    server.verify();
  }

  @Test
  void assignsAnExistingClientRole() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "admin-token");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("clientId=identity")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"client-uuid","clientId":"identity"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(org.hamcrest.Matchers.endsWith("/clients/client-uuid/roles/profile%3Aread")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {"id":"role-id","name":"profile:read"}
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.endsWith(
                    "/users/subject-1/role-mappings/clients/client-uuid")))
        .andExpect(method(POST))
        .andExpect(
            content()
                .json(
                    """
                    [{"id":"role-id","name":"profile:read"}]
                    """))
        .andRespond(withNoContent());

    client.ensureClientRolesAssigned(
        new ClientRoleAssignment("identity", "subject-1", List.of("profile:read")));

    server.verify();
  }

  @Test
  void rejectsAMissingClientRoleWithoutTryingToCreateIt() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "admin-token");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("clientId=identity")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"client-uuid","clientId":"identity"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(org.hamcrest.Matchers.endsWith("/clients/client-uuid/roles/profile%3Aread")))
        .andExpect(method(GET))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(
            () ->
                client.ensureClientRolesAssigned(
                    new ClientRoleAssignment("identity", "subject-1", List.of("profile:read"))))
        .isInstanceOf(KeycloakAuthorizationException.class)
        .hasMessageContaining("Required Keycloak client role is missing")
        .hasMessageContaining("profile:read");

    server.verify();
  }

  @Test
  void removesExistingClientRoles() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "admin-token");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("clientId=identity")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"client-uuid","clientId":"identity"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.endsWith(
                    "/users/subject-1/role-mappings/clients/client-uuid")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [
                  {"id":"role-id","name":"profile:read"},
                  {"id":"other-role-id","name":"profile:write"}
                ]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.endsWith(
                    "/users/subject-1/role-mappings/clients/client-uuid")))
        .andExpect(method(DELETE))
        .andExpect(
            content()
                .json(
                    """
                    [{"id":"role-id","name":"profile:read"}]
                    """))
        .andRespond(withNoContent());

    client.removeClientRoles(
        new ClientRoleRevocation("identity", "subject-1", List.of("profile:read")));

    server.verify();
  }

  @Test
  void ignoresClientRolesThatAreAlreadyAbsent() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "admin-token");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("clientId=identity")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"client-uuid","clientId":"identity"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.endsWith(
                    "/users/subject-1/role-mappings/clients/client-uuid")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    client.removeClientRoles(
        new ClientRoleRevocation("identity", "subject-1", List.of("profile:read")));

    server.verify();
  }

  @Test
  void widensExistingResourceScopes() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "protection-token");
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
            "https://keycloak.example", "cardo", rest, () -> "protection-token");
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

  @Test
  void findsResourceGrantsByCanonicalResourceName() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "protection-token");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("name=clinic:clinic:123")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{"_id":"resource-1","name":"clinic:clinic:123"}]
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("resourceId=resource-1")))
        .andExpect(requestTo(org.hamcrest.Matchers.containsString("requester=subject-1")))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                [{
                  "id": "ticket-1",
                  "resource": "resource-1",
                  "resourceName": "clinic:clinic:123",
                  "requester": "subject-1",
                  "granted": true,
                  "scopeName": "read"
                }]
                """,
                MediaType.APPLICATION_JSON));

    List<GrantedResourceAction> grants =
        client.findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:clinic:123", "subject-1"));

    assertThat(grants)
        .containsExactly(
            new GrantedResourceAction(
                "ticket-1", "resource-1", "clinic:clinic:123", "subject-1", "read", true));
    server.verify();
  }

  @Test
  void returnsNoGrantsWhenCanonicalResourceDoesNotExist() {
    RestClient.Builder rest = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    KeycloakAuthorizationClient client =
        new KeycloakAuthorizationClient(
            "https://keycloak.example", "cardo", rest, () -> "protection-token");
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("name=clinic:clinic:123")))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    List<GrantedResourceAction> grants =
        client.findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:clinic:123"));

    assertThat(grants).isEmpty();
    server.verify();
  }
}
