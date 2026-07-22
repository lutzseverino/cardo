package io.github.lutzseverino.cardo.authorization.spring;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

public class ResourcePermissionEvaluator implements PermissionEvaluator {

  private static final String ALL_RESOURCES = "*";

  @Override
  public boolean hasPermission(
      @Nullable Authentication authentication,
      @Nullable Object targetDomainObject,
      @Nullable Object permission) {
    return false;
  }

  @Override
  public boolean hasPermission(
      @Nullable Authentication authentication,
      @Nullable Serializable targetId,
      @Nullable String targetType,
      @Nullable Object permission) {
    if (authentication == null
        || targetId == null
        || targetType == null
        || targetType.isBlank()
        || !(permission instanceof String action)
        || action.isBlank()) {
      return false;
    }

    String resourceId = targetId.toString();
    String requiredAuthority =
        AuthorizationAuthorityNames.resourceAction(targetType + ":" + resourceId, action);
    String allResourcesAuthority =
        AuthorizationAuthorityNames.resourceAction(targetType + ":" + ALL_RESOURCES, action);

    return authentication.getAuthorities().stream()
        .anyMatch(
            authority ->
                requiredAuthority.equals(authority.getAuthority())
                    || allResourcesAuthority.equals(authority.getAuthority()));
  }
}
