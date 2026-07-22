package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTParser;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class ReferenceKeycloakMaterializerTest {

  @Test
  void materializesAnIdempotentLeastPrivilegeContractWithExactAudiences() throws Exception {
    try (GenericContainer<?> keycloak =
        new GenericContainer<>(
                DockerImageName.parse(System.getProperty("cardo.test.keycloak.image")))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ReferenceKeycloakMaterializer.ADMIN_USERNAME)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ReferenceKeycloakMaterializer.ADMIN_PASSWORD)
            .withCommand("start-dev", "--http-enabled=true", "--hostname-strict=false")
            .withExposedPorts(8080)
            .waitingFor(
                Wait.forHttp("/realms/master")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)))) {
      keycloak.start();
      ReferenceKeycloakMaterializer materializer =
          new ReferenceKeycloakMaterializer(
              "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080));

      ReferenceKeycloakMaterializer.Snapshot first =
          materializer.materialize("https://reference.test/identity/session/callback");
      ReferenceKeycloakMaterializer.Snapshot second =
          materializer.materialize("https://reference.test/identity/session/callback");

      assertThat(second).isEqualTo(first);
      assertThat(second.clientCounts()).allSatisfy((ignored, count) -> assertThat(count).isOne());
      assertThat(second.mapperCounts()).allSatisfy((ignored, count) -> assertThat(count).isOne());
      assertThat(second.scopeCounts()).allSatisfy((ignored, count) -> assertThat(count).isOne());
      assertThat(second.resourceCounts()).allSatisfy((ignored, count) -> assertThat(count).isOne());
      assertThat(second.policyCounts()).isNotEmpty();
      assertThat(second.policyCounts()).allSatisfy((ignored, count) -> assertThat(count).isOne());

      assertToken(
          materializer.clientToken("cardo-invite", "identity"),
          "cardo-invite",
          "identity",
          "identity",
          "user:provision");
      assertToken(
          materializer.clientToken(ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "cardo-invite"),
          ReferenceContract.PRODUCT_OUTBOUND_CLIENT,
          "cardo-invite",
          "cardo-invite",
          "product-service");
      assertToken(
          materializer.clientToken(ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "billing"),
          ReferenceContract.PRODUCT_OUTBOUND_CLIENT,
          "billing",
          "billing",
          "entitlement:read");
      String runtime = materializer.clientToken("cardo-identity", null);
      String inviteRuntime = materializer.clientToken("cardo-invite", null);
      String catalog = materializer.clientToken("identity", null);
      String product = materializer.clientToken(ReferenceContract.PRODUCT_CLIENT, null);
      String outbound =
          materializer.clientToken(ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "cardo-invite");
      assertCatalogToken(catalog, "identity");
      assertCatalogToken(product, ReferenceContract.PRODUCT_CLIENT);
      assertThat(materializer.adminReadStatus(runtime)).isEqualTo(200);
      assertThat(materializer.adminReadStatus(catalog)).isEqualTo(403);
      assertThat(materializer.protectionReadStatus(catalog)).isEqualTo(200);
      assertThat(materializer.protectionReadStatus(product)).isEqualTo(200);
      assertThat(materializer.protectionReadStatus(inviteRuntime)).isEqualTo(403);
      assertThat(materializer.adminReadStatus(product)).isEqualTo(403);
      assertThat(materializer.adminReadStatus(outbound)).isEqualTo(403);
      String identityRoles =
          "/admin/realms/"
              + ReferenceKeycloakMaterializer.REALM
              + "/clients/"
              + materializer.clientUuidFor("identity")
              + "/roles";
      assertThat(
              materializer.definitionWriteStatus(
                  product, identityRoles, Map.of("name", "must-not-exist")))
          .isEqualTo(403);
    }
  }

  @SuppressWarnings("unchecked")
  private void assertToken(
      String token, String authorizedParty, String audience, String resource, String role)
      throws Exception {
    var claims = JWTParser.parse(token).getJWTClaimsSet();
    assertThat(claims.getStringClaim("azp")).isEqualTo(authorizedParty);
    assertThat(claims.getAudience()).containsExactly(audience);
    Map<String, Object> resourceAccess = (Map<String, Object>) claims.getClaim("resource_access");
    assertThat(resourceAccess).containsOnlyKeys(resource);
    Collection<String> roles =
        (Collection<String>) ((Map<String, Object>) resourceAccess.get(resource)).get("roles");
    assertThat(roles).containsExactly(role);
  }

  @SuppressWarnings("unchecked")
  private void assertCatalogToken(String token, String clientId) throws Exception {
    var claims = JWTParser.parse(token).getJWTClaimsSet();
    assertThat(claims.getStringClaim("azp")).isEqualTo(clientId);
    Map<String, Object> resourceAccess = (Map<String, Object>) claims.getClaim("resource_access");
    assertThat(resourceAccess).containsOnlyKeys(clientId);
    Collection<String> roles =
        (Collection<String>) ((Map<String, Object>) resourceAccess.get(clientId)).get("roles");
    assertThat(roles).containsExactly("uma_protection");
  }
}
