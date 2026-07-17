package io.github.lutzseverino.cardo.identity.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum IdentityOperationStatus {
  REQUESTED("requested"),
  AWAITING_USER("awaiting_user"),
  COMPLETED("completed"),
  FAILED("failed");

  private final String wireValue;

  IdentityOperationStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator
  public static IdentityOperationStatus fromWireValue(String value) {
    return IdentityOperationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
