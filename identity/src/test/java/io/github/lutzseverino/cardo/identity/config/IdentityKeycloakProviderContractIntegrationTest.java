package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.ClientRoleAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleRevocation;
import io.github.lutzseverino.cardo.authorization.grant.ResourceActionAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ResourceGrantQuery;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationException;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.identity.integration.keycloak.RealKeycloakIdentityProviderExercise;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class IdentityKeycloakProviderContractIntegrationTest {

  @Test
  void validatesAndRepairsARealProviderWithoutGrantingRuntimeDefinitionWrites() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable());
    try (DisposableKeycloakProvisioner keycloak = new DisposableKeycloakProvisioner()) {
      keycloak.start();
      keycloak.provisionContract();
      RestClient.Builder rest = RestClient.builder();
      KeycloakClientCredentialsTokenProvider runtimeTokens = keycloak.runtimeTokens(rest);
      IdentityKeycloakProviderContractValidator validator =
          new IdentityKeycloakProviderContractValidator(
              keycloak.properties(false), runtimeTokens, rest);

      assertThatCode(validator::validate).doesNotThrowAnyException();

      String runtimeToken = runtimeTokens.clientCredentialsToken();
      assertThat(keycloak.mapperDefinitionWriteStatus(runtimeToken)).isEqualTo(403);
      assertThat(keycloak.roleDefinitionWriteStatus(runtimeToken)).isEqualTo(403);
      RealKeycloakIdentityProviderExercise.verify(
          keycloak.properties(false), runtimeTokens, rest.clone());

      String userId = keycloak.createUser();
      KeycloakAuthorizationClient authorization =
          new KeycloakAuthorizationClient(
              keycloak.properties(false).baseUrl(),
              keycloak.properties(false).realm(),
              rest.clone(),
              runtimeTokens::clientCredentialsToken);
      authorization.ensureClientRolesAssigned(
          new ClientRoleAssignment("identity", userId, List.of("profile:read")));
      authorization.removeClientRoles(
          new ClientRoleRevocation("identity", userId, List.of("profile:read")));
      var resource =
          authorization.ensureResource(
              new AuthorizationResource(
                  "contract", "contract:resource:1", "contract:resource", null, List.of("read")));
      assertThat(resource.id()).isNotBlank();
      keycloak.enableUserManagedAccess(runtimeToken, resource.id());
      authorization.grantResourceActions(
          new ResourceActionAssignment("identity", resource.id(), userId, List.of("read")));
      var grants =
          authorization.findResourceActionGrants(
              ResourceGrantQuery.forResourceId("identity", resource.id(), userId));
      assertThat(grants).hasSize(1);
      authorization.revokeResourceActionGrant(grants.getFirst().id());

      keycloak.deleteRole("profile:read");
      assertThatThrownBy(
              () ->
                  authorization.ensureClientRolesAssigned(
                      new ClientRoleAssignment("identity", userId, List.of("profile:read"))))
          .isInstanceOf(KeycloakAuthorizationException.class)
          .hasMessageContaining("Required Keycloak client role is missing");
      assertThat(keycloak.roleExists("profile:read")).isFalse();

      keycloak.deleteMapper("billing");
      assertThatThrownBy(validator::validate)
          .hasMessageContaining("client billing mapper cardo_user_id expected one definition")
          .hasMessageContaining("Identity role profile:read lookup returned HTTP 404");

      IdentityKeycloakLegacyStartupRepair runtimeRepair =
          new IdentityKeycloakLegacyStartupRepair(
              keycloak.properties(true), runtimeTokens, rest.clone());
      runtimeRepair.repair();
      assertThatThrownBy(validator::validate)
          .hasMessageContaining("client billing mapper cardo_user_id expected one definition")
          .hasMessageContaining("Identity role profile:read lookup returned HTTP 404");

      KeycloakClientCredentialsTokenProvider privileged =
          mock(KeycloakClientCredentialsTokenProvider.class);
      when(privileged.clientCredentialsToken()).thenReturn(keycloak.privilegedToken());
      IdentityKeycloakLegacyStartupRepair privilegedRepair =
          new IdentityKeycloakLegacyStartupRepair(
              keycloak.properties(true), privileged, rest.clone());
      privilegedRepair.repair();

      assertThatCode(validator::validate).doesNotThrowAnyException();
      assertThatCode(privilegedRepair::repair).doesNotThrowAnyException();
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }
  }
}
