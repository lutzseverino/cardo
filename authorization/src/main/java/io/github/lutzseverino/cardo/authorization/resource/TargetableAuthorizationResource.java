package io.github.lutzseverino.cardo.authorization.resource;

import java.util.UUID;

public interface TargetableAuthorizationResource {

  AuthorizationResourceType authorizationResourceType();

  UUID authorizationResourceId();

  default String authorizationOwnerSubject() {
    return null;
  }

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
