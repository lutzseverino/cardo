package io.github.lutzseverino.cardo.authorization.keycloak;

public class KeycloakAuthorizationException extends RuntimeException {

  public KeycloakAuthorizationException(String message) {
    super(message);
  }
}
