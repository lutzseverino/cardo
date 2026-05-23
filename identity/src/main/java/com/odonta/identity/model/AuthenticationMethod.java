package com.odonta.identity.model;

public enum AuthenticationMethod {
  PASSWORD("password"),
  OIDC("oidc"),
  SAML("saml");

  private final String wireValue;

  AuthenticationMethod(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
