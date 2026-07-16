package io.github.lutzseverino.cardo.authorization.grant;

import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GrantPlan(
    List<AuthorizationResource> resources,
    List<ResourceGrant> resourceGrants,
    List<AuthorityGrant> authorityGrants) {

  public GrantPlan {
    resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    resourceGrants = List.copyOf(Objects.requireNonNull(resourceGrants, "resourceGrants"));
    authorityGrants = List.copyOf(Objects.requireNonNull(authorityGrants, "authorityGrants"));
    validate(resources, resourceGrants, authorityGrants);
  }

  public static GrantPlanBuilder builder() {
    return new GrantPlanBuilder();
  }

  public boolean isEmpty() {
    return resources.isEmpty() && resourceGrants.isEmpty() && authorityGrants.isEmpty();
  }

  public record ResourceGrant(
      String resourceServerClientId, String resourceName, String subject, List<String> actions) {

    public ResourceGrant {
      requireText(resourceServerClientId, "resourceServerClientId");
      requireText(resourceName, "resourceName");
      requireText(subject, "subject");
      actions = copyNonEmpty(actions, "actions", "action");
    }
  }

  public record AuthorityGrant(
      String resourceServerClientId, String subject, List<String> authorities) {

    public AuthorityGrant {
      requireText(resourceServerClientId, "resourceServerClientId");
      requireText(subject, "subject");
      authorities = copyNonEmpty(authorities, "authorities", "authority");
    }
  }

  private static List<String> copyNonEmpty(List<String> values, String name, String elementName) {
    List<String> copy = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(values, name)));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    copy.forEach(value -> requireText(value, elementName));
    return copy;
  }

  private static void validate(
      List<AuthorizationResource> resources,
      List<ResourceGrant> resourceGrants,
      List<AuthorityGrant> authorityGrants) {
    Set<ResourceKey> resourceKeys = new HashSet<>();
    for (AuthorizationResource resource : resources) {
      if (!resourceKeys.add(new ResourceKey(resource.resourceServerClientId(), resource.name()))) {
        throw new IllegalArgumentException(
            "Duplicate authorization resource definition: " + resource.name());
      }
    }
    Set<AuthorityGrantKey> authorityKeys = new HashSet<>();
    for (AuthorityGrant grant : authorityGrants) {
      if (!authorityKeys.add(
          new AuthorityGrantKey(grant.resourceServerClientId(), grant.subject()))) {
        throw new IllegalArgumentException(
            "Duplicate authority grant definition: " + grant.resourceServerClientId());
      }
    }
    Set<ResourceGrantKey> grantKeys = new HashSet<>();
    for (ResourceGrant grant : resourceGrants) {
      ResourceKey resourceKey =
          new ResourceKey(grant.resourceServerClientId(), grant.resourceName());
      if (!resourceKeys.contains(resourceKey)) {
        throw new IllegalArgumentException(
            "Resource grant has no resource definition: " + grant.resourceName());
      }
      if (!grantKeys.add(
          new ResourceGrantKey(
              grant.resourceServerClientId(), grant.resourceName(), grant.subject()))) {
        throw new IllegalArgumentException(
            "Duplicate resource grant definition: " + grant.resourceName());
      }
      AuthorizationResource resource =
          resources.stream()
              .filter(
                  candidate ->
                      candidate.resourceServerClientId().equals(grant.resourceServerClientId())
                          && candidate.name().equals(grant.resourceName()))
              .findFirst()
              .orElseThrow();
      if (!resource.actions().containsAll(grant.actions())) {
        throw new IllegalArgumentException(
            "Resource grant contains undefined actions: " + grant.resourceName());
      }
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private record ResourceKey(String resourceServerClientId, String resourceName) {}

  private record ResourceGrantKey(
      String resourceServerClientId, String resourceName, String subject) {}

  private record AuthorityGrantKey(String resourceServerClientId, String subject) {}
}
