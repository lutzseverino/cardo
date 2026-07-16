package io.github.lutzseverino.cardo.authorization.grant;

import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.authorization.resource.TargetableAuthorizationResource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RevocationPlanBuilder {

  private final Map<ResourceRevocationKey, Set<String>> resourceRevocations = new LinkedHashMap<>();
  private final Map<AuthorityRevocationKey, Set<String>> authorityRevocations =
      new LinkedHashMap<>();

  RevocationPlanBuilder() {}

  public RevocationPlanBuilder revokeFullAccess(
      String subject, TargetableAuthorizationResource resource) {
    return revokeFullAccess(subject, resource.toAuthorizationResource());
  }

  public RevocationPlanBuilder revokeFullAccess(String subject, AuthorizationResource resource) {
    return revokeActions(subject, resource, resource.actions());
  }

  public RevocationPlanBuilder revokeActions(
      String subject, AuthorizationResource resource, List<String> actions) {
    ResourceRevocationKey key =
        new ResourceRevocationKey(resource.resourceServerClientId(), resource.name(), subject);
    resourceRevocations.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(actions);
    return this;
  }

  public RevocationPlanBuilder revokeAuthorities(
      String subject, String resourceServerClientId, List<String> authorities) {
    AuthorityRevocationKey key = new AuthorityRevocationKey(resourceServerClientId, subject);
    authorityRevocations.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(authorities);
    return this;
  }

  public RevocationPlan build() {
    return new RevocationPlan(
        resourceRevocations.entrySet().stream()
            .map(
                entry ->
                    new RevocationPlan.ResourceRevocation(
                        entry.getKey().resourceServerClientId(),
                        entry.getKey().resourceName(),
                        entry.getKey().subject(),
                        List.copyOf(entry.getValue())))
            .toList(),
        authorityRevocations.entrySet().stream()
            .map(
                entry ->
                    new RevocationPlan.AuthorityRevocation(
                        entry.getKey().resourceServerClientId(),
                        entry.getKey().subject(),
                        List.copyOf(entry.getValue())))
            .toList());
  }

  private record ResourceRevocationKey(
      String resourceServerClientId, String resourceName, String subject) {}

  private record AuthorityRevocationKey(String resourceServerClientId, String subject) {}
}
