package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class IdentityRuntimeConfigurationTest {

  @Test
  void acceptsLocalDefaultsWithoutProductionCredentials() throws Exception {
    policy(
            new IdentityRuntimeProperties(null, null, null),
            localSession(),
            localKeycloak(),
            new MockEnvironment())
        .afterPropertiesSet();
  }

  @Test
  void acceptsAnIsolatedProductionConfiguration() throws Exception {
    policy(production(), productionSession(), productionKeycloak(), productionEnvironment())
        .afterPropertiesSet();
  }

  @Test
  void rejectsUnsafeProductionConfigurationWithoutDisclosingSecrets() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("spring.datasource.password", "database-secret-value");
    KeycloakProperties keycloak =
        new KeycloakProperties(
            "https://id.example.com",
            "cardo",
            "cardo-identity",
            "",
            "cardo-web",
            URI.create("https://app.example.com/invitations/completed"),
            List.of("identity", "billing"));

    assertThatThrownBy(
            () ->
                policy(production(), productionSession(), keycloak, environment)
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cardo.identity.keycloak.client-secret")
        .hasMessageNotContaining("database-secret-value");
  }

  @Test
  void rejectsNonPositiveWorkflowAndProviderBounds() {
    assertThatThrownBy(
            () -> new IdentityRuntimeProperties(null, Duration.ZERO, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connect-timeout");
    assertThatThrownBy(
            () ->
                new IdentityProviderMutationProperties(
                    Duration.ZERO, Duration.ofSeconds(1), Duration.ofMinutes(1), 3, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dispatch-delay");
  }

  @Test
  void generatedMetadataContainsRuntimeAndProvisionalMutationProperties() throws Exception {
    String metadata =
        new String(
            getClass()
                .getResourceAsStream("/META-INF/spring-configuration-metadata.json")
                .readAllBytes());
    assertThat(metadata)
        .contains("cardo.identity.runtime.connect-timeout")
        .contains("cardo.identity.provider-mutations.max-attempts");
  }

  @Test
  void boundedProviderBuilderStopsAStalledResponse() throws Exception {
    var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/stall",
        exchange -> {
          try {
            Thread.sleep(500);
            exchange.sendResponseHeaders(204, -1);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      IdentityRuntimeProperties bounds =
          new IdentityRuntimeProperties(
              IdentityRuntimeProperties.Mode.LOCAL, Duration.ofMillis(100), Duration.ofMillis(100));
      RestClient client =
          new IdentityRuntimeConfiguration()
              .identityOutboundRestClientBuilder(bounds)
              .baseUrl("http://localhost:" + server.getAddress().getPort())
              .build();
      long started = System.nanoTime();

      assertThatThrownBy(() -> client.get().uri("/stall").retrieve().toBodilessEntity())
          .isInstanceOf(RestClientException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    } finally {
      server.stop(0);
    }
  }

  private org.springframework.beans.factory.InitializingBean policy(
      IdentityRuntimeProperties runtime,
      SessionProperties session,
      KeycloakProperties keycloak,
      MockEnvironment environment) {
    return new IdentityRuntimeConfiguration()
        .identityProductionConfigurationPolicy(runtime, session, keycloak, environment);
  }

  private IdentityRuntimeProperties production() {
    return new IdentityRuntimeProperties(
        IdentityRuntimeProperties.Mode.PRODUCTION, Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  private SessionProperties localSession() {
    return new SessionProperties(
        SessionProperties.Mode.LOCAL,
        "cardo.session",
        "cardo.refresh",
        "cardo.csrf",
        "/api/v1/identity/sessions/current",
        false);
  }

  private SessionProperties productionSession() {
    return new SessionProperties(
        SessionProperties.Mode.PRODUCTION,
        "__Host-cardo.session",
        "__Secure-cardo.refresh",
        "__Host-cardo.csrf",
        "/api/v1/identity/sessions/current",
        true);
  }

  private KeycloakProperties localKeycloak() {
    return new KeycloakProperties(
        "http://localhost:8080",
        "cardo",
        "cardo-identity",
        "",
        "cardo-web",
        URI.create("http://localhost:3000/invitations/completed"),
        List.of("identity"));
  }

  private KeycloakProperties productionKeycloak() {
    return new KeycloakProperties(
        "https://id.example.com",
        "cardo",
        "cardo-identity",
        "identity-secret",
        "cardo-web",
        URI.create("https://app.example.com/invitations/completed"),
        List.of("cardo-identity", "identity", "billing"));
  }

  private MockEnvironment productionEnvironment() {
    return new MockEnvironment()
        .withProperty(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "https://id.example.com/realms/cardo")
        .withProperty(
            "spring.datasource.url", "jdbc:postgresql://db.example.com:5432/cardo_identity")
        .withProperty("spring.datasource.username", "cardo_identity_app")
        .withProperty("spring.datasource.password", "identity-db-secret");
  }
}
