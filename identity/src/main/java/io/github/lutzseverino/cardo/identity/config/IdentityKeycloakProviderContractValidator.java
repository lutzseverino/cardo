package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
final class IdentityKeycloakProviderContractValidator {

  private final KeycloakProperties properties;
  private final KeycloakClientCredentialsTokenProvider clientCredentialsTokens;
  private final RestClient rest;

  IdentityKeycloakProviderContractValidator(
      KeycloakProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest) {
    this.properties = properties;
    this.clientCredentialsTokens = clientCredentialsTokens;
    this.rest = rest.clone().baseUrl(properties.baseUrl()).build();
  }

  void validate() {
    List<String> drift = new ArrayList<>();
    String token;
    try {
      token = clientCredentialsTokens.clientCredentialsToken();
    } catch (RuntimeException exception) {
      throw invalid(List.of("runtime credential token acquisition failed"));
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
    validateCapability(
        "UMA protection resource read",
        drift,
        () ->
            rest.get()
                .uri("/realms/{realm}/authz/protection/resource_set", properties.realm())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity());

    if (!drift.isEmpty()) {
      throw invalid(drift);
    }
  }

  private Map<String, String> clients(String token, List<String> drift) {
    Map<String, String> clientUuids = new LinkedHashMap<>();
    for (String clientId : IdentityKeycloakProviderContract.expectedClientIds(properties)) {
      try {
        IdentityKeycloakProviderContract.ClientRepresentation[] response =
            rest.get()
                .uri(
                    uri ->
                        uri.path("/admin/realms/{realm}/clients")
                            .queryParam("clientId", clientId)
                            .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(IdentityKeycloakProviderContract.ClientRepresentation[].class);
        List<IdentityKeycloakProviderContract.ClientRepresentation> exact =
            response == null
                ? List.of()
                : Arrays.stream(response)
                    .filter(client -> clientId.equals(client.clientId()))
                    .filter(client -> client.id() != null && !client.id().isBlank())
                    .toList();
        if (exact.size() == 1) {
          clientUuids.put(clientId, exact.getFirst().id());
        } else {
          drift.add("client " + clientId + " expected one exact match but found " + exact.size());
        }
      } catch (RestClientResponseException exception) {
        drift.add(
            "client " + clientId + " lookup returned HTTP " + exception.getStatusCode().value());
      } catch (RuntimeException exception) {
        drift.add("client " + clientId + " lookup failed");
      }
    }
    return clientUuids;
  }

  private void validateMappers(String token, Map<String, String> clientUuids, List<String> drift) {
    for (String clientId : IdentityKeycloakProviderContract.mapperClientIds(properties)) {
      String clientUuid = clientUuids.get(clientId);
      if (clientUuid == null) {
        continue;
      }
      try {
        IdentityKeycloakProviderContract.ProtocolMapper[] response =
            rest.get()
                .uri(
                    "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                    properties.realm(),
                    clientUuid)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(IdentityKeycloakProviderContract.ProtocolMapper[].class);
        List<IdentityKeycloakProviderContract.ProtocolMapper> named =
            response == null
                ? List.of()
                : Arrays.stream(response)
                    .filter(
                        mapper ->
                            IdentityKeycloakProviderContract.MAPPER_NAME.equals(mapper.name()))
                    .toList();
        if (named.size() != 1) {
          drift.add(
              "client "
                  + clientId
                  + " mapper "
                  + IdentityKeycloakProviderContract.MAPPER_NAME
                  + " expected one definition but found "
                  + named.size());
        } else if (!IdentityKeycloakProviderContract.isCanonical(named.getFirst())) {
          drift.add(
              "client "
                  + clientId
                  + " mapper "
                  + IdentityKeycloakProviderContract.MAPPER_NAME
                  + " has incompatible semantics");
        }
      } catch (RestClientResponseException exception) {
        drift.add(
            "client "
                + clientId
                + " mapper lookup returned HTTP "
                + exception.getStatusCode().value());
      } catch (RuntimeException exception) {
        drift.add("client " + clientId + " mapper lookup failed");
      }
    }
  }

  private void validateRoles(String token, String clientUuid, List<String> drift) {
    if (clientUuid == null) {
      return;
    }
    for (String role : IdentityKeycloakProviderContract.IDENTITY_ROLES) {
      try {
        IdentityKeycloakProviderContract.RoleRepresentation response =
            rest.get()
                .uri(
                    "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
                    properties.realm(),
                    clientUuid,
                    role)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(IdentityKeycloakProviderContract.RoleRepresentation.class);
        if (response == null
            || response.id() == null
            || response.id().isBlank()
            || !role.equals(response.name())) {
          drift.add("Identity role " + role + " is missing or incompatible");
        }
      } catch (RestClientResponseException exception) {
        drift.add(
            "Identity role " + role + " lookup returned HTTP " + exception.getStatusCode().value());
      } catch (RuntimeException exception) {
        drift.add("Identity role " + role + " lookup failed");
      }
    }
  }

  private void validateCapability(String label, List<String> drift, Runnable request) {
    try {
      request.run();
    } catch (RestClientResponseException exception) {
      drift.add(label + " returned HTTP " + exception.getStatusCode().value());
    } catch (RuntimeException exception) {
      drift.add(label + " failed");
    }
  }

  private IllegalStateException invalid(List<String> drift) {
    return new IllegalStateException(
        "Identity Keycloak provider contract is invalid: "
            + String.join("; ", drift)
            + ". Repair with deployment-owned provisioning or temporarily enable "
            + "cardo.identity.keycloak.legacy-startup-mutation-enabled.");
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
