package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakAuthorizationClient;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

final class ReferenceKeycloakMaterializer {

  static final String REALM = "cardo-reference";
  static final String ADMIN_USERNAME = "reference-bootstrap";
  static final String ADMIN_PASSWORD = "reference-bootstrap-password";
  static final String IDENTITY_RUNTIME_SECRET = "identity-runtime-reference-secret";
  static final String IDENTITY_CATALOG_SECRET = "identity-catalog-reference-secret";
  static final String INVITE_SECRET = "invite-reference-secret";
  static final String BILLING_SECRET = "billing-reference-secret";
  static final String PRODUCT_SECRET = "product-reference-secret";
  static final String PRODUCT_OUTBOUND_SECRET = "product-outbound-reference-secret";
  static final String BROWSER_CLIENT = "credential-browser";

  private static final List<String> CLIENTS =
      List.of(
          "cardo-identity",
          "identity",
          "cardo-invite",
          "billing",
          ReferenceContract.PRODUCT_CLIENT,
          ReferenceContract.PRODUCT_OUTBOUND_CLIENT,
          BROWSER_CLIENT);
  private static final Map<String, String> SECRETS =
      Map.of(
          "cardo-identity",
          IDENTITY_RUNTIME_SECRET,
          "identity",
          IDENTITY_CATALOG_SECRET,
          "cardo-invite",
          INVITE_SECRET,
          "billing",
          BILLING_SECRET,
          ReferenceContract.PRODUCT_CLIENT,
          PRODUCT_SECRET,
          ReferenceContract.PRODUCT_OUTBOUND_CLIENT,
          PRODUCT_OUTBOUND_SECRET,
          BROWSER_CLIENT,
          "browser-reference-secret");

  private final String baseUrl;
  private final RestClient rest;

  ReferenceKeycloakMaterializer(String baseUrl) {
    this.baseUrl = baseUrl;
    this.rest = RestClient.builder().baseUrl(baseUrl).build();
  }

