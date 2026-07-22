package io.github.lutzseverino.cardo.authorization.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleRevocation;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResourceAction;
import io.github.lutzseverino.cardo.authorization.grant.ResourceActionAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ResourceGrantQuery;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.authorization.resource.CreatedAuthorizationResource;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class KeycloakAuthorizationClient implements AuthorizationAdminClient {

  private final String realm;
  private final KeycloakRealmAdminClient admin;
  private final RestClient rest;
  private final Supplier<String> protectionApiToken;

  public KeycloakAuthorizationClient(
      String baseUrl, String realm, RestClient.Builder rest, Supplier<String> protectionApiToken) {
    this.realm = realm;
    this.admin = new KeycloakRealmAdminClient(baseUrl, realm, rest, protectionApiToken);
    this.rest = rest.baseUrl(baseUrl).build();
    this.protectionApiToken = protectionApiToken;
  }

  @Override
  public CreatedAuthorizationResource ensureResource(AuthorizationResource authorizationResource) {
    Optional<ResourceSetResponse> existing = findResourceByName(authorizationResource.name());
    if (existing.isEmpty()) {
      try {
        return createResource(authorizationResource);
      } catch (RestClientResponseException exception) {
        if (!isConflict(exception)) {
          throw exception;
        }
        existing = findResourceByName(authorizationResource.name());
        if (existing.isEmpty()) {
          throw exception;
        }
      }
    }
    ResourceSetResponse resource = resource(existing.orElseThrow().id());
    LinkedHashSet<String> actions =
        resource.resourceScopes().stream()
            .map(ResourceScopeResponse::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (actions.addAll(authorizationResource.actions())) {
      updateResource(resource.id(), authorizationResource, List.copyOf(actions));
    }
    return new CreatedAuthorizationResource(resource.id(), resource.name());
  }

  private CreatedAuthorizationResource createResource(AuthorizationResource authorizationResource) {
    ResourceSetResponse resource =
        rest.post()
            .uri("/realms/{realm}/authz/protection/resource_set", realm)
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .body(
                new ResourceSetCreateRequest(
                    authorizationResource.name(),
                    authorizationResource.type(),
                    authorizationResource.ownerSubject(),
                    authorizationResource.actions()))
            .retrieve()
            .body(ResourceSetResponse.class);
    if (resource == null || resource.id() == null) {
      throw new KeycloakAuthorizationException(
          "Keycloak did not return an authorization resource.");
    }
    return new CreatedAuthorizationResource(resource.id(), resource.name());
  }

  private Optional<ResourceSetResponse> findResourceByName(String resourceName) {
    ResourceSetResponse[] resources =
        rest.get()
            .uri(
                uri ->
                    uri.path("/realms/{realm}/authz/protection/resource_set")
                        .queryParam("name", resourceName)
                        .queryParam("exactName", true)
                        .build(realm))
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(ResourceSetResponse[].class);
    if (resources == null) {
      return Optional.empty();
    }
    return Arrays.stream(resources)
        .filter(resource -> resourceName.equals(resource.name()))
        .findFirst();
  }

  private ResourceSetResponse resource(String resourceId) {
    ResourceSetResponse resource =
        rest.get()
            .uri("/realms/{realm}/authz/protection/resource_set/{resourceId}", realm, resourceId)
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(ResourceSetResponse.class);
    if (resource == null) {
      throw new KeycloakAuthorizationException(
          "Keycloak did not return an authorization resource.");
    }
    return resource;
  }

  private void updateResource(
      String resourceId, AuthorizationResource resource, List<String> actions) {
    rest.put()
        .uri("/realms/{realm}/authz/protection/resource_set/{resourceId}", realm, resourceId)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .body(
            new ResourceSetCreateRequest(
                resource.name(), resource.type(), resource.ownerSubject(), actions))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void grantResourceActions(ResourceActionAssignment assignment) {
    assignment
        .actions()
        .forEach(
            action ->
                rest.post()
                    .uri("/realms/{realm}/authz/protection/permission/ticket", realm)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .body(
                        new PermissionTicketCreateRequest(
                            assignment.resourceId(), assignment.requesterSubject(), action))
                    .retrieve()
                    .toBodilessEntity());
  }

  @Override
  public List<GrantedResourceAction> findResourceActionGrants(ResourceGrantQuery query) {
    String resourceId = query.resourceId();
    if (resourceId == null && query.resourceName() != null) {
      Optional<ResourceSetResponse> resource = findResourceByName(query.resourceName());
      if (resource.isEmpty()) {
        return List.of();
      }
      resourceId = resource.orElseThrow().id();
    }
    String resolvedResourceId = resourceId;
    PermissionTicketRepresentation[] tickets =
        rest.get()
            .uri(
                uri -> {
                  var builder =
                      uri.path("/realms/{realm}/authz/protection/permission/ticket")
                          .queryParam("returnNames", true);
                  if (resolvedResourceId != null) {
                    builder.queryParam("resourceId", resolvedResourceId);
                  }
                  if (query.requesterSubject() != null) {
                    builder.queryParam("requester", query.requesterSubject());
                  }
                  if (query.granted() != null) {
                    builder.queryParam("granted", query.granted());
                  }
                  return builder.build(realm);
                })
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(PermissionTicketRepresentation[].class);
    if (tickets == null) {
      return List.of();
    }
    return Arrays.stream(tickets).map(this::toGrantedResourceAction).toList();
  }

  @Override
  public void revokeResourceActionGrant(String ticketId) {
    rest.delete()
        .uri("/realms/{realm}/authz/protection/permission/ticket/{ticketId}", realm, ticketId)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void ensureClientRolesAssigned(ClientRoleAssignment assignment) {
    String clientUuid = clientUuid(assignment.resourceServerClientId());
    List<RoleRepresentation> roles =
        assignment.authorities().stream().map(roleName -> role(clientUuid, roleName)).toList();
    rest.post()
        .uri(
            "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
            realm,
            assignment.requesterSubject(),
            clientUuid)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .body(roles)
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void removeClientRoles(ClientRoleRevocation revocation) {
    String clientUuid = clientUuid(revocation.resourceServerClientId());
    List<RoleRepresentation> roles =
        clientRoleMappings(clientUuid, revocation.requesterSubject()).stream()
            .filter(role -> revocation.authorities().contains(role.name()))
            .toList();
    if (roles.isEmpty()) {
      return;
    }
    rest.method(HttpMethod.DELETE)
        .uri(
            "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
            realm,
            revocation.requesterSubject(),
            clientUuid)
        .header(HttpHeaders.AUTHORIZATION, authorization())
        .body(roles)
        .retrieve()
        .toBodilessEntity();
  }

  private List<RoleRepresentation> clientRoleMappings(String clientUuid, String subject) {
    RoleRepresentation[] roles =
        rest.get()
            .uri(
                "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
                realm,
                subject,
                clientUuid)
            .header(HttpHeaders.AUTHORIZATION, authorization())
            .retrieve()
            .body(RoleRepresentation[].class);
    return roles == null ? List.of() : List.of(roles);
  }

  private RoleRepresentation role(String clientUuid, String roleName) {
    try {
      return rest.get()
          .uri(
              "/admin/realms/{realm}/clients/{clientUuid}/roles/{roleName}",
              realm,
              clientUuid,
              roleName)
          .header(HttpHeaders.AUTHORIZATION, authorization())
          .retrieve()
          .body(RoleRepresentation.class);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        throw new KeycloakAuthorizationException(
            "Required Keycloak client role is missing: " + roleName);
      }
      throw exception;
    }
  }

  private boolean isConflict(RestClientResponseException exception) {
    return exception.getStatusCode().value() == 409;
  }

  private GrantedResourceAction toGrantedResourceAction(PermissionTicketRepresentation ticket) {
    return new GrantedResourceAction(
        ticket.id(),
        ticket.resource(),
        ticket.resourceName(),
        ticket.requester(),
        ticket.scopeName(),
        Boolean.TRUE.equals(ticket.granted()));
  }

  private String clientUuid(String clientId) {
    return admin.clientUuid(clientId);
  }

  private String authorization() {
    return bearer(protectionApiToken.get());
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private record RoleRepresentation(String id, String name) {}

  private record PermissionTicketCreateRequest(
      String resource, String requester, String scopeName) {

    @JsonProperty("granted")
    public boolean granted() {
      return true;
    }
  }

  private record PermissionTicketRepresentation(
      String id,
      String resource,
      String resourceName,
      String requester,
      Boolean granted,
      String scopeName) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record ResourceSetCreateRequest(
      String name,
      String type,
      String owner,
      @JsonProperty("resource_scopes") List<String> resourceScopes) {}

  private record ResourceSetResponse(
      @JsonProperty("_id") String id,
      String name,
      @JsonProperty("resource_scopes") List<ResourceScopeResponse> resourceScopes) {

    private ResourceSetResponse {
      resourceScopes = resourceScopes == null ? List.of() : List.copyOf(resourceScopes);
    }
  }

  private record ResourceScopeResponse(String name) {}
}
