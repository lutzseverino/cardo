package com.odonta.authorization.grant;

import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.TargetableAuthorizationResource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GrantPlanBuilder {

  private final Map<ResourceKey, AuthorizationResource> resources = new LinkedHashMap<>();
  private final Map<ResourceGrantKey, Set<String>> resourceGrants = new LinkedHashMap<>();
  private final Map<AuthorityGrantKey, Set<String>> authorityGrants = new LinkedHashMap<>();

  GrantPlanBuilder() {}

  public GrantPlanBuilder provision(TargetableAuthorizationResource resource) {
    return provision(resource.toAuthorizationResource());
  }

  public GrantPlanBuilder provision(AuthorizationResource resource) {
    Objects.requireNonNull(resource, "resource");
    resources.merge(resourceKey(resource), resource, this::merge);
    return this;
  }

  public GrantPlanBuilder grantFullAccess(
      String subject, TargetableAuthorizationResource resource) {
    return grantFullAccess(subject, resource.toAuthorizationResource());
  }

  public GrantPlanBuilder grantFullAccess(String subject, AuthorizationResource resource) {
    return grantActions(subject, resource, resource.actions());
  }

  public GrantPlanBuilder grantActions(
      String subject, AuthorizationResource resource, List<String> actions) {
    provision(resource);
    ResourceGrantKey key =
        new ResourceGrantKey(resource.resourceServerClientId(), resource.name(), subject);
    resourceGrants.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(actions);
    return this;
  }

  public GrantPlanBuilder grantAuthorities(
      String subject, String resourceServerClientId, List<String> authorities) {
    AuthorityGrantKey key = new AuthorityGrantKey(resourceServerClientId, subject);
    authorityGrants.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(authorities);
    return this;
  }

  public GrantPlan build() {
    return new GrantPlan(
        List.copyOf(resources.values()),
        resourceGrants.entrySet().stream()
            .map(
                entry ->
                    new GrantPlan.ResourceGrant(
                        entry.getKey().resourceServerClientId(),
                        entry.getKey().resourceName(),
                        entry.getKey().subject(),
                        List.copyOf(entry.getValue())))
            .toList(),
        authorityGrants.entrySet().stream()
            .map(
                entry ->
                    new GrantPlan.AuthorityGrant(
                        entry.getKey().resourceServerClientId(),
                        entry.getKey().subject(),
                        List.copyOf(entry.getValue())))
            .toList());
  }

  private AuthorizationResource merge(
      AuthorizationResource existing, AuthorizationResource candidate) {
    if (!existing.type().equals(candidate.type())
        || !Objects.equals(existing.ownerSubject(), candidate.ownerSubject())) {
      throw new IllegalArgumentException(
          "Conflicting authorization resource definition: " + existing.name());
    }
    LinkedHashSet<String> actions = new LinkedHashSet<>(existing.actions());
    actions.addAll(candidate.actions());
    return new AuthorizationResource(
        existing.resourceServerClientId(),
        existing.name(),
        existing.type(),
        existing.ownerSubject(),
        List.copyOf(actions));
  }

  private ResourceKey resourceKey(AuthorizationResource resource) {
    return new ResourceKey(resource.resourceServerClientId(), resource.name());
  }

  private record ResourceKey(String resourceServerClientId, String resourceName) {}

  private record ResourceGrantKey(
      String resourceServerClientId, String resourceName, String subject) {}

  private record AuthorityGrantKey(String resourceServerClientId, String subject) {}
}
