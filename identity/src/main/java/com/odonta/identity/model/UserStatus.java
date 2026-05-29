package com.odonta.identity.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum UserStatus {
  INVITED("invited"),
  ACTIVE("active"),
  DISABLED("disabled");

  private final String wireValue;

  UserStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  public boolean isOperational() {
    return !INVITED.equals(this);
  }

  @JsonCreator
  public static UserStatus fromWireValue(String value) {
    return UserStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
