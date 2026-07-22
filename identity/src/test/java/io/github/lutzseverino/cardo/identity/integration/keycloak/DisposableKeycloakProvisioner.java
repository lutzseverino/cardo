package io.github.lutzseverino.cardo.identity.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenSettings;
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
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
  private static final String RUNTIME_CLIENT = "cardo-identity";
  private static final String RESOURCE_SERVER_CLIENT = "identity";
  private static final String RUNTIME_SECRET = "identity-runtime-secret";
  private static final String AUTHORIZATION_SECRET = "identity-authorization-secret";
  private static final List<String> RUNTIME_REALM_MANAGEMENT_ROLES =
      List.of("manage-users", "view-clients");
  private static final List<String> MAPPER_CLIENTS =
      List.of(RUNTIME_CLIENT, RESOURCE_SERVER_CLIENT, "billing");
  private static final ObjectMapper JSON = new ObjectMapper();

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

  void bootstrapRealm() {
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
    createSessionUser(admin);
  }

  ContractSnapshot materializeContract() {
    String admin = adminToken();
    ensureClient(admin, RUNTIME_CLIENT, RUNTIME_SECRET, true, true, false, false);
    ensureClient(admin, RESOURCE_SERVER_CLIENT, AUTHORIZATION_SECRET, true, false, true, false);
    ensureClient(admin, "setup", "setup-secret", false, false, false, true);
    ensureClient(admin, "billing", "billing-secret", false, false, false, false);
    MAPPER_CLIENTS.forEach(clientId -> ensureMapper(admin, clientId));
    KeycloakIdentityProviderContract.IDENTITY_ROLES.forEach(role -> ensureRole(admin, role));
    grantRuntimeClientRoles(admin, "realm-management", RUNTIME_REALM_MANAGEMENT_ROLES);
    grantClientRoles(
        admin, RESOURCE_SERVER_CLIENT, RESOURCE_SERVER_CLIENT, List.of("uma_protection"));
    return snapshot(admin);
  }

  KeycloakProperties properties(boolean legacyMutation) {
    return new KeycloakProperties(
        baseUrl(),
        REALM,
        RUNTIME_CLIENT,
        RUNTIME_SECRET,
        AUTHORIZATION_SECRET,
        "setup",
        URI.create("https://app.example/invitations/completed"),
        MAPPER_CLIENTS,
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

  KeycloakClientCredentialsTokenProvider catalogTokens(RestClient.Builder restBuilder) {
    return new KeycloakClientCredentialsTokenProvider(
        baseUrl(),
        REALM,
        RESOURCE_SERVER_CLIENT,
        AUTHORIZATION_SECRET,
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
          KeycloakIdentityProviderContract.canonicalMapper(),
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
          clientUuid(adminToken(), RESOURCE_SERVER_CLIENT));
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

  String sessionUserId() {
    String admin = adminToken();
    UserRepresentation[] users =
        rest.get()
            .uri(
                uri ->
                    uri.path("/admin/realms/{realm}/users")
                        .queryParam("username", "session-user@example.test")
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
    KeycloakIdentityProviderContract.ProtocolMapper[] mappers =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                REALM,
                clientUuid)
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(KeycloakIdentityProviderContract.ProtocolMapper[].class);
    Arrays.stream(mappers)
        .filter(mapper -> KeycloakIdentityProviderContract.MAPPER_NAME.equals(mapper.name()))
        .forEach(mapper -> delete(admin, clientUuid, mapper.id()));
  }

  void deleteRole(String role) {
    String admin = adminToken();
    rest.delete()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
            REALM,
            clientUuid(admin, RESOURCE_SERVER_CLIENT),
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
              clientUuid(admin, RESOURCE_SERVER_CLIENT),
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

  List<String> realmManagementRoles(String token) {
    return resourceRoles(token, "realm-management");
  }

  List<String> runtimeClientRoles(String token) {
    return resourceRoles(token, RUNTIME_CLIENT);
  }

  List<String> authorizationClientRoles(String token) {
    return resourceRoles(token, RESOURCE_SERVER_CLIENT);
  }

  private void ensureClient(
      String admin,
      String clientId,
      String secret,
      boolean serviceAccount,
      boolean directGrant,
      boolean authorizationServices,
      boolean standardFlow) {
    List<ClientRepresentation> existing = exactClients(admin, clientId);
    if (existing.size() > 1) {
      throw new IllegalStateException("Disposable realm has duplicate client " + clientId);
    }
    if (!existing.isEmpty()) {
      return;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientId", clientId);
    body.put("enabled", true);
    body.put("publicClient", false);
    body.put("secret", secret);
    body.put("serviceAccountsEnabled", serviceAccount);
    body.put("standardFlowEnabled", standardFlow);
    body.put("directAccessGrantsEnabled", directGrant);
    body.put("authorizationServicesEnabled", authorizationServices);
    if (standardFlow) {
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

  private void ensureMapper(String admin, String clientId) {
    String clientUuid = clientUuid(admin, clientId);
    List<KeycloakIdentityProviderContract.ProtocolMapper> named = namedMappers(admin, clientUuid);
    if (named.size() > 1) {
      throw new IllegalStateException("Disposable realm has duplicate mapper on " + clientId);
    }
    if (named.isEmpty()) {
      post(
          admin,
          "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
          KeycloakIdentityProviderContract.canonicalMapper(),
          REALM,
          clientUuid);
    } else if (!KeycloakIdentityProviderContract.isCanonical(named.getFirst())) {
      put(
          admin,
          "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models/{mapperId}",
          KeycloakIdentityProviderContract.canonicalMapper().withId(named.getFirst().id()),
          REALM,
          clientUuid,
          named.getFirst().id());
    }
  }

  private void ensureRole(String admin, String role) {
    String resourceServerUuid = clientUuid(admin, RESOURCE_SERVER_CLIENT);
    try {
      rest.get()
          .uri(
              "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
              REALM,
              resourceServerUuid,
              role)
          .header(HttpHeaders.AUTHORIZATION, bearer(admin))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) {
        throw exception;
      }
      post(
          admin,
          "/admin/realms/{realm}/clients/{clientUuid}/roles",
          Map.of("name", role),
          REALM,
          resourceServerUuid);
    }
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

  private void grantRuntimeClientRoles(
      String admin, String targetClientId, List<String> roleNames) {
    grantClientRoles(admin, RUNTIME_CLIENT, targetClientId, roleNames);
  }

  private void grantClientRoles(
      String admin, String sourceClientId, String targetClientId, List<String> roleNames) {
    String runtimeUuid = clientUuid(admin, sourceClientId);
    String targetClientUuid = clientUuid(admin, targetClientId);
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
        roleNames.stream().map(role -> clientRole(admin, targetClientUuid, role)).toList();
    post(
        admin,
        "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
        roles,
        REALM,
        serviceAccount.id(),
        targetClientUuid);
  }

  private RoleRepresentation clientRole(String admin, String clientUuid, String role) {
    return rest.get()
        .uri("/admin/realms/{realm}/clients/{clientUuid}/roles/{role}", REALM, clientUuid, role)
        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
        .retrieve()
        .body(RoleRepresentation.class);
  }

  private ContractSnapshot snapshot(String admin) {
    Map<String, Integer> clientCounts = new LinkedHashMap<>();
    for (String clientId : List.of(RUNTIME_CLIENT, RESOURCE_SERVER_CLIENT, "setup", "billing")) {
      clientCounts.put(clientId, exactClients(admin, clientId).size());
    }
    Map<String, Integer> mapperCounts = new LinkedHashMap<>();
    for (String clientId : MAPPER_CLIENTS) {
      mapperCounts.put(clientId, namedMappers(admin, clientUuid(admin, clientId)).size());
    }
    List<String> identityRoles =
        clientRoles(admin, RESOURCE_SERVER_CLIENT).stream()
            .map(RoleRepresentation::name)
            .filter(KeycloakIdentityProviderContract.IDENTITY_ROLES::contains)
            .sorted()
            .toList();
    return new ContractSnapshot(
        Map.copyOf(clientCounts),
        Map.copyOf(mapperCounts),
        identityRoles,
        assignedClientRoles(admin, RUNTIME_CLIENT, "realm-management"),
        assignedClientRoles(admin, RESOURCE_SERVER_CLIENT, RESOURCE_SERVER_CLIENT));
  }

  private List<RoleRepresentation> clientRoles(String admin, String clientId) {
    RoleRepresentation[] roles =
        rest.get()
            .uri(
                uri ->
                    uri.path("/admin/realms/{realm}/clients/{clientUuid}/roles")
                        .queryParam("first", 0)
                        .queryParam("max", 100)
                        .build(REALM, clientUuid(admin, clientId)))
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(RoleRepresentation[].class);
    return roles == null ? List.of() : Arrays.asList(roles);
  }

  private List<String> assignedClientRoles(
      String admin, String sourceClientId, String targetClientId) {
    String runtimeUuid = clientUuid(admin, sourceClientId);
    UserRepresentation serviceAccount =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/service-account-user",
                REALM,
                runtimeUuid)
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(UserRepresentation.class);
    RoleRepresentation[] roles =
        rest.get()
            .uri(
                "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
                REALM,
                serviceAccount.id(),
                clientUuid(admin, targetClientId))
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .retrieve()
            .body(RoleRepresentation[].class);
    return roles == null
        ? List.of()
        : Arrays.stream(roles).map(RoleRepresentation::name).sorted().toList();
  }

  private List<KeycloakIdentityProviderContract.ProtocolMapper> namedMappers(
      String token, String clientUuid) {
    KeycloakIdentityProviderContract.ProtocolMapper[] mappers =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                REALM,
                clientUuid)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .body(KeycloakIdentityProviderContract.ProtocolMapper[].class);
    return mappers == null
        ? List.of()
        : Arrays.stream(mappers)
            .filter(mapper -> KeycloakIdentityProviderContract.MAPPER_NAME.equals(mapper.name()))
            .toList();
  }

  private List<ClientRepresentation> exactClients(String token, String clientId) {
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
    return clients == null
        ? List.of()
        : Arrays.stream(clients).filter(client -> clientId.equals(client.clientId())).toList();
  }

  private String clientUuid(String token, String clientId) {
    List<ClientRepresentation> clients = exactClients(token, clientId);
    if (clients.size() != 1) {
      throw new IllegalStateException(
          "Expected one disposable Keycloak client " + clientId + " but found " + clients.size());
    }
    return clients.getFirst().id();
  }

  private List<String> resourceRoles(String token, String clientId) {
    try {
      String[] parts = token.split("\\.");
      JwtClaims claims = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), JwtClaims.class);
      ResourceAccess access =
          claims.resourceAccess() == null ? null : claims.resourceAccess().get(clientId);
      return access == null || access.roles() == null
          ? List.of()
          : access.roles().stream().sorted().toList();
    } catch (RuntimeException | IOException exception) {
      throw new IllegalStateException("Could not inspect the disposable runtime token", exception);
    }
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record JwtClaims(
      @JsonProperty("resource_access") Map<String, ResourceAccess> resourceAccess) {}

  private record ResourceAccess(List<String> roles) {}

  record ContractSnapshot(
      Map<String, Integer> clientCounts,
      Map<String, Integer> mapperCounts,
      List<String> identityRoles,
      List<String> realmManagementGrants,
      List<String> authorizationClientGrants) {}
}
