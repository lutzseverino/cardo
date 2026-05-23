package com.odonta.authorization.resource;

import java.util.UUID;

public final class AuthorizationResourceNames {

  private AuthorizationResourceNames() {}

  public static String resource(String product, String resourceType, UUID resourceId) {
    return product + ":" + resourceType + ":" + resourceId;
  }

  public static String all(String product, String resourceType) {
    return product + ":" + resourceType + ":*";
  }
}
