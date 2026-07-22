package io.github.lutzseverino.cardo.identity.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenSettings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class DisposableKeycloakProvisioner implements AutoCloseable {

  static final String IMAGE =
      "quay.io/keycloak/keycloak@sha256:4883630ef9db14031cde3e60700c9a9a8eaf1b5c24db1589d6a2d43de38ba2a9";

  private static final String REALM = "cardo-contract";
  private static final String ADMIN_USERNAME = "cardo-test-admin";
  private static final String ADMIN_PASSWORD = "cardo-test-password";
  private static final String RUNTIME_CLIENT = "identity";
  private static final String RUNTIME_SECRET = "identity-runtime-secret";

  private final GenericContainer<?> container =
      new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USERNAME)
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
          .withCommand("start-dev", "--http-enabled=true", "--hostname-strict=false")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/master")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  private RestClient rest;
  private ServerSocket smtpListener;
  private Thread smtpThread;

  void start() {
    startSmtpSink();
    Testcontainers.exposeHostPorts(smtpListener.getLocalPort());
    container.start();
    rest = RestClient.builder().baseUrl(baseUrl()).build();
  }

  void provisionContract() {
    String admin = adminToken();
    post(
        admin,
        "/admin/realms",
        Map.of(
            "realm",
            REALM,
            "enabled",
            true,
            "verifyEmail",
            false,
            "loginWithEmailAllowed",
            true,
            "smtpServer",
            Map.of(
                "host",
                "host.testcontainers.internal",
                "port",
                Integer.toString(smtpListener.getLocalPort()),
                "from",
                "cardo@example.test",
                "auth",
                "false",
                "starttls",
                "false")));
    configureUserProfile(admin);
    createClient(admin, RUNTIME_CLIENT, RUNTIME_SECRET, true);
    createClient(admin, "setup", "setup-secret", false);
    createClient(admin, "billing", "billing-secret", false);
    installMapper(admin, RUNTIME_CLIENT);
    installMapper(admin, "billing");
    for (String role : IdentityKeycloakProviderContract.IDENTITY_ROLES) {
      createRole(admin, role);
    }
    createSessionUser(admin);
    grantRuntimeManagementRoles(
        admin,
        List.of("manage-users", "query-users", "view-users", "query-clients", "view-clients"));
  }

  KeycloakProperties properties(boolean legacyMutation) {
    return new KeycloakProperties(
        baseUrl(),
        REALM,
        RUNTIME_CLIENT,
        RUNTIME_SECRET,
        "setup",
        URI.create("https://app.example/invitations/completed"),
        List.of(RUNTIME_CLIENT, "billing"),
        legacyMutation);
  }

  KeycloakClientCredentialsTokenProvider runtimeTokens(RestClient.Builder restBuilder) {
    return new KeycloakClientCredentialsTokenProvider(
        baseUrl(),
        REALM,
        RUNTIME_CLIENT,
        RUNTIME_SECRET,
        restBuilder.clone(),
        new KeycloakClientCredentialsTokenSettings(
            Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO));
  }

  String privilegedToken() {
    return adminToken();
  }

  int mapperDefinitionWriteStatus(String token) {
    try {
      post(
          token,
          "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
          IdentityKeycloakProviderContract.canonicalMapper(),
          REALM,
          clientUuid(adminToken(), "billing"));
      return 204;
    } catch (RestClientResponseException exception) {
      return exception.getStatusCode().value();
    }
  }

  int roleDefinitionWriteStatus(String token) {
    try {
      post(
          token,
          "/admin/realms/{realm}/clients/{clientUuid}/roles",
          Map.of("name", "runtime-must-not-create"),
          REALM,
          clientUuid(adminToken(), RUNTIME_CLIENT));
      return 204;
    } catch (RestClientResponseException exception) {
      return exception.getStatusCode().value();
    }
  }

  String createUser() {
    String admin = adminToken();
    post(
        admin,
        "/admin/realms/{realm}/users",
        Map.of("username", "contract-user", "enabled", true),
        REALM);
    UserRepresentation[] users =
        rest.get()
            .uri(
                uri ->
                    uri.path("/admin/realms/{realm}/users")
                        .queryParam("username", "contract-user")
                        .queryParam("exact", true)
                        .build(REALM))
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(UserRepresentation[].class);
    return Arrays.stream(users).findFirst().orElseThrow().id();
  }

  void deleteMapper(String clientId) {
    String admin = adminToken();
    String clientUuid = clientUuid(admin, clientId);
    IdentityKeycloakProviderContract.ProtocolMapper[] mappers =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                REALM,
                clientUuid)
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(IdentityKeycloakProviderContract.ProtocolMapper[].class);
    Arrays.stream(mappers)
        .filter(mapper -> IdentityKeycloakProviderContract.MAPPER_NAME.equals(mapper.name()))
        .forEach(mapper -> delete(admin, clientUuid, mapper.id()));
  }

  void deleteRole(String role) {
    String admin = adminToken();
    rest.delete()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
            REALM,
            clientUuid(admin, RUNTIME_CLIENT),
            role)
        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
        .retrieve()
        .toBodilessEntity();
  }

  boolean roleExists(String role) {
    String admin = adminToken();
    try {
      rest.get()
          .uri(
              "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
              REALM,
              clientUuid(admin, RUNTIME_CLIENT),
              role)
          .header(HttpHeaders.AUTHORIZATION, bearer(admin))
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return false;
      }
      throw exception;
    }
  }

  void enableUserManagedAccess(String token, String resourceId) {
    Map<String, Object> resource =
        rest.get()
            .uri("/realms/{realm}/authz/protection/resource_set/{resourceId}", REALM, resourceId)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<>() {});
    Map<String, Object> enabled = new LinkedHashMap<>(resource == null ? Map.of() : resource);
    enabled.put("ownerManagedAccess", true);
    put(
        token,
        "/realms/{realm}/authz/protection/resource_set/{resourceId}",
        enabled,
        REALM,
        resourceId);
  }

  private void createClient(
      String admin, String clientId, String secret, boolean authorizationServices) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientId", clientId);
    body.put("enabled", true);
    body.put("publicClient", false);
    body.put("secret", secret);
    body.put("serviceAccountsEnabled", authorizationServices);
    body.put("standardFlowEnabled", "setup".equals(clientId));
    body.put("directAccessGrantsEnabled", authorizationServices);
    body.put("authorizationServicesEnabled", authorizationServices);
    if ("setup".equals(clientId)) {
      body.put("redirectUris", List.of("https://app.example/*"));
    }
    post(admin, "/admin/realms/{realm}/clients", body, REALM);
  }

  private void configureUserProfile(String admin) {
    Map<String, Object> current =
        rest.get()
            .uri("/admin/realms/{realm}/users/profile", REALM)
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<>() {});
    Map<String, Object> enabled = new LinkedHashMap<>(current == null ? Map.of() : current);
    enabled.put("unmanagedAttributePolicy", "ENABLED");
    Object attributes = enabled.get("attributes");
    if (attributes instanceof List<?> configuredAttributes) {
      enabled.put(
          "attributes", configuredAttributes.stream().map(this::makeLastNameOptional).toList());
    }
    put(admin, "/admin/realms/{realm}/users/profile", enabled, REALM);
  }

  private Object makeLastNameOptional(Object configuredAttribute) {
    if (!(configuredAttribute instanceof Map<?, ?> raw) || !"lastName".equals(raw.get("name"))) {
      return configuredAttribute;
    }
    Map<Object, Object> optional = new LinkedHashMap<>(raw);
    optional.remove("required");
    return optional;
  }

  private void installMapper(String admin, String clientId) {
    post(
        admin,
        "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
        IdentityKeycloakProviderContract.canonicalMapper(),
        REALM,
        clientUuid(admin, clientId));
  }

  private void createRole(String admin, String role) {
    post(
        admin,
        "/admin/realms/{realm}/clients/{clientUuid}/roles",
        Map.of("name", role),
        REALM,
        clientUuid(admin, RUNTIME_CLIENT));
  }

  private void createSessionUser(String admin) {
    post(
        admin,
        "/admin/realms/{realm}/users",
        Map.of(
            "username",
            "session-user@example.test",
            "email",
            "session-user@example.test",
            "firstName",
            "Session",
            "lastName",
            "User",
            "emailVerified",
            true,
            "enabled",
            true,
            "credentials",
            List.of(
                Map.of("type", "password", "value", "S3cure-cardo-password!", "temporary", false))),
        REALM);
  }

  private void grantRuntimeManagementRoles(String admin, List<String> roleNames) {
    String runtimeUuid = clientUuid(admin, RUNTIME_CLIENT);
    String realmManagementUuid = clientUuid(admin, "realm-management");
    UserRepresentation serviceAccount =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/service-account-user",
                REALM,
                runtimeUuid)
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(UserRepresentation.class);
    List<RoleRepresentation> roles =
        roleNames.stream().map(role -> managementRole(admin, realmManagementUuid, role)).toList();
    post(
        admin,
        "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
        roles,
        REALM,
        serviceAccount.id(),
        realmManagementUuid);
  }

  private RoleRepresentation managementRole(String admin, String realmManagementUuid, String role) {
    return rest.get()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
            REALM,
            realmManagementUuid,
            role)
        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
        .retrieve()
        .body(RoleRepresentation.class);
  }

  private String clientUuid(String token, String clientId) {
    ClientRepresentation[] clients =
        rest.get()
            .uri(
                uri ->
                    uri.path("/admin/realms/{realm}/clients")
                        .queryParam("clientId", clientId)
                        .build(REALM))
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .body(ClientRepresentation[].class);
    return Arrays.stream(clients)
        .filter(client -> clientId.equals(client.clientId()))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private String adminToken() {
    LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "admin-cli");
    form.add("username", ADMIN_USERNAME);
    form.add("password", ADMIN_PASSWORD);
    return rest.post()
        .uri("/realms/master/protocol/openid-connect/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(TokenResponse.class)
        .accessToken();
  }

  private void post(String token, String uri, Object body, Object... uriVariables) {
    rest.post()
        .uri(uri, uriVariables)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  private void put(String token, String uri, Object body, Object... uriVariables) {
    rest.put()
        .uri(uri, uriVariables)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  private void delete(String token, String clientUuid, String mapperId) {
    rest.delete()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models/{mapperId}",
            REALM,
            clientUuid,
            mapperId)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve()
        .toBodilessEntity();
  }

  private String baseUrl() {
    return "http://" + container.getHost() + ":" + container.getMappedPort(8080);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  @Override
  public void close() {
    container.stop();
    if (smtpListener != null) {
      try {
        smtpListener.close();
      } catch (IOException ignored) {
        // Closing a disposable test listener is best effort.
      }
    }
    if (smtpThread != null) {
      try {
        smtpThread.join(Duration.ofSeconds(2));
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void startSmtpSink() {
    try {
      smtpListener = new ServerSocket(0);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not start the disposable SMTP sink", exception);
    }
    smtpThread =
        Thread.ofPlatform().daemon().name("cardo-keycloak-smtp-sink").start(this::serveSmtp);
  }

  private void serveSmtp() {
    while (!smtpListener.isClosed()) {
      try (Socket connection = smtpListener.accept();
          BufferedReader input =
              new BufferedReader(new InputStreamReader(connection.getInputStream()));
          PrintWriter output = new PrintWriter(connection.getOutputStream(), true)) {
        output.print("220 cardo.test ESMTP\r\n");
        output.flush();
        boolean data = false;
        String line;
        while ((line = input.readLine()) != null) {
          if (data) {
            if (".".equals(line)) {
              data = false;
              output.print("250 accepted\r\n");
              output.flush();
            }
          } else if (line.startsWith("EHLO") || line.startsWith("HELO")) {
            output.print("250-cardo.test\r\n250 8BITMIME\r\n");
            output.flush();
          } else if (line.startsWith("DATA")) {
            data = true;
            output.print("354 end with .\r\n");
            output.flush();
          } else if (line.startsWith("QUIT")) {
            output.print("221 bye\r\n");
            output.flush();
            break;
          } else {
            output.print("250 accepted\r\n");
            output.flush();
          }
        }
      } catch (IOException exception) {
        if (!smtpListener.isClosed()) {
          throw new IllegalStateException("Disposable SMTP sink failed", exception);
        }
      }
    }
  }

  private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

  private record ClientRepresentation(String id, String clientId) {}

  private record UserRepresentation(String id) {}

  private record RoleRepresentation(String id, String name) {}
}
