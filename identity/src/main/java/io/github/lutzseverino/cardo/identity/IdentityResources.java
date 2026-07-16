package io.github.lutzseverino.cardo.identity;

import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResourceType;
import java.util.List;

public final class IdentityResources {

  public static final AuthorizationResourceType USER =
      AuthorizationResourceType.of(
          IdentityPermissions.CLIENT_ID,
          "user",
          List.of(IdentityPermissions.READ, IdentityPermissions.WRITE));

  private IdentityResources() {}
}