  void bootstrap(String smtpHost, int smtpPort, String redirectUri) {
    try {
      post(
          adminToken(),
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
                  smtpHost,
                  "port",
                  Integer.toString(smtpPort),
                  "from",
                  "reference@example.test",
                  "auth",
                  "false",
                  "starttls",
                  "false")));
    } catch (RestClientResponseException conflict) {
      if (conflict.getStatusCode().value() != 409) {
        throw conflict;
      }
    }
    String admin = adminToken();
    configureUserProfile(admin);
    for (String client : CLIENTS) {
      ensureClient(admin, client, redirectUri);
    }
    for (String client :
        List.of(
            "cardo-identity",
            "identity",
            "cardo-invite",
            ReferenceContract.PRODUCT_CLIENT,
            ReferenceContract.PRODUCT_OUTBOUND_CLIENT)) {
      removeRealmRoleGrants(admin, client);
    }
    CLIENTS.forEach(client -> ensureCanonicalMapper(admin, client));
  }

  Snapshot materialize(String redirectUri) {
    bootstrap("mailpit", 1025, redirectUri);
    String admin = adminToken();
    ensureRoles(admin, "identity", List.of("profile:read", "profile:write", "user:provision"));
    ensureRoles(admin, "cardo-invite", List.of("product-service"));
    ensureRoles(admin, "billing", List.of("entitlement:read"));
    ensureAudienceScope(admin, "identity");
    ensureAudienceScope(admin, "cardo-invite");
    ensureAudienceScope(admin, "billing");
    attachDefaultScope(admin, "cardo-identity", "identity");
    attachOptionalScope(admin, "cardo-invite", "identity");
    attachOptionalScope(admin, ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "identity");
    attachOptionalScope(admin, ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "cardo-invite");
    attachOptionalScope(admin, ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "billing");
    grantClientRoles(
        admin, "cardo-identity", "realm-management", List.of("manage-users", "view-clients"));
    grantClientRoles(admin, "identity", "identity", List.of("uma_protection"));
    grantClientRoles(admin, "cardo-invite", "identity", List.of("user:provision"));
    grantClientRoles(
        admin, ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "identity", List.of("profile:read"));
    grantClientRoles(
        admin,
        ReferenceContract.PRODUCT_OUTBOUND_CLIENT,
        "cardo-invite",
        List.of("product-service"));
    grantClientRoles(
        admin, ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "billing", List.of("entitlement:read"));
    mapRoleToScope(admin, "identity", "identity", "user:provision");
    mapRoleToScope(admin, "cardo-invite", "cardo-invite", "product-service");
    mapRoleToScope(admin, "billing", "billing", "entitlement:read");
    ensureReferenceResource();
    return snapshot(admin);
  }

  String clientToken(String clientId, String scope) {
    KeycloakClientCredentialsTokenProvider tokens =
        new KeycloakClientCredentialsTokenProvider(
            baseUrl,
            REALM,
            clientId,
            SECRETS.get(clientId),
            RestClient.builder(),
            new KeycloakClientCredentialsTokenSettings(
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ZERO));
    return scope == null ? tokens.clientCredentialsToken() : tokens.clientCredentialsToken(scope);
  }

  int definitionWriteStatus(String token, String path, Object body) {
    try {
      post(token, path, body);
      return 204;
    } catch (RestClientResponseException failure) {
      return failure.getStatusCode().value();
    }
  }

  int adminReadStatus(String token) {
    try {
      maps(token, "/admin/realms/" + REALM + "/clients?first=0&max=1");
      return 200;
    } catch (RestClientResponseException failure) {
      return failure.getStatusCode().value();
    }
  }

  int protectionReadStatus(String token) {
    try {
      rest.get()
          .uri("/realms/" + REALM + "/authz/protection/resource_set")
          .header(HttpHeaders.AUTHORIZATION, bearer(token))
          .retrieve()
          .toBodilessEntity();
      return 200;
    } catch (RestClientResponseException failure) {
      return failure.getStatusCode().value();
    }
  }

  void revokeUserSessions(String authorizationSubject) {
    post(
        adminToken(),
        "/admin/realms/" + REALM + "/users/" + authorizationSubject + "/logout",
        Map.of());
  }

  String clientUuidFor(String clientId) {
    return clientUuid(adminToken(), clientId);
  }

  private void configureUserProfile(String admin) {
    Map<String, Object> current = map(admin, "/admin/realms/" + REALM + "/users/profile");
    Map<String, Object> enabled = new LinkedHashMap<>(current);
    enabled.put("unmanagedAttributePolicy", "ENABLED");
    Object attributes = enabled.get("attributes");
    if (attributes instanceof List<?> configured) {
      enabled.put("attributes", configured.stream().map(this::makeLastNameOptional).toList());
    }
    put(admin, "/admin/realms/" + REALM + "/users/profile", enabled);
  }

  private Object makeLastNameOptional(Object configuredAttribute) {
    if (!(configuredAttribute instanceof Map<?, ?> raw) || !"lastName".equals(raw.get("name"))) {
      return configuredAttribute;
    }
    Map<Object, Object> optional = new LinkedHashMap<>(raw);
    optional.remove("required");
    return optional;
  }

  private void ensureReferenceResource() {
    KeycloakClientCredentialsTokenProvider tokens =
        new KeycloakClientCredentialsTokenProvider(
            baseUrl,
            REALM,
            ReferenceContract.PRODUCT_CLIENT,
            PRODUCT_SECRET,
            RestClient.builder(),
            new KeycloakClientCredentialsTokenSettings(
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ZERO));
    new KeycloakAuthorizationClient(
            baseUrl,
            REALM,
            ReferenceContract.PRODUCT_CLIENT,
            RestClient.builder(),
            tokens::clientCredentialsToken,
            () -> {
              throw new IllegalStateException("Reference catalog has no realm token.");
            })
        .ensureResource(ReferenceContract.tenantResource());
  }

  private void ensureClient(String admin, String clientId, String redirectUri) {
    List<Map<String, Object>> clients = exactClients(admin, clientId);
    if (clients.size() > 1) {
      throw new IllegalStateException("Duplicate reference client: " + clientId);
    }
    if (!clients.isEmpty()) {
      return;
    }
    boolean authorization = Set.of("identity", ReferenceContract.PRODUCT_CLIENT).contains(clientId);
    boolean serviceAccount = !BROWSER_CLIENT.equals(clientId) && !"billing".equals(clientId);
    boolean directGrant = "cardo-identity".equals(clientId);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("clientId", clientId);
    body.put("enabled", true);
    body.put("publicClient", false);
    body.put("secret", SECRETS.get(clientId));
    body.put("serviceAccountsEnabled", serviceAccount || authorization);
    body.put("standardFlowEnabled", BROWSER_CLIENT.equals(clientId));
    body.put("directAccessGrantsEnabled", directGrant);
    body.put("authorizationServicesEnabled", authorization);
    body.put("fullScopeAllowed", Set.of("cardo-identity", "identity").contains(clientId));
    if (BROWSER_CLIENT.equals(clientId)) {
      body.put("redirectUris", List.of(redirectUri));
    }
    post(admin, "/admin/realms/" + REALM + "/clients", body);
  }

  private void ensureCanonicalMapper(String admin, String clientId) {
    String clientUuid = clientUuid(admin, clientId);
    List<Map<String, Object>> named =
        maps(
                admin,
                "/admin/realms/" + REALM + "/clients/" + clientUuid + "/protocol-mappers/models")
            .stream()
            .filter(mapper -> "cardo_user_id".equals(mapper.get("name")))
            .toList();
    if (named.size() > 1) {
      throw new IllegalStateException("Duplicate cardo_user_id mapper on " + clientId);
    }
    if (named.isEmpty()) {
      post(
          admin,
          "/admin/realms/" + REALM + "/clients/" + clientUuid + "/protocol-mappers/models",
          Map.of(
              "name",
              "cardo_user_id",
              "protocol",
              "openid-connect",
              "protocolMapper",
              "oidc-usermodel-attribute-mapper",
              "consentRequired",
              false,
              "config",
              Map.of(
                  "user.attribute",
                  "cardo_user_id",
                  "claim.name",
                  "cardo_user_id",
                  "jsonType.label",
                  "String",
                  "access.token.claim",
                  "true",
                  "id.token.claim",
                  "false",
                  "userinfo.token.claim",
                  "false",
                  "multivalued",
                  "false")));
    }
  }

  private void ensureRoles(String admin, String clientId, List<String> roles) {
    String uuid = clientUuid(admin, clientId);
    for (String role : roles) {
      try {
        map(admin, "/admin/realms/" + REALM + "/clients/" + uuid + "/roles/" + role);
      } catch (RestClientResponseException missing) {
        if (missing.getStatusCode().value() != 404) {
          throw missing;
        }
        post(admin, "/admin/realms/" + REALM + "/clients/" + uuid + "/roles", Map.of("name", role));
      }
    }
  }

  private void ensureAudienceScope(String admin, String audience) {
    List<Map<String, Object>> scopes =
        maps(admin, "/admin/realms/" + REALM + "/client-scopes").stream()
            .filter(scope -> audience.equals(scope.get("name")))
            .toList();
    if (scopes.size() > 1) {
      throw new IllegalStateException("Duplicate audience scope: " + audience);
    }
    if (scopes.isEmpty()) {
      post(
          admin,
          "/admin/realms/" + REALM + "/client-scopes",
          Map.of(
              "name",
              audience,
              "protocol",
              "openid-connect",
              "protocolMappers",
              List.of(
                  Map.of(
                      "name",
                      audience + "-audience",
                      "protocol",
                      "openid-connect",
                      "protocolMapper",
                      "oidc-audience-mapper",
                      "config",
                      Map.of(
                          "included.client.audience",
                          audience,
                          "access.token.claim",
                          "true",
                          "id.token.claim",
                          "false")))));
    }
  }

  private void attachOptionalScope(String admin, String clientId, String scopeName) {
    attachScope(admin, clientId, scopeName, "optional-client-scopes");
  }

  private void attachDefaultScope(String admin, String clientId, String scopeName) {
    attachScope(admin, clientId, scopeName, "default-client-scopes");
  }

  private void attachScope(String admin, String clientId, String scopeName, String type) {
    String scopeUuid = scopeUuid(admin, scopeName);
    try {
      put(
          admin,
          "/admin/realms/"
              + REALM
              + "/clients/"
              + clientUuid(admin, clientId)
              + "/"
              + type
              + "/"
              + scopeUuid,
          Map.of());
    } catch (RestClientResponseException conflict) {
      if (conflict.getStatusCode().value() != 409) {
        throw conflict;
      }
    }
  }

  private void grantClientRoles(
      String admin, String sourceClientId, String targetClientId, List<String> roleNames) {
    String sourceUuid = clientUuid(admin, sourceClientId);
    String targetUuid = clientUuid(admin, targetClientId);
    Map<String, Object> account =
        map(admin, "/admin/realms/" + REALM + "/clients/" + sourceUuid + "/service-account-user");
    List<Map<String, Object>> roles = new ArrayList<>();
    for (String role : roleNames) {
      roles.add(map(admin, "/admin/realms/" + REALM + "/clients/" + targetUuid + "/roles/" + role));
    }
    post(
        admin,
        "/admin/realms/"
            + REALM
            + "/users/"
            + account.get("id")
            + "/role-mappings/clients/"
            + targetUuid,
        roles);
  }

  private void removeRealmRoleGrants(String admin, String sourceClientId) {
    String sourceUuid = clientUuid(admin, sourceClientId);
    Map<String, Object> account =
        map(admin, "/admin/realms/" + REALM + "/clients/" + sourceUuid + "/service-account-user");
    String path = "/admin/realms/" + REALM + "/users/" + account.get("id") + "/role-mappings/realm";
    List<Map<String, Object>> roles = maps(admin, path);
    if (!roles.isEmpty()) {
      rest.method(HttpMethod.DELETE)
          .uri(path)
          .header(HttpHeaders.AUTHORIZATION, bearer(admin))
          .contentType(MediaType.APPLICATION_JSON)
          .body(roles)
          .retrieve()
          .toBodilessEntity();
    }
  }

  private void mapRoleToScope(
      String admin, String scopeName, String targetClientId, String roleName) {
    String targetUuid = clientUuid(admin, targetClientId);
    Map<String, Object> role =
        map(admin, "/admin/realms/" + REALM + "/clients/" + targetUuid + "/roles/" + roleName);
    post(
        admin,
        "/admin/realms/"
            + REALM
            + "/client-scopes/"
            + scopeUuid(admin, scopeName)
            + "/scope-mappings/clients/"
            + targetUuid,
        List.of(role));
  }

  private Snapshot snapshot(String admin) {
    Map<String, Integer> clients = new LinkedHashMap<>();
    Map<String, Integer> mappers = new LinkedHashMap<>();
    for (String client : CLIENTS) {
      clients.put(client, exactClients(admin, client).size());
      mappers.put(
          client,
          (int)
              maps(
                      admin,
                      "/admin/realms/"
                          + REALM
                          + "/clients/"
                          + clientUuid(admin, client)
                          + "/protocol-mappers/models")
                  .stream()
                  .filter(mapper -> "cardo_user_id".equals(mapper.get("name")))
                  .count());
    }
    Map<String, Integer> scopes = new LinkedHashMap<>();
    for (String scope : List.of("identity", "cardo-invite", "billing")) {
      scopes.put(
          scope,
          (int)
              maps(admin, "/admin/realms/" + REALM + "/client-scopes").stream()
                  .filter(candidate -> scope.equals(candidate.get("name")))
                  .count());
    }
    String authorizationPath =
        "/admin/realms/"
            + REALM
            + "/clients/"
            + clientUuid(admin, ReferenceContract.PRODUCT_CLIENT)
            + "/authz/resource-server/";
    Map<String, Integer> resources =
        Map.of(
            ReferenceContract.TENANT_RESOURCE,
            (int)
                maps(admin, authorizationPath + "resource").stream()
                    .filter(
                        resource -> ReferenceContract.TENANT_RESOURCE.equals(resource.get("name")))
                    .count());
    Map<String, Integer> policies =
        maps(admin, authorizationPath + "policy?first=0&max=100").stream()
            .map(policy -> String.valueOf(policy.get("name")))
            .collect(Collectors.toMap(Function.identity(), ignored -> 1, Integer::sum));
    return new Snapshot(
        Map.copyOf(clients),
        Map.copyOf(mappers),
        Map.copyOf(scopes),
        resources,
        Map.copyOf(policies));
  }

  private List<Map<String, Object>> exactClients(String token, String clientId) {
    return maps(token, "/admin/realms/" + REALM + "/clients?clientId=" + clientId).stream()
        .filter(client -> clientId.equals(client.get("clientId")))
        .toList();
  }

  private String clientUuid(String token, String clientId) {
    List<Map<String, Object>> clients = exactClients(token, clientId);
    if (clients.size() != 1) {
      throw new IllegalStateException("Expected exactly one client " + clientId);
    }
    return clients.getFirst().get("id").toString();
  }

  private String scopeUuid(String admin, String scopeName) {
    return maps(admin, "/admin/realms/" + REALM + "/client-scopes").stream()
        .filter(scope -> scopeName.equals(scope.get("name")))
        .map(scope -> scope.get("id").toString())
        .findFirst()
        .orElseThrow();
  }

  private String adminToken() {
    LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "admin-cli");
    form.add("username", ADMIN_USERNAME);
    form.add("password", ADMIN_PASSWORD);
    Map<String, Object> response =
        rest.post()
            .uri("/realms/master/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return response.get("access_token").toString();
  }

  private Map<String, Object> map(String token, String path) {
    return rest.get()
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  private List<Map<String, Object>> maps(String token, String path) {
    List<Map<String, Object>> response =
        rest.get()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return response == null ? List.of() : response;
  }

  private void post(String token, String path, Object body) {
    rest.post()
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  private void put(String token, String path, Object body) {
    rest.put()
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  record Snapshot(
      Map<String, Integer> clientCounts,
      Map<String, Integer> mapperCounts,
      Map<String, Integer> scopeCounts,
      Map<String, Integer> resourceCounts,
      Map<String, Integer> policyCounts) {}
}
