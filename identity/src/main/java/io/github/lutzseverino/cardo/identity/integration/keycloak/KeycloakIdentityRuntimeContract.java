package io.github.lutzseverino.cardo.identity.integration.keycloak;

import com.nimbusds.jwt.JWTParser;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.config.KeycloakProperties;
import io.github.lutzseverino.cardo.identity.provider.IdentityRuntimeContract;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
final class KeycloakIdentityRuntimeContract implements IdentityRuntimeContract {

  private final KeycloakProperties properties;
  private final KeycloakClientCredentialsTokenProvider runtimeTokens;
  private final KeycloakClientCredentialsTokenProvider catalogTokens;
  private final KeycloakLegacyStartupRepair legacyRepair;
  private final RestClient rest;

  KeycloakIdentityRuntimeContract(
      KeycloakProperties properties,
      @Qualifier("identityProviderRuntimeTokenProvider") KeycloakClientCredentialsTokenProvider runtimeTokens,
      @Qualifier("identityAuthorizationCatalogTokenProvider") KeycloakClientCredentialsTokenProvider catalogTokens,
      KeycloakLegacyStartupRepair legacyRepair,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest) {
    this.properties = properties;
    this.runtimeTokens = runtimeTokens;
    this.catalogTokens = catalogTokens;
    this.legacyRepair = legacyRepair;
    this.rest = rest.clone().baseUrl(properties.baseUrl()).build();
  }

