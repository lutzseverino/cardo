package com.odonta.authorization.resource;

import java.util.UUID;

public interface TargetableAuthorizationResource {

  AuthorizationResourceType authorizationResourceType();

  UUID authorizationResourceId();

  default String authorizationOwnerSubject() {
    return null;
  }

  void markAuthorizationSynced(String keycloakResourceId);

  void markAuthorizationSyncFailed(String message);

  default AuthorizationResource toAuthorizationResource() {
    AuthorizationResourceType type = authorizationResourceType();
    return new AuthorizationResource(
        type.product(),
        type.resourceName(authorizationResourceId()),
        type.typeName(),
        authorizationOwnerSubject(),
        type.actions());
  }
}
