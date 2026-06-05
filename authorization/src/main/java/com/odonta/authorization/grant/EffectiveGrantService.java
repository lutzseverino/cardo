package com.odonta.authorization.grant;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.resource.AuthorizationResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class EffectiveGrantService {

  private final AuthorizationAdminClient authorization;

  public EffectiveGrantService(AuthorizationAdminClient authorization) {
    this.authorization = authorization;
  }

  public List<SubjectGrants> list(List<AuthorizationResourceType> resourceTypes, UUID resourceId) {
    Map<String, Map<GrantedResource, List<String>>> grantsBySubject = new LinkedHashMap<>();
    resourceTypesByProduct(resourceTypes)
        .forEach((product, types) -> list(product, types, resourceId, grantsBySubject));
    return grantsBySubject.entrySet().stream()
        .map(entry -> new SubjectGrants(entry.getKey(), grants(entry.getValue())))
        .toList();
  }

  public void revoke(
      List<AuthorizationResourceType> resourceTypes, UUID resourceId, String authorizationSubject) {
    resourceTypesByProduct(resourceTypes)
        .forEach((product, types) -> revoke(product, types, resourceId, authorizationSubject));
  }

  private void list(
      String product,
      List<AuthorizationResourceType> resourceTypes,
      UUID resourceId,
      Map<String, Map<GrantedResource, List<String>>> grantsBySubject) {
    resourceTypes.forEach(
        resourceType ->
            authorization
                .findResourceActionGrants(
                    ResourceGrantQuery.forResourceName(
                        product, resourceType.resourceName(resourceId)))
                .stream()
                .filter(GrantedResourceAction::granted)
                .forEach(
                    grant -> {
                      GrantedResource grantedResource =
                          new GrantedResource(resourceType.typeName(), resourceId.toString());
                      addGrant(
                          grantsBySubject,
                          grant.requesterSubject(),
                          grantedResource,
                          grant.action());
                    }));
  }

  private void revoke(
      String product,
      List<AuthorizationResourceType> resourceTypes,
      UUID resourceId,
      String authorizationSubject) {
    resourceTypes.forEach(
        resourceType ->
            authorization
                .findResourceActionGrants(
                    ResourceGrantQuery.forResourceName(
                        product, resourceType.resourceName(resourceId), authorizationSubject))
                .stream()
                .filter(GrantedResourceAction::granted)
                .map(GrantedResourceAction::id)
                .forEach(authorization::revokeResourceActionGrant));
  }

  private Map<String, List<AuthorizationResourceType>> resourceTypesByProduct(
      List<AuthorizationResourceType> resourceTypes) {
    return resourceTypes.stream()
        .collect(
            Collectors.groupingBy(
                AuthorizationResourceType::product, LinkedHashMap::new, Collectors.toList()));
  }

  private void addGrant(
      Map<String, Map<GrantedResource, List<String>>> grantsBySubject,
      String subject,
      GrantedResource resource,
      String action) {
    Map<GrantedResource, List<String>> actionsByResource =
        grantsBySubject.computeIfAbsent(subject, ignored -> new LinkedHashMap<>());
    List<String> actions =
        actionsByResource.computeIfAbsent(resource, ignored -> new ArrayList<>());
    if (!actions.contains(action)) {
      actions.add(action);
    }
  }

  private List<EffectiveGrant> grants(Map<GrantedResource, List<String>> actionsByResource) {
    return actionsByResource.entrySet().stream()
        .map(entry -> new EffectiveGrant(entry.getKey(), entry.getValue()))
        .toList();
  }
}
