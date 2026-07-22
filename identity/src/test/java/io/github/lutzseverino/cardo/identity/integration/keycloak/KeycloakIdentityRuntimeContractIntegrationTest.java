package io.github.lutzseverino.cardo.identity.integration.keycloak;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class KeycloakIdentityRuntimeContractIntegrationTest {

  @Test
  void validatesAndRepairsARealProviderWithoutGrantingRuntimeDefinitionWrites() {
    try (DisposableKeycloakProvisioner keycloak = new DisposableKeycloakProvisioner()) {
      keycloak.start();
      keycloak.bootstrapRealm();
      DisposableKeycloakProvisioner.ContractSnapshot first = keycloak.materializeContract();
      DisposableKeycloakProvisioner.ContractSnapshot second = keycloak.materializeContract();
      assertThat(second).isEqualTo(first);
      assertThat(second.clientCounts())
          .containsEntry("cardo-identity", 1)
          .containsEntry("identity", 1)
          .containsEntry("setup", 1)
          .containsEntry("billing", 1);
      assertThat(second.mapperCounts())
          .containsEntry("cardo-identity", 1)
          .containsEntry("identity", 1)
          .containsEntry("billing", 1);
      assertThat(second.identityRoles())
          .containsExactlyInAnyOrder("profile:read", "profile:write", "user:provision");
      assertThat(second.realmManagementGrants())
          .containsExactlyInAnyOrder("manage-users", "view-clients");
      assertThat(second.runtimeClientGrants()).containsExactly("uma_protection");
      RestClient.Builder rest = RestClient.builder();
      KeycloakClientCredentialsTokenProvider runtimeTokens = keycloak.runtimeTokens(rest);
      KeycloakLegacyStartupRepair repair =
          new KeycloakLegacyStartupRepair(keycloak.properties(false), runtimeTokens, rest.clone());
      KeycloakIdentityRuntimeContract validator =
          new KeycloakIdentityRuntimeContract(
              keycloak.properties(false), runtimeTokens, repair, rest);

      String runtimeToken = runtimeTokens.clientCredentialsToken();
      assertThat(keycloak.realmManagementRoles(runtimeToken))
          .containsExactlyInAnyOrder("manage-users", "query-clients", "view-clients");
      assertThat(keycloak.runtimeClientRoles(runtimeToken)).containsExactly("uma_protection");
      assertThatCode(validator::validate).doesNotThrowAnyException();
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

      KeycloakLegacyStartupRepair runtimeRepair =
          new KeycloakLegacyStartupRepair(keycloak.properties(true), runtimeTokens, rest.clone());
      runtimeRepair.repair();
      assertThatThrownBy(validator::validate)
          .hasMessageContaining("client billing mapper cardo_user_id expected one definition")
          .hasMessageContaining("Identity role profile:read lookup returned HTTP 404");

      KeycloakClientCredentialsTokenProvider privileged =
          mock(KeycloakClientCredentialsTokenProvider.class);
      when(privileged.clientCredentialsToken()).thenReturn(keycloak.privilegedToken());
      KeycloakLegacyStartupRepair privilegedRepair =
          new KeycloakLegacyStartupRepair(keycloak.properties(true), privileged, rest.clone());
      privilegedRepair.repair();

      assertThatCode(validator::validate).doesNotThrowAnyException();
      assertThatCode(privilegedRepair::repair).doesNotThrowAnyException();
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }
  }
}
