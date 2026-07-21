package io.github.lutzseverino.cardo.invite.model;

public enum InvitationStatus {
  PENDING("pending"),
  ACCEPTED("accepted"),
  REVOKED("revoked");

  private final String wireValue;

  InvitationStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
