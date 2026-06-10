package com.odonta.authorization.grant;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;
import java.util.Objects;

public record GrantPlan(
    List<AuthorizationResource> resources,
    List<ResourceGrant> resourceGrants,
    List<AuthorityGrant> authorityGrants) {

  public GrantPlan {
    resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
    resourceGrants = List.copyOf(Objects.requireNonNull(resourceGrants, "resourceGrants"));
    authorityGrants = List.copyOf(Objects.requireNonNull(authorityGrants, "authorityGrants"));
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
    List<String> copy = List.copyOf(Objects.requireNonNull(values, name));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    copy.forEach(value -> requireText(value, elementName));
    return copy;
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
