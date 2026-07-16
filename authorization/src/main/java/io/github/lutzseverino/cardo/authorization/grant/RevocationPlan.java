package io.github.lutzseverino.cardo.authorization.grant;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RevocationPlan(
    List<ResourceRevocation> resourceRevocations, List<AuthorityRevocation> authorityRevocations) {

  public RevocationPlan {
    resourceRevocations =
        List.copyOf(Objects.requireNonNull(resourceRevocations, "resourceRevocations"));
    authorityRevocations =
        List.copyOf(Objects.requireNonNull(authorityRevocations, "authorityRevocations"));
    validate(resourceRevocations, authorityRevocations);
  }

  public static RevocationPlanBuilder builder() {
    return new RevocationPlanBuilder();
  }

  public boolean isEmpty() {
    return resourceRevocations.isEmpty() && authorityRevocations.isEmpty();
  }

  public record ResourceRevocation(
      String resourceServerClientId, String resourceName, String subject, List<String> actions) {

    public ResourceRevocation {
      requireText(resourceServerClientId, "resourceServerClientId");
      requireText(resourceName, "resourceName");
      requireText(subject, "subject");
      actions = copyNonEmpty(actions, "actions", "action");
    }
  }

  public record AuthorityRevocation(
      String resourceServerClientId, String subject, List<String> authorities) {

    public AuthorityRevocation {
      requireText(resourceServerClientId, "resourceServerClientId");
      requireText(subject, "subject");
      authorities = copyNonEmpty(authorities, "authorities", "authority");
    }
  }

  private static void validate(
      List<ResourceRevocation> resourceRevocations,
      List<AuthorityRevocation> authorityRevocations) {
    Set<ResourceRevocationKey> resourceKeys = new HashSet<>();
    for (ResourceRevocation revocation : resourceRevocations) {
      if (!resourceKeys.add(
          new ResourceRevocationKey(
              revocation.resourceServerClientId(),
              revocation.resourceName(),
              revocation.subject()))) {
        throw new IllegalArgumentException(
            "Duplicate resource revocation definition: " + revocation.resourceName());
      }
    }
    Set<AuthorityRevocationKey> authorityKeys = new HashSet<>();
    for (AuthorityRevocation revocation : authorityRevocations) {
      if (!authorityKeys.add(
          new AuthorityRevocationKey(revocation.resourceServerClientId(), revocation.subject()))) {
        throw new IllegalArgumentException(
            "Duplicate authority revocation definition: " + revocation.resourceServerClientId());
      }
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

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private record ResourceRevocationKey(
      String resourceServerClientId, String resourceName, String subject) {}

  private record AuthorityRevocationKey(String resourceServerClientId, String subject) {}
}
