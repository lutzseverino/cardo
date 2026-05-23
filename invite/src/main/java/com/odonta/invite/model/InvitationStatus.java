package com.odonta.invite.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum InvitationStatus {
  PENDING("pending"),
  ACCEPTED("accepted"),
  REVOKED("revoked");

  private final String wireValue;

  InvitationStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator
  public static InvitationStatus fromWireValue(String value) {
    return InvitationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
