package io.github.lutzseverino.cardo.authorization.keycloak;

import io.github.lutzseverino.cardo.authorization.spring.AuthorizationAuthorityNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String REALM_ACCESS = "realm_access";
  private static final String RESOURCE_ACCESS = "resource_access";
  private static final String AUTHORIZATION = "authorization";
  private static final String PERMISSIONS = "permissions";
  private static final String RESOURCE_NAME = "rsname";
  private static final String SCOPES = "scopes";
  private static final String ROLES = "roles";
  private static final String REALM_AUTHORITY_PREFIX = "realm:";

  @Override
  public @NonNull Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    addRealmRoles(jwt, authorities);
    addClientRoles(jwt, authorities);
    addAuthorizationPermissions(jwt, authorities);
    return authorities;
  }

  private void addRealmRoles(Jwt jwt, Collection<GrantedAuthority> authorities) {
    Object realmAccess = jwt.getClaim(REALM_ACCESS);
    if (realmAccess instanceof Map<?, ?> access) {
      addRoles(access.get(ROLES), REALM_AUTHORITY_PREFIX, authorities);
    }
  }

  private void addClientRoles(Jwt jwt, Collection<GrantedAuthority> authorities) {
    Object resourceAccess = jwt.getClaim(RESOURCE_ACCESS);
    if (!(resourceAccess instanceof Map<?, ?> resources)) {
      return;
    }
    resources.forEach(
        (clientId, access) -> {
          if (clientId instanceof String client && access instanceof Map<?, ?> clientAccess) {
            addRoles(clientAccess.get(ROLES), client + ":", authorities);
          }
        });
  }

  private void addRoles(
      Object roles, String authorityPrefix, Collection<GrantedAuthority> authorities) {
    if (!(roles instanceof Collection<?> roleNames)) {
      return;
    }
    roleNames.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(role -> authorityPrefix + role)
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);
  }

  private void addAuthorizationPermissions(Jwt jwt, Collection<GrantedAuthority> authorities) {
    Object authorization = jwt.getClaim(AUTHORIZATION);
    if (!(authorization instanceof Map<?, ?> authorizationClaims)) {
      return;
    }
    Object permissions = authorizationClaims.get(PERMISSIONS);
    if (!(permissions instanceof Collection<?> permissionClaims)) {
      return;
    }
    permissionClaims.stream()
        .filter(Map.class::isInstance)
        .map(permission -> (Map<?, ?>) permission)
        .forEach(permission -> addAuthorizationPermission(permission, authorities));
  }

  private void addAuthorizationPermission(
      Map<?, ?> permission, Collection<GrantedAuthority> authorities) {
    Object resourceName = permission.get(RESOURCE_NAME);
    Object scopes = permission.get(SCOPES);
    if (!(resourceName instanceof String name) || !(scopes instanceof Collection<?> scopeNames)) {
      return;
    }
    scopeNames.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(action -> AuthorizationAuthorityNames.resourceAction(name, action))
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);
  }
}
