package io.github.lutzseverino.cardo.identity.config;

import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakClientCredentialsTokenProvider;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
final class IdentityKeycloakLegacyStartupRepair {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IdentityKeycloakLegacyStartupRepair.class);

  private final KeycloakProperties properties;
  private final KeycloakClientCredentialsTokenProvider clientCredentialsTokens;
  private final RestClient rest;

  IdentityKeycloakLegacyStartupRepair(
      KeycloakProperties properties,
      KeycloakClientCredentialsTokenProvider clientCredentialsTokens,
      @Qualifier("identityOutboundRestClientBuilder") RestClient.Builder rest) {
    this.properties = properties;
    this.clientCredentialsTokens = clientCredentialsTokens;
    this.rest = rest.clone().baseUrl(properties.baseUrl()).build();
  }

  void repair() {
    String token;
    try {
      token = clientCredentialsTokens.clientCredentialsToken();
    } catch (RuntimeException exception) {
      LOGGER.warn("Legacy Keycloak startup repair could not acquire its runtime credential");
      return;
    }

    for (String clientId : IdentityKeycloakProviderContract.mapperClientIds(properties)) {
      repairMapper(token, clientId);
    }
    for (String role : IdentityKeycloakProviderContract.IDENTITY_ROLES) {
      repairRole(token, role);
    }
  }

  private void repairMapper(String token, String clientId) {
    try {
      String clientUuid = exactClientUuid(token, clientId);
      if (clientUuid == null) {
        LOGGER.warn("Legacy Keycloak startup repair found no unique client named {}", clientId);
        return;
      }
      List<IdentityKeycloakProviderContract.ProtocolMapper> named = namedMappers(token, clientUuid);
      if (named.isEmpty()) {
        createMapper(token, clientUuid);
        return;
      }

      List<IdentityKeycloakProviderContract.ProtocolMapper> repairable =
          named.stream()
              .filter(mapper -> mapper.id() != null && !mapper.id().isBlank())
              .sorted(Comparator.comparing(IdentityKeycloakProviderContract.ProtocolMapper::id))
              .toList();
      if (repairable.isEmpty()) {
        LOGGER.warn(
            "Legacy Keycloak startup repair could not identify mapper definitions for client {}",
            clientId);
        return;
      }

      IdentityKeycloakProviderContract.ProtocolMapper retained = repairable.getFirst();
      if (!IdentityKeycloakProviderContract.isCanonical(retained)) {
        updateMapper(token, clientUuid, retained.id());
      }
      for (IdentityKeycloakProviderContract.ProtocolMapper duplicate :
          repairable.subList(1, repairable.size())) {
        deleteMapper(token, clientUuid, duplicate.id());
      }
    } catch (RestClientResponseException exception) {
      LOGGER.warn(
          "Legacy Keycloak startup repair for mapper client {} returned HTTP {}",
          clientId,
          exception.getStatusCode().value());
    } catch (RuntimeException exception) {
      LOGGER.warn("Legacy Keycloak startup repair for mapper client {} failed", clientId);
    }
  }

  private void repairRole(String token, String role) {
    try {
      String clientUuid = exactClientUuid(token, IdentityPermissions.CLIENT_ID);
      if (clientUuid == null) {
        LOGGER.warn(
            "Legacy Keycloak startup repair found no unique client named {}",
            IdentityPermissions.CLIENT_ID);
        return;
      }
      try {
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
                properties.realm(),
                clientUuid,
                role)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .toBodilessEntity();
      } catch (RestClientResponseException exception) {
        if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
          throw exception;
        }
        createRole(token, clientUuid, role);
      }
    } catch (RestClientResponseException exception) {
      LOGGER.warn(
          "Legacy Keycloak startup repair for Identity role {} returned HTTP {}",
          role,
          exception.getStatusCode().value());
    } catch (RuntimeException exception) {
      LOGGER.warn("Legacy Keycloak startup repair for Identity role {} failed", role);
    }
  }

  private String exactClientUuid(String token, String clientId) {
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
    return exact.size() == 1 ? exact.getFirst().id() : null;
  }

  private List<IdentityKeycloakProviderContract.ProtocolMapper> namedMappers(
      String token, String clientUuid) {
    IdentityKeycloakProviderContract.ProtocolMapper[] response =
        rest.get()
            .uri(
                "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
                properties.realm(),
                clientUuid)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .body(IdentityKeycloakProviderContract.ProtocolMapper[].class);
    return response == null
        ? List.of()
        : Arrays.stream(response)
            .filter(mapper -> IdentityKeycloakProviderContract.MAPPER_NAME.equals(mapper.name()))
            .toList();
  }

  private void createMapper(String token, String clientUuid) {
    try {
      rest.post()
          .uri(
              "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models",
              properties.realm(),
              clientUuid)
          .header(HttpHeaders.AUTHORIZATION, bearer(token))
          .body(IdentityKeycloakProviderContract.canonicalMapper())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() != HttpStatus.CONFLICT) {
        throw exception;
      }
      List<IdentityKeycloakProviderContract.ProtocolMapper> concurrent =
          namedMappers(token, clientUuid);
      if (concurrent.size() == 1
          && concurrent.getFirst().id() != null
          && !IdentityKeycloakProviderContract.isCanonical(concurrent.getFirst())) {
        updateMapper(token, clientUuid, concurrent.getFirst().id());
      }
    }
  }

  private void updateMapper(String token, String clientUuid, String mapperId) {
    rest.put()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models/{mapperId}",
            properties.realm(),
            clientUuid,
            mapperId)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .body(IdentityKeycloakProviderContract.canonicalMapper().withId(mapperId))
        .retrieve()
        .toBodilessEntity();
  }

  private void deleteMapper(String token, String clientUuid, String mapperId) {
    rest.delete()
        .uri(
            "/admin/realms/{realm}/clients/{clientUuid}/protocol-mappers/models/{mapperId}",
            properties.realm(),
            clientUuid,
            mapperId)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .retrieve()
        .toBodilessEntity();
  }

  private void createRole(String token, String clientUuid, String role) {
    try {
      rest.post()
          .uri("/admin/realms/{realm}/clients/{clientUuid}/roles", properties.realm(), clientUuid)
          .header(HttpHeaders.AUTHORIZATION, bearer(token))
          .body(new IdentityKeycloakProviderContract.RoleRepresentation(null, role))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() != HttpStatus.CONFLICT) {
        throw exception;
      }
      rest.get()
          .uri(
              "/admin/realms/{realm}/clients/{clientUuid}/roles/{role}",
              properties.realm(),
              clientUuid,
              role)
          .header(HttpHeaders.AUTHORIZATION, bearer(token))
          .retrieve()
          .toBodilessEntity();
    }
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
