package io.github.lutzseverino.cardo.invite.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum InvitationCompletionStatus {
  REQUESTED("requested"),
  AWAITING_IDENTITY("awaiting_identity"),
  COMPLETED("completed"),
  FAILED("failed");

  private final String wireValue;

  InvitationCompletionStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator
  public static InvitationCompletionStatus fromWireValue(String value) {
    return InvitationCompletionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
