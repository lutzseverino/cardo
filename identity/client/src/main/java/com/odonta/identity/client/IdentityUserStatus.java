package com.odonta.identity.client;

public enum IdentityUserStatus {
  INVITED("invited"),
  ACTIVE("active"),
  DISABLED("disabled");

  private final String wireValue;

  IdentityUserStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