  @Override
  public void validate() {
    List<Drift> drift = new ArrayList<>();
    String token;
    try {
      token = runtimeTokens.clientCredentialsToken();
      validateAuthorizedParty("runtime credential", token, properties.clientId(), drift);
    } catch (RuntimeException exception) {
      throw invalid(List.of(new Drift("runtime credential token acquisition failed", false)));
    }

    Map<String, String> clientUuids = clients(token, drift);
    validateMappers(token, clientUuids, drift);
    validateRoles(token, clientUuids.get(IdentityPermissions.CLIENT_ID), drift);
    validateCapability(
        "user directory read",
        drift,
        () ->
            rest.get()
                .uri(
                    uri ->
                        uri.path("/admin/realms/{realm}/users")
                            .queryParam("first", 0)
                            .queryParam("max", 1)
                            .queryParam("briefRepresentation", true)
                            .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity());
    String catalogToken;
    try {
      catalogToken = catalogTokens.clientCredentialsToken();
      validateCatalogToken(catalogToken, drift);
      validateCapability(
          "Identity catalog UMA protection resource read",
          drift,
          () ->
              rest.get()
                  .uri("/realms/{realm}/authz/protection/resource_set", properties.realm())
                  .header(HttpHeaders.AUTHORIZATION, bearer(catalogToken))
                  .retrieve()
                  .toBodilessEntity());
      validateCatalogAdminIsolation(catalogToken, clientUuids, drift);
    } catch (RuntimeException exception) {
      drift.add(new Drift("Identity catalog credential token acquisition failed", false));
    }

    if (!drift.isEmpty()) {
      throw invalid(drift);
    }
  }

  private void validateCatalogToken(String token, List<Drift> drift) {
    validateAuthorizedParty(
        "Identity catalog credential", token, IdentityPermissions.CLIENT_ID, drift);
    try {
      Map<String, Object> claims = JWTParser.parse(token).getJWTClaimsSet().getClaims();
      Map<?, ?> resourceAccess =
          claims.get("resource_access") instanceof Map<?, ?> value ? value : Map.of();
      Map<?, ?> identityAccess =
          resourceAccess.get(IdentityPermissions.CLIENT_ID) instanceof Map<?, ?> value
              ? value
              : Map.of();
      Set<String> identityRoles = stringSet(identityAccess.get("roles"));
      if (!Set.of("uma_protection").equals(identityRoles)) {
        drift.add(
            new Drift(
                "Identity catalog credential must have exactly identity:uma_protection", false));
      }
      if (resourceAccess.containsKey("realm-management")) {
        drift.add(
            new Drift(
                "Identity catalog credential must not have realm-management authority", false));
      }
    } catch (java.text.ParseException exception) {
      drift.add(new Drift("Identity catalog credential returned an unreadable token", false));
    }
  }

  private void validateAuthorizedParty(
      String label, String token, String expectedAuthorizedParty, List<Drift> drift) {
    try {
      String authorizedParty = JWTParser.parse(token).getJWTClaimsSet().getStringClaim("azp");
      if (!expectedAuthorizedParty.equals(authorizedParty)) {
        drift.add(new Drift(label + " has an unexpected authorized party", false));
      }
    } catch (java.text.ParseException exception) {
      drift.add(new Drift(label + " returned an unreadable token", false));
    }
  }

  private Set<String> stringSet(Object value) {
    if (!(value instanceof List<?> list)) {
      return Set.of();
    }
    return list.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private void validateCatalogAdminIsolation(
      String token, Map<String, String> clientUuids, List<Drift> drift) {
    expectForbidden(
        "Identity catalog client administration",
        drift,
        () ->
            rest.get()
                .uri(
                    uri ->
                        uri.path("/admin/realms/{realm}/clients")
                            .queryParam("clientId", IdentityPermissions.CLIENT_ID)
                            .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity());
    expectForbidden(
        "Identity catalog user administration",
        drift,
        () ->
            rest.get()
                .uri(
                    uri ->
                        uri.path("/admin/realms/{realm}/users")
                            .queryParam("first", 0)
                            .queryParam("max", 1)
                            .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity());
    String identityUuid = clientUuids.get(IdentityPermissions.CLIENT_ID);
    if (identityUuid == null) {
      return;
    }
    expectForbidden(
        "Identity catalog mapper administration",
        drift,
        () ->
            rest.get()
                .uri(
                    "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                    properties.realm(),
                    identityUuid)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity());
    expectForbidden(
        "Identity catalog role administration",
        drift,
        () ->
            rest.get()
                .uri(
                    "/admin/realms/{realm}/clients/{clientUuid}/roles",
                    properties.realm(),
                    identityUuid)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity());
  }

  private void expectForbidden(String label, List<Drift> drift, Runnable request) {
    try {
      request.run();
      drift.add(new Drift(label + " was unexpectedly allowed", false));
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 403) {
        drift.add(new Drift(label + " returned HTTP " + exception.getStatusCode().value(), false));
      }
    } catch (RuntimeException exception) {
      drift.add(new Drift(label + " failed without an authorization response", false));
    }
  }

  @Override
  public void repairLegacyStartupDefinitions() {
    legacyRepair.repair();
  }

  private Map<String, String> clients(String token, List<Drift> drift) {
    Map<String, String> clientUuids = new LinkedHashMap<>();
    for (String clientId : KeycloakIdentityProviderContract.expectedClientIds(properties)) {
      try {
        KeycloakIdentityProviderContract.ClientRepresentation[] response =
            rest.get()
                .uri(
                    uri ->
                        uri.path("/admin/realms/{realm}/clients")
                            .queryParam("clientId", clientId)
                            .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(KeycloakIdentityProviderContract.ClientRepresentation[].class);
        List<KeycloakIdentityProviderContract.ClientRepresentation> exact =
            response == null
                ? List.of()
                : Arrays.stream(response)
                    .filter(client -> clientId.equals(client.clientId()))
                    .filter(client -> client.id() != null && !client.id().isBlank())
                    .toList();
        if (exact.size() == 1) {
          clientUuids.put(clientId, exact.getFirst().id());
        } else {
          drift.add(
              new Drift(
                  "client " + clientId + " expected one exact match but found " + exact.size(),
                  false));
        }
      } catch (RestClientResponseException exception) {
        drift.add(
            new Drift(
                "client " + clientId + " lookup returned HTTP " + exception.getStatusCode().value(),
                false));
      } catch (RuntimeException exception) {
        drift.add(new Drift("client " + clientId + " lookup failed", false));
      }
    }
    return clientUuids;
  }

  private void validateMappers(String token, Map<String, String> clientUuids, List<Drift> drift) {
    for (String clientId : KeycloakIdentityProviderContract.mapperClientIds(properties)) {
      String clientUuid = clientUuids.get(clientId);
      if (clientUuid == null) {
        continue;
      }
      try {
        KeycloakIdentityProviderContract.ProtocolMapper[] response =
            rest.get()
                .uri(
                    "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                    properties.realm(),
                    clientUuid)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(KeycloakIdentityProviderContract.ProtocolMapper[].class);
        List<KeycloakIdentityProviderContract.ProtocolMapper> named =
            response == null
                ? List.of()
                : Arrays.stream(response)
                    .filter(
                        mapper ->
                            KeycloakIdentityProviderContract.MAPPER_NAME.equals(mapper.name()))
                    .toList();
        if (named.size() != 1) {
          drift.add(
              new Drift(
                  "client "
                      + clientId
                      + " mapper "
                      + KeycloakIdentityProviderContract.MAPPER_NAME
                      + " expected one definition but found "
                      + named.size(),
                  true));
        } else if (!KeycloakIdentityProviderContract.isCanonical(named.getFirst())) {
          drift.add(
              new Drift(
                  "client "
                      + clientId
                      + " mapper "
                      + KeycloakIdentityProviderContract.MAPPER_NAME
                      + " has incompatible semantics",
                  true));
        }
      } catch (RestClientResponseException exception) {
        drift.add(
            new Drift(
                "client "
                    + clientId
                    + " mapper lookup returned HTTP "
                    + exception.getStatusCode().value(),
                false));
      } catch (RuntimeException exception) {
        drift.add(new Drift("client " + clientId + " mapper lookup failed", false));
      }
    }
  }

  private void validateRoles(String token, String clientUuid, List<Drift> drift) {
    if (clientUuid == null) {
      return;
    }
    for (String role : KeycloakIdentityProviderContract.IDENTITY_ROLES) {
      try {
        KeycloakIdentityProviderContract.RoleRepresentation response =
            rest.get()
                .uri(
                    "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
                    properties.realm(),
                    clientUuid,
                    role)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(KeycloakIdentityProviderContract.RoleRepresentation.class);
        if (response == null
            || response.id() == null
            || response.id().isBlank()
            || !role.equals(response.name())) {
          drift.add(new Drift("Identity role " + role + " is missing or incompatible", true));
        }
      } catch (RestClientResponseException exception) {
        drift.add(
            new Drift(
                "Identity role "
                    + role
                    + " lookup returned HTTP "
                    + exception.getStatusCode().value(),
                exception.getStatusCode().value() == 404));
      } catch (RuntimeException exception) {
        drift.add(new Drift("Identity role " + role + " lookup failed", false));
      }
    }
  }

  private void validateCapability(String label, List<Drift> drift, Runnable request) {
    try {
      request.run();
    } catch (RestClientResponseException exception) {
      drift.add(new Drift(label + " returned HTTP " + exception.getStatusCode().value(), false));
    } catch (RuntimeException exception) {
      drift.add(new Drift(label + " failed", false));
    }
  }

  private IllegalStateException invalid(List<Drift> drift) {
    String advice = ". Repair all drift with deployment-owned provisioning.";
    if (!drift.isEmpty() && drift.stream().allMatch(Drift::legacyRepairable)) {
      advice +=
          " For canonical mapper and fixed Identity-role drift only, the temporary "
              + "cardo.identity.keycloak.legacy-startup-mutation-enabled flag may be used.";
    }
    return new IllegalStateException(
        "Identity Keycloak provider contract is invalid: "
            + drift.stream()
                .map(Drift::description)
                .collect(java.util.stream.Collectors.joining("; "))
            + advice);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private record Drift(String description, boolean legacyRepairable) {}
}
