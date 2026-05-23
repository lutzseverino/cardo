package com.odonta.authorization.spring;

public final class AuthorizationAuthorityNames {

  private AuthorizationAuthorityNames() {}

  public static String clientRole(String clientId, String permission) {
    return clientId + ":" + permission;
  }

  public static String resourceAction(String resourceName, String action) {
    return resourceName + ":" + action;
  }
}
